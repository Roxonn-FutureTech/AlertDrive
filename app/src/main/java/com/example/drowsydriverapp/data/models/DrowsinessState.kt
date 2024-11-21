package com.example.drowsydriverapp.data.models

data class DrowsinessState(
    val alertLevel: AlertLevel = AlertLevel.NORMAL,
    val message: String = "",
    val sessionId: String = "",
    val isProcessing: Boolean = false,
    val eyeOpenness: Float = 1f,
    val blinkCount: Int = 0,
    val headRotationX: Float = 0f,
    val headRotationY: Float = 0f,
    val headRotationZ: Float = 0f,
    val isDrowsy: Boolean = false,
    val isDriverPresent: Boolean = true,
    val drowsinessScore: Float = 0f,
    val confidence: Float = 1f,
    val error: String? = null,
    val sessionEvents: List<DrowsinessEvent> = emptyList(),
    val sessionStatistics: SessionStatistics = SessionStatistics()
)
