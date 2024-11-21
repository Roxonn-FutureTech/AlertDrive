package com.example.drowsydriverapp.data

data class DrowsinessState(
    val isDriverPresent: Boolean = false,
    val isDrowsy: Boolean = false,
    val eyeOpenness: Float = 1f,
    val headRotationX: Float = 0f,
    val headRotationY: Float = 0f,
    val headRotationZ: Float = 0f,
    val blinkCount: Int = 0
)

data class SessionStatistics(
    val totalEvents: Int = 0,
    val drowsyEvents: Int = 0,
    val alertsTriggered: Int = 0,
    val averageEyeOpenness: Float = 0f,
    val averageBlinkRate: Float = 0f,
    val sessionDuration: Long = 0
)
