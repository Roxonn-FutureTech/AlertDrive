package com.example.drowsydriverapp.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.example.drowsydriverapp.data.models.SessionStatistics
import kotlinx.coroutines.flow.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class DrowsinessRepository(private val context: Context) {
    private val database = DrowsinessDatabase.getDatabase(context)
    private val drowsinessEventDao = database.drowsinessEventDao()
    private val currentSessionId = stringPreferencesKey("current_session_id")

    suspend fun startNewSession(): String {
        val sessionId = UUID.randomUUID().toString()
        context.dataStore.edit { preferences ->
            preferences[currentSessionId] = sessionId
        }
        drowsinessEventDao.insertEvent(
            DrowsinessEvent(
                eventType = EventType.SESSION_START,
                eyeOpenness = 1f,
                blinkCount = 0,
                headRotationX = 0f,
                headRotationY = 0f,
                headRotationZ = 0f,
                isDrowsy = false,
                sessionId = sessionId
            )
        )
        return sessionId
    }

    suspend fun logDrowsinessEvent(
        eventType: EventType,
        eyeOpenness: Float,
        blinkCount: Int,
        headRotationX: Float,
        headRotationY: Float,
        headRotationZ: Float,
        isDrowsy: Boolean
    ) {
        val sessionId = context.dataStore.data.map { preferences ->
            preferences[currentSessionId] ?: startNewSession()
        }.first()

        drowsinessEventDao.insertEvent(
            DrowsinessEvent(
                eventType = eventType,
                eyeOpenness = eyeOpenness,
                blinkCount = blinkCount,
                headRotationX = headRotationX,
                headRotationY = headRotationY,
                headRotationZ = headRotationZ,
                isDrowsy = isDrowsy,
                sessionId = sessionId
            )
        )
    }

    fun getSessionEvents(sessionId: String): Flow<List<DrowsinessEvent>> {
        return drowsinessEventDao.getSessionEvents(sessionId)
    }

    suspend fun exportSessionData(sessionId: String): String {
        val events = drowsinessEventDao.getSessionEvents(sessionId).first()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
        val fileName = "drowsiness_session_${dateFormat.format(Date())}.csv"
        val file = File(context.getExternalFilesDir(null), fileName)

        file.bufferedWriter().use { writer ->
            writer.write("Timestamp,EventType,EyeOpenness,BlinkCount,HeadRotationX,HeadRotationY,HeadRotationZ,IsDrowsy\n")
            events.forEach { event ->
                writer.write("${event.timestamp},${event.eventType},${event.eyeOpenness},${event.blinkCount}," +
                        "${event.headRotationX},${event.headRotationY},${event.headRotationZ},${event.isDrowsy}\n")
            }
        }

        return file.absolutePath
    }

    suspend fun getSessionStatistics(sessionId: String): SessionStatistics {
        val events = drowsinessEventDao.getSessionEvents(sessionId).first()
        if (events.isEmpty()) {
            return SessionStatistics(
                totalEvents = 0,
                drowsyEvents = 0,
                alertsTriggered = 0,
                averageEyeOpenness = 0f,
                averageBlinkRate = 0f,
                sessionDuration = 0
            )
        }

        val drowsyCount = events.count { it.eventType == EventType.DROWSY_DETECTED }
        val alertCount = events.count { it.eventType == EventType.ALERT_TRIGGERED }
        val firstEvent = events.minByOrNull { it.timestamp }
        val lastEvent = events.maxByOrNull { it.timestamp }
        val sessionDuration = if (firstEvent != null && lastEvent != null) {
            lastEvent.timestamp - firstEvent.timestamp
        } else 0L

        return SessionStatistics(
            totalEvents = events.size,
            drowsyEvents = drowsyCount,
            alertsTriggered = alertCount,
            averageEyeOpenness = events.map { it.eyeOpenness }.average().toFloat(),
            averageBlinkRate = events.map { it.blinkCount }.average().toFloat(),
            sessionDuration = sessionDuration
        )
    }
}
