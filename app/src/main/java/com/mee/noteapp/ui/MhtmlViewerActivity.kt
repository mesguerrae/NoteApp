package com.mee.noteapp.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.mee.noteapp.R
import com.mee.noteapp.db.AppDatabase
import com.mee.noteapp.db.MhtmlNote
import com.mee.noteapp.db.NoteStyle
import com.mee.noteapp.mhtml.MhtmlAssetRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Shows every .mhtml file from assets/mhtml, in filename order, one per page.
 * Swipe left/right to move between pages. Tap anywhere on a page to write a note
 * there; the note is saved to the internal SQLite (Room) database keyed by file name.
 */
class MhtmlViewerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_START_INDEX = "start_index"
        private const val STATE_PAGE_LOCKED = "state_page_locked"
        private const val MENU_GENERAL_NOTES = 1
        private const val MENU_ADD_NOTE = 2
        private const val MENU_LOCK_PAGE = 3
    }

    private val db by lazy { AppDatabase.get(this) }
    private lateinit var pager: ViewPager2
    private lateinit var files: List<String>
    private var pageLocked = false
    private var lockMenuItem: MenuItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePrefs.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mhtml_viewer)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if (savedInstanceState != null) {
            pageLocked = savedInstanceState.getBoolean(STATE_PAGE_LOCKED, false)
        }

        files = MhtmlAssetRepository.listOrderedFiles(this)
        pager = findViewById(R.id.pager)
        pager.adapter = MhtmlPagerAdapter(files)
        pager.isUserInputEnabled = !pageLocked

        val startIndex = intent.getIntExtra(EXTRA_START_INDEX, 0)
        if (files.isNotEmpty()) {
            pager.setCurrentItem(startIndex.coerceIn(0, files.size - 1), false)
        }

        val txtIndicator = findViewById<TextView>(R.id.txtPageIndicator)
        val btnPrev = findViewById<ImageButton>(R.id.btnPrevPage)
        val btnNext = findViewById<ImageButton>(R.id.btnNextPage)
        val btnZoomIn = findViewById<ImageButton>(R.id.btnZoomIn)
        val btnZoomOut = findViewById<ImageButton>(R.id.btnZoomOut)

        fun updateIndicator(position: Int) {
            if (files.isEmpty()) {
                txtIndicator.text = "0/0"
            } else {
                txtIndicator.text = getString(R.string.page_indicator_format, position + 1, files.size)
            }
        }

        fun updateTitle(position: Int) {
            val fileName = files.getOrNull(position) ?: return
            lifecycleScope.launch {
                val meta = db.pageMetaDao().get(fileName)
                supportActionBar?.title = meta?.customName?.takeIf { it.isNotBlank() } ?: fileName
            }
        }

        updateIndicator(pager.currentItem)
        updateTitle(pager.currentItem)

        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateIndicator(position)
                updateTitle(position)
            }
        })

        btnPrev.setOnClickListener { pager.currentItem = (pager.currentItem - 1).coerceAtLeast(0) }
        btnNext.setOnClickListener {
            pager.currentItem = (pager.currentItem + 1).coerceAtMost((files.size - 1).coerceAtLeast(0))
        }
        btnZoomIn.setOnClickListener { currentPageHolder()?.zoomIn() }
        btnZoomOut.setOnClickListener { currentPageHolder()?.zoomOut() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_PAGE_LOCKED, pageLocked)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, MENU_GENERAL_NOTES, 0, R.string.general_notes).apply {
            setIcon(R.drawable.ic_add)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        menu.add(0, MENU_ADD_NOTE, 1, R.string.add_note_menu).apply {
            setIcon(R.drawable.ic_note_add)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        lockMenuItem = menu.add(0, MENU_LOCK_PAGE, 2, R.string.lock_page).apply {
            setIcon(if (pageLocked) R.drawable.ic_lock else R.drawable.ic_lock_open)
            setTitle(if (pageLocked) R.string.unlock_page else R.string.lock_page)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            MENU_GENERAL_NOTES -> lifecycleScope.launch {
                val fileName = files.getOrNull(pager.currentItem)
                val meta = fileName?.let { db.pageMetaDao().get(it) }
                val label = meta?.customName?.takeIf { it.isNotBlank() } ?: fileName
                showGeneralNotesDialog(this@MhtmlViewerActivity, label)
            }
            MENU_ADD_NOTE -> currentPageHolder()?.addNoteViaMenu()
            MENU_LOCK_PAGE -> togglePageLock()
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * When locked, ViewPager2's swipe-to-change-page gesture is disabled so a horizontal
     * drag always moves/resizes a note instead of flipping the page — otherwise the pager
     * steals that gesture before the note overlay can use it.
     */
    private fun togglePageLock() {
        pageLocked = !pageLocked
        pager.isUserInputEnabled = !pageLocked
        lockMenuItem?.apply {
            setIcon(if (pageLocked) R.drawable.ic_lock else R.drawable.ic_lock_open)
            setTitle(if (pageLocked) R.string.unlock_page else R.string.lock_page)
        }
        if (pageLocked) {
            Toast.makeText(this, R.string.page_locked_hint, Toast.LENGTH_LONG).show()
        }
    }

    private fun currentPageHolder(): PageHolder? {
        val recyclerView = pager.getChildAt(0) as? RecyclerView ?: return null
        return recyclerView.findViewHolderForAdapterPosition(pager.currentItem) as? PageHolder
    }

    private inner class MhtmlPagerAdapter(val files: List<String>) : RecyclerView.Adapter<PageHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.page_mhtml, parent, false)
            return PageHolder(v)
        }

        override fun getItemCount() = files.size

        override fun onBindViewHolder(holder: PageHolder, position: Int) {
            holder.bind(files[position])
        }

        override fun onViewRecycled(holder: PageHolder) {
            super.onViewRecycled(holder)
            holder.unbind()
        }
    }

    private inner class PageHolder(v: View) : RecyclerView.ViewHolder(v) {
        private val webView: WebView = v.findViewById(R.id.webView)
        private val overlay: AnnotationOverlayView = v.findViewById(R.id.overlay)
        private var observeJob: Job? = null
        private var currentScale: Float = 1f

        init {
            webView.settings.apply {
                builtInZoomControls = true
                displayZoomControls = false
                setSupportZoom(true)
                useWideViewPort = true
                loadWithOverviewMode = true
            }
            overlay.zoomPanTarget = webView
            overlay.externalScaleProvider = { currentScale }
            webView.webViewClient = object : WebViewClient() {
                override fun onScaleChanged(view: WebView, oldScale: Float, newScale: Float) {
                    currentScale = newScale
                    overlay.invalidate()
                }
            }
        }

        @Suppress("DEPRECATION")
        fun zoomIn() { webView.zoomIn(); overlay.invalidate() }
        @Suppress("DEPRECATION")
        fun zoomOut() { webView.zoomOut(); overlay.invalidate() }
        fun addNoteViaMenu() { overlay.onRequestNewNote?.invoke(0.5f, 0.5f) }

        fun unbind() {
            observeJob?.cancel()
            observeJob = null
        }

        fun bind(fileName: String) {
            unbind()
            webView.loadUrl(MhtmlAssetRepository.assetPath(fileName))

            observeJob = lifecycleScope.launch {
                db.mhtmlNoteDao().observeForFile(fileName).collect { list ->
                    overlay.notes = list.map {
                        OverlayNote(it.id, it.text, it.xPercent, it.yPercent, it.style, it.sizeScale, it.createdAt)
                    }
                }
            }

            overlay.onRequestNewNote = { x, y ->
                showNoteStyleDialog(itemView.context, null, NoteStyle.GENERAL, null) { text, style ->
                    lifecycleScope.launch {
                        val now = System.currentTimeMillis()
                        db.mhtmlNoteDao().insert(
                            MhtmlNote(
                                fileName = fileName,
                                text = text,
                                xPercent = x,
                                yPercent = y,
                                updatedAt = now,
                                style = style,
                                createdAt = now
                            )
                        )
                    }
                }
            }

            overlay.onRequestEditNote = { note ->
                showNoteStyleDialog(itemView.context, note.text, note.style, note.createdAt) { text, style ->
                    lifecycleScope.launch {
                        val existing = db.mhtmlNoteDao().getForFile(fileName).firstOrNull { it.id == note.id } ?: return@launch
                        if (text.isBlank()) db.mhtmlNoteDao().delete(existing)
                        else db.mhtmlNoteDao().update(existing.copy(text = text, style = style, updatedAt = System.currentTimeMillis()))
                    }
                }
            }

            overlay.onNoteMoved = { note, x, y ->
                lifecycleScope.launch {
                    val existing = db.mhtmlNoteDao().getForFile(fileName).firstOrNull { it.id == note.id } ?: return@launch
                    db.mhtmlNoteDao().update(existing.copy(xPercent = x, yPercent = y, updatedAt = System.currentTimeMillis()))
                }
            }

            overlay.onNoteResized = { note, sizeScale ->
                lifecycleScope.launch {
                    val existing = db.mhtmlNoteDao().getForFile(fileName).firstOrNull { it.id == note.id } ?: return@launch
                    db.mhtmlNoteDao().update(existing.copy(sizeScale = sizeScale, updatedAt = System.currentTimeMillis()))
                }
            }

            overlay.onRequestDeleteNote = { note ->
                lifecycleScope.launch {
                    val existing = db.mhtmlNoteDao().getForFile(fileName).firstOrNull { it.id == note.id } ?: return@launch
                    db.mhtmlNoteDao().delete(existing)
                }
            }
        }
    }
}
