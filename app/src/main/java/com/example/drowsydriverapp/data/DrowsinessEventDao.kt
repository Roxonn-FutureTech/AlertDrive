package com.example.drowsydriverapp.data

import androidx.room.*
import com.example.drowsydriverapp.data.models.DrowsinessEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface DrowsinessEventDao {
    @Query("SELECT * FROM drowsiness_events WHERE sessionId = :sessionId ORDER BY timestamp DESC")
    fun getSessionEvents(sessionId: String): Flow<List<DrowsinessEvent>>

    @Query("SELECT * FROM drowsiness_events WHERE sessionId = :sessionId AND drowsinessScore > 0.6")
    suspend fun getDrowsyEventsForSession(sessionId: String): List<DrowsinessEvent>

    @Query("SELECT AVG(drowsinessScore) FROM drowsiness_events WHERE sessionId = :sessionId")
    suspend fun getAverageDrowsinessScore(sessionId: String): Float

    @Query("SELECT AVG(confidence) FROM drowsiness_events WHERE sessionId = :sessionId")
    suspend fun getAverageConfidence(sessionId: String): Float

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: DrowsinessEvent)

    @Query("DELETE FROM drowsiness_events WHERE sessionId = :sessionId")
    suspend fun deleteSessionEvents(sessionId: String)

    @Query("SELECT * FROM drowsiness_events WHERE sessionId = :sessionId AND eventType != 'SESSION_START' AND eventType != 'SESSION_END' ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastEvent(sessionId: String): DrowsinessEvent?

    @Query("SELECT COUNT(*) FROM drowsiness_events WHERE sessionId = :sessionId")
    suspend fun getEventCount(sessionId: String): Int
}
