package com.example.uk_trains_app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.uk_trains_app.data.model.Group
import com.example.uk_trains_app.data.model.StationEntry

@Database(
    entities = [Group::class, StationEntry::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun groupDao(): GroupDao
    abstract fun stationEntryDao(): StationEntryDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE station_entries ADD COLUMN filterCrs TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE station_entries ADD COLUMN filterName TEXT DEFAULT NULL")
            }
        }
    }
}
