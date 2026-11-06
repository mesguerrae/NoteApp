package com.mee.noteapp.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.mee.noteapp.db.NoteStyle
import com.mee.noteapp.db.PdfNote
import java.io.File
import java.io.FileOutputStream

/**
 * Wraps [PdfRenderer] to render pages to bitmaps, and flattens saved notes back into a
 * brand new PDF file (the original document is never modified) for export.
 */
class PdfRepository(private val context: Context, private val uri: Uri) {

    private val lock = Any()
    private var fd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null

    fun open(): Int = synchronized(lock) {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: error("No se pudo abrir el PDF")
        fd = pfd
        val r = PdfRenderer(pfd)
        renderer = r
        r.pageCount
    }

    fun pageSize(index: Int): Pair<Int, Int> = synchronized(lock) {
        val r = renderer ?: error("PDF no abierto")
        r.openPage(index).use { p -> p.width to p.height }
    }

    /** Renders a page at [scale]x its native point size (higher scale = sharper zoom-in). */
    fun renderPage(index: Int, scale: Int = 2): Bitmap = synchronized(lock) {
        val r = renderer ?: error("PDF no abierto")
        r.openPage(index).use { page ->
            val bmp = Bitmap.createBitmap(page.width * scale, page.height * scale, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bmp
        }
    }

    fun close() = synchronized(lock) {
        renderer?.close(); fd?.close()
        renderer = null; fd = null
    }

    /**
     * Builds a new PDF file in [outFile] with every page re-rendered and all [notesByPage]
     * drawn as text at their stored positions. Returns the written file.
     */
    fun exportWithNotes(outFile: File, notesByPage: Map<Int, List<PdfNote>>): File {
        val pageCount = open()
        val document = PdfDocument()
        try {
            for (i in 0 until pageCount) {
                val (w, h) = pageSize(i)
                val info = PdfDocument.PageInfo.Builder(w, h, i + 1).create()
                val page = document.startPage(info)
                val canvas = page.canvas
                val bmp = renderPage(i, scale = 1)
                canvas.drawBitmap(bmp, null, android.graphics.RectF(0f, 0f, w.toFloat(), h.toFloat()), null)
                bmp.recycle()

                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                notesByPage[i]?.forEach { n ->
                    val important = n.style == NoteStyle.IMPORTANT
                    bgPaint.color = Color.parseColor(if (important) "#F5B7B1" else "#F9E79F")
                    textPaint.color = if (important) Color.parseColor("#78281F") else Color.BLACK
                    textPaint.textSize = 14f * n.sizeScale
                    val x = n.xPercent * w
                    val y = n.yPercent * h
                    val textWidth = textPaint.measureText(n.text) + 8f
                    val padding = 16f * n.sizeScale
                    canvas.drawRect(x, y - padding, x + textWidth, y + 6f * n.sizeScale, bgPaint)
                    canvas.drawText(n.text, x + 4f, y, textPaint)
                }
                document.finishPage(page)
            }
            FileOutputStream(outFile).use { document.writeTo(it) }
        } finally {
            document.close()
            close()
        }
        return outFile
    }
}
