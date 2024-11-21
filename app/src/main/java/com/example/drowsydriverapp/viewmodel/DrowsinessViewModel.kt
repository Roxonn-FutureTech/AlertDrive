package com.example.drowsydriverapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.drowsydriverapp.data.DrowsinessRepository
import com.example.drowsydriverapp.data.EventType
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class DrowsinessState(
    val isProcessing: Boolean = false,
    val eyeOpenness: Float = 1.0f,
    val blinkCount: Int = 0,
    val headRotationX: Float = 0f,
    val headRotationY: Float = 0f,
    val headRotationZ: Float = 0f,
    val isDrowsy: Boolean = false,
    val isDriverPresent: Boolean = false
)

class DrowsinessViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = DrowsinessRepository(application)
    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()
    )

    private val _drowsinessState = MutableStateFlow(DrowsinessState())
    val drowsinessState: StateFlow<DrowsinessState> = _drowsinessState

    private var currentSessionId: String? = null
    private var lastBlinkTimestamp = 0L
    private var blinkCount = 0

    init {
        viewModelScope.launch {
            currentSessionId = repository.startNewSession()
        }
    }

    fun processFrame(image: InputImage) =
        if (_drowsinessState.value.isProcessing) null
        else {
            _drowsinessState.value = _drowsinessState.value.copy(isProcessing = true)

            faceDetector.process(image)
                .addOnSuccessListener { faces ->
                    val face = faces.firstOrNull()
                    if (face != null) {
                        analyzeFace(face)
                        _drowsinessState.value = _drowsinessState.value.copy(
                            isProcessing = false,
                            isDriverPresent = true
                        )
                    } else {
                        _drowsinessState.value = _drowsinessState.value.copy(
                            isProcessing = false,
                            isDriverPresent = false
                        )
                    }
                }
                .addOnFailureListener { e ->
                    _drowsinessState.value = _drowsinessState.value.copy(
                        isProcessing = false,
                        isDriverPresent = false
                    )
                    e.printStackTrace()
                }
        }

    private fun analyzeFace(face: Face) {
        val leftEyeOpenProb = face.leftEyeOpenProbability ?: 1f
        val rightEyeOpenProb = face.rightEyeOpenProbability ?: 1f
        val eyeOpenness = (leftEyeOpenProb + rightEyeOpenProb) / 2

        // Detect blinks
        if (eyeOpenness < 0.3 && System.currentTimeMillis() - lastBlinkTimestamp > 500) {
            blinkCount++
            lastBlinkTimestamp = System.currentTimeMillis()
        }

        val rotX = face.headEulerAngleX
        val rotY = face.headEulerAngleY
        val rotZ = face.headEulerAngleZ

        val isDrowsy = eyeOpenness < 0.5 || blinkCount > 30 ||
                Math.abs(rotY) > 30 ||
                Math.abs(rotX) > 20

        viewModelScope.launch {
            repository.logDrowsinessEvent(
                eventType = if (isDrowsy) EventType.DROWSY_DETECTED else EventType.NORMAL,
                eyeOpenness = eyeOpenness,
                blinkCount = blinkCount,
                headRotationX = rotX,
                headRotationY = rotY,
                headRotationZ = rotZ,
                isDrowsy = isDrowsy
            )
        }

        _drowsinessState.value = _drowsinessState.value.copy(
            eyeOpenness = eyeOpenness,
            blinkCount = blinkCount,
            headRotationX = rotX,
            headRotationY = rotY,
            headRotationZ = rotZ,
            isDrowsy = isDrowsy
        )
    }
}
