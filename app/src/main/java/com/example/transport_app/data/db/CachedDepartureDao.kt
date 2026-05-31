package com.example.transport_app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.example.transport_app.data.model.CachedDeparture

@Dao
interface CachedDepartureDao {

    @Query("""
        SELECT * FROM cached_departures
        WHERE groupId = :groupId AND crsCode = :crsCode
          AND (filterCrs = :filterCrs OR (filterCrs IS NULL AND :filterCrs IS NULL))
          AND (direction = :direction OR (direction IS NULL AND :direction IS NULL))
    """)
    suspend fun getByStation(groupId: Long, crsCode: String, filterCrs: String?, direction: String?): List<CachedDeparture>

    @Query("SELECT MIN(cachedAt) FROM cached_departures WHERE groupId = :groupId")
    suspend fun getCachedTimestamp(groupId: Long): Long?

    @Insert
    suspend fun insertAll(departures: List<CachedDeparture>)

    @Query("DELETE FROM cached_departures WHERE groupId = :groupId AND crsCode = :crsCode AND (filterCrs = :filterCrs OR (filterCrs IS NULL AND :filterCrs IS NULL)) AND (direction = :direction OR (direction IS NULL AND :direction IS NULL))")
    suspend fun deleteByStation(groupId: Long, crsCode: String, filterCrs: String?, direction: String?)

    @Query("DELETE FROM cached_departures WHERE groupId = :groupId")
    suspend fun deleteByGroup(groupId: Long)

    @Query("DELETE FROM cached_departures WHERE cachedAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Transaction
    suspend fun replaceForStation(
        groupId: Long,
        crsCode: String,
        filterCrs: String?,
        direction: String?,
        departures: List<CachedDeparture>
    ) {
        deleteByStation(groupId, crsCode, filterCrs, direction)
        insertAll(departures)
    }
}
