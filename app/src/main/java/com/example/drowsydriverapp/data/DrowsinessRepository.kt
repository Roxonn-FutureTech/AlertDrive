package com.example.drowsydriverapp.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.drowsydriverapp.data.models.*
import kotlinx.coroutines.flow.Flow
import java.util.*

private val Context.dataStore by preferencesDataStore(name = "drowsiness_settings")
private val CURRENT_SESSION_ID = stringPreferencesKey("current_session_id")

class DrowsinessRepository(private val context: Context) {
    private val database = DrowsinessDatabase.getDatabase(context)
    private val drowsinessEventDao = database.drowsinessEventDao()

    suspend fun startNewSession(): String {
        val sessionId = UUID.randomUUID().toString()
        context.dataStore.edit { preferences ->
            preferences[CURRENT_SESSION_ID] = sessionId
        }
        logDrowsinessEvent(
            eventType = EventType.SESSION_START,
            sessionId = sessionId,
            eyeOpenness = 1f,
            blinkCount = 0,
            headRotationX = 0f,
            headRotationY = 0f,
            headRotationZ = 0f,
            drowsinessScore = 0f,
            confidence = 1f
        )
        return sessionId
    }

    fun getSessionEventsFlow(sessionId: String): Flow<List<DrowsinessEvent>> {
        return drowsinessEventDao.getSessionEvents(sessionId)
    }

    suspend fun logDrowsinessEvent(
        eventType: EventType,
        sessionId: String,
        eyeOpenness: Float,
        blinkCount: Int,
        headRotationX: Float,
        headRotationY: Float,
        headRotationZ: Float,
        drowsinessScore: Float,
        confidence: Float
    ) {
        val event = DrowsinessEvent(
            sessionId = sessionId,
            eventType = eventType,
            eyeOpenness = eyeOpenness,
            blinkCount = blinkCount,
            headRotationX = headRotationX,
            headRotationY = headRotationY,
            headRotationZ = headRotationZ,
            drowsinessScore = drowsinessScore,
            confidence = confidence
        )
        drowsinessEventDao.insertEvent(event)
    }

    suspend fun endSession(sessionId: String) {
        logDrowsinessEvent(
            eventType = EventType.SESSION_END,
            sessionId = sessionId,
            eyeOpenness = 1f,
            blinkCount = 0,
            headRotationX = 0f,
            headRotationY = 0f,
            headRotationZ = 0f,
            drowsinessScore = 0f,
            confidence = 1f
        )
    }

    suspend fun getSessionStatistics(sessionId: String): SessionStatistics {
        val drowsyEvents = drowsinessEventDao.getDrowsyEventsForSession(sessionId)
        val avgDrowsinessScore = drowsinessEventDao.getAverageDrowsinessScore(sessionId)
        val avgConfidence = drowsinessEventDao.getAverageConfidence(sessionId)

        return SessionStatistics(
            totalEvents = drowsyEvents.size,
            drowsyEvents = drowsyEvents.size,
            averageDrowsinessScore = avgDrowsinessScore,
            averageConfidence = avgConfidence
        )
    }

    suspend fun exportSessionData(sessionId: String): String {
        val events = drowsinessEventDao.getSessionEvents(sessionId)
        // Convert events to CSV or JSON format
        return events.toString() // Simplified for now
    }
}
