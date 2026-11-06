package com.mee.noteapp.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Explicit Room migrations, one per schema version bump, so upgrading the app NEVER wipes
 * the user's notes (no `fallbackToDestructiveMigration`). Whenever an entity in
 * `Entities.kt` changes shape, bump `AppDatabase.version` and add a new `MIGRATION_x_y`
 * here that mutates the SQLite schema to match — see the bottom of this file for the
 * step-by-step checklist to follow.
 */

/** v1 -> v2: added `page_meta` (custom name / done flag) and `general_notes`. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `page_meta` (
                `fileName` TEXT NOT NULL,
                `customName` TEXT,
                `completed` INTEGER NOT NULL,
                PRIMARY KEY(`fileName`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `general_notes` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `text` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

/** v2 -> v3: added `page_groups` (collapsible, named groups) and `page_meta.groupId`. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `page_groups` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `collapsed` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("ALTER TABLE `page_meta` ADD COLUMN `groupId` INTEGER")
    }
}

/** v3 -> v4: added `style` ("general" / "important") to both note tables. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `mhtml_notes` ADD COLUMN `style` TEXT NOT NULL DEFAULT 'general'")
        db.execSQL("ALTER TABLE `pdf_notes` ADD COLUMN `style` TEXT NOT NULL DEFAULT 'general'")
    }
}

/** v4 -> v5: added `sizeScale` (manual note resize factor) to both note tables. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `mhtml_notes` ADD COLUMN `sizeScale` REAL NOT NULL DEFAULT 1.0")
        db.execSQL("ALTER TABLE `pdf_notes` ADD COLUMN `sizeScale` REAL NOT NULL DEFAULT 1.0")
    }
}

/**
 * v5 -> v6: added `createdAt` to all three note tables, kept separate from `updatedAt` so
 * editing a note doesn't change its displayed creation date/time. Existing rows get their
 * current `updatedAt` as a best-effort `createdAt` (we have no better data for old notes).
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `mhtml_notes` ADD COLUMN `createdAt` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE `mhtml_notes` SET `createdAt` = `updatedAt`")
        db.execSQL("ALTER TABLE `pdf_notes` ADD COLUMN `createdAt` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE `pdf_notes` SET `createdAt` = `updatedAt`")
        db.execSQL("ALTER TABLE `general_notes` ADD COLUMN `createdAt` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE `general_notes` SET `createdAt` = `updatedAt`")
    }
}

/** v6 -> v7: added `sourcePage` (which MHTML page/PDF a general note was created from). */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `general_notes` ADD COLUMN `sourcePage` TEXT")
    }
}

val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)

/*
 * CHECKLIST — adding a new migration when an entity changes:
 * 1. Edit the data class in Entities.kt (add/remove/rename a column or table).
 *    - A new NOT NULL column being added to an EXISTING table needs a Kotlin default
 *      value AND a matching `@ColumnInfo(defaultValue = "...")` — the string must be the
 *      exact SQL literal you'll use in the `ALTER TABLE ... DEFAULT ...` below (quote text
 *      literals, e.g. "'general'"; leave numbers bare, e.g. "1.0").
 * 2. Bump `version` in the @Database annotation in AppDatabase.kt.
 * 3. Add a new `val MIGRATION_(old)_(new) = object : Migration(old, new) { ... }` above,
 *    with the SQL that turns the OLD schema into the NEW one (CREATE TABLE for new tables,
 *    ALTER TABLE ... ADD COLUMN for new columns — SQLite can't drop/rename columns easily,
 *    so avoid removing fields if you can).
 * 4. Add it to the `ALL_MIGRATIONS` array above.
 * 5. Rebuild and run the app on a device/emulator that still has the OLD version installed
 *    (don't uninstall first!) to confirm the migration runs cleanly over real data.
 */
