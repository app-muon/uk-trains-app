package com.example.transport_app.data.repository

import androidx.room.withTransaction
import com.example.transport_app.data.db.AppDatabase
import com.example.transport_app.data.db.CachedDepartureDao
import com.example.transport_app.data.db.GroupDao
import com.example.transport_app.data.db.StationEntryDao
import com.example.transport_app.data.model.Group
import com.example.transport_app.data.model.GroupWithCount
import com.example.transport_app.data.model.StationEntry
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupRepository @Inject constructor(
    private val db: AppDatabase,
    private val groupDao: GroupDao,
    private val stationEntryDao: StationEntryDao,
    private val cachedDepartureDao: CachedDepartureDao
) {
    fun observeGroupsWithCount(): Flow<List<GroupWithCount>> =
        groupDao.observeGroupsWithCount()

    suspend fun getGroup(id: Long): Group? = groupDao.getById(id)

    suspend fun getStations(groupId: Long): List<StationEntry> =
        stationEntryDao.getByGroup(groupId)

    suspend fun createGroup(name: String): Long {
        val order = (groupDao.maxDisplayOrder() ?: -1) + 1
        return groupDao.insert(Group(name = name, displayOrder = order))
    }

    suspend fun renameGroup(id: Long, newName: String) {
        val group = groupDao.getById(id) ?: return
        groupDao.update(group.copy(name = newName))
    }

    suspend fun reorderGroups(groupIds: List<Long>) {
        db.withTransaction {
            groupIds.forEachIndexed { index, id ->
                groupDao.updateDisplayOrder(id, index)
            }
        }
    }

    suspend fun deleteGroup(id: Long) {
        val group = groupDao.getById(id) ?: return
        cachedDepartureDao.deleteByGroup(id)
        groupDao.delete(group)
    }

    suspend fun saveStations(groupId: Long, stations: List<StationEntry>) {
        val ordered = stations.mapIndexed { index, entry ->
            entry.copy(groupId = groupId, displayOrder = index)
        }
        db.withTransaction {
            stationEntryDao.deleteByGroup(groupId)
            stationEntryDao.insertAll(ordered)
        }
    }
}
