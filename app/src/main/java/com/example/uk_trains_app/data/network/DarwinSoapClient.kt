package com.example.uk_trains_app.data.network

import android.util.Log
import com.example.uk_trains_app.BuildConfig
import com.example.uk_trains_app.data.model.CallingPoint
import com.example.uk_trains_app.data.model.Departure
import com.example.uk_trains_app.data.model.ServiceDetail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import javax.inject.Inject
import javax.xml.parsers.DocumentBuilderFactory

data class BoardResult(
    val departures: List<Departure>,
    val messages: List<String> = emptyList()
)

class DarwinSoapClient @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private val endpoint = "https://lite.realtime.nationalrail.co.uk/OpenLDBWS/ldb12.asmx"
    private val apiKey = BuildConfig.DARWIN_API_KEY

    suspend fun getDepartureBoard(
        crsCode: String,
        numRows: Int = 10,
        originCrs: String = crsCode,
        originName: String = crsCode,
        filterCrs: String? = null,
        timeOffset: Int = 0
    ): BoardResult = withContext(Dispatchers.IO) {
        if (filterCrs != null) {
            val soap = buildBoardWithDetailsEnvelope(crsCode, numRows, filterCrs, timeOffset)
            val body = executeSoapRequest(soap, "http://thalesgroup.com/RTTI/2015-05-14/ldb/GetDepBoardWithDetails")
            parseBoardWithDetailsResponse(body, originCrs, originName, filterCrs)
        } else {
            val soap = buildBoardEnvelope(crsCode, numRows, null, timeOffset)
            val body = executeSoapRequest(soap, "http://thalesgroup.com/RTTI/2012-01-13/ldb/GetDepartureBoard")
            parseBoardResponse(body, originCrs, originName)
        }
    }

    suspend fun getServiceDetails(serviceId: String): ServiceDetail = withContext(Dispatchers.IO) {
        val soap = buildServiceDetailEnvelope(serviceId)
        val body = executeSoapRequest(soap, "http://thalesgroup.com/RTTI/2012-01-13/ldb/GetServiceDetails")
        parseServiceDetailResponse(body)
    }

    private fun executeSoapRequest(soap: String, soapAction: String): String {
        val request = Request.Builder()
            .url(endpoint)
            .post(soap.toRequestBody("text/xml; charset=utf-8".toMediaType()))
            .header("SOAPAction", "\"$soapAction\"")
            .build()

        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: ""
            Log.e("DarwinSoapClient", "HTTP ${response.code} for $soapAction:\n$errorBody")
            throw Exception("HTTP ${response.code}")
        }
        return response.body?.string() ?: throw Exception("Empty response body")
    }

    private fun buildBoardEnvelope(
        crs: String, numRows: Int, filterCrs: String?, timeOffset: Int
    ): String {
        val filterXml = if (filterCrs != null) {
            "\n      <ldb:filterCrs>${filterCrs.escapeXml()}</ldb:filterCrs>" +
            "\n      <ldb:filterType>to</ldb:filterType>"
        } else ""

        return """<?xml version="1.0" encoding="utf-8"?>
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
               xmlns:typ="http://thalesgroup.com/RTTI/2013-11-28/Token/types"
               xmlns:ldb="http://thalesgroup.com/RTTI/2021-11-01/ldb/">
  <soap:Header>
    <typ:AccessToken>
      <typ:TokenValue>${apiKey.escapeXml()}</typ:TokenValue>
    </typ:AccessToken>
  </soap:Header>
  <soap:Body>
    <ldb:GetDepartureBoardRequest>
      <ldb:numRows>$numRows</ldb:numRows>
      <ldb:crs>${crs.escapeXml()}</ldb:crs>$filterXml
      <ldb:timeOffset>$timeOffset</ldb:timeOffset>
    </ldb:GetDepartureBoardRequest>
  </soap:Body>
</soap:Envelope>"""
    }

    private fun buildBoardWithDetailsEnvelope(
        crs: String, numRows: Int, filterCrs: String, timeOffset: Int
    ): String = """<?xml version="1.0" encoding="utf-8"?>
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
               xmlns:typ="http://thalesgroup.com/RTTI/2013-11-28/Token/types"
               xmlns:ldb="http://thalesgroup.com/RTTI/2021-11-01/ldb/">
  <soap:Header>
    <typ:AccessToken>
      <typ:TokenValue>${apiKey.escapeXml()}</typ:TokenValue>
    </typ:AccessToken>
  </soap:Header>
  <soap:Body>
    <ldb:GetDepBoardWithDetailsRequest>
      <ldb:numRows>$numRows</ldb:numRows>
      <ldb:crs>${crs.escapeXml()}</ldb:crs>
      <ldb:filterCrs>${filterCrs.escapeXml()}</ldb:filterCrs>
      <ldb:filterType>to</ldb:filterType>
      <ldb:timeOffset>$timeOffset</ldb:timeOffset>
    </ldb:GetDepBoardWithDetailsRequest>
  </soap:Body>
</soap:Envelope>"""

    private fun buildServiceDetailEnvelope(serviceId: String): String =
        """<?xml version="1.0" encoding="utf-8"?>
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
               xmlns:typ="http://thalesgroup.com/RTTI/2013-11-28/Token/types"
               xmlns:ldb="http://thalesgroup.com/RTTI/2021-11-01/ldb/">
  <soap:Header>
    <typ:AccessToken>
      <typ:TokenValue>${apiKey.escapeXml()}</typ:TokenValue>
    </typ:AccessToken>
  </soap:Header>
  <soap:Body>
    <ldb:GetServiceDetailsRequest>
      <ldb:serviceID>${serviceId.escapeXml()}</ldb:serviceID>
    </ldb:GetServiceDetailsRequest>
  </soap:Body>
</soap:Envelope>"""

    private fun parseBoardResponse(xml: String, originCrs: String, originName: String): BoardResult {
        val doc = parseXml(xml)
        val messages = parseNrccMessages(doc)
        val services = doc.getElementsByTagNameNS("*", "service")
        val departures = mutableListOf<Departure>()

        for (i in 0 until services.length) {
            val service = services.item(i) as? Element ?: continue
            val std = service.textByLocalName("std") ?: continue
            val etd = service.textByLocalName("etd") ?: "Unknown"
            val platform = service.textByLocalName("platform")
            val isCancelled = service.textByLocalName("isCancelled")
                ?.toBooleanStrictOrNull() ?: false
            val serviceId = service.textByLocalName("serviceID") ?: continue

            val destNode = service.elementsByLocalName("destination").firstOrNull()
            val destLocation = destNode?.elementsByLocalName("location")?.firstOrNull()
            val destination = destLocation?.textByLocalName("locationName") ?: "Unknown"

            departures += Departure(
                scheduledTime = std,
                estimatedTime = etd,
                platform = platform,
                destination = destination,
                isCancelled = isCancelled,
                originCrs = originCrs,
                originName = originName,
                serviceId = serviceId
            )
        }
        return BoardResult(departures, messages)
    }

    private fun parseBoardWithDetailsResponse(
        xml: String, originCrs: String, originName: String, filterCrs: String
    ): BoardResult {
        val doc = parseXml(xml)
        val messages = parseNrccMessages(doc)
        val services = doc.getElementsByTagNameNS("*", "service")
        val departures = mutableListOf<Departure>()

        for (i in 0 until services.length) {
            val service = services.item(i) as? Element ?: continue
            val std = service.textByLocalName("std") ?: continue
            val serviceId = service.textByLocalName("serviceID") ?: continue

            // Check if filterCrs appears in subsequent calling points
            val subCPs = parseCallingPoints(service, "subsequentCallingPoints")
            if (subCPs.none { it.crs.equals(filterCrs, ignoreCase = true) }) continue

            val etd = service.textByLocalName("etd") ?: "Unknown"
            val platform = service.textByLocalName("platform")
            val isCancelled = service.textByLocalName("isCancelled")
                ?.toBooleanStrictOrNull() ?: false

            val destNode = service.elementsByLocalName("destination").firstOrNull()
            val destLocation = destNode?.elementsByLocalName("location")?.firstOrNull()
            val destination = destLocation?.textByLocalName("locationName") ?: "Unknown"

            departures += Departure(
                scheduledTime = std,
                estimatedTime = etd,
                platform = platform,
                destination = destination,
                isCancelled = isCancelled,
                originCrs = originCrs,
                originName = originName,
                serviceId = serviceId
            )
        }
        return BoardResult(departures, messages)
    }

    private fun parseServiceDetailResponse(xml: String): ServiceDetail {
        val doc = parseXml(xml)
        val root = doc.getElementsByTagNameNS("*", "GetServiceDetailsResult")
        val detail = root.item(0) as? Element ?: throw Exception("No service details in response")

        return ServiceDetail(
            locationName = detail.textByLocalName("locationName") ?: "Unknown",
            crs = detail.textByLocalName("crs") ?: "",
            operator = detail.textByLocalName("operator"),
            platform = detail.textByLocalName("platform"),
            sta = detail.textByLocalName("sta"),
            eta = detail.textByLocalName("eta"),
            std = detail.textByLocalName("std"),
            etd = detail.textByLocalName("etd"),
            isCancelled = detail.textByLocalName("isCancelled")?.toBooleanStrictOrNull() ?: false,
            cancelReason = detail.textByLocalName("cancelReason"),
            delayReason = detail.textByLocalName("delayReason"),
            previousCallingPoints = parseCallingPoints(detail, "previousCallingPoints"),
            subsequentCallingPoints = parseCallingPoints(detail, "subsequentCallingPoints")
        )
    }

    private fun parseNrccMessages(doc: org.w3c.dom.Document): List<String> {
        val messages = mutableListOf<String>()
        val nrccSections = doc.getElementsByTagNameNS("*", "nrccMessages")
        for (s in 0 until nrccSections.length) {
            val section = nrccSections.item(s) as? Element ?: continue
            for (msg in section.elementsByLocalName("message")) {
                val text = msg.textContent
                    ?.replace(Regex("<[^>]*>"), "") // strip HTML tags
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                if (text != null) messages += text
            }
        }
        return messages
    }

    private fun parseCallingPoints(parent: Element, sectionName: String): List<CallingPoint> {
        val section = parent.elementsByLocalName(sectionName).firstOrNull() ?: return emptyList()
        val lists = section.elementsByLocalName("callingPointList")
        val points = mutableListOf<CallingPoint>()
        for (list in lists) {
            for (cp in list.elementsByLocalName("callingPoint")) {
                points += CallingPoint(
                    stationName = cp.textByLocalName("locationName") ?: "Unknown",
                    crs = cp.textByLocalName("crs") ?: "",
                    scheduledTime = cp.textByLocalName("st") ?: "",
                    estimatedTime = cp.textByLocalName("et"),
                    actualTime = cp.textByLocalName("at"),
                    isCancelled = cp.textByLocalName("isCancelled")?.toBooleanStrictOrNull() ?: false
                )
            }
        }
        return points
    }

    private fun parseXml(xml: String) =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }.newDocumentBuilder().parse(InputSource(StringReader(
            xml.replace(Regex("<!DOCTYPE[^>]*>"), "")
        )))

    private fun Element.elementsByLocalName(name: String): List<Element> {
        val result = mutableListOf<Element>()
        val nodes = childNodes
        for (i in 0 until nodes.length) {
            val child = nodes.item(i)
            if (child is Element && child.localName == name) result += child
        }
        return result
    }

    private fun Element.textByLocalName(name: String): String? {
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child is Element && child.localName == name)
                return child.textContent?.trim()?.takeIf { it.isNotEmpty() }
        }
        return null
    }

    private fun String.escapeXml() = replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
