package com.example.uk_trains_app.data.repository

import com.example.uk_trains_app.data.model.BusStop
import com.example.uk_trains_app.data.network.TflApiClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BusStopRepository @Inject constructor(
    private val tflApiClient: TflApiClient
) {
    suspend fun search(query: String): List<BusStop> = tflApiClient.searchStops(query)
}
