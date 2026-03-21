package com.example.uk_trains_app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "station_groups")
data class Group(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val displayOrder: Int = 0
)
