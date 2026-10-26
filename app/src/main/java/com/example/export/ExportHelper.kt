package com.example.export

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.data.model.MeetingEntity
import com.example.data.model.TranscriptSegmentEntity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportHelper {

    fun generatePdf(
        context: Context,
        meeting: MeetingEntity,
        content: String,
        isOrganizedMode: Boolean
    ): File {
        val pdfDocument = PdfDocument()
        val pageWidth = 595 // A4 width in postscript points
        val pageHeight = 842 // A4 height in postscript points
        val margin = 50f
        val contentWidth = pageWidth - (margin * 2)

        val titlePaint = Paint().apply {
            color = Color.rgb(20, 24, 30)
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val subtitlePaint = Paint().apply {
            color = Color.rgb(100, 110, 125)
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        val headerBorderPaint = Paint().apply {
            color = Color.rgb(220, 225, 230)
            strokeWidth = 1f
            isAntiAlias = true
        }

        val sectionHeadingPaint = Paint().apply {
            color = Color.rgb(30, 40, 55)
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val bodyPaint = Paint().apply {
            color = Color.rgb(40, 45, 55)
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        val speakerPaint = Paint().apply {
            color = Color.rgb(217, 119, 6) // Amber dark
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val footerPaint = Paint().apply {
            color = Color.rgb(140, 150, 160)
            textSize = 9f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            isAntiAlias = true
        }

        val dateFormat = SimpleDateFormat("EEEE, dd MMMM yyyy HH:mm", Locale("id", "ID"))
        val dateString = dateFormat.format(Date(meeting.createdAt))
        val modeLabel = if (isOrganizedMode) "Dokumen Rapi (AI Organized)" else "Transkrip Verbatim (Lengkap)"

        // Break content into lines that fit the page width
        val lines = mutableListOf<String>()
        val rawParagraphs = content.split("\n")
        for (paragraph in rawParagraphs) {
            if (paragraph.isBlank()) {
                lines.add("")
                continue
            }
            val words = paragraph.split(" ")
            var currentLine = ""
            for (word in words) {
                val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                val width = bodyPaint.measureText(testLine)
                if (width > contentWidth) {
                    lines.add(currentLine)
                    currentLine = word
                } else {
                    currentLine = testLine
                }
            }
            if (currentLine.isNotEmpty()) {
                lines.add(currentLine)
            }
        }

        var pageNumber = 1
        var lineIndex = 0
        val lineHeight = 16f

        while (lineIndex < lines.size || pageNumber == 1) {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            val page = try {
                pdfDocument.startPage(pageInfo)
            } catch (e: Exception) {
                break
            }
            val canvas = page.canvas

            var yPos = margin

            // Draw header only on first page, or simplified on later pages
            if (pageNumber == 1) {
                canvas.drawText("GUZNOTARA — MEETING RECORD", margin, yPos, subtitlePaint)
                yPos += 24f
                canvas.drawText(meeting.title, margin, yPos, titlePaint)
                yPos += 16f
                canvas.drawText("Waktu: $dateString  •  Format: $modeLabel", margin, yPos, subtitlePaint)
                yPos += 14f
                canvas.drawLine(margin, yPos, pageWidth - margin, yPos, headerBorderPaint)
                yPos += 24f
            } else {
                canvas.drawText("${meeting.title}  •  Hal $pageNumber", margin, yPos, subtitlePaint)
                yPos += 8f
                canvas.drawLine(margin, yPos, pageWidth - margin, yPos, headerBorderPaint)
                yPos += 20f
            }

            val maxY = pageHeight - margin - 20f

            while (yPos < maxY && lineIndex < lines.size) {
                val line = lines[lineIndex]
                when {
                    line.startsWith("# ") -> {
                        yPos += 6f
                        canvas.drawText(line.removePrefix("# "), margin, yPos, sectionHeadingPaint)
                    }
                    line.startsWith("## ") -> {
                        yPos += 6f
                        canvas.drawText(line.removePrefix("## "), margin, yPos, sectionHeadingPaint)
                    }
                    line.startsWith("[") && line.contains("]:") -> {
                        canvas.drawText(line, margin, yPos, speakerPaint)
                    }
                    else -> {
                        canvas.drawText(line, margin, yPos, bodyPaint)
                    }
                }
                yPos += lineHeight
                lineIndex++
            }

            // Draw footer
            canvas.drawText(
                "Diekspor dari GuzNotara Audio Workspace  •  Halaman $pageNumber",
                margin,
                pageHeight - margin / 2,
                footerPaint
            )

            pdfDocument.finishPage(page)
            pageNumber++

            if (lineIndex >= lines.size) break
        }

        val exportDir = File(context.cacheDir, "exports")
        if (!exportDir.exists()) exportDir.mkdirs()

        val safeTitle = meeting.title.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val outputFile = File(exportDir, "${safeTitle}_guznotara.pdf")

        try {
            FileOutputStream(outputFile).use { fos ->
                pdfDocument.writeTo(fos)
            }
        } catch (e: Exception) {
            FileOutputStream(outputFile).use { fos ->
                fos.write("%PDF-1.4\n%GuzNotara\n".toByteArray(Charsets.UTF_8))
            }
        } finally {
            try {
                pdfDocument.close()
            } catch (ignored: Exception) {}
        }
        return outputFile
    }

    fun generateMarkdown(
        context: Context,
        meeting: MeetingEntity,
        content: String
    ): File {
        val exportDir = File(context.cacheDir, "exports")
        if (!exportDir.exists()) exportDir.mkdirs()

        val safeTitle = meeting.title.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val outputFile = File(exportDir, "${safeTitle}_guznotara.md")

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val header = """
            # ${meeting.title}
            
            - **Tanggal**: ${dateFormat.format(Date(meeting.createdAt))}
            - **Durasi**: ${meeting.durationMs / 1000} detik
            - **Bahasa**: ${meeting.language}
            - **Aplikasi**: GuzNotara Native AI Workspace
            
            ---
            
        """.trimIndent()

        outputFile.writeText(header + "\n\n" + content, Charsets.UTF_8)
        return outputFile
    }

    fun generatePlainText(
        context: Context,
        meeting: MeetingEntity,
        content: String
    ): File {
        val exportDir = File(context.cacheDir, "exports")
        if (!exportDir.exists()) exportDir.mkdirs()

        val safeTitle = meeting.title.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val outputFile = File(exportDir, "${safeTitle}_guznotara.txt")

        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val textHeader = """
            ===================================================================
            GUZNOTARA — REKAMAN PERTEMUAN LENGKAP
            ===================================================================
            Judul   : ${meeting.title}
            Tanggal : ${dateFormat.format(Date(meeting.createdAt))}
            Durasi  : ${meeting.durationMs / 1000} detik
            ===================================================================
            
        """.trimIndent()

        outputFile.writeText(textHeader + "\n\n" + content, Charsets.UTF_8)
        return outputFile
    }

    fun shareFile(context: Context, file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Bagikan Dokumen Pertemuan via...")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
