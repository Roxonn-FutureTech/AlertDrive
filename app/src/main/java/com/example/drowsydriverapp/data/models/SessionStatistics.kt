package com.example.drowsydriverapp.data.models

data class SessionStatistics(
    val totalEvents: Int = 0,
    val drowsyEvents: Int = 0,
    val averageDrowsinessScore: Float = 0f,
    val averageConfidence: Float = 0f
)
