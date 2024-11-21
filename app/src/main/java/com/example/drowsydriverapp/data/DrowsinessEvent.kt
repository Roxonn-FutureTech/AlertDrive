package com.example.drowsydriverapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.drowsydriverapp.data.converters.EventTypeConverter

@Entity(tableName = "drowsiness_events")
@TypeConverters(EventTypeConverter::class)
data class DrowsinessEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: EventType,
    val eyeOpenness: Float,
    val blinkCount: Int,
    val headRotationX: Float,
    val headRotationY: Float,
    val headRotationZ: Float,
    val isDrowsy: Boolean,
    val sessionId: String
)
