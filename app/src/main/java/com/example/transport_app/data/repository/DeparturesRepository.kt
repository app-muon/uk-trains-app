package com.example.transport_app.data.repository

import android.util.Log
import com.example.transport_app.BuildConfig
import com.example.transport_app.data.db.CachedDepartureDao
import com.example.transport_app.data.model.CachedDeparture
import com.example.transport_app.data.model.Departure
import com.example.transport_app.data.model.StationEntry
import com.example.transport_app.data.model.TransportType
import com.example.transport_app.data.network.DarwinSoapClient
import com.example.transport_app.data.network.TflApiClient
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

sealed class StationResult {
    data class Success(
        val departures: List<Departure>,
        val messages: List<String> = emptyList(),
        val fromCache: Boolean = false
    ) : StationResult()
    data class Error(val crs: String, val message: String) : StationResult()
}

@Singleton
class DeparturesRepository @Inject constructor(
    private val soapClient: DarwinSoapClient,
    private val tflApiClient: TflApiClient,
    private val cachedDepartureDao: CachedDepartureDao
) {
    suspend fun fetchAll(
        stations: List<StationEntry>,
        groupId: Long,
        timeOffset: Int = 0
    ): List<StationResult> {
        // Evict cache entries older than 6 hours
        try {
            cachedDepartureDao.deleteOlderThan(System.currentTimeMillis() - 6 * 60 * 60 * 1000L)
        } catch (e: Exception) {
            Log.w("DeparturesRepository", "Cache eviction failed", e)
        }

        val trainStations = stations.filter { it.type == TransportType.TRAIN }
        val busStops = stations.filter { it.type == TransportType.BUS }

        val trainResults = fetchTrains(trainStations, groupId, timeOffset)
        val busResults = fetchBuses(busStops, groupId)

        // Reassemble in original order
        val trainMap = trainStations.zip(trainResults).toMap()
        val busMap = busStops.zip(busResults).toMap()
        return stations.map { trainMap[it] ?: busMap[it] ?: StationResult.Error(it.crsCode, "Unknown type") }
    }

    private suspend fun fetchTrains(
        stations: List<StationEntry>,
        groupId: Long,
        timeOffset: Int
    ): List<StationResult> {
        if (stations.isEmpty()) return emptyList()
        val results = mutableListOf<StationResult>()
        val chunks = stations.chunked(5)
        chunks.forEachIndexed { chunkIndex, chunk ->
            if (chunkIndex > 0) delay(1_000L)
            val chunkResults = coroutineScope {
                chunk.map { station ->
                    async { fetchSingleTrain(station, groupId, timeOffset) }
                }.map { it.await() }
            }
            results += chunkResults
        }
        return results
    }

    private suspend fun fetchSingleTrain(
        station: StationEntry,
        groupId: Long,
        timeOffset: Int
    ): StationResult = try {
        val result = soapClient.getDepartureBoard(
            crsCode = station.crsCode,
            originName = station.stationName,
            filterCrs = station.filterCrs,
            timeOffset = timeOffset
        )
        if (timeOffset == 0) {
            cacheResults(result.departures, groupId, station)
        }
        StationResult.Success(result.departures, result.messages)
    } catch (e: Exception) {
        Log.e("DeparturesRepository", "Error fetching ${station.crsCode}", e)
        fallbackToCache(station, groupId, e)
    }

    private suspend fun fetchBuses(
        stations: List<StationEntry>,
        groupId: Long
    ): List<StationResult> {
        if (stations.isEmpty()) return emptyList()
        return coroutineScope {
            stations.map { station ->
                async { fetchSingleBus(station, groupId) }
            }.map { it.await() }
        }
    }

    private suspend fun fetchSingleBus(
        station: StationEntry,
        groupId: Long
    ): StationResult = try {
        if (BuildConfig.DEBUG) Log.d("DeparturesRepository", "fetchSingleBus: naptanId=${station.crsCode} filter=${station.filterCrs} type=${station.type}")
        val departures = tflApiClient.getArrivals(station.crsCode, station.filterCrs)
        if (BuildConfig.DEBUG) Log.d("DeparturesRepository", "fetchSingleBus: got ${departures.size} departures for ${station.crsCode}")
        cacheResults(departures, groupId, station)
        StationResult.Success(departures)
    } catch (e: Exception) {
        Log.e("DeparturesRepository", "Error fetching bus ${station.crsCode}", e)
        fallbackToCache(station, groupId, e)
    }

    private suspend fun cacheResults(
        departures: List<Departure>,
        groupId: Long,
        station: StationEntry
    ) {
        try {
            val now = System.currentTimeMillis()
            val cached = departures.map {
                CachedDeparture.from(it, groupId, station.crsCode, station.filterCrs, now)
            }
            cachedDepartureDao.replaceForStation(groupId, station.crsCode, station.filterCrs, cached)
        } catch (cacheEx: Exception) {
            Log.e("DeparturesRepository", "Failed to cache ${station.crsCode}", cacheEx)
        }
    }

    private suspend fun fallbackToCache(
        station: StationEntry,
        groupId: Long,
        originalError: Exception
    ): StationResult = try {
        val cached = cachedDepartureDao.getByStation(groupId, station.crsCode, station.filterCrs)
        if (cached.isNotEmpty()) {
            StationResult.Success(departures = cached.map { it.toDeparture() }, fromCache = true)
        } else {
            StationResult.Error(crs = station.crsCode, message = originalError.message ?: "Unknown error")
        }
    } catch (cacheException: Exception) {
        Log.e("DeparturesRepository", "Cache fallback failed for ${station.crsCode}", cacheException)
        StationResult.Error(crs = station.crsCode, message = originalError.message ?: "Unknown error")
    }

    suspend fun getCachedTimestamp(groupId: Long): Long? =
        cachedDepartureDao.getCachedTimestamp(groupId)
}
