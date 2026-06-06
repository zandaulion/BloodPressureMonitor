package com.zandaulion.bpdigitized.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.zandaulion.bpdigitized.domain.model.BPCategory

class BPCategoryConverter {
    @TypeConverter fun fromCategory(value: BPCategory): String = value.name
    @TypeConverter fun toCategory(value: String): BPCategory = BPCategory.valueOf(value)
}

@Database(entities = [BPReadingEntity::class], version = 3, exportSchema = true)
@TypeConverters(BPCategoryConverter::class)
abstract class BPDatabase : RoomDatabase() {
    abstract fun readingDao(): BPReadingDao

    companion object {
        /**
         * v2 → v3: adds the `tags` column for structured contextual tags.
         *
         * `ADD COLUMN ... NOT NULL` requires a DEFAULT so existing rows are
         * backfilled (here, the empty string = no tags). The `DEFAULT ''` here
         * MUST match `@ColumnInfo(defaultValue = "''")` on
         * [BPReadingEntity.tags], or Room's post-migration validation throws.
         *
         * This is a pure additive ALTER TABLE — no data is read, moved, or
         * dropped, so every existing reading is preserved untouched.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE bp_readings ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
