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

    fun writePdfToStream(
        settings: CollegeSettings,
        records: List<AbsenceRecord>,
        residencyStats: ResidencyStats,
        outputStream: OutputStream
    ) {
        val pdfDocument = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842

        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val violetPrimary = Paint().apply {
            color = Color.rgb(124, 58, 237)
            isAntiAlias = true
        }

        val textDarkPaint = Paint().apply {
            color = Color.rgb(30, 27, 46)
            isAntiAlias = true
            textSize = 9.5f
        }

        val textMutedPaint = Paint().apply {
            color = Color.rgb(100, 116, 139)
            isAntiAlias = true
            textSize = 8.5f
        }

        val titlePaint = Paint().apply {
            color = Color.rgb(124, 58, 237)
            isAntiAlias = true
            textSize = 17f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val headerCardPaint = Paint().apply {
            color = Color.rgb(243, 232, 255)
            isAntiAlias = true
        }

        val linePaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1f
        }

        var y = 38f

        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 6f, violetPrimary)

        y += 10f
        canvas.drawText(settings.collegeName, 40f, y, titlePaint)
        y += 16f
        canvas.drawText("Report Assenze & Riepilogo Permanenza", 40f, y, Paint().apply {
            color = Color.rgb(71, 85, 105)
            textSize = 10.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        })

        val reportDate = DateUtil.formatDateTime(System.currentTimeMillis())
        canvas.drawText("Generato il $reportDate", 40f, y + 13f, textMutedPaint)

        y += 24f

        val summaryCard = RectF(40f, y, (pageWidth - 40).toFloat(), y + 64f)
        canvas.drawRoundRect(summaryCard, 10f, 10f, headerCardPaint)

        val boldLabelPaint = Paint().apply {
            color = Color.rgb(30, 27, 46)
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        canvas.drawText("Studente: ${settings.studentName}", 52f, y + 18f, boldLabelPaint)
        canvas.drawText("Camera: ${settings.roomNumber} • Anno: ${settings.academicYear}", 52f, y + 34f, textDarkPaint)
        canvas.drawText("Decorrenza: ${DateUtil.formatShortDate(settings.academicYearStartDate)} al 30/09", 52f, y + 50f, textMutedPaint)

        canvas.drawText("Assenze Totali: ${residencyStats.totalAbsencesCount} gg", 320f, y + 18f, boldLabelPaint)
        if (residencyStats.justifiedAbsencesCount > 0) {
            canvas.drawText("Di cui con certificato medico: ${residencyStats.justifiedAbsencesCount} gg", 320f, y + 34f, Paint().apply {
                color = Color.rgb(37, 99, 235)
                textSize = 9f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            })
        }
        canvas.drawText("Permanenza stimata: ${String.format(Locale.US, "%.1f%%", residencyStats.projectedAnnualPresencePercentage)} (Minimo richiesto: > 56%)", 320f, y + 50f, textDarkPaint)

        y += 78f

        val thPaint = Paint().apply {
            color = Color.rgb(124, 58, 237)
            isAntiAlias = true
        }
        val thTextPaint = Paint().apply {
            color = Color.WHITE
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val tableHeaderRect = RectF(40f, y, (pageWidth - 40).toFloat(), y + 19f)
        canvas.drawRoundRect(tableHeaderRect, 6f, 6f, thPaint)

        // Well-proportioned, non-overlapping column positions:
        // Col 1: # (46f)
        // Col 2: DATA / PERIODO (70f .. 255f)
        // Col 3: TIPO (258f .. 345f)
        // Col 4: CERTIFICATO (348f .. 428f)
        // Col 5: NOTE (432f .. 552f)
        canvas.drawText("#", 46f, y + 13f, thTextPaint)
        canvas.drawText("DATA / PERIODO", 70f, y + 13f, thTextPaint)
        canvas.drawText("TIPO", 258f, y + 13f, thTextPaint)
        canvas.drawText("CERTIFICATO", 348f, y + 13f, thTextPaint)
        canvas.drawText("NOTE", 432f, y + 13f, thTextPaint)

        y += 24f

        val zebraPaint = Paint().apply {
            color = Color.rgb(250, 245, 255)
            isAntiAlias = true
        }

        val dateCellPaint = Paint().apply {
            color = Color.rgb(30, 27, 46)
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val bodyCellPaint = Paint().apply {
            color = Color.rgb(51, 65, 85)
            textSize = 8.5f
            isAntiAlias = true
        }

        val maxRows = 28
        val displayedRecords = records.take(maxRows)

        for ((index, record) in displayedRecords.withIndex()) {
            val rowY = y + 13f
            if (index % 2 == 1) {
                canvas.drawRect(40f, y, (pageWidth - 40).toFloat(), y + 17f, zebraPaint)
            }

            canvas.drawText("${index + 1}", 46f, rowY, textMutedPaint)
            canvas.drawText(record.formattedShortDate, 70f, rowY, dateCellPaint)
            canvas.drawText(record.typeDisplayName, 258f, rowY, bodyCellPaint)

            val justText = if (record.isJustified) {
                if (!record.medicalCertificateNote.isNullOrBlank()) "Sì (${record.medicalCertificateNote})" else "Sì (Medico)"
            } else "No"

            val justColor = if (record.isJustified) Color.rgb(37, 99, 235) else Color.rgb(148, 163, 184)
            canvas.drawText(justText, 348f, rowY, Paint().apply {
                color = justColor
                textSize = 8.5f
                typeface = if (record.isJustified) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
                isAntiAlias = true
            })

            val note = if (record.reason.isNotBlank()) record.reason else "-"
            val truncatedNote = if (note.length > 22) note.take(20) + "..." else note
            canvas.drawText(truncatedNote, 432f, rowY, bodyCellPaint)

            canvas.drawLine(40f, y + 17f, (pageWidth - 40).toFloat(), y + 17f, linePaint)
            y += 18f
        }

        if (records.size > maxRows) {
            y += 8f
            canvas.drawText("... e altre ${records.size - maxRows} assenze registrate.", 50f, y + 10f, textMutedPaint)
        } else if (records.isEmpty()) {
            y += 22f
            canvas.drawText("Nessuna assenza registrata nel periodo.", 210f, y, textMutedPaint)
        }

        val footerY = (pageHeight - 40).toFloat()
        canvas.drawLine(40f, footerY - 10f, (pageWidth - 40).toFloat(), footerY - 10f, linePaint)
        canvas.drawText("Report assenze • ${settings.collegeName}", 40f, footerY + 4f, textMutedPaint)

        pdfDocument.finishPage(page)
        pdfDocument.writeTo(outputStream)
        pdfDocument.close()
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
