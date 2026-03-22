package com.example.uk_trains_app.data.repository

import android.util.Log
import com.example.uk_trains_app.data.db.CachedDepartureDao
import com.example.uk_trains_app.data.model.CachedDeparture
import com.example.uk_trains_app.data.model.StationEntry
import com.example.uk_trains_app.data.network.DarwinSoapClient
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

sealed class StationResult {
    data class Success(
        val departures: List<com.example.uk_trains_app.data.model.Departure>,
        val messages: List<String> = emptyList(),
        val fromCache: Boolean = false
    ) : StationResult()
    data class Error(val crs: String, val message: String) : StationResult()
}

@Singleton
class DeparturesRepository @Inject constructor(
    private val soapClient: DarwinSoapClient,
    private val cachedDepartureDao: CachedDepartureDao
) {
    suspend fun fetchAll(
        stations: List<StationEntry>,
        groupId: Long,
        timeOffset: Int = 0
    ): List<StationResult> {
        val results = mutableListOf<StationResult>()
        val chunks = stations.chunked(5)
        chunks.forEachIndexed { chunkIndex, chunk ->
            if (chunkIndex > 0) delay(1_000L)
            val chunkResults = coroutineScope {
                chunk.map { station ->
                    async {
                        try {
                            val result = soapClient.getDepartureBoard(
                                crsCode = station.crsCode,
                                originName = station.stationName,
                                filterCrs = station.filterCrs,
                                timeOffset = timeOffset
                            )
                            // Cache on success (only for initial loads, not load-more)
                            if (timeOffset == 0) {
                                try {
                                    val now = System.currentTimeMillis()
                                    val cached = result.departures.map {
                                        CachedDeparture.from(it, groupId, station.crsCode, station.filterCrs, now)
                                    }
                                    cachedDepartureDao.replaceForStation(groupId, station.crsCode, station.filterCrs, cached)
                                } catch (cacheEx: Exception) {
                                    Log.e("DeparturesRepository", "Failed to cache ${station.crsCode}", cacheEx)
                                }
                            }
                            StationResult.Success(result.departures, result.messages)
                        } catch (e: Exception) {
                            Log.e("DeparturesRepository", "Error fetching ${station.crsCode}", e)
                            // Fall back to cache
                            try {
                                val cached = cachedDepartureDao.getByStation(groupId, station.crsCode, station.filterCrs)
                                if (cached.isNotEmpty()) {
                                    StationResult.Success(
                                        departures = cached.map { it.toDeparture() },
                                        fromCache = true
                                    )
                                } else {
                                    StationResult.Error(
                                        crs = station.crsCode,
                                        message = e.message ?: "Unknown error"
                                    )
                                }
                            } catch (cacheException: Exception) {
                                Log.e("DeparturesRepository", "Cache fallback failed for ${station.crsCode}", cacheException)
                                StationResult.Error(
                                    crs = station.crsCode,
                                    message = e.message ?: "Unknown error"
                                )
                            }
                        }
                    }
                }.map { it.await() }
            }
            results += chunkResults
        }
        return results
    }

    suspend fun getCachedTimestamp(groupId: Long): Long? =
        cachedDepartureDao.getCachedTimestamp(groupId)
}
