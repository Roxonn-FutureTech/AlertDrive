package com.example.drowsydriverapp.data.models

data class SessionStatistics(
    val totalEvents: Int,
    val drowsyEvents: Int,
    val alertsTriggered: Int,
    val averageEyeOpenness: Float,
    val averageBlinkRate: Float,
    val sessionDuration: Long
)
