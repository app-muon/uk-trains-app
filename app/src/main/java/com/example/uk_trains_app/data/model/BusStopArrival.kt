package com.example.uk_trains_app.data.model

data class BusStopArrival(
    val stopName: String,
    val naptanId: String,
    val expectedTime: String,
    val minutesAway: Int,
    val towards: String,
    val lineName: String,
    val timeToStation: Int
)
