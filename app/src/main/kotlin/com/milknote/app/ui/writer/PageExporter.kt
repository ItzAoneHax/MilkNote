package com.milknote.app.ui.writer

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.provider.MediaStore
import com.milknote.ink.model.Stroke
import java.io.OutputStream

/** 导出:PNG(单页)/ PDF(整本;DESIGN-V0 §6.2 导出目标;页面尺寸按笔记本元数据) */
object PageExporter {

    private const val MM_TO_PX = 5.67f

    fun savePng(context: Context, bitmap: Bitmap, name: String): String {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "${name.replace('/', '_')}-${System.currentTimeMillis() % 100000}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MilkNote")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return "保存失败"
        context.contentResolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return "已导出 PNG → 图片/MilkNote"
    }

    /** 整本 PDF:页面物理尺寸(1mm = 72/25.4pt),方向生效 */
    fun savePdf(
        context: Context,
        pages: List<Pair<String, List<Stroke>>>,
        paper: String,
        name: String,
        pageSize: String = "A4",
        orientation: String = "portrait",
    ): String {
        if (pages.isEmpty()) return "没有页面"
        val (wMm, hMm) = com.milknote.notes.model.PAGE_SIZES_MM[pageSize] ?: com.milknote.notes.model.PAGE_SIZES_MM.getValue("A4")
        val (pwMm, phMm) = if (orientation == "landscape") hMm to wMm else wMm to hMm
        val doc = PdfDocument()
        val pw = (pwMm / 25.4f * 72).toInt()
        val ph = (phMm / 25.4f * 72).toInt()
        val pageWpx = pwMm * MM_TO_PX
        val pageHpx = phMm * MM_TO_PX
        for ((pageId, strokes) in pages) {
            val pageInfo = PdfDocument.PageInfo.Builder(pw, ph, pages.indexOfFirst { it.first == pageId } + 1).create()
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas
            val scale = pw / pageWpx
            canvas.scale(scale, scale)
            canvas.drawColor(0xFFFAF9F5.toInt())
            com.milknote.ink.render.PaperRenderer.draw(canvas, paper, pageWpx, pageHpx, 0xFFE5E1D6.toInt())
            val paint = android.graphics.Paint().apply { isAntiAlias = true; style = android.graphics.Paint.Style.FILL }
            for (s in strokes) {
                paint.color = s.color.toInt()
                paint.alpha = if (s.tool == com.milknote.ink.model.InkTool.HIGHLIGHTER) (255 * 0.45f).toInt() else 255
                canvas.drawPath(
                    com.milknote.ink.render.StrokeRenderer.buildPath(s, MM_TO_PX, com.milknote.ink.model.PressureGamma.STANDARD),
                    paint,
                )
            }
            doc.finishPage(page)
        }
        val values = ContentValues().apply {
            put(MediaStore.Files.FileColumns.DISPLAY_NAME, "${name.replace('/', '_')}-${System.currentTimeMillis() % 100000}.pdf")
            put(MediaStore.Files.FileColumns.MIME_TYPE, "application/pdf")
            put(MediaStore.Files.FileColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/MilkNote")
        }
        val uri = context.contentResolver.insert(MediaStore.Files.getContentUri("external"), values)
            ?: return "保存失败"
        context.contentResolver.openOutputStream(uri)?.use { out: OutputStream -> doc.writeTo(out) }
        doc.close()
        return "已导出 PDF(${pages.size} 页) → 文档/MilkNote"
    }
}
