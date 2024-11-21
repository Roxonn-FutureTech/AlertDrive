package com.example.drowsydriverapp.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.*

@Entity(tableName = "drowsiness_events")
data class DrowsinessEvent(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val timestamp: Date = Date(),
    val eventType: EventType,
    val eyeOpenness: Float,
    val blinkCount: Int,
    val headRotationX: Float,
    val headRotationY: Float,
    val headRotationZ: Float,
    val drowsinessScore: Float,
    val confidence: Float
)
