package com.example.uk_trains_app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_departures")
data class CachedDeparture(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long,
    val crsCode: String,
    val filterCrs: String?,
    val scheduledTime: String,
    val estimatedTime: String,
    val platform: String?,
    val destination: String,
    val isCancelled: Boolean,
    val originCrs: String,
    val originName: String,
    val serviceId: String,
    val cachedAt: Long
) {
    fun toDeparture() = Departure(
        scheduledTime = scheduledTime,
        estimatedTime = estimatedTime,
        platform = platform,
        destination = destination,
        isCancelled = isCancelled,
        originCrs = originCrs,
        originName = originName,
        serviceId = serviceId
    )

    companion object {
        fun from(departure: Departure, groupId: Long, crsCode: String, filterCrs: String?, cachedAt: Long) =
            CachedDeparture(
                groupId = groupId,
                crsCode = crsCode,
                filterCrs = filterCrs,
                scheduledTime = departure.scheduledTime,
                estimatedTime = departure.estimatedTime,
                platform = departure.platform,
                destination = departure.destination,
                isCancelled = departure.isCancelled,
                originCrs = departure.originCrs,
                originName = departure.originName,
                serviceId = departure.serviceId,
                cachedAt = cachedAt
            )
    }
}
