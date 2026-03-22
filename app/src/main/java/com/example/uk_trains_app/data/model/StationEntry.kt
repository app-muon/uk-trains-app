package com.example.uk_trains_app.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "station_entries",
    foreignKeys = [
        ForeignKey(
            entity = Group::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("groupId")]
)
data class StationEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long,
    val stationName: String,
    val crsCode: String,
    val displayOrder: Int = 0,
    val filterCrs: String? = null,
    val filterName: String? = null,
    val type: String = TransportType.TRAIN
)
