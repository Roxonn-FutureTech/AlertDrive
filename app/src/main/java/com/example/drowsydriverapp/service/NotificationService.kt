package com.example.drowsydriverapp.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.drowsydriverapp.R
import com.example.drowsydriverapp.data.models.SessionStatistics
import com.example.drowsydriverapp.data.models.AlertLevel

class NotificationService : Service() {
    companion object {
        private const val CHANNEL_ID = "DrowsyDriverChannel"
        private const val NOTIFICATION_ID = 1
    }

    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Drowsy Driver Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts for drowsy driving detection"
            enableVibration(true)
            enableLights(true)
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun showDrowsinessAlert(alertLevel: AlertLevel, statistics: SessionStatistics) {
        val (title, message) = when (alertLevel) {
            AlertLevel.WARNING -> Pair(
                "Drowsiness Warning",
                "You're showing early signs of drowsiness. Consider taking a break."
            )
            AlertLevel.SEVERE -> Pair(
                "Severe Drowsiness Detected",
                "You appear very drowsy. Please find a safe place to stop."
            )
            AlertLevel.CRITICAL -> Pair(
                "Critical Alert!",
                "Immediate action required! Pull over safely now!"
            )
            else -> return // Don't show notification for NORMAL state
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_warning)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                """
                $message
                
                Session Statistics:
                Total Events: ${statistics.totalEvents}
                Drowsy Events: ${statistics.drowsyEvents}
                Average Score: ${String.format("%.2f", statistics.averageDrowsinessScore)}
                """.trimIndent()
            ))
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
