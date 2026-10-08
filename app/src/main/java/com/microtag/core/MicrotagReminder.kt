package com.microtag.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import com.microtag.R

object MicrotagReminder {
    private const val CHANNEL_ID = "microtag_live_capsules"
    private const val CHANNEL_NAME = "Live Status Capsules"

    fun createNotificationChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Ephemeral live status chips and ongoing capsules"
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    fun showCapsule(
        context: Context,
        pillText: String,
        title: String,
        content: String,
        notificationId: Int,
        timeoutSeconds: Int = 0,
        iconName: String? = null
    ) {
        createNotificationChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val iconRes = resolveDrawable(context, iconName)

        val dismissIntent = Intent(context, CapsuleDismissReceiver::class.java).apply {
            putExtra("notification_id", notificationId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Android 16 Live Updates / Status Chip bundle keys
        val liveExtras = Bundle().apply {
            putString("android.substName", pillText)
            putCharSequence("android.ongoingActivity.shortText", pillText)
            putBoolean("android.ongoingActivity.isPromoted", true)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(iconRes)
            .setContentTitle(title)
            .setContentText(content)
            .setSubText(pillText) // Status bar chip fallback text
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .addExtras(liveExtras)
            .addAction(0, "Dismiss", dismissPendingIntent)

        if (timeoutSeconds > 0) {
            builder.setTimeoutAfter(timeoutSeconds * 1000L)
        }

        val notification = builder.build()

        // Hook reflection for Android 16 OngoingActivityStyle if present on device runtime
        try {
            val styleClass = Class.forName("android.app.Notification\$OngoingActivityStyle")
            val constructor = styleClass.getConstructor()
            val styleInstance = constructor.newInstance()

            val setShortTextMethod = styleClass.getMethod("setShortText", CharSequence::class.java)
            setShortTextMethod.invoke(styleInstance, pillText)

            val applyMethod = styleClass.getMethod("apply", Notification::class.java)
            applyMethod.invoke(styleInstance, notification)
        } catch (_: Exception) {
            // Falls back cleanly to standard high-priority ongoing status notification
        }

        manager.notify(notificationId, notification)
    }

    private fun resolveDrawable(context: Context, name: String?): Int {
        if (name.isNullOrBlank()) return R.drawable.ic_capsule_bolt
        val resId = context.resources.getIdentifier(name, "drawable", context.packageName)
        return if (resId != 0) resId else R.drawable.ic_capsule_bolt
    }
}
