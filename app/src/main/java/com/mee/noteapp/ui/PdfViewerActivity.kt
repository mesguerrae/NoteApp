package com.mee.noteapp.ui

import android.content.Intent
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.mee.noteapp.R
import com.mee.noteapp.db.AppDatabase
import com.mee.noteapp.db.NoteStyle
import com.mee.noteapp.db.PdfNote
import com.mee.noteapp.pdf.PdfRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Built-in PDF reader: zoom (pinch/double-tap), pan, and write a note at any position
 * of any page. Notes are kept in the internal SQLite database and can be flattened
 * into a brand new annotated PDF via the toolbar "export" action.
 */
class PdfViewerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URI = "pdf_uri"
    }

    private lateinit var uri: Uri
    private lateinit var repo: PdfRepository
    private val db by lazy { AppDatabase.get(this) }
    private var pageCount = 0
    private lateinit var pager: ViewPager2

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePrefs.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pdf_viewer)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.tab_pdf)

        uri = Uri.parse(intent.getStringExtra(EXTRA_URI) ?: return finish())
        repo = PdfRepository(this, uri)

        pager = findViewById(R.id.pager)
        val txtIndicator = findViewById<TextView>(R.id.txtPageIndicator)
        val btnPrev = findViewById<ImageButton>(R.id.btnPrevPage)
        val btnNext = findViewById<ImageButton>(R.id.btnNextPage)
        val btnZoomIn = findViewById<ImageButton>(R.id.btnZoomIn)
        val btnZoomOut = findViewById<ImageButton>(R.id.btnZoomOut)

        fun updateIndicator(position: Int) {
            txtIndicator.text = "${position + 1}/$pageCount"
        }

        lifecycleScope.launch {
            pageCount = withContext(Dispatchers.IO) { repo.open() }
            pager.adapter = PdfPagerAdapter(pageCount)
            updateIndicator(0)
            pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) = updateIndicator(position)
            })
        }

        btnPrev.setOnClickListener { pager.currentItem = (pager.currentItem - 1).coerceAtLeast(0) }
        btnNext.setOnClickListener { pager.currentItem = (pager.currentItem + 1).coerceAtMost((pageCount - 1).coerceAtLeast(0)) }
        btnZoomIn.setOnClickListener { currentPageHolder()?.zoomIn() }
        btnZoomOut.setOnClickListener { currentPageHolder()?.zoomOut() }
    }

    private fun currentPageHolder(): PageHolder? {
        val recyclerView = pager.getChildAt(0) as? RecyclerView ?: return null
        return recyclerView.findViewHolderForAdapterPosition(pager.currentItem) as? PageHolder
    }

    /** Best-effort human-readable file name for [uri], used to tag general notes with their source. */
    private fun pdfDisplayName(): String {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                cursor.getString(nameIndex)?.let { return it }
            }
        }
        return uri.lastPathSegment ?: uri.toString()
    }

    override fun onDestroy() {
        super.onDestroy()
        repo.close()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, R.string.general_notes).apply {
            setIcon(R.drawable.ic_add)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        menu.add(0, 2, 1, R.string.add_note_menu).apply {
            setIcon(R.drawable.ic_note_add)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        menu.add(0, 3, 2, R.string.export_pdf).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            1 -> showGeneralNotesDialog(this, pdfDisplayName())
            2 -> currentPageHolder()?.addNoteViaMenu()
            3 -> exportAnnotatedPdf()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun exportAnnotatedPdf() {
        lifecycleScope.launch {
            Toast.makeText(this@PdfViewerActivity, "Exportando…", Toast.LENGTH_SHORT).show()
            val outFile = withContext(Dispatchers.IO) {
                val notesByPage = (0 until pageCount).associateWith { page ->
                    db.pdfNoteDao().getForPage(uri.toString(), page)
                }
                val dir = File(getExternalFilesDir(null), "pdf_export").apply { mkdirs() }
                val file = File(dir, "anotado_${System.currentTimeMillis()}.pdf")
                // Export reopens its own renderer/fd, independent from the one driving the UI.
                PdfRepository(this@PdfViewerActivity, uri).exportWithNotes(file, notesByPage)
            }
            val shareUri = FileProvider.getUriForFile(this@PdfViewerActivity, "$packageName.fileprovider", outFile)
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, shareUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(share, getString(R.string.export_pdf)))
        }
    }

    private inner class PdfPagerAdapter(private val count: Int) : RecyclerView.Adapter<PageHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.page_pdf, parent, false)
            return PageHolder(v)
        }
        override fun getItemCount() = count
        override fun onBindViewHolder(holder: PageHolder, position: Int) = holder.bind(position)
    }

    private inner class PageHolder(v: View) : RecyclerView.ViewHolder(v) {
        private val imageView: ZoomableImageView = v.findViewById(R.id.pageImage)
        private val overlay: AnnotationOverlayView = v.findViewById(R.id.overlay)

        fun zoomIn() = imageView.zoomBy(1.25f)
        fun zoomOut() = imageView.zoomBy(0.8f)
        fun addNoteViaMenu() { overlay.onRequestNewNote?.invoke(0.5f, 0.5f) }

        fun bind(pageIndex: Int) {
            overlay.zoomPanTarget = imageView
            lifecycleScope.launch {
                val bmp = withContext(Dispatchers.IO) { repo.renderPage(pageIndex, scale = 2) }
                imageView.setImageBitmap(bmp)
                overlay.pageSize = bmp.width to bmp.height
                imageView.resetToFit(bmp.width, bmp.height)
                imageView.onMatrixChanged = { m ->
                    overlay.baseScale = imageView.fitScale
                    overlay.pageMatrix = Matrix(m)
                }
                overlay.pageMatrix = imageView.imageMatrix
            }

            val pdfUri = uri.toString()
            lifecycleScope.launch {
                db.pdfNoteDao().observeForPage(pdfUri, pageIndex).collect { list ->
                    overlay.notes = list.map { OverlayNote(it.id, it.text, it.xPercent, it.yPercent, it.style, it.sizeScale, it.createdAt) }
                }
            }

            overlay.onRequestNewNote = { x, y ->
                showNoteStyleDialog(itemView.context, null, NoteStyle.GENERAL, null) { text, style ->
                    lifecycleScope.launch {
                        val now = System.currentTimeMillis()
                        db.pdfNoteDao().insert(
                            PdfNote(pdfUri = pdfUri, pageIndex = pageIndex, text = text, xPercent = x, yPercent = y, updatedAt = now, style = style, createdAt = now)
                        )
                    }
                }
            }

            overlay.onRequestEditNote = { note ->
                showNoteStyleDialog(itemView.context, note.text, note.style, note.createdAt) { text, style ->
                    lifecycleScope.launch {
                        val existing = db.pdfNoteDao().getForPage(pdfUri, pageIndex).firstOrNull { it.id == note.id } ?: return@launch
                        if (text.isBlank()) db.pdfNoteDao().delete(existing)
                        else db.pdfNoteDao().update(existing.copy(text = text, style = style, updatedAt = System.currentTimeMillis()))
                    }
                }
            }

            overlay.onNoteMoved = { note, x, y ->
                lifecycleScope.launch {
                    val existing = db.pdfNoteDao().getForPage(pdfUri, pageIndex).firstOrNull { it.id == note.id } ?: return@launch
                    db.pdfNoteDao().update(existing.copy(xPercent = x, yPercent = y, updatedAt = System.currentTimeMillis()))
                }
            }

            overlay.onNoteResized = { note, sizeScale ->
                lifecycleScope.launch {
                    val existing = db.pdfNoteDao().getForPage(pdfUri, pageIndex).firstOrNull { it.id == note.id } ?: return@launch
                    db.pdfNoteDao().update(existing.copy(sizeScale = sizeScale, updatedAt = System.currentTimeMillis()))
                }
            }

            overlay.onRequestDeleteNote = { note ->
                lifecycleScope.launch {
                    val existing = db.pdfNoteDao().getForPage(pdfUri, pageIndex).firstOrNull { it.id == note.id } ?: return@launch
                    db.pdfNoteDao().delete(existing)
                }
            }
        }
    }
}
