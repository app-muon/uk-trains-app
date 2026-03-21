package com.example.uk_trains_app.data.model

data class Departure(
    val scheduledTime: String,   // "HH:MM"
    val estimatedTime: String,   // "On time", "Cancelled", "HH:MM", "Delayed"
    val platform: String?,
    val destination: String,
    val isCancelled: Boolean,
    val originCrs: String,
    val originName: String,
    val serviceId: String
)
