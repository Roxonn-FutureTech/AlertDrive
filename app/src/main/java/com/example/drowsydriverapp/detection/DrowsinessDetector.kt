package com.example.drowsydriverapp.detection

import android.util.Log
import com.example.drowsydriverapp.data.DrowsinessState
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.abs

class DrowsinessDetector {
    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()
    )

    private var lastBlinkTimestamp = 0L
    private var blinkCount = 0
    private val BLINK_PERIOD = 60000L // 1 minute in milliseconds
    private val DROWSY_BLINK_THRESHOLD = 15 // Blinks per minute threshold

    suspend fun processImage(image: InputImage): DrowsinessState = suspendCancellableCoroutine { continuation ->
        try {
            faceDetector.process(image)
                .addOnSuccessListener { faces ->
                    if (faces.isEmpty()) {
                        continuation.resume(DrowsinessState(isDriverPresent = false))
                        return@addOnSuccessListener
                    }

                    val face = faces[0] // Assuming single face (driver)
                    continuation.resume(analyzeFace(face))
                }
                .addOnFailureListener { e ->
                    Log.e("DrowsinessDetector", "Face detection failed", e)
                    continuation.resume(DrowsinessState(isDriverPresent = false))
                }
        } catch (e: Exception) {
            Log.e("DrowsinessDetector", "Error processing image", e)
            continuation.resume(DrowsinessState(isDriverPresent = false))
        }
    }

    private fun analyzeFace(face: Face): DrowsinessState {
        val currentTime = System.currentTimeMillis()
        
        // Reset blink count after BLINK_PERIOD
        if (currentTime - lastBlinkTimestamp > BLINK_PERIOD) {
            blinkCount = 0
            lastBlinkTimestamp = currentTime
        }

        // Detect blinks using eye openness
        val leftEyeOpenness = face.leftEyeOpenProbability ?: 0f
        val rightEyeOpenness = face.rightEyeOpenProbability ?: 0f
        val averageEyeOpenness = (leftEyeOpenness + rightEyeOpenness) / 2

        // Check for head rotation (nodding)
        val xRotation = face.headEulerAngleX // Vertical head rotation
        val yRotation = face.headEulerAngleY // Horizontal head rotation
        val zRotation = face.headEulerAngleZ // Tilt

        // Detect if eyes are closed (potential blink)
        if (averageEyeOpenness < 0.2) {
            blinkCount++
        }

        // Calculate drowsiness based on various factors
        val isDrowsy = (blinkCount >= DROWSY_BLINK_THRESHOLD) || // Too many blinks
                (averageEyeOpenness < 0.5) || // Eyes half-closed
                (abs(xRotation) > 30) // Head nodding

        return DrowsinessState(
            isDriverPresent = true,
            isDrowsy = isDrowsy,
            eyeOpenness = averageEyeOpenness,
            headRotationX = xRotation,
            headRotationY = yRotation,
            headRotationZ = zRotation,
            blinkCount = blinkCount
        )
    }
}
