package com.example.formeit

import com.example.BuildConfig
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Base URLs used by the official FormeIt Residenze client (environment module of its web bundle). */
object FormeitEndpoints {
    const val API = "https://api-residenze.meitsrl.it/formeitAPI/"
    const val UTENTI = "https://residenze.meitsrl.it/api/utenti/"
    const val REPOSITORY = "https://api-residenze.meitsrl.it/formeit/download?nomefile="
    const val CALENDAR = "https://formeit.meitsrl.it/api/calendar/"
    const val DOWNLOAD_TOKEN = "XYZ"
}

/** A failure reported to the user as-is (server `errormessage`, or a readable network message). */
class FormeitException(message: String, val isNetwork: Boolean = false) : Exception(message)

/** First login with the initial key: the password must be set in the official app first. */
class FormeitFirstAccessException : Exception(
    "Primo accesso: completa il cambio password nell'app ufficiale FormeIt, poi accedi qui con la nuova password."
)

/**
 * Thin HTTP layer reproducing the official client's calls: same URLs, same JSON bodies,
 * same shared Basic authorization header. Every call runs on [Dispatchers.IO].
 */
class FormeitClient(
    private val http: OkHttpClient = defaultHttp(),
    moshi: Moshi = Moshi.Builder().build()
) {
    private val anyAdapter = moshi.adapter(Any::class.java)
    private val authHeader = Credentials.basic(BuildConfig.FORMEIT_API_USER, BuildConfig.FORMEIT_API_PASSWORD)
    private val jsonType = "application/json".toMediaType()

    val isConfigured: Boolean
        get() = BuildConfig.FORMEIT_API_USER.isNotBlank() && BuildConfig.FORMEIT_API_PASSWORD.isNotBlank()

    fun url(base: String, path: String, query: Map<String, String> = emptyMap()): HttpUrl {
        val b = (base + path).toHttpUrl().newBuilder()
        // The official client interpolates filter strings straight into the URL; the browser's
        // URL parser drops tab/newline characters and percent-encodes the rest. Reproduce that.
        query.forEach { (k, v) -> b.addQueryParameter(k, v.replace(Regex("[\\t\\r\\n]"), "")) }
        return b.build()
    }

    suspend fun get(url: HttpUrl): Any? = execute(
        Request.Builder().url(url).get().header("Content-Type", "application/json"),
        retryOnNetworkError = true
    )

    suspend fun post(url: HttpUrl, body: Any?): Any? = execute(
        Request.Builder().url(url).post(jsonBody(body)),
        retryOnNetworkError = false
    )

    suspend fun put(url: HttpUrl, body: Any?): Any? = execute(
        Request.Builder().url(url).put(jsonBody(body)),
        retryOnNetworkError = false
    )

    suspend fun postMultipart(url: HttpUrl, body: MultipartBody): Any? = execute(
        Request.Builder().url(url).post(body),
        retryOnNetworkError = false
    )

    /** Downloads a repository file (documents, menu, ticket attachments) into [target]. */
    suspend fun download(url: HttpUrl, target: File) = withContext(Dispatchers.IO) {
        // The official client fetches repository files without Authorization, with the token query param.
        val request = Request.Builder().url(url).get().build()
        try {
            http.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw FormeitException("Download non riuscito (HTTP ${resp.code}).")
                val body = resp.body ?: throw FormeitException("Download vuoto.")
                target.parentFile?.mkdirs()
                val tmp = File(target.parentFile, target.name + ".part")
                tmp.outputStream().use { out -> body.byteStream().copyTo(out) }
                if (!tmp.renameTo(target)) {
                    tmp.copyTo(target, overwrite = true); tmp.delete()
                }
            }
        } catch (e: IOException) {
            throw FormeitException("Connessione non riuscita durante il download. Riprova.", isNetwork = true)
        }
    }

    private fun jsonBody(body: Any?): RequestBody =
        (if (body == null) "null" else anyAdapter.toJson(normalize(body))).toRequestBody(jsonType)

    /**
     * Moshi decodes every JSON number as Double; echoing an id back would serialize `3` as `3.0`.
     * JavaScript (the official client) writes integral numbers without a fraction: do the same.
     */
    private fun normalize(v: Any?): Any? = when (v) {
        is Double -> if (v % 1.0 == 0.0 && kotlin.math.abs(v) < 9.0e15) v.toLong() else v
        is Float -> if (v % 1f == 0f) v.toLong() else v
        is Map<*, *> -> LinkedHashMap<String, Any?>().apply { v.forEach { (k, x) -> put(k.toString(), normalize(x)) } }
        is List<*> -> v.map { normalize(it) }
        else -> v
    }

    private suspend fun execute(builder: Request.Builder, retryOnNetworkError: Boolean): Any? =
        withContext(Dispatchers.IO) {
            if (!isConfigured) throw FormeitException("Credenziali di servizio FormeIt non configurate nella build.")
            val request = builder.header("Authorization", authHeader).build()
            var attempt = 0
            while (true) {
                attempt++
                try {
                    return@withContext http.newCall(request).execute().use { parse(it) }
                } catch (e: IOException) {
                    if (retryOnNetworkError && attempt < 2) continue
                    throw FormeitException(
                        "Server FormeIt non raggiungibile. Verifica la connessione e riprova.",
                        isNetwork = true
                    )
                }
            }
            @Suppress("UNREACHABLE_CODE") null
        }

    private fun parse(resp: Response): Any? {
        val text = resp.body?.string().orEmpty()
        val parsed: Any? = try {
            if (text.isBlank()) null else anyAdapter.fromJson(text)
        } catch (_: Exception) {
            text // some endpoints answer with plain "OK"/"NOK"
        }
        if (BuildConfig.DEBUG) logShape(resp, parsed)
        if (!resp.isSuccessful) {
            val msg = JObj.asObj(parsed)?.str("errormessage")
                ?: JObj.asObj(parsed)?.str("error")
                ?: "Errore HTTP ${resp.code}"
            throw FormeitException(msg)
        }
        return parsed
    }

    /** Debug builds only: logs the response *shape* (keys and value types), never values. */
    private fun logShape(resp: Response, parsed: Any?) {
        fun shape(v: Any?, depth: Int): String = when {
            v == null -> "null"
            v is Map<*, *> -> if (depth > 2) "{…}" else v.entries.joinToString(prefix = "{", postfix = "}") { (k, x) -> "$k:${shape(x, depth + 1)}" }
            v is List<*> -> "[${v.size}]" + (v.firstOrNull()?.let { shape(it, depth + 1) } ?: "")
            v is String -> "str"
            v is Number -> "num"
            v is Boolean -> "bool"
            else -> v.javaClass.simpleName
        }
        val ret = JObj.asObj(parsed)?.str("retcode")
        android.util.Log.d("FormeitBeta", "${resp.request.method} ${resp.request.url.encodedPath} -> ${resp.code} retcode=$ret shape=${shape(parsed, 0).take(3000)}")
    }

    companion object {
        fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        /** Server envelope `{retcode, errormessage, ...}`: throw on anything but OK. */
        fun requireOk(response: Any?, fallback: String): JObj {
            val obj = JObj.asObj(response) ?: throw FormeitException(fallback)
            if (obj.str("retcode") != "OK") {
                throw FormeitException(obj.str("errormessage")?.takeIf { it.isNotBlank() } ?: fallback)
            }
            return obj
        }
    }
}
