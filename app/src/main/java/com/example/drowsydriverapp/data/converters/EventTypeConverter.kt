package com.example.drowsydriverapp.data.converters

import androidx.room.TypeConverter
import com.example.drowsydriverapp.data.EventType

class EventTypeConverter {
    @TypeConverter
    fun fromEventType(eventType: EventType): String {
        return eventType.name
    }

    @TypeConverter
    fun toEventType(value: String): EventType {
        return EventType.valueOf(value)
    }
}
