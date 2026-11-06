package com.mee.noteapp.db

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Internal SQLite database (via Room) that stores every note the user writes,
 * both over MHTML pages and over PDF pages, plus per-page metadata (custom name,
 * done/not-done flag) and the free-form general notes list.
 *
 * Uses explicit migrations ([ALL_MIGRATIONS] in Migrations.kt) instead of
 * `fallbackToDestructiveMigration`, so updating the app never deletes the user's notes.
 * See Migrations.kt for the checklist to follow when changing any entity.
 */
@Database(
    entities = [MhtmlNote::class, PdfNote::class, PageMeta::class, GeneralNote::class, PageGroup::class],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mhtmlNoteDao(): MhtmlNoteDao
    abstract fun pdfNoteDao(): PdfNoteDao
    abstract fun pageMetaDao(): PageMetaDao
    abstract fun pageGroupDao(): PageGroupDao
    abstract fun generalNoteDao(): GeneralNoteDao

    companion object {
        private const val TAG = "AppDatabase"
        private const val DB_NAME = "noteapp.db"

        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }

        private fun buildDatabase(context: Context): AppDatabase {
            val db = build(context)
            return try {
                // Forces Room's schema identity check right now instead of on the first
                // query from some random screen, so we can recover from it here.
                db.openHelper.writableDatabase
                db
            } catch (e: IllegalStateException) {
                // This should never happen for a real user going through ALL_MIGRATIONS from
                // a consistent prior version — it only happens if a database was left in an
                // inconsistent state (e.g. a dev/test build from before a migration existed
                // for its on-disk schema). Recreating from scratch beats crashing on every launch.
                Log.e(TAG, "Room schema validation failed, recreating the database from scratch", e)
                db.close()
                context.deleteDatabase(DB_NAME)
                build(context)
            }
        }

        private fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DB_NAME)
                .addMigrations(*ALL_MIGRATIONS)
                // Only wipes data if someone installs an OLDER version over a newer DB
                // (e.g. testing an old APK) — normal upgrades always go through ALL_MIGRATIONS.
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
    }
}
