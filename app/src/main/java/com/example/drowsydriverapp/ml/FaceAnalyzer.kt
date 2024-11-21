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
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .setMinFaceSize(0.15f)
        .enableTracking()
        .build()

    private val detector = FaceDetection.getClient(realTimeOpts)
    
    // Thresholds for drowsiness detection
    private companion object {
        const val EYE_CLOSED_THRESHOLD = 0.4f      // Lower threshold for considering eyes closed
        const val EYE_DROWSY_THRESHOLD = 0.6f      // Threshold for considering eyes drowsy
        const val HEAD_NOD_THRESHOLD = 20f         // Threshold for head nodding (pitch)
        const val HEAD_ROTATION_THRESHOLD = 30f    // Threshold for side head rotation (yaw)
        const val DROWSY_FRAMES_WARNING = 5        // Frames for WARNING level
        const val DROWSY_FRAMES_SEVERE = 10        // Frames for SEVERE level
        const val DROWSY_FRAMES_CRITICAL = 15      // Frames for CRITICAL level
        const val RECOVERY_FRAMES = 10             // Frames needed to recover alert state
    }
    
    private var drowsyFrameCount = 0
    private var recoveryFrameCount = 0
    private var lastEyeOpenness = 1.0f
    private var lastPitch = 0f
    private var consecutiveNods = 0
    
    @androidx.camera.core.ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )

            detector.process(image)
                .addOnSuccessListener { faces ->
                    if (faces.isEmpty()) {
                        viewModel.updateDrowsinessState(DrowsinessState(
                            alertLevel = AlertLevel.WARNING,
                            message = "No face detected - please look at the camera",
                            isDriverPresent = false
                        ))
                        drowsyFrameCount = 0
                        recoveryFrameCount = 0
                    } else {
                        val face = faces[0]
                        val leftEyeOpenProb = face.leftEyeOpenProbability ?: 0f
                        val rightEyeOpenProb = face.rightEyeOpenProbability ?: 0f
                        
                        // Calculate average eye openness
                        val eyeOpenness = (leftEyeOpenProb + rightEyeOpenProb) / 2
                        
                        // Get head rotation
                        val pitch = face.headEulerAngleX // Head up/down
                        val yaw = face.headEulerAngleY   // Head left/right
                        val roll = face.headEulerAngleZ  // Head tilt
                        
                        // Detect head nodding (rapid changes in pitch)
                        if (abs(pitch - lastPitch) > 15f && abs(pitch) > HEAD_NOD_THRESHOLD) {
                            consecutiveNods++
                        } else {
                            consecutiveNods = max(0, consecutiveNods - 1)
                        }
                        lastPitch = pitch
                        
                        // Check for drowsiness indicators
                        val isEyesClosed = eyeOpenness < EYE_CLOSED_THRESHOLD
                        val isEyesDrowsy = eyeOpenness < EYE_DROWSY_THRESHOLD
                        val isHeadNodding = consecutiveNods >= 2
                        val isHeadRotated = abs(yaw) > HEAD_ROTATION_THRESHOLD
                        
                        // Calculate drowsiness score
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
                        
                        // Update alert level based on consecutive drowsy frames
                        val (alertLevel, message) = when {
                            drowsyFrameCount > DROWSY_FRAMES_CRITICAL -> AlertLevel.CRITICAL to 
                                "CRITICAL: Pull over immediately! High drowsiness detected!"
                            drowsyFrameCount > DROWSY_FRAMES_SEVERE -> AlertLevel.SEVERE to 
                                "Severe drowsiness detected! Please find a safe place to stop"
                            drowsyFrameCount > DROWSY_FRAMES_WARNING -> AlertLevel.WARNING to 
                                "Warning: You appear to be getting drowsy"
                            else -> AlertLevel.NORMAL to "Stay alert!"
                        }
                        
                        viewModel.updateDrowsinessState(DrowsinessState(
                            alertLevel = alertLevel,
                            message = message,
                            isDriverPresent = true,
                            eyeOpenness = eyeOpenness,
                            headRotationX = pitch,
                            headRotationY = yaw,
                            headRotationZ = roll,
                            drowsinessScore = drowsinessScore,
                            isDrowsy = drowsyFrameCount > DROWSY_FRAMES_WARNING
                        ))
                        
                        lastEyeOpenness = eyeOpenness
                    }
                }
                .addOnFailureListener { e ->
                    e.printStackTrace()
                    viewModel.updateDrowsinessState(DrowsinessState(
                        alertLevel = AlertLevel.NORMAL,
                        message = "Error in face detection",
                        error = e.message
                    ))
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
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
        
        // Eye openness contribution (0.0 - 0.4)
        score += (1 - eyeOpenness) * 0.4f
        
        // Head nodding contribution (0.0 - 0.3)
        if (isHeadNodding) {
            score += 0.3f
        }
        
        // Head rotation contribution (0.0 - 0.3)
        val rotationScore = max(
            max(abs(pitch) / HEAD_NOD_THRESHOLD,
                abs(yaw) / HEAD_ROTATION_THRESHOLD),
            abs(roll) / HEAD_ROTATION_THRESHOLD
        ) * 0.3f
        score += rotationScore
        
        return score.coerceIn(0f, 1f)
    }
}
