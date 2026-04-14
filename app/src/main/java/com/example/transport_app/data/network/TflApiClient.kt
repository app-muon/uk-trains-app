package com.example.transport_app.data.network

import android.util.Log
import com.example.transport_app.BuildConfig
import com.example.transport_app.data.model.BusStop
import com.example.transport_app.data.model.BusStopArrival
import com.example.transport_app.data.model.Departure
import com.example.transport_app.data.model.TransportType
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
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TflApiClient @Inject constructor(
    private val client: OkHttpClient
) {
    private val baseUrl = "https://api.tfl.gov.uk"
    private val apiKey = BuildConfig.TFL_API_KEY

    /**
     * Unified cache for group/hub stop data. A single /StopPoint/{id} response
     * provides child IDs, child BusStop details, AND bus routes — so we parse once
     * and serve all three features (arrivals, direction picker, route filter) from cache.
     */
    private data class StopPointInfo(
        val childBusStops: List<BusStop>,
        val busRoutes: List<String>
    )

    private val stopPointCache = ConcurrentHashMap<String, StopPointInfo>()

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

    // region --- Stop point info (unified cache) ---

    /**
     * Fetch and cache all bus-relevant data for a group/hub stop in one API call per level.
     * Recursively expands nested groups (HUB → 490G → individual stops).
     * Results are cached in memory so subsequent calls avoid API requests.
     */
    private suspend fun fetchStopPointInfo(
        groupId: String,
        visited: MutableSet<String> = mutableSetOf()
    ): StopPointInfo {
        stopPointCache[groupId]?.let {
            if (BuildConfig.DEBUG) Log.d(TAG, "fetchStopPointInfo (cached): $groupId -> ${it.childBusStops.size} busStops, ${it.busRoutes.size} routes")
            return it
        }

        // Cycle detection: if we've already visited this ID, treat it as a leaf
        if (!visited.add(groupId)) {
            if (BuildConfig.DEBUG) Log.w(TAG, "fetchStopPointInfo: cycle detected for $groupId, treating as leaf")
            val leaf = StopPointInfo(
                childBusStops = emptyList(),
                busRoutes = emptyList()
            )
            stopPointCache[groupId] = leaf
            return leaf
        }

        if (BuildConfig.DEBUG) Log.d(TAG, "fetchStopPointInfo: fetching $groupId from API")

        val url = appendKey("$baseUrl/StopPoint/$groupId")
        val body = executeRequest(url)
        val json = JSONObject(body)

        // Detect API redirects (e.g. 490G000682 -> HUBNGW)
        val responseId = json.optString("naptanId", groupId)
        if (BuildConfig.DEBUG && responseId != groupId) {
            Log.d(TAG, "fetchStopPointInfo: API redirected $groupId -> $responseId")
        }

        val busRoutes = parseBusRoutes(json).toMutableList()
        val children = json.optJSONArray("children")
        if (BuildConfig.DEBUG) {
            val childCount = children?.length() ?: 0
            val allChildIds = mutableListOf<String>()
            if (children != null) {
                for (i in 0 until children.length()) {
                    allChildIds += children.getJSONObject(i).optString("naptanId", "???")
                }
            }
            Log.d(TAG, "fetchStopPointInfo: $groupId has $childCount raw children: $allChildIds")
        }

        val childBusStops = mutableListOf<BusStop>()

        if (children != null) {
            for (i in 0 until children.length()) {
                val child = children.getJSONObject(i)
                val childId = child.getString("naptanId")
                if (!childId.startsWith("490")) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "fetchStopPointInfo: skipping non-bus child $childId")
                    continue
                }

                if (isGroupStop(childId)) {
                    // Check if this group child has nested children in the response.
                    // This handles two cases:
                    // 1. API redirects (e.g. 490G000682 -> HUBNGW): the response includes
                    //    490G000682 as a child WITH its children array of individual stops.
                    // 2. Self-references: 490G000682 lists itself as its own child, but
                    //    the nested children array contains the actual individual bus stops.
                    val nestedChildren = child.optJSONArray("children")
                    if (nestedChildren != null && nestedChildren.length() > 0) {
                        if (BuildConfig.DEBUG) Log.d(TAG, "fetchStopPointInfo: parsing ${nestedChildren.length()} nested children of $childId inline")
                        // Prefer the child group's own routes over hub-level routes.
                        // When API redirects (e.g. 490G000682 -> HUBNGW), the top-level
                        // lineModeGroups belong to the hub and may include routes from
                        // other bus groups. The child group's routes are specific to
                        // the stop the user actually selected.
                        val nestedRoutes = parseBusRoutes(child)
                        if (nestedRoutes.isNotEmpty()) {
                            busRoutes.clear()
                            busRoutes += nestedRoutes
                        }
                        for (j in 0 until nestedChildren.length()) {
                            val nested = nestedChildren.getJSONObject(j)
                            val nestedId = nested.getString("naptanId")
                            if (!nestedId.startsWith("490") || isGroupStop(nestedId)) continue
                            childBusStops += BusStop(
                                name = nested.optString("commonName", ""),
                                naptanId = nestedId,
                                indicator = nested.optString("indicator", "").ifEmpty { null },
                                towards = parseTowards(nested)
                            )
                        }
                    } else if (childId == groupId) {
                        // Self-reference with no nested children — skip to prevent infinite recursion
                        if (BuildConfig.DEBUG) Log.d(TAG, "fetchStopPointInfo: skipping self-reference $childId (no nested children)")
                    } else {
                        // Different group child with no nested children — fetch via API
                        try {
                            val childInfo = fetchStopPointInfo(childId, visited)
                            childBusStops += childInfo.childBusStops
                            if (busRoutes.isEmpty()) busRoutes += childInfo.busRoutes
                        } catch (e: Exception) {
                            if (BuildConfig.DEBUG) Log.w(TAG, "Failed to resolve nested group $childId: ${e.message}")
                        }
                    }
                } else {
                    childBusStops += BusStop(
                        name = child.optString("commonName", ""),
                        naptanId = childId,
                        indicator = child.optString("indicator", "").ifEmpty { null },
                        towards = parseTowards(child)
                    )
                }
            }
        }

        val info = StopPointInfo(
            childBusStops = childBusStops,
            busRoutes = busRoutes.distinct().sorted()
        )

        stopPointCache[groupId] = info

        if (BuildConfig.DEBUG) Log.d(TAG, "fetchStopPointInfo: $groupId -> ${info.childBusStops.size} busStops, ${info.busRoutes.size} routes")
        return info
    }

    private fun parseBusRoutes(json: JSONObject): List<String> {
        val groups = json.optJSONArray("lineModeGroups") ?: return emptyList()
        val routes = mutableListOf<String>()
        for (i in 0 until groups.length()) {
            val group = groups.getJSONObject(i)
            if (group.optString("modeName") == "bus") {
                val lineIds = group.optJSONArray("lineIdentifier") ?: continue
                for (j in 0 until lineIds.length()) {
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
        return routes
    }

    private fun parseTowards(child: JSONObject): String? {
        val additionalProps = child.optJSONArray("additionalProperties") ?: return null
        for (j in 0 until additionalProps.length()) {
            val prop = additionalProps.getJSONObject(j)
            if (prop.optString("key") == "Towards") {
                return prop.optString("value", "").ifEmpty { null }
            }
        }
        return null
    }

    // endregion

    // region --- Public API ---

    suspend fun searchStops(query: String): List<BusStop> = withContext(Dispatchers.IO) {
        if (query.length < 2) return@withContext emptyList()

        // If the query looks like a NaPTAN ID (e.g. "490014934E" copied from the stop info card),
        // resolve it directly — the Search API only searches by name, not by ID.
        if (query.matches(Regex("490[A-Z0-9]+", RegexOption.IGNORE_CASE))) {
            return@withContext try {
                listOf(getStopInfo(query.uppercase()))
            } catch (_: Exception) {
                emptyList()
            }
        }

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
                naptanId = match.getString("id"),
                towards = match.optString("towards", "").ifEmpty { null },
                parentId = match.optString("parentId", "").ifEmpty { null }
            )
        }
        results
    }

    suspend fun getArrivals(naptanId: String, lineFilter: String? = null): List<Departure> =
        withContext(Dispatchers.IO) {
            if (BuildConfig.DEBUG) Log.d(TAG, "getArrivals: naptanId=$naptanId lineFilter=$lineFilter")
            // Use naptanId directly — the TfL arrivals endpoint handles both individual
            // stops and group IDs (490G...) natively in a single call.
            // When the user picks a specific direction, naptanId is already the individual
            // stop ID. When they pick "All directions", it's the group ID.
            val arrivals = fetchArrivalsForStop(naptanId, naptanId, lineFilter)
            arrivals.sortedBy { it.second }.map { it.first }
        }

    suspend fun getRoutes(naptanId: String): List<String> = withContext(Dispatchers.IO) {
        if (BuildConfig.DEBUG) Log.d(TAG, "getRoutes: naptanId=$naptanId isGroup=${isGroupStop(naptanId)}")
        if (isGroupStop(naptanId)) {
            // Unified cache: one API call gives us children AND routes
            return@withContext try {
                val routes = fetchStopPointInfo(naptanId).busRoutes
                if (BuildConfig.DEBUG) Log.d(TAG, "getRoutes: group $naptanId -> ${routes.size} routes: $routes")
                routes
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.w(TAG, "Failed to get routes for $naptanId: ${e.message}")
                emptyList()
            }
        }

        // Individual stop: /StopPoint/{id} often redirects to a parent hub, returning
        // all hub routes instead of this stop's routes. Go directly to /arrivals which
        // doesn't redirect and returns only buses actually serving this specific stop.
        if (BuildConfig.DEBUG) Log.d(TAG, "getRoutes: using arrivals to discover routes for $naptanId")
        val arrivalsUrl = appendKey("$baseUrl/StopPoint/$naptanId/arrivals")
        val arrivalsBody = executeRequest(arrivalsUrl)
        val arr = JSONArray(arrivalsBody)
        val discoveredRoutes = mutableSetOf<String>()
        for (i in 0 until arr.length()) {
            val lineName = arr.getJSONObject(i).optString("lineName", "")
            if (lineName.isNotEmpty()) discoveredRoutes += lineName
        }
        if (BuildConfig.DEBUG) Log.d(TAG, "getRoutes: discovered from arrivals=$discoveredRoutes")
        discoveredRoutes.sorted()
    }

    /**
     * Enriches a list of individual stops by fetching each one's indicator and towards fields
     * from /StopPoint/{id}. Used when search results return multiple stops with the same name.
     */
    suspend fun enrichStops(stops: List<BusStop>): List<BusStop> = withContext(Dispatchers.IO) {
        stops.map { stop ->
            try {
                val url = appendKey("$baseUrl/StopPoint/${stop.naptanId}")
                val body = executeRequest(url)
                val json = JSONObject(body)
                val indicator = json.optString("indicator", "").ifEmpty { null }
                val towards = parseTowards(json)
                if (BuildConfig.DEBUG) Log.d(TAG, "enrichStops: ${stop.naptanId} indicator=$indicator towards=$towards")
                // Preserve existing towards if the API doesn't return one (individual stops
                // often lack additionalProperties but the search response already had towards).
                stop.copy(indicator = indicator, towards = towards ?: stop.towards)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.w(TAG, "enrichStops: failed for ${stop.naptanId}: ${e.message}")
                stop
            }
        }
    }

    suspend fun getStopInfo(naptanId: String): BusStop = withContext(Dispatchers.IO) {
        val url = appendKey("$baseUrl/StopPoint/$naptanId")
        val body = executeRequest(url)
        val json = JSONObject(body)
        BusStop(
            name = json.optString("commonName", ""),
            naptanId = naptanId,
            indicator = json.optString("indicator", "").ifEmpty { null },
            towards = parseTowards(json)
        )
    }

    suspend fun getChildStops(groupId: String): List<BusStop> = withContext(Dispatchers.IO) {
        if (BuildConfig.DEBUG) Log.d(TAG, "getChildStops: groupId=$groupId")
        val result = fetchStopPointInfo(groupId).childBusStops
        if (BuildConfig.DEBUG) Log.d(TAG, "getChildStops: $groupId -> ${result.size} bus stops")
        result
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

    // endregion

    // region --- Private helpers ---

    private fun fetchArrivalsForStop(
        stopId: String,
        originalId: String,
        lineFilter: String?
    ): List<Pair<Departure, Int>> {
        val url = appendKey("$baseUrl/StopPoint/$stopId/arrivals")
        if (BuildConfig.DEBUG) Log.d(TAG, "fetchArrivals: stopId=$stopId originalId=$originalId lineFilter=$lineFilter")
        val body = executeRequest(url)
        val arr = JSONArray(body)
        if (BuildConfig.DEBUG) {
            val lineNames = mutableSetOf<String>()
            for (k in 0 until arr.length()) {
                lineNames += arr.getJSONObject(k).optString("lineName", "")
            }
            Log.d(TAG, "fetchArrivals: ${arr.length()} arrivals for $stopId, lines=$lineNames")
        }
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
        if (BuildConfig.DEBUG) Log.d(TAG, "fetchArrivals: returning ${arrivals.size} departures for $stopId (filter=$lineFilter)")
        return arrivals
    }

    // endregion

    companion object {
        private const val TAG = "TflApiClient"

        fun isGroupStop(naptanId: String): Boolean =
            naptanId.startsWith("490G") || naptanId.startsWith("HUB")
    }
}
