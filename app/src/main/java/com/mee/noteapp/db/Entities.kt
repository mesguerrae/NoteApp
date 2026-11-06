package com.mee.noteapp.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Visual style of a page note. Stored as plain strings so old rows default to "general". */
object NoteStyle {
    const val GENERAL = "general"
    const val IMPORTANT = "important"
}

/**
 * One handwritten/typed note placed over an MHTML page (identified by its asset file name).
 * [xPercent]/[yPercent] are 0f..1f so the note position is independent of screen size/zoom.
 * [style] is one of [NoteStyle]. [sizeScale] is the user-chosen resize factor (1f = default size).
 *
 * [style] and [sizeScale] were added in schema v4/v5 via [MIGRATION_3_4]/[MIGRATION_4_5]
 * (see AppDatabase.kt) — their `defaultValue` here MUST match the `DEFAULT` used in those
 * migrations' `ALTER TABLE ... ADD COLUMN` statements, or Room's schema validation fails.
 */
@Entity(tableName = "mhtml_notes")
data class MhtmlNote(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val text: String,
    val xPercent: Float,
    val yPercent: Float,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "'general'") val style: String = NoteStyle.GENERAL,
    @ColumnInfo(defaultValue = "1.0") val sizeScale: Float = 1f,
    @ColumnInfo(defaultValue = "0") val createdAt: Long = 0L
)

/**
 * One annotation placed over a page of a PDF the user opened in the built-in reader.
 * [pdfUri] identifies the source document, [pageIndex] the page (0-based).
 * [style] is one of [NoteStyle]. [sizeScale] is the user-chosen resize factor (1f = default size).
 * See the migration note on [MhtmlNote] — the same rule applies here.
 */
@Entity(tableName = "pdf_notes")
data class PdfNote(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pdfUri: String,
    val pageIndex: Int,
    val text: String,
    val xPercent: Float,
    val yPercent: Float,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "'general'") val style: String = NoteStyle.GENERAL,
    @ColumnInfo(defaultValue = "1.0") val sizeScale: Float = 1f,
    @ColumnInfo(defaultValue = "0") val createdAt: Long = 0L
)

/**
 * Per-MHTML-page metadata: an optional custom display name (shown in the pages list and
 * as the viewer's title instead of the raw file name), a "done/not done" flag, and the
 * optional group ([groupId], pointing at [PageGroup.id]) the page has been placed in.
 */
@Entity(tableName = "page_meta")
data class PageMeta(
    @PrimaryKey val fileName: String,
    val customName: String? = null,
    val completed: Boolean = false,
    val groupId: Long? = null
)

/**
 * A user-named group of pages (e.g. "Capítulo 1"). [collapsed] is persisted so the
 * expand/collapse state of the group survives closing and reopening the app.
 */
@Entity(tableName = "page_groups")
data class PageGroup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val collapsed: Boolean = false
)

/**
 * A free-form note not tied to any specific page or document, shown in the general notes
 * popup. [sourcePage] optionally records which MHTML page or PDF was open when the note was
 * created (e.g. "Page03.mhtml" or "informe.pdf") — null if created from the main screen,
 * with no page open.
 */
@Entity(tableName = "general_notes")
data class GeneralNote(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "0") val createdAt: Long = 0L,
    val sourcePage: String? = null
)
