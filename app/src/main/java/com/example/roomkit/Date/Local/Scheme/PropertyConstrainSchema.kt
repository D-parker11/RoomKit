package com.roomkit.Date.Local.Scheme

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object PropertyConstraintSchema {
    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            installGuards(db)
        }
    }

    fun installGuards(db: SupportSQLiteDatabase) {
        // Normalize the definition on both fresh installs and upgrades.
        // Existing rows, including legacy databases with multiple properties,
        // are preserved. Further property inserts are blocked.
        db.execSQL("DROP TRIGGER IF EXISTS one_property_only")
        db.execSQL("""
            CREATE TRIGGER one_property_only BEFORE INSERT ON buildings
            WHEN EXISTS(SELECT 1 FROM buildings)
            BEGIN SELECT RAISE(ABORT, 'Only one property is supported. Edit your existing property.'); END
        """.trimIndent())
    }
}
