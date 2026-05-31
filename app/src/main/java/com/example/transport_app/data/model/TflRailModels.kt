package com.example.transport_app.data.model

data class TflLine(
    val id: String,
    val name: String,
    val mode: String,
    val stopIds: List<String> = emptyList()
)

data class TflRailStation(
    val id: String,
    val name: String,
    val modes: List<String> = emptyList(),
    val lines: List<TflLine> = emptyList(),
    val arrivalStopIds: List<String> = emptyList()
)

data class TflDirection(
    val id: String,
    val label: String
)
