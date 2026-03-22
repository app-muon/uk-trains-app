package com.example.uk_trains_app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.example.uk_trains_app.data.model.CachedDeparture

@Dao
interface CachedDepartureDao {

    @Query("""
        SELECT * FROM cached_departures
        WHERE groupId = :groupId AND crsCode = :crsCode
          AND (filterCrs = :filterCrs OR (filterCrs IS NULL AND :filterCrs IS NULL))
        ORDER BY scheduledTime ASC
    """)
    suspend fun getByStation(groupId: Long, crsCode: String, filterCrs: String?): List<CachedDeparture>

    @Query("SELECT cachedAt FROM cached_departures WHERE groupId = :groupId LIMIT 1")
    suspend fun getCachedTimestamp(groupId: Long): Long?

    @Insert
    suspend fun insertAll(departures: List<CachedDeparture>)

    @Query("DELETE FROM cached_departures WHERE groupId = :groupId AND crsCode = :crsCode AND (filterCrs = :filterCrs OR (filterCrs IS NULL AND :filterCrs IS NULL))")
    suspend fun deleteByStation(groupId: Long, crsCode: String, filterCrs: String?)

    @Query("DELETE FROM cached_departures WHERE groupId = :groupId")
    suspend fun deleteByGroup(groupId: Long)

    @Transaction
    suspend fun replaceForStation(groupId: Long, crsCode: String, filterCrs: String?, departures: List<CachedDeparture>) {
        deleteByStation(groupId, crsCode, filterCrs)
        insertAll(departures)
    }
}
