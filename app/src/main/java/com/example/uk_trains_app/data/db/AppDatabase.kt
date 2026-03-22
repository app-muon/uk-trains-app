package com.example.uk_trains_app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.uk_trains_app.data.model.CachedDeparture
import com.example.uk_trains_app.data.model.Group
import com.example.uk_trains_app.data.model.StationEntry

@Database(
    entities = [Group::class, StationEntry::class, CachedDeparture::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun groupDao(): GroupDao
    abstract fun stationEntryDao(): StationEntryDao
    abstract fun cachedDepartureDao(): CachedDepartureDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE station_entries ADD COLUMN filterCrs TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE station_entries ADD COLUMN filterName TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS cached_departures (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        groupId INTEGER NOT NULL,
                        crsCode TEXT NOT NULL,
                        filterCrs TEXT,
                        scheduledTime TEXT NOT NULL,
                        estimatedTime TEXT NOT NULL,
                        platform TEXT,
                        destination TEXT NOT NULL,
                        isCancelled INTEGER NOT NULL,
                        originCrs TEXT NOT NULL,
                        originName TEXT NOT NULL,
                        serviceId TEXT NOT NULL,
                        cachedAt INTEGER NOT NULL
                    )
                """)
            }
        }
    }
}
