package com.example.transport_app.data.repository

import com.example.transport_app.data.model.BusStop
import com.example.transport_app.data.network.TflApiClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BusStopRepository @Inject constructor(
    private val tflApiClient: TflApiClient
) {
    suspend fun search(query: String): List<BusStop> = tflApiClient.searchStops(query)
}
