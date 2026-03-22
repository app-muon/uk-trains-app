package com.example.transport_app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.transport_app.data.model.StationEntry

@Dao
interface StationEntryDao {

    @Query("SELECT * FROM station_entries WHERE groupId = :groupId ORDER BY displayOrder ASC")
    suspend fun getByGroup(groupId: Long): List<StationEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<StationEntry>)

    @Delete
    suspend fun delete(entry: StationEntry)

    @Query("DELETE FROM station_entries WHERE groupId = :groupId")
    suspend fun deleteByGroup(groupId: Long)
}
