package com.example.drowsydriverapp.detection

import android.util.Log
import com.example.drowsydriverapp.data.models.DrowsinessState
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class DrowsinessDetector {
    companion object {
        private const val TAG = "DrowsinessDetector"
        private const val BLINK_PERIOD = 60000L // 1 minute in milliseconds
        private const val MIN_DROWSY_BLINK_THRESHOLD = 15 // Minimum blinks per minute
        private const val MAX_DROWSY_BLINK_THRESHOLD = 30 // Maximum blinks per minute
        private const val EYE_CLOSED_THRESHOLD = 0.2f
        private const val EYE_DROWSY_THRESHOLD = 0.5f
        private const val HEAD_NOD_THRESHOLD = 30f
        private const val HEAD_TILT_THRESHOLD = 20f
        private const val CONFIDENCE_THRESHOLD = 0.7f
        private const val HISTORY_SIZE = 10 // Number of frames to keep in history
    }

    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.15f)
            .enableTracking()
            .build()
    )

    private var lastBlinkTime = 0L
    private var blinkCount = 0
    private val eyeOpennessHistory = mutableListOf<Float>()
    private val headRotationHistory = mutableListOf<Triple<Float, Float, Float>>()

    suspend fun processImage(image: InputImage): DrowsinessState = suspendCancellableCoroutine { continuation ->
        faceDetector.process(image)
            .addOnSuccessListener { faces ->
                val face = faces.firstOrNull()
                if (face == null) {
                    continuation.resume(DrowsinessState(isDriverPresent = false))
                    return@addOnSuccessListener
                }

                val eyeOpenness = calculateEyeOpenness(face)
                val (headRotationX, headRotationY, headRotationZ) = getHeadRotation(face)
                
                // Update histories
                updateHistories(eyeOpenness, headRotationX, headRotationY, headRotationZ)
                
                // Calculate drowsiness metrics
                val isDrowsy = detectDrowsiness(eyeOpenness, headRotationX, headRotationY, headRotationZ)
                val drowsinessScore = calculateDrowsinessScore(eyeOpenness, headRotationX, headRotationY, headRotationZ)
                val confidence = calculateConfidence(face)

                continuation.resume(
                    DrowsinessState(
                        isProcessing = false,
                        isDriverPresent = true,
                        eyeOpenness = eyeOpenness,
                        blinkCount = blinkCount,
                        headRotationX = headRotationX,
                        headRotationY = headRotationY,
                        headRotationZ = headRotationZ,
                        drowsinessScore = drowsinessScore,
                        confidence = confidence
                    )
                )
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Face detection failed", e)
                continuation.resume(DrowsinessState(isDriverPresent = false))
            }
    }

    private fun calculateEyeOpenness(face: Face): Float {
        val leftEyeOpenProb = face.leftEyeOpenProbability ?: 0f
        val rightEyeOpenProb = face.rightEyeOpenProbability ?: 0f
        return (leftEyeOpenProb + rightEyeOpenProb) / 2f
    }

    private fun getHeadRotation(face: Face): Triple<Float, Float, Float> {
        return Triple(
            face.headEulerAngleX,
            face.headEulerAngleY,
            face.headEulerAngleZ
        )
    }

    private fun updateHistories(eyeOpenness: Float, rotX: Float, rotY: Float, rotZ: Float) {
        // Update eye openness history
        eyeOpennessHistory.add(eyeOpenness)
        if (eyeOpennessHistory.size > HISTORY_SIZE) {
            eyeOpennessHistory.removeAt(0)
        }

        // Update head rotation history
        headRotationHistory.add(Triple(rotX, rotY, rotZ))
        if (headRotationHistory.size > HISTORY_SIZE) {
            headRotationHistory.removeAt(0)
        }

        // Update blink count
        if (eyeOpenness < EYE_CLOSED_THRESHOLD && eyeOpennessHistory.lastOrNull() ?: 1f > EYE_CLOSED_THRESHOLD) {
            blinkCount++
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastBlinkTime > BLINK_PERIOD) {
                blinkCount = 1
                lastBlinkTime = currentTime
            }
        }
    }

    private fun detectDrowsiness(eyeOpenness: Float, rotX: Float, rotY: Float, rotZ: Float): Boolean {
        // Check for rapid blinking
        val blinksPerMinute = (blinkCount * 60000L) / max(1L, System.currentTimeMillis() - lastBlinkTime)
        val isRapidBlinking = blinksPerMinute in MIN_DROWSY_BLINK_THRESHOLD..MAX_DROWSY_BLINK_THRESHOLD

        // Check for droopy eyes
        val isDroopyEyes = eyeOpenness < EYE_DROWSY_THRESHOLD

        // Check for head nodding
        val isHeadNodding = abs(rotX) > HEAD_NOD_THRESHOLD

        // Check for head tilting
        val isHeadTilting = abs(rotZ) > HEAD_TILT_THRESHOLD

        return isRapidBlinking || isDroopyEyes || isHeadNodding || isHeadTilting
    }

    private fun calculateDrowsinessScore(eyeOpenness: Float, rotX: Float, rotY: Float, rotZ: Float): Float {
        var score = 0f

        // Eye openness contribution (0.4 weight)
        score += (1 - eyeOpenness) * 0.4f

        // Head rotation contribution (0.3 weight)
        val normalizedRotX = min(1f, abs(rotX) / HEAD_NOD_THRESHOLD)
        val normalizedRotZ = min(1f, abs(rotZ) / HEAD_TILT_THRESHOLD)
        score += (normalizedRotX + normalizedRotZ) * 0.15f

        // Blink rate contribution (0.3 weight)
        val blinksPerMinute = (blinkCount * 60000L) / max(1L, System.currentTimeMillis() - lastBlinkTime)
        val normalizedBlinkRate = when {
            blinksPerMinute < MIN_DROWSY_BLINK_THRESHOLD -> 0f
            blinksPerMinute > MAX_DROWSY_BLINK_THRESHOLD -> 1f
            else -> (blinksPerMinute - MIN_DROWSY_BLINK_THRESHOLD) / 
                    (MAX_DROWSY_BLINK_THRESHOLD - MIN_DROWSY_BLINK_THRESHOLD).toFloat()
        }
        score += normalizedBlinkRate * 0.3f

        return score
    }

    private fun calculateConfidence(face: Face): Float {
        // Base confidence on face detection confidence and tracking quality
        return face.trackingId?.let { 1f } ?: CONFIDENCE_THRESHOLD
    }
}
