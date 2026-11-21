package uz.xakerovchisi

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Jurnaldagi hodisalarni PDF hisobotga aylantiradi va ulashish uchun taqdim etadi. */
object ReportExporter {

    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 40
    private const val CONTENT_W = PAGE_W - MARGIN * 2

    private fun fmt(t: Long) = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date(t))

    private fun layoutFor(text: String, paint: TextPaint): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, CONTENT_W)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(2f, 1f)
            .build()

    fun buildPdf(ctx: Context, events: List<Event>): File {
        val doc = PdfDocument()
        val titlePaint = TextPaint().apply { color = Color.BLACK; textSize = 20f; isFakeBoldText = true }
        val metaPaint = TextPaint().apply { color = Color.DKGRAY; textSize = 11f }
        val bodyPaint = TextPaint().apply { color = Color.BLACK; textSize = 11f }

        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
        var canvas = page.canvas
        var y = MARGIN.toFloat()

        canvas.drawText("Xaker Ovchisi — hodisalar hisoboti", MARGIN.toFloat(), y + 18, titlePaint)
        y += 36
        val meta = "Shakllantirilgan: ${fmt(System.currentTimeMillis())}  ·  Jami hodisalar: ${events.size}"
        canvas.drawText(meta, MARGIN.toFloat(), y, metaPaint)
        y += 24

        for (e in events) {
            val sevLabel = when (e.severity) {
                3 -> "XAVFLI"
                2 -> "OGOHLANTIRISH"
                else -> "Ma'lumot"
            }
            val sevColor = when (e.severity) {
                3 -> Color.rgb(200, 30, 30)
                2 -> Color.rgb(170, 110, 10)
                else -> Color.DKGRAY
            }
            val headerPaint = TextPaint(bodyPaint).apply { isFakeBoldText = true; color = sevColor }
            val header = "${fmt(e.time)}  ·  ${e.type}  ·  $sevLabel"
            val headerLayout = layoutFor(header, headerPaint)
            val bodyLayout = layoutFor(e.text, bodyPaint)
            val blockHeight = headerLayout.height + bodyLayout.height + 14

            if (y + blockHeight > PAGE_H - MARGIN) {
                doc.finishPage(page)
                pageNum++
                page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
                canvas = page.canvas
                y = MARGIN.toFloat()
            }

            canvas.save()
            canvas.translate(MARGIN.toFloat(), y)
            headerLayout.draw(canvas)
            canvas.translate(0f, headerLayout.height.toFloat() + 2)
            bodyLayout.draw(canvas)
            canvas.restore()
            y += blockHeight
        }
        doc.finishPage(page)

        val dir = File(ctx.getExternalFilesDir(null), "reports")
        dir.mkdirs()
        val file = File(dir, "xaker_ovchisi_hisobot_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }

    fun shareFile(ctx: Context, file: File) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(intent, "Hisobotni ulashish").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
