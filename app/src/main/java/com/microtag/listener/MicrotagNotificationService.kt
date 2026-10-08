package com.microtag.listener

import android.app.Notification
import android.app.NotificationManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.microtag.core.MicrotagReminder
import com.microtag.rules.RuleEngine

class MicrotagNotificationService : NotificationListenerService() {

    override fun onCreate() {
        super.onCreate()
        RuleEngine.init(applicationContext)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val pkg = sbn.packageName ?: return
        val notif = sbn.notification ?: return
        val extras = notif.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        // 1. Evaluate Dynamic Rules (custom_rules.json)
        val match = RuleEngine.evaluate(pkg, title, text)
        if (match != null) {
            MicrotagReminder.showCapsule(
                context = applicationContext,
                pillText = match.pillText,
                title = title.ifBlank { pkg },
                content = text,
                notificationId = sbn.id,
                timeoutSeconds = match.timeoutSeconds,
                iconName = match.iconName
            )
            return
        }

        // 2. Fall-Through to System Hooks & Ongoing Events
        when (pkg) {
            "ch.protonvpn.android" -> handleProtonVpn(sbn, title, text)
            "com.microsoft.teams" -> handleTeams(sbn, title, text)
            "com.google.android.apps.maps" -> handleMaps(sbn, title, text)
            "com.android.providers.downloads" -> handleDownloads(sbn, notif, title, text)
            else -> {
                if (notif.extras.containsKey(Notification.EXTRA_PROGRESS)) {
                    handleGenericProgress(sbn, notif, title, text)
                }
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn == null) return

        // Automatically cancel any active capsule if an ongoing source was cleared
        if (sbn.isOngoing) {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(sbn.id)
        }
    }

    private fun handleProtonVpn(sbn: StatusBarNotification, title: String, text: String) {
        val isConnected = text.contains("Connected", ignoreCase = true) || title.contains("Connected", ignoreCase = true)
        if (!isConnected) return

        val serverMatch = Regex("""(?i)(?:to\s+)?([A-Z]{2}(?:-[A-Z]+)?#\d+)""").find("$title $text")
        val nodeName = serverMatch?.groupValues?.get(1) ?: "VPN Active"

        MicrotagReminder.showCapsule(
            context = applicationContext,
            pillText = nodeName,
            title = "Proton VPN",
            content = "Encrypted tunnel active • $nodeName",
            notificationId = sbn.id,
            timeoutSeconds = 8
        )
    }

    private fun handleTeams(sbn: StatusBarNotification, title: String, text: String) {
        if (title.contains("meeting", ignoreCase = true) || text.contains("call", ignoreCase = true)) {
            MicrotagReminder.showCapsule(
                context = applicationContext,
                pillText = "Teams Live",
                title = title.ifBlank { "Microsoft Teams" },
                content = text,
                notificationId = sbn.id,
                timeoutSeconds = 15
            )
        }
    }

    private fun handleMaps(sbn: StatusBarNotification, title: String, text: String) {
        if (sbn.isOngoing) {
            MicrotagReminder.showCapsule(
                context = applicationContext,
                pillText = "Nav",
                title = title,
                content = text,
                notificationId = sbn.id,
                timeoutSeconds = 10
            )
        }
    }

    private fun handleDownloads(sbn: StatusBarNotification, notif: Notification, title: String, text: String) {
        val max = notif.extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val progress = notif.extras.getInt(Notification.EXTRA_PROGRESS, 0)

        if (max > 0 && progress < max) {
            val pct = ((progress.toDouble() / max) * 100).toInt()
            MicrotagReminder.showCapsule(
                context = applicationContext,
                pillText = "$pct%",
                title = title.ifBlank { "Downloading" },
                content = "$progress of $max",
                notificationId = sbn.id,
                timeoutSeconds = 5
            )
        }
    }

    private fun handleGenericProgress(sbn: StatusBarNotification, notif: Notification, title: String, text: String) {
        val max = notif.extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val current = notif.extras.getInt(Notification.EXTRA_PROGRESS, 0)
        if (max > 0 && current < max) {
            val pct = ((current.toDouble() / max) * 100).toInt()
            MicrotagReminder.showCapsule(
                context = applicationContext,
                pillText = "$pct%",
                title = title,
                content = text,
                notificationId = sbn.id,
                timeoutSeconds = 4
            )
        }
    }
}
