package com.example.bpdigitizer.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.bpdigitizer.domain.model.BPCategory

class BPCategoryConverter {
    @TypeConverter fun fromCategory(value: BPCategory): String = value.name
    @TypeConverter fun toCategory(value: String): BPCategory = BPCategory.valueOf(value)
}

@Database(entities = [BPReadingEntity::class], version = 2, exportSchema = false)
@TypeConverters(BPCategoryConverter::class)
abstract class BPDatabase : RoomDatabase() {
    abstract fun readingDao(): BPReadingDao
}
