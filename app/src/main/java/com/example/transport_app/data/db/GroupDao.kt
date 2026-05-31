package com.example.transport_app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.transport_app.data.model.Group
import com.example.transport_app.data.model.GroupWithCount
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {

    @Query(
        """SELECT g.id, g.name, COUNT(s.id) as stationCount
           FROM station_groups g
           LEFT JOIN station_entries s ON s.groupId = g.id
           GROUP BY g.id
           ORDER BY g.displayOrder ASC"""
    )
    fun observeGroupsWithCount(): Flow<List<GroupWithCount>>

    @Query("SELECT * FROM station_groups WHERE id = :id")
    suspend fun getById(id: Long): Group?

    @Query("SELECT MAX(displayOrder) FROM station_groups")
    suspend fun maxDisplayOrder(): Int?

    @Insert
    suspend fun insert(group: Group): Long

    @Update
    suspend fun update(group: Group)

    @Query("UPDATE station_groups SET displayOrder = :displayOrder WHERE id = :id")
    suspend fun updateDisplayOrder(id: Long, displayOrder: Int)

    @Delete
    suspend fun delete(group: Group)
}
