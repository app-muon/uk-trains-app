package com.example.transport_app.data.repository

import android.content.Context
import com.example.transport_app.data.model.Station
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StationRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val allStations: List<Station> by lazy { loadFromAssets() }

    private fun loadFromAssets(): List<Station> =
        context.assets.open("stations.csv").bufferedReader().readLines()
            .drop(1) // skip header
            .mapNotNull { line ->
                val parts = line.split(",")
                if (parts.size >= 2) Station(parts[0].trim(), parts[1].trim()) else null
            }
            .sortedBy { it.name }

    fun search(query: String): List<Station> {
        if (query.length < 2) return emptyList()
        return allStations
            .filter { it.name.contains(query, ignoreCase = true) }
            .take(20)
    }

}
