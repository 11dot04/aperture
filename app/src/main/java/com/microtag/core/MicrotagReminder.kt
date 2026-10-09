package com.microtag.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.util.Log
import com.microtag.R
import com.microtag.inspect.InspectPayload
import com.microtag.inspect.ProcessTextActivity

object MicrotagReminder {
    private const val TAG = "MicrotagReminder"
    private const val CHANNEL_ID = "microtag_live_capsules_v3"
    private const val CHANNEL_NAME = "Live Status Capsules"
    
    // Android 16 (API 36) Promoted Ongoing Extra Key
    private const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"

    fun InspectPayload(
        domain: String = "",
        title: String = "",
        subtitle: String = "",
        fullContent: String = "",
        copyText: String = "",
        primaryText: String = "",
        secondaryText: String = "",
        actionType: String = "COPY",
        copyContent: String = "",
        extraData: String = ""
    ): InspectPayload = com.microtag.inspect.InspectPayload(
        domain = domain,
        title = title,
        subtitle = subtitle,
        fullContent = fullContent,
        copyText = copyText,
        primaryText = primaryText,
        secondaryText = secondaryText,
        actionType = actionType,
        copyContent = copyContent,
        extraData = extraData
    )

    fun createNotificationChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Live Updates and ongoing activity status capsules"
            setShowBadge(true)
            enableVibration(false)
            setSound(null, null)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
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
        iconName: String? = null,
        customIcon: Icon? = null,
        detailPayload: InspectPayload? = null
    ) {
        createNotificationChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val dismissIntent = Intent(context, CapsuleDismissReceiver::class.java).apply {
            putExtra("notification_id", notificationId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentPendingIntent = detailPayload?.let { payload ->
            val tapIntent = Intent(context, ProcessTextActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("EXTRA_PAYLOAD", payload)
            }
            PendingIntent.getActivity(
                context,
                notificationId + 1000,
                tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val finalIcon = customIcon ?: run {
            val resId = resolveDrawable(context, iconName)
            Icon.createWithResource(context, resId)
        }

        val builder = Notification.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(finalIcon)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_WORKOUT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .addAction(
                Notification.Action.Builder(
                    null,
                    "Dismiss",
                    dismissPendingIntent
                ).build()
            )

        if (contentPendingIntent != null) {
            builder.setContentIntent(contentPendingIntent)
        }

        if (timeoutSeconds > 0) {
            builder.setTimeoutAfter(timeoutSeconds * 1000L)
        }

        // ==========================================
        // Android 16 (API 36) Native Live Updates Configuration
        // ==========================================
        
        try {
            // Apply the short critical text to populate the Status Bar Chip
            val setShortCriticalTextMethod = Notification.Builder::class.java.getMethod("setShortCriticalText", CharSequence::class.java)
            setShortCriticalTextMethod.invoke(builder, pillText)
        } catch (e: Exception) {
            Log.w(TAG, "setShortCriticalText not available: ${e.message}")
        }
        
        // Request OS-level promotion directly in the extras bundle
        builder.extras.putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true)

        // Ensure fallback compatibility for heavily customized vendor skins (ColorOS/HyperOS)
        builder.extras.putString("oplus.liveNotificationType", "capsule")

        manager.notify(notificationId, builder.build())
    }

    private fun resolveDrawable(context: Context, name: String?): Int {
        if (name.isNullOrBlank()) return R.drawable.ic_capsule_bolt
        val resId = context.resources.getIdentifier(name, "drawable", context.packageName)
        return if (resId != 0) resId else R.drawable.ic_capsule_bolt
    }
}
