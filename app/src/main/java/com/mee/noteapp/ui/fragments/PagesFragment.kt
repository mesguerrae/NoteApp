package com.mee.noteapp.ui.fragments

import android.content.Intent
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mee.noteapp.R
import com.mee.noteapp.db.AppDatabase
import com.mee.noteapp.db.PageGroup
import com.mee.noteapp.db.PageMeta
import com.mee.noteapp.mhtml.MhtmlAssetRepository
import com.mee.noteapp.ui.MhtmlViewerActivity
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

private const val TYPE_GROUP = 0
private const val TYPE_PAGE = 1

private sealed class Row {
    data class GroupHeaderRow(val group: PageGroup) : Row()
    data class PageRow(val fileName: String, val indented: Boolean) : Row()
}

/**
 * Pestaña "Páginas": lista las páginas MHTML empaquetadas, en orden, como tarjetas.
 * Cada página puede renombrarse (el nombre se usa también como título del visor),
 * marcarse como realizada/no realizada, y agruparse bajo un grupo con nombre propio.
 * Los grupos pueden colapsarse/expandirse; ese estado se guarda en SQLite para que se
 * recuerde la próxima vez que se abra la app.
 */
class PagesFragment : Fragment(R.layout.fragment_pages) {

    private val db by lazy { AppDatabase.get(requireContext()) }
    private val files by lazy { MhtmlAssetRepository.listOrderedFiles(requireContext()) }
    private var metaByFile: Map<String, PageMeta> = emptyMap()
    private var groups: List<PageGroup> = emptyList()
    private var rows: List<Row> = emptyList()
    private lateinit var adapter: RowAdapter
    private lateinit var txtGlobalProgress: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.txtEmpty).visibility =
            if (files.isEmpty()) View.VISIBLE else View.GONE

        txtGlobalProgress = view.findViewById(R.id.txtGlobalProgress)

        val recycler = view.findViewById<RecyclerView>(R.id.recyclerFiles)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        adapter = RowAdapter()
        recycler.adapter = adapter

        view.findViewById<ImageButton>(R.id.btnNewGroup).setOnClickListener { showNewGroupDialog() }

        viewLifecycleOwner.lifecycleScope.launch {
            combine(db.pageMetaDao().observeAll(), db.pageGroupDao().observeAll()) { metas, groupList ->
                metas.associateBy { it.fileName } to groupList
            }.collect { (metas, groupList) ->
                metaByFile = metas
                groups = groupList
                rebuildRows()
                updateGlobalProgress()
            }
        }
    }

    private fun updateGlobalProgress() {
        if (files.isEmpty()) { txtGlobalProgress.text = ""; return }
        val completedCount = files.count { metaByFile[it]?.completed == true }
        val percent = (completedCount * 100) / files.size
        txtGlobalProgress.text = getString(R.string.global_progress, percent)
    }

    private fun rebuildRows() {
        val newRows = mutableListOf<Row>()
        val validGroupIds = groups.map { it.id }.toSet()
        for (group in groups) {
            newRows += Row.GroupHeaderRow(group)
            if (!group.collapsed) {
                files.filter { metaByFile[it]?.groupId == group.id }
                    .forEach { newRows += Row.PageRow(it, indented = true) }
            }
        }
        files.filter { fileName ->
            val groupId = metaByFile[fileName]?.groupId
            groupId == null || groupId !in validGroupIds
        }.forEach { newRows += Row.PageRow(it, indented = false) }
        rows = newRows
        adapter.notifyDataSetChanged()
    }

    private fun showNewGroupDialog() {
        val editText = EditText(requireContext()).apply { setPadding(48, 32, 48, 32) }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.new_group_title)
            .setView(editText)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotEmpty()) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        db.pageGroupDao().insert(PageGroup(name = name))
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showAssignGroupDialog(fileName: String) {
        val options = mutableListOf(getString(R.string.no_group)) + groups.map { it.name } + getString(R.string.create_new_group)
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.assign_group)
            .setItems(options.toTypedArray()) { _, which ->
                when (which) {
                    0 -> updatePageGroup(fileName, null)
                    options.size - 1 -> showNewGroupDialog()
                    else -> updatePageGroup(fileName, groups[which - 1].id)
                }
            }
            .show()
    }

    private fun updatePageGroup(fileName: String, groupId: Long?) {
        viewLifecycleOwner.lifecycleScope.launch {
            val existing = metaByFile[fileName]
            db.pageMetaDao().upsert(
                PageMeta(
                    fileName = fileName,
                    customName = existing?.customName,
                    completed = existing?.completed ?: false,
                    groupId = groupId
                )
            )
        }
    }

    private inner class RowAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemViewType(position: Int) = when (rows[position]) {
            is Row.GroupHeaderRow -> TYPE_GROUP
            is Row.PageRow -> TYPE_PAGE
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == TYPE_GROUP) {
                GroupHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_group_header, parent, false))
            } else {
                FileHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false))
            }
        }

        override fun getItemCount() = rows.size

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val row = rows[position]) {
                is Row.GroupHeaderRow -> (holder as GroupHolder).bind(row.group)
                is Row.PageRow -> (holder as FileHolder).bind(row.fileName, row.indented)
            }
        }
    }

    private inner class GroupHolder(v: View) : RecyclerView.ViewHolder(v) {
        private val txtName: TextView = v.findViewById(R.id.txtGroupName)
        private val txtProgress: TextView = v.findViewById(R.id.txtGroupProgress)
        private val imgChevron: ImageView = v.findViewById(R.id.imgChevron)

        fun bind(group: PageGroup) {
            txtName.text = group.name
            imgChevron.rotation = if (group.collapsed) -90f else 0f

            val groupFiles = files.filter { metaByFile[it]?.groupId == group.id }
            val completedCount = groupFiles.count { metaByFile[it]?.completed == true }
            val percent = if (groupFiles.isEmpty()) 0 else (completedCount * 100) / groupFiles.size
            txtProgress.text = "$percent%"

            itemView.setOnClickListener {
                viewLifecycleOwner.lifecycleScope.launch {
                    db.pageGroupDao().update(group.copy(collapsed = !group.collapsed))
                }
            }
        }
    }

    private inner class FileHolder(v: View) : RecyclerView.ViewHolder(v) {
        private val text: TextView = v.findViewById(R.id.txtFileName)
        private val btnMarkDone: ImageButton = v.findViewById(R.id.btnMarkDone)
        private val btnRename: ImageButton = v.findViewById(R.id.btnRename)
        private val btnAssignGroup: ImageButton = v.findViewById(R.id.btnAssignGroup)

        fun bind(fileName: String, indented: Boolean) {
            val meta = metaByFile[fileName]
            val displayName = meta?.customName?.takeIf { it.isNotBlank() } ?: fileName
            text.text = displayName

            val params = itemView.layoutParams as ViewGroup.MarginLayoutParams
            params.marginStart = if (indented) 28.dp(itemView) else 8.dp(itemView)
            itemView.layoutParams = params

            val done = meta?.completed == true
            text.paintFlags = if (done) text.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                else text.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            btnMarkDone.setImageResource(if (done) R.drawable.ic_check_circle else R.drawable.ic_check_circle_outline)
            val accentColor = ContextCompat.getColor(
                requireContext(), if (done) R.color.page_done_accent else R.color.page_pending_accent
            )
            btnMarkDone.setColorFilter(accentColor)
            val cardColor = ContextCompat.getColor(
                requireContext(), if (done) R.color.page_done_bg else R.color.page_pending_bg
            )
            (itemView.background.mutate() as GradientDrawable).setColor(cardColor)

            itemView.setOnClickListener {
                startActivity(
                    Intent(requireContext(), MhtmlViewerActivity::class.java)
                        .putExtra(MhtmlViewerActivity.EXTRA_START_INDEX, files.indexOf(fileName))
                )
            }

            btnMarkDone.setOnClickListener {
                viewLifecycleOwner.lifecycleScope.launch {
                    db.pageMetaDao().upsert(
                        PageMeta(fileName = fileName, customName = meta?.customName, completed = !done, groupId = meta?.groupId)
                    )
                }
            }

            btnRename.setOnClickListener { showRenameDialog(fileName, meta?.customName) }
            btnAssignGroup.setOnClickListener { showAssignGroupDialog(fileName) }
        }

        private fun showRenameDialog(fileName: String, currentName: String?) {
            val editText = EditText(requireContext()).apply {
                setText(currentName ?: "")
                hint = fileName
                setPadding(48, 32, 48, 32)
            }
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.rename_page_title)
                .setView(editText)
                .setPositiveButton(R.string.save) { _, _ ->
                    val newName = editText.text.toString().trim()
                    viewLifecycleOwner.lifecycleScope.launch {
                        val existing = metaByFile[fileName]
                        db.pageMetaDao().upsert(
                            PageMeta(
                                fileName = fileName,
                                customName = newName.ifBlank { null },
                                completed = existing?.completed ?: false,
                                groupId = existing?.groupId
                            )
                        )
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }
}

private fun Int.dp(view: View): Int = (this * view.resources.displayMetrics.density).toInt()
