package com.microtag.listener

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.microtag.core.MicrotagLog
import com.microtag.core.MicrotagLog.Decision
import com.microtag.core.MicrotagPrefs
import com.microtag.core.MicrotagReminder
import com.microtag.rules.RuleEngine
import com.microtag.shizuku.ShizukuClipboardWatcher
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

class MicrotagNotificationService : NotificationListenerService() {

    private lateinit var prefs: MicrotagPrefs
    private val handler = Handler(Looper.getMainLooper())

    // sbn.key -> signature of what we last showed (skip identical re-posts)
    private val lastShown = ConcurrentHashMap<String, String>()
    // sbn.key -> capsule notification id we posted for it
    private val capsuleIds = ConcurrentHashMap<String, Int>()
    // keys whose capsule expires by itself (timeoutSeconds > 0)
    private val timedKeys = ConcurrentHashMap.newKeySet<String>()

    private val pollTask = object : Runnable {
        override fun run() {
            pollActiveNotifications()
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = MicrotagPrefs(applicationContext)
        MicrotagLog.init(applicationContext)
        RuleEngine.init(applicationContext)
        if (prefs.isAppEnabled(MicrotagPrefs.KEY_CLIPBOARD)) {
            ShizukuClipboardWatcher.init(applicationContext)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Listener connected")
        handler.removeCallbacks(pollTask)
        handler.post(pollTask)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "Listener disconnected, requesting rebind")
        handler.removeCallbacks(pollTask)
        try {
            requestRebind(ComponentName(this, MicrotagNotificationService::class.java))
        } catch (e: Exception) {
            Log.e(TAG, "Rebind failed", e)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return
        try {
            process(sbn)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process ${sbn.key}", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn == null || sbn.packageName == packageName) return

        val id = capsuleIds[sbn.key]
        if (id != null) MicrotagReminder.cancelTicker(id)

        // Timed capsules (OTP, songs...) expire on their own; everything else follows the source.
        if (!timedKeys.contains(sbn.key)) {
            if (capsuleIds.containsKey(sbn.key)) {
                logDecision(sbn, "REMOVED", Decision.CLEARED, "source notification removed")
            }
            clearCapsule(sbn.key)
        } else {
            lastShown.remove(sbn.key)
            capsuleIds.remove(sbn.key)
            timedKeys.remove(sbn.key)
        }
    }

    // Some apps update in ways that never trigger a fresh post event, so re-check ongoing ones.
    private fun pollActiveNotifications() {
        val active = try {
            activeNotifications
        } catch (e: Exception) {
            Log.w(TAG, "Poll failed reading active notifications", e)
            null
        } ?: return

        for (sbn in active) {
            if (sbn.packageName == packageName || !sbn.isOngoing) continue
            try {
                process(sbn)
            } catch (e: Exception) {
                Log.e(TAG, "Poll failed for ${sbn.key}", e)
            }
        }
    }

    private fun process(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return // never react to our own capsules
        val notif = sbn.notification ?: return
        val extras = notif.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val id = capsuleIdFor(sbn.key)

        // 1. Dynamic rules (custom_rules.json)
        val match = RuleEngine.evaluate(pkg, title, text)
        if (match != null) {
            val signature = "${match.pillText}|$title|$text|${match.iconName}"
            if (lastShown[sbn.key] == signature) {
                logDecision(sbn, "RULE", Decision.UNCHANGED, "rule match, same as last shown",
                    match.pillText, match.iconName)
                return
            }
            Log.d(TAG, "RULE   $pkg pill='${match.pillText}' icon=${match.iconName}")
            logDecision(sbn, "RULE", Decision.PROMOTED, "custom rule matched",
                match.pillText, match.iconName)
            lastShown[sbn.key] = signature
            capsuleIds[sbn.key] = id
            trackTimeout(sbn.key, match.timeoutSeconds)
            prefs.incrementInterceptCount()
            MicrotagReminder.showCapsule(
                context = applicationContext,
                pillText = match.pillText,
                title = title.ifBlank { pkg },
                content = text,
                notificationId = id,
                timeoutSeconds = match.timeoutSeconds,
                iconName = match.iconName
            )
            return
        }

        // 2. Capsule parser
        val parsed = CapsuleParser.parse(sbn)
        if (parsed != null) {
            if (isDisabledByToggle(pkg, extras)) {
                logDecision(sbn, "PARSER", Decision.SKIPPED, "app toggle is off in dashboard",
                    parsed.pillText, parsed.iconName)
                return
            }

            val signature = "${parsed.pillText}|${parsed.title}|${parsed.content}|${parsed.iconName}"
            if (lastShown[sbn.key] == signature) {
                logDecision(sbn, "PARSER", Decision.UNCHANGED, "parsed, same as last shown",
                    parsed.pillText, parsed.iconName)
                return
            }
            Log.d(TAG, "PARSER $pkg pill='${parsed.pillText}' icon=${parsed.iconName}")
            logDecision(sbn, "PARSER", Decision.PROMOTED, "parser produced a capsule",
                parsed.pillText, parsed.iconName)
            lastShown[sbn.key] = signature
            capsuleIds[sbn.key] = id
            trackTimeout(sbn.key, parsed.timeoutSeconds)

            prefs.incrementInterceptCount()
            MicrotagReminder.showCapsule(
                context = applicationContext,
                pillText = parsed.pillText,
                title = parsed.title,
                content = parsed.content,
                notificationId = id,
                timeoutSeconds = parsed.timeoutSeconds,
                iconName = parsed.iconName,
                customIcon = null,
                detailPayload = parsed.payload,
                chronometerTargetMillis = parsed.chronometerTargetMillis,
                actions = parsed.actions
            )
            return
        }

        // 3. Simple fallbacks (Teams / Maps)
        val handled = when {
            pkg == "com.microsoft.teams" && prefs.isAppEnabled(MicrotagPrefs.KEY_TEAMS) ->
                handleTeams(sbn, id, title, text)
            pkg == "com.google.android.apps.maps" && prefs.isAppEnabled(MicrotagPrefs.KEY_MAPS) ->
                handleMaps(sbn, id, title, text)
            else -> false
        }
        if (handled) return

        // The source stopped matching anything (VPN disconnected, call ended, workout stopped...)
        if (lastShown.containsKey(sbn.key) && !timedKeys.contains(sbn.key)) {
            Log.d(TAG, "CLEAR  $pkg (no longer matches)")
            logDecision(sbn, "PARSER", Decision.CLEARED, "source no longer matches any rule or parser")
            clearCapsule(sbn.key)
        } else {
            logDecision(sbn, "NONE", Decision.IGNORED, "no rule, parser or fallback matched")
        }
    }

    private fun isDisabledByToggle(pkg: String, extras: Bundle): Boolean {
        val isStrava = pkg == "com.strava" || pkg.contains("workout") || pkg.contains("fitness")
        val isDiscord = pkg == "com.discord" || pkg.contains("discord")
        val isOtp = pkg.contains("messaging") || pkg.contains("sms") || pkg.contains("google.android.apps.messaging")
        val isProton = pkg == "ch.protonvpn.android"
        val isProgress = extras.containsKey(Notification.EXTRA_PROGRESS) && pkg != "ch.protonvpn.android"

        return (isStrava && !prefs.isAppEnabled(MicrotagPrefs.KEY_STRAVA)) ||
                (isDiscord && !prefs.isAppEnabled(MicrotagPrefs.KEY_DISCORD)) ||
                (isOtp && !prefs.isAppEnabled(MicrotagPrefs.KEY_OTP)) ||
                (isProton && !prefs.isAppEnabled(MicrotagPrefs.KEY_PROTON)) ||
                (isProgress && !prefs.isAppEnabled(MicrotagPrefs.KEY_DOWNLOADS))
    }

    private fun handleTeams(sbn: StatusBarNotification, id: Int, title: String, text: String): Boolean {
        if (!(title.contains("meeting", ignoreCase = true) || text.contains("call", ignoreCase = true))) {
            return false
        }
        val signature = "Teams Live|$title|$text"
        if (lastShown[sbn.key] == signature) {
            logDecision(sbn, "FALLBACK", Decision.UNCHANGED, "Teams, same as last shown", "Teams Live")
            return true
        }
        logDecision(sbn, "FALLBACK", Decision.PROMOTED, "Teams meeting/call fallback", "Teams Live")
        lastShown[sbn.key] = signature
        capsuleIds[sbn.key] = id
        trackTimeout(sbn.key, 15)
        prefs.incrementInterceptCount()
        MicrotagReminder.showCapsule(
            context = applicationContext,
            pillText = "Teams Live",
            title = title.ifBlank { "Microsoft Teams" },
            content = text,
            notificationId = id,
            timeoutSeconds = 15,
            actions = sbn.notification.actions?.toList() ?: emptyList()
        )
        return true
    }

    private fun handleMaps(sbn: StatusBarNotification, id: Int, title: String, text: String): Boolean {
        if (!sbn.isOngoing) return false
        val signature = "Nav|$title|$text"
        if (lastShown[sbn.key] == signature) {
            logDecision(sbn, "FALLBACK", Decision.UNCHANGED, "Maps, same as last shown", "Nav")
            return true
        }
        logDecision(sbn, "FALLBACK", Decision.PROMOTED, "Maps ongoing navigation fallback", "Nav")
        lastShown[sbn.key] = signature
        capsuleIds[sbn.key] = id
        trackTimeout(sbn.key, 10)
        prefs.incrementInterceptCount()
        MicrotagReminder.showCapsule(
            context = applicationContext,
            pillText = "Nav",
            title = title,
            content = text,
            notificationId = id,
            timeoutSeconds = 10
        )
        return true
    }

    private fun clearCapsule(key: String) {
        val id = capsuleIds.remove(key)
        lastShown.remove(key)
        timedKeys.remove(key)
        if (id != null) {
            MicrotagReminder.cancelTicker(id)
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(id)
        }
    }

    private fun trackTimeout(key: String, timeoutSeconds: Int) {
        if (timeoutSeconds > 0) timedKeys.add(key) else timedKeys.remove(key)
    }

    // Unique per source notification (package + id + tag), so apps can't overwrite each other.
    private fun capsuleIdFor(key: String): Int {
        val h = key.hashCode()
        return if (h == Int.MIN_VALUE) 0 else abs(h)
    }

    // ---- Logging -----------------------------------------------------------------------------

    private fun logDecision(
        sbn: StatusBarNotification,
        path: String,
        decision: Decision,
        reason: String,
        pill: String? = null,
        icon: String? = null
    ) {
        val extras = sbn.notification?.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val sub = extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
        MicrotagLog.add(
            key = sbn.key,
            pkg = sbn.packageName.orEmpty(),
            path = path,
            decision = decision,
            reason = reason,
            pill = pill,
            icon = icon,
            title = title,
            text = if (sub.isNotBlank()) "$text | sub: $sub" else text,
            meta = metaOf(sbn)
        )
    }

    private fun metaOf(sbn: StatusBarNotification): String {
        val n = sbn.notification ?: return ""
        val channel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) n.channelId.orEmpty() else ""
        val actions = n.actions?.joinToString("|") { it.title?.toString().orEmpty() }.orEmpty()
        val template = n.extras?.getString(Notification.EXTRA_TEMPLATE)
            ?.substringAfterLast('$').orEmpty()
        // 0x40000 = FLAG_PROMOTED_ONGOING: the source app is already showing its own Live Update
        val sourcePromoted = Build.VERSION.SDK_INT >= 36 && (n.flags and 0x40000) != 0
        return "ongoing=${sbn.isOngoing} clearable=${sbn.isClearable} cat=${n.category} " +
                "ch=$channel tmpl=$template promotedSrc=$sourcePromoted actions=[$actions]"
    }

    companion object {
        private const val TAG = "MicrotagListener"
        private const val POLL_INTERVAL_MS = 3_000L
    }
}
