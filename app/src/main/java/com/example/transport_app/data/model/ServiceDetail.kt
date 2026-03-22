package com.example.transport_app.data.model

data class CallingPoint(
    val stationName: String,
    val crs: String,
    val scheduledTime: String,
    val estimatedTime: String?,
    val actualTime: String?,
    val isCancelled: Boolean
)

data class ServiceDetail(
    val locationName: String,
    val crs: String,
    val operator: String?,
    val platform: String?,
    val sta: String?,
    val eta: String?,
    val std: String?,
    val etd: String?,
    val isCancelled: Boolean,
    val cancelReason: String?,
    val delayReason: String?,
    val previousCallingPoints: List<CallingPoint>,
    val subsequentCallingPoints: List<CallingPoint>
)
