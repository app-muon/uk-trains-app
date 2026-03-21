package com.example.uk_trains_app.data.repository

import android.util.Log
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
        val messages: List<String> = emptyList()
    ) : StationResult()
    data class Error(val crs: String, val message: String) : StationResult()
}

@Singleton
class DeparturesRepository @Inject constructor(
    private val soapClient: DarwinSoapClient
) {
    suspend fun fetchAll(stations: List<StationEntry>, timeOffset: Int = 0): List<StationResult> {
        val results = mutableListOf<StationResult>()
        // Stay under 5 req/sec — chunk into groups of 5 with a 1-second gap between chunks
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
                            StationResult.Success(result.departures, result.messages)
                        } catch (e: Exception) {
                            Log.e("DeparturesRepository", "Error fetching ${station.crsCode}", e)
                            StationResult.Error(
                                crs = station.crsCode,
                                message = e.message ?: "Unknown error"
                            )
                        }
                    }
                }.map { it.await() }
            }
            results += chunkResults
        }
        return results
    }
}
