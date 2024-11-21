package com.example.drowsydriverapp.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromEventType(value: EventType): String {
        return value.name
    }

    @TypeConverter
    fun toEventType(value: String): EventType {
        return enumValueOf(value)
    }
}
