package com.shopmanager.app.reports

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.shopmanager.app.util.asDateTime
import java.io.ByteArrayOutputStream
import java.io.File

enum class ExportFormat(val label: String, val ext: String, val mime: String) {
    PDF("PDF", "pdf", "application/pdf"),
    EXCEL("Excel (CSV)", "csv", "text/csv")
}

/** Result of saving a file: where it is and a Uri that can be opened. */
data class SavedFile(val uri: Uri, val location: String, val mime: String)

object ReportExporter {

    fun bytes(report: Report, format: ExportFormat, storeName: String): ByteArray = when (format) {
        ExportFormat.EXCEL -> csv(report, storeName)
        ExportFormat.PDF -> pdf(report, storeName)
    }

    // ---------------- CSV (opens in Excel / Google Sheets) ----------------

    private fun esc(v: String): String =
        if (v.any { it == ',' || it == '"' || it == '\n' }) "\"" + v.replace("\"", "\"\"") + "\"" else v

    private fun csv(r: Report, storeName: String): ByteArray {
        val sb = StringBuilder()
        sb.append(esc(storeName.ifBlank { "Shop Manager" })).append('\n')
        sb.append(esc(r.title)).append('\n')
        sb.append(esc(r.period)).append('\n')
        sb.append(esc("Generated: " + System.currentTimeMillis().asDateTime())).append("\n\n")
        r.summary.forEach { (k, v) -> sb.append(esc(k)).append(',').append(esc(v)).append('\n') }
        sb.append('\n')
        sb.append(r.columns.joinToString(",") { esc(it.title) }).append('\n')
        r.rows.forEach { row -> sb.append(row.joinToString(",") { esc(it) }).append('\n') }
        r.totals?.let { sb.append(it.joinToString(",") { v -> esc(v) }).append('\n') }
        // UTF-8 BOM so Excel shows ₹ and other symbols correctly
        return byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + sb.toString().toByteArray(Charsets.UTF_8)
    }

    // ---------------- PDF (A4, auto page breaks) ----------------

    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 32f
    private const val ROW_H = 18f

    private fun pdf(r: Report, storeName: String): ByteArray {
        val doc = PdfDocument()
        val maroon = Color.rgb(122, 31, 61)
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 18f; typeface = Typeface.DEFAULT_BOLD; color = maroon }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; color = Color.DKGRAY }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9.5f; color = Color.BLACK }
        val boldPaint = Paint(textPaint).apply { typeface = Typeface.DEFAULT_BOLD }
        val headPaint = Paint(boldPaint).apply { color = Color.WHITE }
        val fill = Paint().apply { style = Paint.Style.FILL }
        val line = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.6f }

        val tableW = PAGE_W - 2 * MARGIN
        val weightSum = r.columns.sumOf { it.weight.toDouble() }.toFloat()
        val widths = r.columns.map { it.weight / weightSum * tableW }

        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = 0f

        fun fit(text: String, paint: Paint, width: Float): String {
            if (paint.measureText(text) <= width) return text
            var t = text
            while (t.isNotEmpty() && paint.measureText("$t…") > width) t = t.dropLast(1)
            return "$t…"
        }

        fun drawRow(values: List<String>, paint: Paint, background: Int?) {
            val c = page!!.canvas
            if (background != null) {
                fill.color = background
                c.drawRect(MARGIN, y, MARGIN + tableW, y + ROW_H, fill)
            }
            var x = MARGIN
            values.forEachIndexed { i, raw ->
                val w = widths.getOrElse(i) { 0f }
                val text = fit(raw, paint, w - 6f)
                val tx = if (r.columns.getOrNull(i)?.alignEnd == true) x + w - 3f - paint.measureText(text) else x + 3f
                c.drawText(text, tx, y + ROW_H - 5.5f, paint)
                x += w
            }
            c.drawLine(MARGIN, y + ROW_H, MARGIN + tableW, y + ROW_H, line)
            y += ROW_H
        }

        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
            val c = page!!.canvas
            y = MARGIN
            if (pageNo == 1) {
                c.drawText(storeName.ifBlank { "Shop Manager" }, MARGIN, y + 14f, titlePaint); y += 24f
                c.drawText(r.title + "  •  " + r.period, MARGIN, y + 10f, subPaint); y += 16f
                c.drawText("Generated: " + System.currentTimeMillis().asDateTime(), MARGIN, y + 10f, subPaint); y += 22f
                // Summary box (two columns)
                val half = tableW / 2
                r.summary.chunked(2).forEach { pair ->
                    pair.forEachIndexed { i, (k, v) ->
                        val x = MARGIN + i * half
                        c.drawText(k, x, y + 11f, textPaint)
                        c.drawText(v, x + half - 12f - boldPaint.measureText(v), y + 11f, boldPaint)
                    }
                    y += 15f
                }
                y += 12f
            } else {
                c.drawText(r.title + " (continued)", MARGIN, y + 10f, subPaint); y += 18f
            }
            drawRow(r.columns.map { it.title }, headPaint, maroon)
            // footer
            c.drawText("Page $pageNo", PAGE_W - MARGIN - subPaint.measureText("Page $pageNo"), PAGE_H - 16f, subPaint)
        }

        newPage()
        if (r.rows.isEmpty()) {
            page!!.canvas.drawText("No data for this period.", MARGIN + 3f, y + 14f, textPaint)
            y += ROW_H
        }
        r.rows.forEachIndexed { i, row ->
            if (y + ROW_H > PAGE_H - MARGIN - 20f) newPage()
            drawRow(row, textPaint, if (i % 2 == 1) Color.rgb(250, 242, 245) else null)
        }
        r.totals?.let {
            if (y + ROW_H > PAGE_H - MARGIN - 20f) newPage()
            drawRow(it, boldPaint, Color.rgb(255, 239, 201))
        }
        page?.let { doc.finishPage(it) }

        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }

    // ---------------- Save / share ----------------

    /** Saves to Downloads/ShopManager (Android 10+) or the app's Downloads folder on older phones. */
    fun saveToDownloads(context: Context, fileName: String, format: ExportFormat, data: ByteArray): SavedFile {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, format.mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ShopManager")
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Could not create file in Downloads")
            resolver.openOutputStream(uri)?.use { it.write(data) }
                ?: throw IllegalStateException("Could not write file")
            return SavedFile(uri, "Downloads/ShopManager/$fileName", format.mime)
        }
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "ShopManager").apply { mkdirs() }
        val file = File(dir, fileName).apply { writeBytes(data) }
        return SavedFile(fileUri(context, file), file.absolutePath, format.mime)
    }

    /** Writes to the app cache and returns a shareable Uri (WhatsApp, Gmail, Drive…). */
    fun shareIntent(context: Context, fileName: String, format: ExportFormat, data: ByteArray, subject: String): Intent {
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, fileName).apply { writeBytes(data) }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = format.mime
            putExtra(Intent.EXTRA_STREAM, fileUri(context, file))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Share report")
    }

    fun openIntent(saved: SavedFile): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(saved.uri, saved.mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    private fun fileUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, context.packageName + ".files", file)
}
