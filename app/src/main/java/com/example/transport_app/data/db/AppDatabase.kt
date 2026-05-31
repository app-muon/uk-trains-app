package com.example.transport_app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.transport_app.data.model.CachedDeparture
import com.example.transport_app.data.model.Group
import com.example.transport_app.data.model.StationEntry

@Database(
    entities = [Group::class, StationEntry::class, CachedDeparture::class],
    version = 5,
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
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE station_entries ADD COLUMN type TEXT NOT NULL DEFAULT 'train'")
                db.execSQL("ALTER TABLE cached_departures ADD COLUMN type TEXT NOT NULL DEFAULT 'train'")
                db.execSQL("ALTER TABLE cached_departures ADD COLUMN routeNumber TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE station_entries ADD COLUMN dataSource TEXT NOT NULL DEFAULT 'darwin'")
                db.execSQL("ALTER TABLE station_entries ADD COLUMN tflMode TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE station_entries ADD COLUMN direction TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE station_entries ADD COLUMN directionName TEXT DEFAULT NULL")
                db.execSQL("UPDATE station_entries SET dataSource = 'tfl' WHERE type = 'bus'")
                db.execSQL("ALTER TABLE cached_departures ADD COLUMN direction TEXT DEFAULT NULL")
            }
        }
    }
}
