package com.microtag.listener

import android.app.Notification
import android.service.notification.StatusBarNotification
import com.microtag.R
import com.microtag.inspect.InspectPayload

data class ParsedCapsule(
    val pillText: String,
    val title: String,
    val content: String,
    val iconName: String? = null,
    val iconRes: Int = 0,
    val timeoutSeconds: Int = 0,
    val payload: InspectPayload? = null
)

object CapsuleParser {

    fun parse(sbn: StatusBarNotification): ParsedCapsule? {
        val pkg = sbn.packageName ?: return null
        val notif = sbn.notification ?: return null
        val extras = notif.extras ?: return null

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""
        val combined = "$title $text $subText".trim()

        return when {
            // 1. STRAVA & RUNNING WORKOUTS
            pkg == "com.strava" || pkg.contains("workout") || pkg.contains("fitness") -> {
                parseStrava(combined, title, text)
            }

            // 2. DISCORD MENTIONS & DMS
            pkg == "com.discord" || pkg.contains("discord") -> {
                parseDiscord(combined, title, text)
            }

            // 3. OTP & SMS CODES
            pkg.contains("messaging") || pkg.contains("sms") || pkg.contains("google.android.apps.messaging") -> {
                parseOtp(combined, title, text)
            }

            // 4. ACTIVE DOWNLOADS & PROGRESS
            extras.containsKey(Notification.EXTRA_PROGRESS) -> {
                parseProgress(notif, title, text)
            }

            else -> null
        }
    }

    private fun parseStrava(combined: String, title: String, text: String): ParsedCapsule? {
        // Extract split pace (e.g. 5:12 /km) or elapsed time
        val paceMatch = Regex("""\b\d{1,2}:\d{2}(?:\s?/\s?(?:km|mi))?\b""").find(combined)
        val distanceMatch = Regex("""\b\d+(?:\.\d+)?\s?(?:km|mi)\b""", RegexOption.IGNORE_CASE).find(combined)

        val pill = when {
            paceMatch != null && distanceMatch != null -> "${distanceMatch.value} • ${paceMatch.value}"
            distanceMatch != null -> distanceMatch.value
            paceMatch != null -> paceMatch.value
            else -> "Live Run"
        }

        return ParsedCapsule(
            pillText = pill,
            title = title.ifBlank { "Strava Workout" },
            content = text,
            iconName = "ic_capsule_run",
            timeoutSeconds = 0 // Ongoing until stopped
        )
    }

    private fun parseDiscord(combined: String, title: String, text: String): ParsedCapsule? {
        val hasMention = combined.contains("@") || combined.contains("mentioned", ignoreCase = true)
        val isDm = combined.contains("sent you a message", ignoreCase = true) || !combined.contains("#")

        val pill = when {
            hasMention -> "@Mention"
            isDm -> "Discord DM"
            else -> "Discord"
        }

        return ParsedCapsule(
            pillText = pill,
            title = title.ifBlank { "Discord" },
            content = text,
            iconName = "ic_capsule_chat",
            timeoutSeconds = 8
        )
    }

    private fun parseOtp(combined: String, title: String, text: String): ParsedCapsule? {
        val otpMatch = Regex("""(?i)\b(?:otp|code|verify|código|pin)[^\d\n\r]*(\d{4,8})\b""").find(combined)
            ?: Regex("""\b\d{4,8}\b""").find(combined)

        val code = otpMatch?.groupValues?.lastOrNull { it.isNotBlank() } ?: return null

        return ParsedCapsule(
            pillText = "OTP: $code",
            title = "Verification Code",
            content = text.ifBlank { "Tap to copy $code" },
            iconName = "ic_capsule_bolt",
            timeoutSeconds = 30,
            payload = InspectPayload(
                title = "Authentication Token",
                primaryText = code,
                secondaryText = title,
                actionType = "COPY",
                copyContent = code
            )
        )
    }

    private fun parseProgress(notif: Notification, title: String, text: String): ParsedCapsule? {
        val max = notif.extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val progress = notif.extras.getInt(Notification.EXTRA_PROGRESS, 0)
        if (max <= 0 || progress >= max) return null

        val pct = ((progress.toDouble() / max) * 100).toInt()
        return ParsedCapsule(
            pillText = "$pct%",
            title = title.ifBlank { "In Progress" },
            content = text.ifBlank { "$progress / $max completed" },
            iconName = "ic_capsule_bolt",
            timeoutSeconds = 4
        )
    }
}
