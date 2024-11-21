package com.example.drowsydriverapp.data

import androidx.room.TypeConverter
import com.example.drowsydriverapp.data.models.EventType
import java.util.*

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }

    @TypeConverter
    fun fromEventType(value: EventType): String {
        return value.name
    }

    @TypeConverter
    fun toEventType(value: String): EventType {
        return EventType.valueOf(value)
    }
}
