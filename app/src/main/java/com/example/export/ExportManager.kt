package com.example.export

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.AbsenceRecord
import com.example.data.AuditLogEntry
import com.example.data.CollegeSettings
import com.example.data.ResidencyRiskLevel
import com.example.data.ResidencyStats
import com.example.util.DateUtil
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@JsonClass(generateAdapter = true)
data class DatabaseBackupPayload(
    val formatVersion: Int = 2,
    val exportedAt: Long = System.currentTimeMillis(),
    val studentName: String,
    val collegeName: String,
    val roomNumber: String,
    val academicYear: String,
    val academicYearStartDate: Long,
    val academicYearEndDate: Long,
    val records: List<AbsenceRecord>,
    val auditLogs: List<AuditLogEntry>
)

object ExportManager {

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    fun writeJsonToStream(
        settings: CollegeSettings,
        records: List<AbsenceRecord>,
        auditLogs: List<AuditLogEntry>,
        outputStream: OutputStream
    ) {
        val payload = DatabaseBackupPayload(
            studentName = settings.studentName,
            collegeName = settings.collegeName,
            roomNumber = settings.roomNumber,
            academicYear = settings.academicYear,
            academicYearStartDate = settings.academicYearStartDate,
            academicYearEndDate = settings.academicYearEndDate,
            records = records,
            auditLogs = auditLogs
        )

        val adapter = moshi.adapter(DatabaseBackupPayload::class.java).indent("  ")
        val jsonString = adapter.toJson(payload)
        outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(jsonString) }
    }

    fun exportDatabaseJson(
        context: Context,
        settings: CollegeSettings,
        records: List<AbsenceRecord>,
        auditLogs: List<AuditLogEntry>
    ): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ITALIAN).format(Date())
        val fileName = "backup_assenze_$timeStamp.json"
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val targetFile = File(exportDir, fileName)

        FileOutputStream(targetFile).use { out ->
            writeJsonToStream(settings, records, auditLogs, out)
        }
        return targetFile
    }

    fun importDatabaseJson(inputStream: InputStream): DatabaseBackupPayload? {
        return try {
            val jsonString = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val adapter = moshi.adapter(DatabaseBackupPayload::class.java)
            adapter.fromJson(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun writeCsvToStream(
        records: List<AbsenceRecord>,
        residencyStats: ResidencyStats?,
        outputStream: OutputStream
    ) {
        outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write("\uFEFF") // UTF-8 BOM for Excel

            residencyStats?.let { stats ->
                writer.write("# REPORT ASSENZE RESIDENZA (${DateUtil.formatShortDate(stats.academicYearStart)} - ${DateUtil.formatShortDate(stats.academicYearEnd)})\n")
                writer.write("# Giorni Utili: ${stats.totalUsefulDaysInYear} | Assenze Totali: ${stats.totalAbsencesCount}\n")
                writer.write("# Permanenza Minima Richiesta: > 56% | Assenze Residue: ${stats.remainingAbsencesBefore56} giorni\n\n")
            }

            writer.write("N,Data_Periodo,Tipo,Giustificata,Certificato_Medico,Note\n")

            for ((i, r) in records.withIndex()) {
                val line = buildString {
                    append(i + 1).append(",")
                    append(escapeCsv(r.formattedShortDate)).append(",")
                    append(escapeCsv(r.typeDisplayName)).append(",")
                    append(if (r.isJustified) "Sì" else "No").append(",")
                    append(escapeCsv(r.medicalCertificateNote ?: "")).append(",")
                    append(escapeCsv(r.reason)).append("\n")
                }
                writer.write(line)
            }
        }
    }

    fun exportToCsv(
        context: Context,
        records: List<AbsenceRecord>,
        residencyStats: ResidencyStats? = null
    ): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.ITALIAN).format(Date())
        val fileName = "report_assenze_$timeStamp.csv"
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val targetFile = File(exportDir, fileName)

        FileOutputStream(targetFile).use { out ->
            writeCsvToStream(records, residencyStats, out)
        }
        return targetFile
    }

    private fun escapeCsv(value: String): String {
        val containsSpecial = value.contains(",") || value.contains("\"") || value.contains("\n")
        return if (containsSpecial) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    // ---------------------------------------------------------------- PDF report

    private const val PAGE_W = 595f
    private const val PAGE_H = 842f
    private const val MARGIN = 42f
    private const val CONTENT_W = PAGE_W - 2 * MARGIN

    private val INK = Color.rgb(17, 24, 39)
    private val BODY = Color.rgb(55, 65, 81)
    private val MUTED = Color.rgb(107, 114, 128)
    private val RULE = Color.rgb(209, 213, 219)
    private val ZEBRA = Color.rgb(246, 244, 252)
    private val ACCENT = Color.rgb(91, 33, 182)
    private val ACCENT_SOFT = Color.rgb(237, 233, 254)
    private val BLUE = Color.rgb(29, 78, 216)
    private val GREEN = Color.rgb(4, 120, 87)
    private val AMBER = Color.rgb(180, 83, 9)
    private val RED = Color.rgb(185, 28, 28)

    private fun paint(size: Float, color: Int = BODY, bold: Boolean = false) = Paint().apply {
        isAntiAlias = true
        textSize = size
        this.color = color
        typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    /** Word-wraps [text] to [maxWidth], hard-breaking words that are longer than a line. */
    private fun wrap(text: String, p: Paint, maxWidth: Float, maxLines: Int = Int.MAX_VALUE): List<String> {
        val lines = mutableListOf<String>()
        for (paragraph in text.split('\n')) {
            var line = ""
            for (word in paragraph.split(' ').filter { it.isNotEmpty() }) {
                var w = word
                while (p.measureText(w) > maxWidth) {
                    if (line.isNotEmpty()) { lines += line; line = "" }
                    val n = p.breakText(w, true, maxWidth, null).coerceAtLeast(1)
                    lines += w.take(n); w = w.drop(n)
                }
                val candidate = if (line.isEmpty()) w else "$line $w"
                if (p.measureText(candidate) <= maxWidth) line = candidate else { lines += line; line = w }
            }
            if (line.isNotEmpty() || paragraph.isEmpty()) lines += line
        }
        if (lines.size <= maxLines) return lines
        val kept = lines.take(maxLines).toMutableList()
        var last = kept.last()
        while (last.isNotEmpty() && p.measureText("$last…") > maxWidth) last = last.dropLast(1)
        kept[kept.lastIndex] = "$last…"
        return kept
    }

    private class PdfCol(val title: String, val x: Float, val width: Float, val alignRight: Boolean = false)

    fun writePdfToStream(
        settings: CollegeSettings,
        records: List<AbsenceRecord>,
        residencyStats: ResidencyStats,
        outputStream: OutputStream
    ) {
        val doc = PdfDocument()
        val sorted = records.sortedWith(compareBy<AbsenceRecord> { it.dateMillis }.thenBy { it.entryNumber })

        val title = paint(20f, INK, bold = true)
        val subtitle = paint(11.5f, ACCENT, bold = true)
        val label = paint(8.5f, MUTED, bold = true)
        val value = paint(11f, INK, bold = true)
        val body = paint(10f, BODY)
        val bodyBold = paint(10f, INK, bold = true)
        val small = paint(8.5f, MUTED)
        val th = paint(8.5f, Color.WHITE, bold = true)
        val rule = Paint().apply { color = RULE; strokeWidth = 0.8f }
        val fill = Paint().apply { isAntiAlias = true }

        // Table layout: # | Periodo | Notti | Tipo | Giustificata | Note
        val cols = listOf(
            PdfCol("#", MARGIN + 8f, 22f),
            PdfCol("PERIODO", MARGIN + 32f, 150f),
            PdfCol("NOTTI", MARGIN + 186f, 34f, alignRight = true),
            PdfCol("TIPO", MARGIN + 232f, 84f),
            PdfCol("GIUSTIFICATA", MARGIN + 320f, 84f),
            PdfCol("NOTE", MARGIN + 408f, CONTENT_W - 408f - 8f)
        )
        val lineH = 13f
        val rowPad = 6f

        data class PdfRow(val cells: List<List<String>>, val justified: Boolean, val height: Float)

        val rows = sorted.mapIndexed { i, r ->
            val period = if (r.isRange) "${DateUtil.formatShortDate(r.dateMillis)} → ${DateUtil.formatShortDate(r.endDateMillis)}"
            else DateUtil.formatFullDate(r.dateMillis)
            val just = if (r.isJustified) {
                "Sì" + (r.medicalCertificateNote?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: " · certificato")
            } else "No"
            val cells = listOf(
                listOf("${i + 1}"),
                wrap(period, bodyBold, cols[1].width, 2),
                listOf("${r.daysCount}"),
                wrap(r.typeDisplayName, body, cols[3].width, 2),
                wrap(just, body, cols[4].width, 3),
                wrap(r.reason.ifBlank { "—" }, body, cols[5].width, 4)
            )
            PdfRow(cells, r.isJustified, cells.maxOf { it.size } * lineH + 2 * rowPad)
        }

        val note = "Giorni utili nell'anno: ${residencyStats.totalUsefulDaysInYear} (festività civili e chiusure escluse, Regolamento art. 11). " +
            "Per la conferma del posto serve una permanenza superiore al 56%, cioè almeno ${residencyStats.minRequiredPresenceDays} notti in residenza. " +
            "Le assenze con certificato medico non riducono la permanenza."
        val noteLines = wrap(note, small, CONTENT_W)
        val noteTop = 264f
        val listTitleBaseline = noteTop + noteLines.size * 11.5f + 16f

        // Paginate: the first page holds the summary, the others only the table.
        val firstTableTop = listTitleBaseline + 10f
        val otherTableTop = MARGIN + 46f
        val tableBottom = PAGE_H - MARGIN - 30f
        val headerH = 22f
        val pages = mutableListOf<MutableList<PdfRow>>(mutableListOf())
        var cursor = firstTableTop + headerH
        for (row in rows) {
            if (cursor + row.height > tableBottom && pages.last().isNotEmpty()) {
                pages += mutableListOf<PdfRow>(); cursor = otherTableTop + headerH
            }
            pages.last() += row; cursor += row.height
        }
        val totalPages = pages.size
        val generated = DateUtil.formatDateTime(System.currentTimeMillis())

        fun drawTableHeader(c: Canvas, top: Float) {
            fill.color = ACCENT
            c.drawRoundRect(RectF(MARGIN, top, PAGE_W - MARGIN, top + headerH), 5f, 5f, fill)
            cols.forEach { col ->
                val x = if (col.alignRight) col.x + col.width - th.measureText(col.title) else col.x
                c.drawText(col.title, x, top + 14.5f, th)
            }
        }

        fun drawFooter(c: Canvas, pageNo: Int) {
            val y = PAGE_H - MARGIN
            c.drawLine(MARGIN, y - 14f, PAGE_W - MARGIN, y - 14f, rule)
            c.drawText("${settings.collegeName} · Report assenze · generato il $generated", MARGIN, y, small)
            val pageText = "Pagina $pageNo di $totalPages"
            c.drawText(pageText, PAGE_W - MARGIN - small.measureText(pageText), y, small)
        }

        var counter = 0
        pages.forEachIndexed { pageIndex, pageRows ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W.toInt(), PAGE_H.toInt(), pageIndex + 1).create())
            val c = page.canvas
            fill.color = ACCENT
            c.drawRect(0f, 0f, PAGE_W, 5f, fill)

            var y: Float
            if (pageIndex == 0) {
                y = MARGIN + 14f
                c.drawText(settings.collegeName.ifBlank { "Residenza" }, MARGIN, y, title)
                y += 18f
                c.drawText("Report assenze e permanenza in residenza", MARGIN, y, subtitle)

                // Student details
                y += 22f
                fill.color = ACCENT_SOFT
                c.drawRoundRect(RectF(MARGIN, y, PAGE_W - MARGIN, y + 62f), 8f, 8f, fill)
                val info = listOf(
                    "STUDENTE" to settings.studentName.ifBlank { "—" },
                    "CAMERA" to settings.roomNumber.ifBlank { "—" },
                    "ANNO ACCADEMICO" to settings.academicYear.ifBlank { "—" },
                    "PERIODO" to "${DateUtil.formatShortDate(residencyStats.academicYearStart)} – ${DateUtil.formatShortDate(residencyStats.academicYearEnd)}"
                )
                val cellW = CONTENT_W / 2
                info.forEachIndexed { i, (l, v) ->
                    val cx = MARGIN + 14f + (i % 2) * cellW
                    val cy = y + 20f + (i / 2) * 26f
                    c.drawText(l, cx, cy - 8f, label)
                    c.drawText(wrap(v, value, cellW - 24f, 1).first(), cx, cy + 5f, value)
                }

                // KPI tiles
                y += 78f
                val riskColor = when (residencyStats.riskLevel) {
                    ResidencyRiskLevel.SAFE -> GREEN
                    ResidencyRiskLevel.WARNING -> AMBER
                    ResidencyRiskLevel.CRITICAL -> RED
                }
                val kpis = listOf(
                    Triple("ASSENZE CONTEGGIATE", "${residencyStats.totalAbsencesCount}", "su ${residencyStats.maxAllowedAbsenceDays} consentite") to INK,
                    Triple("ASSENZE RESIDUE", "${residencyStats.remainingAbsencesBefore56}", "prima del limite") to riskColor,
                    Triple("GIUSTIFICATE", "${residencyStats.justifiedAbsencesCount}", "con certificato, non contano") to BLUE,
                    Triple("PERMANENZA STIMATA", String.format(Locale.ITALIAN, "%.1f%%", residencyStats.projectedAnnualPresencePercentage), "minimo richiesto > 56%") to riskColor
                )
                val gap = 8f
                val tileW = (CONTENT_W - gap * 3) / 4
                kpis.forEachIndexed { i, (t, color) ->
                    val x = MARGIN + i * (tileW + gap)
                    val box = RectF(x, y, x + tileW, y + 72f)
                    fill.color = Color.WHITE
                    c.drawRoundRect(box, 8f, 8f, fill)
                    c.drawRoundRect(box, 8f, 8f, Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE; strokeWidth = 1f; this.color = RULE })
                    fill.color = color
                    c.drawRect(x, y + 8f, x + 3f, y + 64f, fill)
                    c.drawText(wrap(t.first, label, tileW - 18f, 1).first(), x + 11f, y + 18f, label)
                    c.drawText(t.second, x + 11f, y + 45f, paint(22f, color, bold = true))
                    c.drawText(wrap(t.third, small, tileW - 18f, 1).first(), x + 11f, y + 61f, small)
                }

                // Explanation
                y = noteTop
                noteLines.forEach { c.drawText(it, MARGIN, y, small); y += 11.5f }

                c.drawText(
                    "Elenco assenze (${sorted.size} registrazioni, ${sorted.sumOf { it.daysCount }} notti)",
                    MARGIN, listTitleBaseline, paint(12f, INK, bold = true)
                )
                y = firstTableTop
            } else {
                y = MARGIN + 14f
                c.drawText("Elenco assenze (continua)", MARGIN, y, paint(12f, INK, bold = true))
                c.drawText(settings.studentName, PAGE_W - MARGIN - small.measureText(settings.studentName), y, small)
                y = otherTableTop
            }

            drawTableHeader(c, y)
            y += headerH
            if (pageRows.isEmpty() && pageIndex == 0) {
                y += 22f
                val empty = "Nessuna assenza registrata nel periodo."
                c.drawText(empty, (PAGE_W - body.measureText(empty)) / 2, y, body)
            }
            pageRows.forEach { row ->
                if (counter % 2 == 1) {
                    fill.color = ZEBRA
                    c.drawRect(MARGIN, y, PAGE_W - MARGIN, y + row.height, fill)
                }
                row.cells.forEachIndexed { ci, lines ->
                    val col = cols[ci]
                    val p = when (ci) {
                        0 -> small
                        1 -> bodyBold
                        4 -> if (row.justified) paint(10f, BLUE, bold = true) else paint(10f, MUTED)
                        else -> body
                    }
                    lines.forEachIndexed { li, text ->
                        val baseline = y + rowPad + 10f + li * lineH
                        val x = if (col.alignRight) col.x + col.width - p.measureText(text) else col.x
                        c.drawText(text, x, baseline, p)
                    }
                }
                y += row.height
                c.drawLine(MARGIN, y, PAGE_W - MARGIN, y, rule)
                counter++
            }

            drawFooter(c, pageIndex + 1)
            doc.finishPage(page)
        }

        doc.writeTo(outputStream)
        doc.close()
    }

    fun exportToPdf(
        context: Context,
        settings: CollegeSettings,
        records: List<AbsenceRecord>,
        residencyStats: ResidencyStats
    ): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.ITALIAN).format(Date())
        val fileName = "report_assenze_$timeStamp.pdf"
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val targetFile = File(exportDir, fileName)

        FileOutputStream(targetFile).use { out ->
            writePdfToStream(settings, records, residencyStats, out)
        }
        return targetFile
    }

    fun shareExportedFile(context: Context, file: File, mimeType: String, chooserTitle: String) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            putExtra(Intent.EXTRA_TEXT, "Report Assenze - ${file.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(intent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
