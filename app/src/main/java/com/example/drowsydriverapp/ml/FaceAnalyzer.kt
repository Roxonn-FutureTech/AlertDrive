package com.example.drowsydriverapp.ml

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.drowsydriverapp.data.models.AlertLevel
import com.example.drowsydriverapp.data.models.DrowsinessState
import com.example.drowsydriverapp.ui.DrowsinessViewModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.flow.MutableStateFlow
import android.util.Log
import kotlin.math.abs
import kotlin.math.max

class FaceAnalyzer(
    private val viewModel: DrowsinessViewModel
) : ImageAnalysis.Analyzer {
    private val TAG = "FaceAnalyzer"
    
    private val realTimeOpts = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .setMinFaceSize(0.2f)
        .build()

    private val detector = FaceDetection.getClient(realTimeOpts)
    
    private companion object {
        const val EYE_CLOSED_THRESHOLD = 0.4f
        const val EYE_DROWSY_THRESHOLD = 0.6f
        const val HEAD_NOD_THRESHOLD = 20f
        const val HEAD_ROTATION_THRESHOLD = 30f
        const val DROWSY_FRAMES_WARNING = 5
        const val DROWSY_FRAMES_SEVERE = 10
        const val DROWSY_FRAMES_CRITICAL = 15
        const val RECOVERY_FRAMES = 10
        const val PROCESS_FRAME_INTERVAL = 2
    }
    
    private var drowsyFrameCount = 0
    private var recoveryFrameCount = 0
    private var lastEyeOpenness = 1.0f
    private var lastPitch = 0f
    private var consecutiveNods = 0
    private var frameCounter = 0
    private var lastProcessedState: DrowsinessState? = null
    private var lastAlertLevel: AlertLevel = AlertLevel.NORMAL
    
    @androidx.camera.core.ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        frameCounter++
        
        if (frameCounter % PROCESS_FRAME_INTERVAL != 0) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )

            detector.process(image)
                .addOnSuccessListener { faces ->
                    if (faces.isEmpty()) {
                        Log.d(TAG, "No face detected")
                        val newState = DrowsinessState(
                            alertLevel = AlertLevel.WARNING,
                            message = "No face detected - please look at the camera",
                            isDriverPresent = false
                        )
                        updateStateWithAlert(newState)
                        drowsyFrameCount = 0
                        recoveryFrameCount = 0
                    } else {
                        val face = faces[0]
                        val leftEyeOpenProb = face.leftEyeOpenProbability ?: 0f
                        val rightEyeOpenProb = face.rightEyeOpenProbability ?: 0f
                        val eyeOpenness = (leftEyeOpenProb + rightEyeOpenProb) / 2
                        val pitch = face.headEulerAngleX
                        val yaw = face.headEulerAngleY
                        val roll = face.headEulerAngleZ
                        
                        Log.d(TAG, "Face detected - Eye openness: $eyeOpenness, Pitch: $pitch, Yaw: $yaw")
                        
                        if (abs(pitch - lastPitch) > 15f && abs(pitch) > HEAD_NOD_THRESHOLD) {
                            consecutiveNods++
                            Log.d(TAG, "Head nod detected - consecutive nods: $consecutiveNods")
                        } else {
                            consecutiveNods = max(0, consecutiveNods - 1)
                        }
                        lastPitch = pitch
                        
                        val isEyesClosed = eyeOpenness < EYE_CLOSED_THRESHOLD
                        val isEyesDrowsy = eyeOpenness < EYE_DROWSY_THRESHOLD
                        val isHeadNodding = consecutiveNods >= 2
                        val isHeadRotated = abs(yaw) > HEAD_ROTATION_THRESHOLD
                        
                        val drowsinessScore = calculateDrowsinessScore(
                            eyeOpenness,
                            isHeadNodding,
                            isHeadRotated,
                            pitch,
                            yaw,
                            roll
                        )
                        
                        Log.d(TAG, "Drowsiness score: $drowsinessScore")
                        
                        if (isEyesClosed || isEyesDrowsy || isHeadNodding || isHeadRotated) {
                            drowsyFrameCount++
                            recoveryFrameCount = 0
                            Log.d(TAG, "Drowsy frame count increased: $drowsyFrameCount")
                        } else {
                            recoveryFrameCount++
                            if (recoveryFrameCount >= RECOVERY_FRAMES) {
                                drowsyFrameCount = max(0, drowsyFrameCount - 1)
                                Log.d(TAG, "Recovery detected - drowsy frames: $drowsyFrameCount")
                            }
                        }
                        
                        val (alertLevel, message) = when {
                            drowsyFrameCount > DROWSY_FRAMES_CRITICAL -> AlertLevel.CRITICAL to 
                                "CRITICAL: Pull over immediately!"
                            drowsyFrameCount > DROWSY_FRAMES_SEVERE -> AlertLevel.SEVERE to 
                                "Severe drowsiness detected!"
                            drowsyFrameCount > DROWSY_FRAMES_WARNING -> AlertLevel.WARNING to 
                                "Warning: You appear to be drowsy"
                            else -> AlertLevel.NORMAL to "Stay alert!"
                        }
                        
                        val newState = DrowsinessState(
                            alertLevel = alertLevel,
                            message = message,
                            isDriverPresent = true,
                            eyeOpenness = eyeOpenness,
                            headRotationX = pitch,
                            headRotationY = yaw,
                            headRotationZ = roll,
                            drowsinessScore = drowsinessScore,
                            isDrowsy = drowsyFrameCount > DROWSY_FRAMES_WARNING
                        )
                        
                        updateStateWithAlert(newState)
                        lastEyeOpenness = eyeOpenness
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Face detection failed", e)
                    val newState = DrowsinessState(
                        alertLevel = AlertLevel.NORMAL,
                        message = "Error in face detection",
                        error = e.message
                    )
                    updateStateWithAlert(newState)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
    
    private fun updateStateWithAlert(newState: DrowsinessState) {
        if (shouldUpdateState(newState)) {
            // Check if alert level has changed
            if (newState.alertLevel != lastAlertLevel) {
                Log.d(TAG, "Alert level changed from $lastAlertLevel to ${newState.alertLevel}")
                lastAlertLevel = newState.alertLevel
            }
            
            viewModel.updateDrowsinessState(newState)
            lastProcessedState = newState
        }
    }
    
    private fun shouldUpdateState(newState: DrowsinessState): Boolean {
        val lastState = lastProcessedState ?: return true
        
        // Always update if alert level changes
        if (newState.alertLevel != lastState.alertLevel) {
            Log.d(TAG, "State update: Alert level changed")
            return true
        }
        
        // Update if there are significant changes in measurements
        return abs(newState.drowsinessScore - lastState.drowsinessScore) > 0.1f ||
               newState.isDriverPresent != lastState.isDriverPresent ||
               abs(newState.eyeOpenness - lastState.eyeOpenness) > 0.1f
    }
    
    private fun calculateDrowsinessScore(
        eyeOpenness: Float,
        isHeadNodding: Boolean,
        isHeadRotated: Boolean,
        pitch: Float,
        yaw: Float,
        roll: Float
    ): Float {
        var score = 0f
        
        // Eye openness has the highest weight (40%)
        score += (1 - eyeOpenness) * 0.4f
        
        // Head nodding contributes 30%
        if (isHeadNodding) {
            score += 0.3f
        }
        
        // Head rotation contributes 30%
        val rotationScore = max(
            max(abs(pitch) / HEAD_NOD_THRESHOLD,
                abs(yaw) / HEAD_ROTATION_THRESHOLD),
            abs(roll) / HEAD_ROTATION_THRESHOLD
        ) * 0.3f
        
        score += rotationScore
        
        return score.coerceIn(0f, 1f)
    }
}
