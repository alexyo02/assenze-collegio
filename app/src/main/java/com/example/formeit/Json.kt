package com.example.formeit

/**
 * Lenient view over an untyped JSON object decoded by Moshi.
 *
 * The FormeIt backend is consumed by its official client as loosely typed JavaScript:
 * the same field can arrive as a number or as a numeric string, and new fields appear
 * without notice. Reading through this wrapper keeps the beta tolerant to those variations
 * instead of failing a whole screen on a single unexpected type.
 */
class JObj(val raw: Map<String, Any?>) {

    fun has(key: String): Boolean = raw[key] != null

    fun str(key: String): String? = when (val v = raw[key]) {
        null -> null
        is String -> v
        is Double -> if (v % 1.0 == 0.0 && kotlin.math.abs(v) < 1e15) v.toLong().toString() else v.toString()
        is Number -> v.toString()
        is Boolean -> v.toString()
        else -> v.toString()
    }

    fun text(key: String): String = str(key)?.trim().orEmpty()

    fun long(key: String): Long? = when (val v = raw[key]) {
        null -> null
        is Number -> v.toLong()
        is String -> v.trim().toDoubleOrNull()?.toLong()
        is Boolean -> if (v) 1L else 0L
        else -> null
    }

    fun int(key: String): Int? = long(key)?.toInt()

    fun bool(key: String): Boolean? = when (val v = raw[key]) {
        null -> null
        is Boolean -> v
        is Number -> v.toInt() != 0
        is String -> when (v.trim().lowercase()) {
            "true", "1", "si", "sì", "s", "y", "yes" -> true
            "false", "0", "no", "n" -> false
            else -> null
        }
        else -> null
    }

    @Suppress("UNCHECKED_CAST")
    fun obj(key: String): JObj? = (raw[key] as? Map<String, Any?>)?.let(::JObj)

    fun list(key: String): List<JObj> = asObjList(raw[key])

    /** Shallow copy with extra/overridden fields (mirrors the `{...item, extra}` spreads of the official client). */
    fun with(vararg pairs: Pair<String, Any?>): JObj = JObj(raw + pairs)

    override fun toString(): String = "JObj(keys=${raw.keys})"

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun asObjList(value: Any?): List<JObj> =
            (value as? List<*>)?.mapNotNull { (it as? Map<String, Any?>)?.let(::JObj) } ?: emptyList()

        @Suppress("UNCHECKED_CAST")
        fun asObj(value: Any?): JObj? = (value as? Map<String, Any?>)?.let(::JObj)
    }
}
