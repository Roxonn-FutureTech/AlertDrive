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
import kotlin.math.abs
import kotlin.math.max

class FaceAnalyzer(
    private val viewModel: DrowsinessViewModel
) : ImageAnalysis.Analyzer {
    private val realTimeOpts = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE) // Disable landmarks for better performance
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)  // Disable contours for better performance
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .setMinFaceSize(0.2f)  // Increased min face size for better performance
        .build()

    private val detector = FaceDetection.getClient(realTimeOpts)
    
    // Thresholds for drowsiness detection
    private companion object {
        const val EYE_CLOSED_THRESHOLD = 0.4f
        const val EYE_DROWSY_THRESHOLD = 0.6f
        const val HEAD_NOD_THRESHOLD = 20f
        const val HEAD_ROTATION_THRESHOLD = 30f
        const val DROWSY_FRAMES_WARNING = 5
        const val DROWSY_FRAMES_SEVERE = 10
        const val DROWSY_FRAMES_CRITICAL = 15
        const val RECOVERY_FRAMES = 10
        const val PROCESS_FRAME_INTERVAL = 2 // Process every 2nd frame
    }
    
    private var drowsyFrameCount = 0
    private var recoveryFrameCount = 0
    private var lastEyeOpenness = 1.0f
    private var lastPitch = 0f
    private var consecutiveNods = 0
    private var frameCounter = 0
    private var lastProcessedState: DrowsinessState? = null
    
    @androidx.camera.core.ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        frameCounter++
        
        // Process only every nth frame for better performance
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
                        // Reuse last state if available to reduce UI updates
                        if (lastProcessedState?.alertLevel != AlertLevel.WARNING || 
                            lastProcessedState?.isDriverPresent == true) {
                            val newState = DrowsinessState(
                                alertLevel = AlertLevel.WARNING,
                                message = "No face detected - please look at the camera",
                                isDriverPresent = false
                            )
                            viewModel.updateDrowsinessState(newState)
                            lastProcessedState = newState
                        }
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
                        
                        // Detect head nodding with smoothing
                        if (abs(pitch - lastPitch) > 15f && abs(pitch) > HEAD_NOD_THRESHOLD) {
                            consecutiveNods++
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
                        
                        if (isEyesClosed || isEyesDrowsy || isHeadNodding || isHeadRotated) {
                            drowsyFrameCount++
                            recoveryFrameCount = 0
                        } else {
                            recoveryFrameCount++
                            if (recoveryFrameCount >= RECOVERY_FRAMES) {
                                drowsyFrameCount = max(0, drowsyFrameCount - 1)
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
                        
                        // Only update state if there's a significant change
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
                        
                        if (shouldUpdateState(newState)) {
                            viewModel.updateDrowsinessState(newState)
                            lastProcessedState = newState
                        }
                        
                        lastEyeOpenness = eyeOpenness
                    }
                }
                .addOnFailureListener { e ->
                    e.printStackTrace()
                    // Only update on error if not already in error state
                    if (lastProcessedState?.error == null) {
                        val newState = DrowsinessState(
                            alertLevel = AlertLevel.NORMAL,
                            message = "Error in face detection",
                            error = e.message
                        )
                        viewModel.updateDrowsinessState(newState)
                        lastProcessedState = newState
                    }
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
    
    private fun shouldUpdateState(newState: DrowsinessState): Boolean {
        val lastState = lastProcessedState ?: return true
        
        return newState.alertLevel != lastState.alertLevel ||
               abs(newState.drowsinessScore - lastState.drowsinessScore) > 0.1f ||
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
        
        score += (1 - eyeOpenness) * 0.4f
        
        if (isHeadNodding) {
            score += 0.3f
        }
        
        val rotationScore = max(
            max(abs(pitch) / HEAD_NOD_THRESHOLD,
                abs(yaw) / HEAD_ROTATION_THRESHOLD),
            abs(roll) / HEAD_ROTATION_THRESHOLD
        ) * 0.3f
        score += rotationScore
        
        return score.coerceIn(0f, 1f)
    }
}
