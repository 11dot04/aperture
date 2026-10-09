package com.microtag.listener

import android.app.Notification
import android.app.NotificationManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.microtag.core.MicrotagPrefs
import com.microtag.core.MicrotagReminder
import com.microtag.rules.RuleEngine
import com.microtag.shizuku.ShizukuClipboardWatcher

class MicrotagNotificationService : NotificationListenerService() {

    private lateinit var prefs: MicrotagPrefs

    override fun onCreate() {
        super.onCreate()
        prefs = MicrotagPrefs(applicationContext)
        RuleEngine.init(applicationContext)
        if (prefs.isAppEnabled(MicrotagPrefs.KEY_CLIPBOARD)) {
            ShizukuClipboardWatcher.init(applicationContext)
        }
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
            prefs.incrementInterceptCount()
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

        // 2. Comprehensive Capsule Parser
        val parsed = CapsuleParser.parse(sbn)
        if (parsed != null) {
            
            // Check App Toggles from Dashboard Preferences
            val isStrava = pkg == "com.strava" || pkg.contains("workout") || pkg.contains("fitness")
            val isDiscord = pkg == "com.discord" || pkg.contains("discord")
            val isOtp = pkg.contains("messaging") || pkg.contains("sms") || pkg.contains("google.android.apps.messaging")
            val isProton = pkg == "ch.protonvpn.android"
            val isProgress = extras.containsKey(Notification.EXTRA_PROGRESS) && pkg != "ch.protonvpn.android"

            if ((isStrava && !prefs.isAppEnabled(MicrotagPrefs.KEY_STRAVA)) ||
                (isDiscord && !prefs.isAppEnabled(MicrotagPrefs.KEY_DISCORD)) ||
                (isOtp && !prefs.isAppEnabled(MicrotagPrefs.KEY_OTP)) ||
                (isProton && !prefs.isAppEnabled(MicrotagPrefs.KEY_PROTON)) ||
                (isProgress && !prefs.isAppEnabled(MicrotagPrefs.KEY_DOWNLOADS))
            ) {
                return
            }

            prefs.incrementInterceptCount()
            
            // Forward everything, including native buttons and chronometers, to the engine
            MicrotagReminder.showCapsule(
                context = applicationContext,
                pillText = parsed.pillText,
                title = parsed.title,
                content = parsed.content,
                notificationId = sbn.id,
                timeoutSeconds = parsed.timeoutSeconds,
                iconName = parsed.iconName,
                customIcon = null,
                detailPayload = parsed.payload,
                chronometerTargetMillis = parsed.chronometerTargetMillis,
                actions = parsed.actions
            )
            return
        }

        // 3. Simple Fallbacks (Teams/Maps)
        when {
            pkg == "com.microsoft.teams" && prefs.isAppEnabled(MicrotagPrefs.KEY_TEAMS) -> {
                handleTeams(sbn, title, text)
            }
            pkg == "com.google.android.apps.maps" && prefs.isAppEnabled(MicrotagPrefs.KEY_MAPS) -> {
                handleMaps(sbn, title, text)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn == null) return
        
        // Critically important: Stop the recursive minute ticker if the calendar event is dismissed
        MicrotagReminder.cancelTicker(sbn.id)
        
        if (sbn.isOngoing) {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(sbn.id)
        }
    }

    private fun handleTeams(sbn: StatusBarNotification, title: String, text: String) {
        if (title.contains("meeting", ignoreCase = true) || text.contains("call", ignoreCase = true)) {
            prefs.incrementInterceptCount()
            MicrotagReminder.showCapsule(
                context = applicationContext,
                pillText = "Teams Live",
                title = title.ifBlank { "Microsoft Teams" },
                content = text,
                notificationId = sbn.id,
                timeoutSeconds = 15,
                actions = sbn.notification.actions?.toList() ?: emptyList() // Pass native actions
            )
        }
    }

    private fun handleMaps(sbn: StatusBarNotification, title: String, text: String) {
        if (sbn.isOngoing) {
            prefs.incrementInterceptCount()
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
}
