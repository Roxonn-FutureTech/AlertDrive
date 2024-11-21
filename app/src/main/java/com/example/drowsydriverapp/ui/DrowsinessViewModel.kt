package com.example.drowsydriverapp.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.drowsydriverapp.data.DrowsinessRepository
import com.example.drowsydriverapp.data.DrowsinessEvent
import com.example.drowsydriverapp.data.EventType
import com.example.drowsydriverapp.data.models.SessionStatistics
import com.google.mlkit.vision.face.Face
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*

data class DrowsinessState(
    val isProcessing: Boolean = false,
    val eyeOpenness: Float = 1f,
    val blinkCount: Int = 0,
    val headRotationX: Float = 0f,
    val headRotationY: Float = 0f,
    val headRotationZ: Float = 0f,
    val isDrowsy: Boolean = false,
    val isDriverPresent: Boolean = false,
    val sessionId: String = "",
    val sessionEvents: List<DrowsinessEvent> = emptyList(),
    val sessionStatistics: SessionStatistics? = null,
    val error: String? = null
)

class DrowsinessViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = DrowsinessRepository(application)
    private val _state = MutableStateFlow(DrowsinessState())
    val state: StateFlow<DrowsinessState> = _state.asStateFlow()

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
            _state.update { it.copy(sessionId = sessionId) }
            observeSessionEvents(sessionId)
        } catch (e: Exception) {
            _state.update { it.copy(error = "Failed to start session: ${e.message}") }
        }
    }

    private fun observeSessionEvents(sessionId: String) {
        viewModelScope.launch {
            repository.getSessionEvents(sessionId)
                .catch { e -> _state.update { it.copy(error = "Failed to load events: ${e.message}") } }
                .collect { events ->
                    _state.update { it.copy(sessionEvents = events) }
                    updateSessionStatistics()
                }
        }
    }

    private suspend fun updateSessionStatistics() {
        try {
            val statistics = repository.getSessionStatistics(_state.value.sessionId)
            _state.update { it.copy(sessionStatistics = statistics) }
        } catch (e: Exception) {
            _state.update { it.copy(error = "Failed to update statistics: ${e.message}") }
        }
    }

    fun processFrame(faces: List<Face>) {
        viewModelScope.launch {
            val isDriverPresent = faces.isNotEmpty()
            if (isDriverPresent) {
                val face = faces[0]
                analyzeFace(face)
            }
            _state.update { it.copy(isDriverPresent = isDriverPresent) }
            
            if (!isDriverPresent) {
                logDrowsinessEvent(EventType.DRIVER_ABSENT)
            }
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

            // Determine if drowsy based on multiple factors
            val isDrowsy = averageEyeOpenness < DROWSY_THRESHOLD ||
                    Math.abs(rotY) > HEAD_ROTATION_THRESHOLD ||
                    Math.abs(rotZ) > HEAD_ROTATION_THRESHOLD

            _state.update { currentState ->
                currentState.copy(
                    isProcessing = true,
                    eyeOpenness = averageEyeOpenness,
                    blinkCount = blinkCount,
                    headRotationX = rotX,
                    headRotationY = rotY,
                    headRotationZ = rotZ,
                    isDrowsy = isDrowsy
                )
            }

            // Log appropriate event
            if (isDrowsy) {
                logDrowsinessEvent(EventType.DROWSY_DETECTED)
            } else {
                logDrowsinessEvent(EventType.NORMAL)
            }
        } catch (e: Exception) {
            _state.update { it.copy(error = "Failed to analyze face: ${e.message}") }
        } finally {
            _state.update { it.copy(isProcessing = false) }
        }
    }

    private suspend fun logDrowsinessEvent(eventType: EventType) {
        try {
            repository.logDrowsinessEvent(
                eventType = eventType,
                eyeOpenness = _state.value.eyeOpenness,
                blinkCount = _state.value.blinkCount,
                headRotationX = _state.value.headRotationX,
                headRotationY = _state.value.headRotationY,
                headRotationZ = _state.value.headRotationZ,
                isDrowsy = _state.value.isDrowsy
            )
        } catch (e: Exception) {
            _state.update { it.copy(error = "Failed to log event: ${e.message}") }
        }
    }

    suspend fun exportSessionData(): String {
        return repository.exportSessionData(_state.value.sessionId)
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
