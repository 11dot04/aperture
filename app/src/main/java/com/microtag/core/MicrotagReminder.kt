package com.microtag.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.microtag.inspect.InspectDetailActivity

object MicrotagReminder {

    private const val CHANNEL_ID = "microtag_live_capsules"
    private const val CHANNEL_NAME = "Live Status Capsules"
    private val mainHandler = Handler(Looper.getMainLooper())

    data class InspectPayload(
        val domain: String,
        val title: String,
        val subtitle: String,
        val fullContent: String,
        val copyText: String
    )

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Microtag micro-status ambient capsules"
                    enableVibration(false)
                    setSound(null, null)
                    setShowBadge(false)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun showCapsule(
        context: Context,
        pillText: String,
        title: String,
        content: String,
        notificationId: Int,
        timeoutSeconds: Int = 12,
        iconName: String? = null,
        customIcon: Icon? = null,
        actions: List<Notification.Action> = emptyList(),
        detailPayload: InspectPayload? = null
    ) {
        ensureChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("[$pillText] $title")
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(false)
            .setAutoCancel(true)
            .setSilent(true)

        if (customIcon != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Can be used if small icon supports custom dynamic icons
        }

        // Tap capsule -> Open detail inspection monograph
        if (detailPayload != null) {
            val detailIntent = Intent(context, InspectDetailActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("EXTRA_NOTIFICATION_ID", notificationId)
                putExtra("EXTRA_DOMAIN", detailPayload.domain)
                putExtra("EXTRA_TITLE", detailPayload.title)
                putExtra("EXTRA_SUBTITLE", detailPayload.subtitle)
                putExtra("EXTRA_FULL_CONTENT", detailPayload.fullContent)
                putExtra("EXTRA_COPY_PAYLOAD", detailPayload.copyText)
            }
            val pIntent = PendingIntent.getActivity(
                context,
                notificationId,
                detailIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setContentIntent(pIntent)
        }

        // Tap action -> Headless Dismiss
        val dismissIntent = Intent(context, CapsuleDismissReceiver::class.java).apply {
            putExtra("notification_id", notificationId)
        }
        val dismissPending = PendingIntent.getBroadcast(
            context,
            notificationId,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", dismissPending)

        manager.notify(notificationId, builder.build())

        // Ephemeral timeout handling
        if (timeoutSeconds > 0) {
            mainHandler.postDelayed({
                manager.cancel(notificationId)
            }, (timeoutSeconds * 1000).toLong())
        }
    }
}
