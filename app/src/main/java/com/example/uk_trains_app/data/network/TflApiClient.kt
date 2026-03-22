package com.example.uk_trains_app.data.network

import android.util.Log
import com.example.uk_trains_app.BuildConfig
import com.example.uk_trains_app.data.model.BusStop
import com.example.uk_trains_app.data.model.BusStopArrival
import com.example.uk_trains_app.data.model.Departure
import com.example.uk_trains_app.data.model.TransportType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TflApiClient @Inject constructor(
    private val client: OkHttpClient
) {
    private val baseUrl = "https://api.tfl.gov.uk"
    private val apiKey = BuildConfig.TFL_API_KEY

    private fun appendKey(url: String): String {
        if (apiKey.isBlank()) return url
        val separator = if ('?' in url) '&' else '?'
        return "${url}${separator}app_key=$apiKey"
    }

    private fun executeRequest(url: String): String {
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        val body = response.body?.string()
        if (!response.isSuccessful) {
            throw Exception("HTTP ${response.code}: ${body?.take(200) ?: "no body"}")
        }
        return body ?: throw Exception("Empty response body")
    }

    suspend fun searchStops(query: String): List<BusStop> = withContext(Dispatchers.IO) {
        if (query.length < 2) return@withContext emptyList()
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = appendKey("$baseUrl/StopPoint/Search?query=$encoded&stopTypes=NaptanPublicBusCoachTram&maxResults=20")
        val body = executeRequest(url)
        val json = JSONObject(body)
        val matches = json.optJSONArray("matches") ?: return@withContext emptyList()
        val results = mutableListOf<BusStop>()
        for (i in 0 until matches.length()) {
            val match = matches.getJSONObject(i)
            results += BusStop(
                name = match.getString("name"),
                naptanId = match.getString("id")
            )
        }
        results
    }

    private suspend fun resolveChildStops(groupId: String): List<String> {
        val url = appendKey("$baseUrl/StopPoint/$groupId")
        val body = executeRequest(url)
        val json = JSONObject(body)
        val children = json.optJSONArray("children") ?: return listOf(groupId)
        val ids = mutableListOf<String>()
        for (i in 0 until children.length()) {
            ids += children.getJSONObject(i).getString("naptanId")
        }
        if (BuildConfig.DEBUG) Log.d(TAG, "resolveChildStops: $groupId -> $ids")
        return ids.ifEmpty { listOf(groupId) }
    }

    suspend fun getArrivals(naptanId: String, lineFilter: String? = null): List<Departure> =
        withContext(Dispatchers.IO) {
            // Group IDs (490G...) don't return arrivals — resolve to child stops
            val stopIds = if (naptanId.startsWith("490G")) {
                resolveChildStops(naptanId)
            } else {
                listOf(naptanId)
            }

            val allArrivals = mutableListOf<Pair<Departure, Int>>()
            for (stopId in stopIds) {
                allArrivals += fetchArrivalsForStop(stopId, naptanId, lineFilter)
            }
            // Deduplicate by vehicleId when merging multiple child stops
            val seen = mutableSetOf<String>()
            allArrivals.sortBy { it.second }
            allArrivals.filter { seen.add(it.first.serviceId) }.map { it.first }
        }

    private fun fetchArrivalsForStop(
        stopId: String,
        originalId: String,
        lineFilter: String?
    ): List<Pair<Departure, Int>> {
        val url = appendKey("$baseUrl/StopPoint/$stopId/arrivals")
        if (BuildConfig.DEBUG) Log.d(TAG, "fetchArrivals: stopId=$stopId lineFilter=$lineFilter")
        val body = executeRequest(url)
        val arr = JSONArray(body)
        if (BuildConfig.DEBUG) Log.d(TAG, "fetchArrivals: ${arr.length()} arrivals for $stopId")
        val timeFormat = SimpleDateFormat("HH:mm", Locale.UK).apply {
            timeZone = TimeZone.getTimeZone("Europe/London")
        }
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.UK).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val arrivals = mutableListOf<Pair<Departure, Int>>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val lineName = obj.optString("lineName", "")
            if (lineFilter != null && !lineName.equals(lineFilter, ignoreCase = true)) continue
            val timeToStation = obj.optInt("timeToStation", 0)
            val expectedArrival = obj.optString("expectedArrival", "")
            val scheduledTime = try {
                val date = isoFormat.parse(expectedArrival)
                if (date != null) timeFormat.format(date) else ""
            } catch (_: Exception) { "" }
            val minutes = timeToStation / 60
            val estimatedTime = if (minutes <= 0) "Due" else "$minutes min"
            val vehicleId = obj.optString("vehicleId", "").ifEmpty { "$stopId-$i" }
            arrivals += Pair(
                Departure(
                    scheduledTime = scheduledTime,
                    estimatedTime = estimatedTime,
                    platform = null,
                    destination = obj.optString("destinationName", ""),
                    isCancelled = false,
                    originCrs = originalId,
                    originName = obj.optString("stationName", ""),
                    serviceId = vehicleId,
                    routeNumber = lineName,
                    type = TransportType.BUS
                ),
                timeToStation
            )
        }
        return arrivals
    }

    suspend fun getVehicleArrivals(vehicleId: String): List<BusStopArrival> =
        withContext(Dispatchers.IO) {
            val url = appendKey("$baseUrl/Vehicle/$vehicleId/Arrivals")
            if (BuildConfig.DEBUG) Log.d(TAG, "getVehicleArrivals: vehicleId=$vehicleId")
            val body = executeRequest(url)
            val arr = JSONArray(body)
            val timeFormat = SimpleDateFormat("HH:mm", Locale.UK).apply {
                timeZone = TimeZone.getTimeZone("Europe/London")
            }
            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.UK).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val stops = mutableListOf<BusStopArrival>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val expectedArrival = obj.optString("expectedArrival", "")
                val expectedTime = try {
                    val date = isoFormat.parse(expectedArrival)
                    if (date != null) timeFormat.format(date) else ""
                } catch (_: Exception) { "" }
                val timeToStation = obj.optInt("timeToStation", 0)
                val minutes = timeToStation / 60
                stops += BusStopArrival(
                    stopName = obj.optString("stationName", ""),
                    naptanId = obj.optString("naptanId", ""),
                    expectedTime = expectedTime,
                    minutesAway = minutes,
                    towards = obj.optString("towards", ""),
                    lineName = obj.optString("lineName", ""),
                    timeToStation = timeToStation
                )
            }
            stops.sortBy { it.timeToStation }
            stops
        }

    suspend fun getRoutes(naptanId: String): List<String> = withContext(Dispatchers.IO) {
        val url = appendKey("$baseUrl/StopPoint/$naptanId")
        if (BuildConfig.DEBUG) Log.d(TAG, "getRoutes: naptanId=$naptanId")
        val body = executeRequest(url)
        val json = JSONObject(body)

        // "lineModeGroups" is the most reliable source — each group has modeName + lineIdentifier
        val groups = json.optJSONArray("lineModeGroups")
        if (groups != null) {
            val routes = mutableListOf<String>()
            for (i in 0 until groups.length()) {
                val group = groups.getJSONObject(i)
                if (group.optString("modeName") == "bus") {
                    val lineIds = group.optJSONArray("lineIdentifier") ?: continue
                    for (j in 0 until lineIds.length()) {
                        // lineIdentifier can be strings or objects with a "name" field
                        val item = lineIds.get(j)
                        val name = when (item) {
                            is JSONObject -> item.optString("name", "")
                            is String -> item
                            else -> item.toString()
                        }
                        if (name.isNotEmpty()) routes += name
                    }
                }
            }
            if (routes.isNotEmpty()) return@withContext routes.sorted()
        }

        // Fallback: "lines" array (Identifier objects don't have modeName, so take all)
        val lines = json.optJSONArray("lines")
        if (lines != null && lines.length() > 0) {
            val routes = mutableListOf<String>()
            for (i in 0 until lines.length()) {
                val name = lines.getJSONObject(i).optString("name", "")
                if (name.isNotEmpty()) routes += name
            }
            if (routes.isNotEmpty()) return@withContext routes.sorted()
        }

        // Last resort for group stops: resolve a child and retry
        if (naptanId.startsWith("490G")) {
            val children = json.optJSONArray("children")
            if (children != null && children.length() > 0) {
                val childId = children.getJSONObject(0).getString("naptanId")
                if (BuildConfig.DEBUG) Log.d(TAG, "getRoutes: resolving child $childId for $naptanId")
                return@withContext getRoutes(childId)
            }
        }

        emptyList()
    }

    companion object {
        private const val TAG = "TflApiClient"
    }
}
