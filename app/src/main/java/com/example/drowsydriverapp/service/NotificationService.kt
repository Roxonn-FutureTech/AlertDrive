package com.example.drowsydriverapp.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.drowsydriverapp.R
import com.example.drowsydriverapp.data.SessionStatistics

class NotificationService(private val context: Context) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId = "drowsy_driver_channel"

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Drowsy Driver Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for drowsy driving detection"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showDrowsinessAlert() {
        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Drowsiness Alert!")
            .setContentText("You appear to be drowsy. Please take a break.")
            .setSmallIcon(R.drawable.ic_notification_alert)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1, notification)
    }

    fun showSessionSummary(statistics: SessionStatistics) {
        val duration = String.format(
            "%d min %d sec",
            statistics.sessionDuration / 60000,
            (statistics.sessionDuration % 60000) / 1000
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Session Summary")
            .setContentText("Duration: $duration, Drowsy Events: ${statistics.drowsyEvents}")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("""
                    Session Duration: $duration
                    Drowsy Events: ${statistics.drowsyEvents}
                    Alerts Triggered: ${statistics.alertsTriggered}
                    Average Blink Rate: %.1f per minute
                """.trimIndent().format(statistics.averageBlinkRate * 60)))
            .setSmallIcon(R.drawable.ic_notification_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(2, notification)
    }
}
