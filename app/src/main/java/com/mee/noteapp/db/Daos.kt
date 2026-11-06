package com.mee.noteapp.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MhtmlNoteDao {
    @Query("SELECT * FROM mhtml_notes WHERE fileName = :fileName ORDER BY id")
    fun observeForFile(fileName: String): Flow<List<MhtmlNote>>

    @Query("SELECT * FROM mhtml_notes WHERE fileName = :fileName ORDER BY id")
    suspend fun getForFile(fileName: String): List<MhtmlNote>

    @Insert
    suspend fun insert(note: MhtmlNote): Long

    @Update
    suspend fun update(note: MhtmlNote)

    @Delete
    suspend fun delete(note: MhtmlNote)
}

@Dao
interface PdfNoteDao {
    @Query("SELECT * FROM pdf_notes WHERE pdfUri = :pdfUri AND pageIndex = :pageIndex ORDER BY id")
    fun observeForPage(pdfUri: String, pageIndex: Int): Flow<List<PdfNote>>

    @Query("SELECT * FROM pdf_notes WHERE pdfUri = :pdfUri AND pageIndex = :pageIndex ORDER BY id")
    suspend fun getForPage(pdfUri: String, pageIndex: Int): List<PdfNote>

    @Insert
    suspend fun insert(note: PdfNote): Long

    @Update
    suspend fun update(note: PdfNote)

    @Delete
    suspend fun delete(note: PdfNote)
}

@Dao
interface PageMetaDao {
    @Query("SELECT * FROM page_meta")
    fun observeAll(): Flow<List<PageMeta>>

    @Query("SELECT * FROM page_meta WHERE fileName = :fileName")
    suspend fun get(fileName: String): PageMeta?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(meta: PageMeta)
}

@Dao
interface PageGroupDao {
    @Query("SELECT * FROM page_groups ORDER BY id")
    fun observeAll(): Flow<List<PageGroup>>

    @Insert
    suspend fun insert(group: PageGroup): Long

    @Update
    suspend fun update(group: PageGroup)

    @Delete
    suspend fun delete(group: PageGroup)
}

@Dao
interface GeneralNoteDao {
    @Query("SELECT * FROM general_notes ORDER BY id DESC")
    fun observeAll(): Flow<List<GeneralNote>>

    @Insert
    suspend fun insert(note: GeneralNote): Long

    @Update
    suspend fun update(note: GeneralNote)

    @Delete
    suspend fun delete(note: GeneralNote)
}
