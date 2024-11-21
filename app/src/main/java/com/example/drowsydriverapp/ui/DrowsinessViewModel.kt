package com.example.drowsydriverapp.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.drowsydriverapp.data.DrowsinessRepository
import com.example.drowsydriverapp.data.models.*
import com.example.drowsydriverapp.utils.SoundManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import java.util.*

class DrowsinessViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = DrowsinessRepository(application)
    private val soundManager = SoundManager(application)
    private val _drowsinessState = MutableStateFlow(DrowsinessState())
    val drowsinessState: StateFlow<DrowsinessState> = _drowsinessState.asStateFlow()

    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()
    )

    private var lastBlinkTime = 0L
    private var blinkCount = 0
    private val BLINK_COOLDOWN = 500L // Minimum time between blinks in milliseconds
    private val DROWSY_THRESHOLD = 0.6f // Threshold for eye openness to consider drowsy
    private val HEAD_ROTATION_THRESHOLD = 30f // Threshold for head rotation in degrees

    init {
        viewModelScope.launch {
            startNewSession()
        }
    }

    private suspend fun startNewSession() {
        try {
            val sessionId = repository.startNewSession()
            _drowsinessState.update { it.copy(sessionId = sessionId) }
            logDrowsinessEvent(EventType.SESSION_START, sessionId, 0f, 1f)
            observeSessionEvents(sessionId)
        } catch (e: Exception) {
            _drowsinessState.update { it.copy(error = "Failed to start session: ${e.message}") }
        }
    }

    private fun observeSessionEvents(sessionId: String) {
        viewModelScope.launch {
            repository.getSessionEventsFlow(sessionId)
                .catch { e -> _drowsinessState.update { it.copy(error = "Failed to load events: ${e.message}") } }
                .collect { events ->
                    _drowsinessState.update { it.copy(sessionEvents = events) }
                    updateSessionStatistics()
                }
        }
    }

    private suspend fun updateSessionStatistics() {
        try {
            val statistics = repository.getSessionStatistics(_drowsinessState.value.sessionId)
            _drowsinessState.update { it.copy(sessionStatistics = statistics) }
        } catch (e: Exception) {
            _drowsinessState.update { it.copy(error = "Failed to update statistics: ${e.message}") }
        }
    }

    suspend fun processFrame(image: InputImage) {
        try {
            val faces = faceDetector.process(image).await()
            val isDriverPresent = faces.isNotEmpty()
            
            if (isDriverPresent) {
                val face = faces[0]
                analyzeFace(face)
            } else {
                _drowsinessState.update { it.copy(
                    isDriverPresent = false,
                    alertLevel = AlertLevel.WARNING
                )}
                logDrowsinessEvent(
                    eventType = EventType.DRIVER_ABSENT,
                    sessionId = _drowsinessState.value.sessionId,
                    drowsinessScore = 0.5f,
                    confidence = 1f
                )
            }
        } catch (e: Exception) {
            _drowsinessState.update { it.copy(error = "Failed to process frame: ${e.message}") }
        }
    }

    private suspend fun analyzeFace(face: Face) {
        try {
            val leftEye = face.leftEyeOpenProbability ?: 1f
            val rightEye = face.rightEyeOpenProbability ?: 1f
            val averageEyeOpenness = (leftEye + rightEye) / 2

            // Detect blinks
            if (averageEyeOpenness < 0.3f && System.currentTimeMillis() - lastBlinkTime > BLINK_COOLDOWN) {
                blinkCount++
                lastBlinkTime = System.currentTimeMillis()
            }

            // Get head rotation
            val rotX = face.headEulerAngleX
            val rotY = face.headEulerAngleY
            val rotZ = face.headEulerAngleZ

            // Calculate drowsiness score
            val drowsinessScore = calculateDrowsinessScore(averageEyeOpenness, rotY, rotZ, blinkCount)
            val alertLevel = determineAlertLevel(drowsinessScore)
            val confidence = calculateConfidence(face)

            _drowsinessState.update { currentState ->
                currentState.copy(
                    isProcessing = true,
                    eyeOpenness = averageEyeOpenness,
                    blinkCount = blinkCount,
                    headRotationX = rotX,
                    headRotationY = rotY,
                    headRotationZ = rotZ,
                    isDrowsy = drowsinessScore > DROWSY_THRESHOLD,
                    isDriverPresent = true,
                    drowsinessScore = drowsinessScore,
                    confidence = confidence,
                    alertLevel = alertLevel
                )
            }

            // Log appropriate event based on drowsiness score
            val eventType = when {
                drowsinessScore > 0.8f -> EventType.DROWSY_CRITICAL
                drowsinessScore > 0.6f -> EventType.DROWSY_SEVERE
                drowsinessScore > 0.4f -> EventType.DROWSY_WARNING
                else -> EventType.NORMAL
            }

            logDrowsinessEvent(
                eventType = eventType,
                sessionId = _drowsinessState.value.sessionId,
                drowsinessScore = drowsinessScore,
                confidence = confidence
            )
        } catch (e: Exception) {
            _drowsinessState.update { it.copy(error = "Failed to analyze face: ${e.message}") }
        } finally {
            _drowsinessState.update { it.copy(isProcessing = false) }
        }
    }

    private fun calculateDrowsinessScore(
        eyeOpenness: Float,
        headRotationY: Float,
        headRotationZ: Float,
        blinkCount: Int
    ): Float {
        val eyeScore = 1f - eyeOpenness
        val rotationScore = (Math.abs(headRotationY) + Math.abs(headRotationZ)) / (2 * HEAD_ROTATION_THRESHOLD)
        val blinkScore = (blinkCount / 60f).coerceAtMost(1f)
        
        return (eyeScore * 0.5f + rotationScore * 0.3f + blinkScore * 0.2f).coerceIn(0f, 1f)
    }

    private fun calculateConfidence(face: Face): Float {
        return face.leftEyeOpenProbability?.let { left ->
            face.rightEyeOpenProbability?.let { right ->
                (left + right) / 2
            }
        } ?: 0.5f
    }

    private fun determineAlertLevel(drowsinessScore: Float): AlertLevel {
        return when {
            drowsinessScore > 0.8f -> AlertLevel.CRITICAL
            drowsinessScore > 0.6f -> AlertLevel.SEVERE
            drowsinessScore > 0.4f -> AlertLevel.WARNING
            else -> AlertLevel.NORMAL
        }
    }

    private suspend fun logDrowsinessEvent(
        eventType: EventType,
        sessionId: String,
        drowsinessScore: Float,
        confidence: Float
    ) {
        try {
            repository.logDrowsinessEvent(
                eventType = eventType,
                sessionId = sessionId,
                eyeOpenness = _drowsinessState.value.eyeOpenness,
                blinkCount = _drowsinessState.value.blinkCount,
                headRotationX = _drowsinessState.value.headRotationX,
                headRotationY = _drowsinessState.value.headRotationY,
                headRotationZ = _drowsinessState.value.headRotationZ,
                drowsinessScore = drowsinessScore,
                confidence = confidence
            )
        } catch (e: Exception) {
            _drowsinessState.update { it.copy(error = "Failed to log event: ${e.message}") }
        }
    }

    fun updateDrowsinessState(newState: DrowsinessState) {
        viewModelScope.launch {
            try {
                // Check if alert level has changed
                if (newState.alertLevel != _drowsinessState.value.alertLevel) {
                    android.util.Log.d("DrowsinessViewModel", "Alert level changed via updateDrowsinessState from ${_drowsinessState.value.alertLevel} to ${newState.alertLevel}")
                    soundManager.playAlertSound(newState.alertLevel)
                }
                
                _drowsinessState.update { currentState ->
                    newState.copy(
                        sessionId = currentState.sessionId,
                        sessionEvents = currentState.sessionEvents,
                        sessionStatistics = currentState.sessionStatistics
                    )
                }
                
                // Log event if alert level changed
                if (newState.alertLevel != _drowsinessState.value.alertLevel) {
                    val eventType = when (newState.alertLevel) {
                        AlertLevel.CRITICAL -> EventType.DROWSY_CRITICAL
                        AlertLevel.SEVERE -> EventType.DROWSY_SEVERE
                        AlertLevel.WARNING -> EventType.DROWSY_WARNING
                        AlertLevel.NORMAL -> EventType.NORMAL
                    }
                    
                    logDrowsinessEvent(
                        eventType = eventType,
                        sessionId = _drowsinessState.value.sessionId,
                        drowsinessScore = newState.drowsinessScore,
                        confidence = newState.confidence ?: 1.0f
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("DrowsinessViewModel", "Error updating drowsiness state", e)
                _drowsinessState.update { it.copy(error = "Failed to update state: ${e.message}") }
            }
        }
    }

    suspend fun exportSessionData(): String {
        return repository.exportSessionData(_drowsinessState.value.sessionId)
    }

    fun clearError() {
        _drowsinessState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        soundManager.release()
    }
}
