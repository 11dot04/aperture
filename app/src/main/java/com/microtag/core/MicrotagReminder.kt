package com.microtag.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import com.microtag.R
import com.microtag.inspect.InspectPayload
import com.microtag.inspect.ProcessTextActivity

object MicrotagReminder {
    private const val CHANNEL_ID = "microtag_live_capsules"
    private const val CHANNEL_NAME = "Live Status Capsules"

    // Factory bridge: retains modularity while allowing MicrotagReminder.InspectPayload(...) call syntax
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
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Live Updates and ongoing activity status capsules"
            setShowBadge(false)
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

        val liveExtras = Bundle().apply {
            putString("android.substName", pillText)
            putCharSequence("android.ongoingActivity.shortText", pillText)
            putBoolean("android.ongoingActivity.isPromoted", true)
            putInt("android.ongoingActivity.style", 1)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSubText(pillText)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .addExtras(liveExtras)
            .addAction(0, "Dismiss", dismissPendingIntent)

        if (customIcon != null) {
            builder.setSmallIcon(IconCompat.createFromIcon(customIcon)!!)
        } else {
            builder.setSmallIcon(resolveDrawable(context, iconName))
        }

        if (contentPendingIntent != null) {
            builder.setContentIntent(contentPendingIntent)
        }

        if (timeoutSeconds > 0) {
            builder.setTimeoutAfter(timeoutSeconds * 1000L)
        }

        val notification = builder.build()

        try {
            val styleClass = Class.forName("android.app.Notification\$OngoingActivityStyle")
            val styleInstance = styleClass.getConstructor().newInstance()
            val setShortTextMethod = styleClass.getMethod("setShortText", CharSequence::class.java)
            setShortTextMethod.invoke(styleInstance, pillText)

            val platformBuilderClass = Notification.Builder::class.java
            val recoverBuilderMethod = platformBuilderClass.getMethod("recoverBuilder", Context::class.java, Notification::class.java)
            val platformBuilder = recoverBuilderMethod.invoke(null, context, notification) as Notification.Builder

            val setStyleMethod = platformBuilderClass.getMethod("setStyle", Notification.Style::class.java)
            setStyleMethod.invoke(platformBuilder, styleInstance)

            val styledNotification = platformBuilder.build()
            manager.notify(notificationId, styledNotification)
            return
        } catch (_: Throwable) {}

        manager.notify(notificationId, notification)
    }

    private fun resolveDrawable(context: Context, name: String?): Int {
        if (name.isNullOrBlank()) return R.drawable.ic_capsule_bolt
        val resId = context.resources.getIdentifier(name, "drawable", context.packageName)
        return if (resId != 0) resId else R.drawable.ic_capsule_bolt
    }
}
