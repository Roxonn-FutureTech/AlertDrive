package com.example.drowsydriverapp.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.example.drowsydriverapp.data.converters.EventTypeConverter

@Dao
interface DrowsinessEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: DrowsinessEvent)

    @Query("SELECT * FROM drowsiness_events WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getSessionEvents(sessionId: String): Flow<List<DrowsinessEvent>>
}

@Database(entities = [DrowsinessEvent::class], version = 1)
@TypeConverters(EventTypeConverter::class)
abstract class DrowsinessDatabase : RoomDatabase() {
    abstract fun drowsinessEventDao(): DrowsinessEventDao

    companion object {
        @Volatile
        private var instance: DrowsinessDatabase? = null

        fun getDatabase(context: Context): DrowsinessDatabase {
            return instance ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DrowsinessDatabase::class.java,
                    "drowsiness_database"
                ).build()
                this.instance = instance
                instance
            }
        }
    }
}
