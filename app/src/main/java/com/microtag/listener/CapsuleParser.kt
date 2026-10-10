package com.microtag.listener

import android.app.Notification
import android.os.Build
import android.service.notification.StatusBarNotification
import com.microtag.inspect.InspectPayload
import java.util.Calendar

data class ParsedCapsule(
    val pillText: String,
    val title: String,
    val content: String,
    val iconName: String? = null,
    val iconRes: Int = 0,
    val timeoutSeconds: Int = 0,
    val chronometerTargetMillis: Long? = null,
    val payload: InspectPayload? = null,
    val actions: List<Notification.Action> = emptyList()
)

object CapsuleParser {

    private const val DISCORD_VOICE_CHANNEL_ID = "mediaConnections"

    private val discordVoiceTextPattern = Regex(
        """\bvoice\s+(?:connected|connection|channel)\b|\bin\s+call\b|\bon\s+call\b|\bconnecting\b|\bwaiting\s+for\s+voice\b""",
        RegexOption.IGNORE_CASE
    )
    private val discordConnectingPattern = Regex(
        """\b(?:connecting|reconnecting|authenticating|waiting|rtc|ice)\b""",
        RegexOption.IGNORE_CASE
    )
    private val discordMutedPattern = Regex("""\bmuted\b""", RegexOption.IGNORE_CASE)
    private val discordDeafenedPattern = Regex("""\bdeafened\b""", RegexOption.IGNORE_CASE)

    // Strava's "Stop" button pauses the recording and its notification then says "Stopped".
    private val stravaPausedPattern = Regex("""paused|stopped|暫停|暂停""", RegexOption.IGNORE_CASE)
    private val stravaDistancePattern = Regex("""\b\d+(?:[.,]\d+)?\s?(?:km|mi)\b""", RegexOption.IGNORE_CASE)
    private val stravaTimePattern = Regex("""\b(?:\d{1,2}:)?\d{1,2}:\d{2}\b""")

    private val protonCountryPattern = Regex("""(?i)connected\s*(?:to|:|-|–)?\s*(.+)""")

    fun parse(sbn: StatusBarNotification): ParsedCapsule? {
        val pkg = sbn.packageName ?: return null
        val notif = sbn.notification ?: return null
        val extras = notif.extras ?: return null

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""
        val combined = "$title $text $subText".trim()

        return when {
            extras.containsKey(Notification.EXTRA_MEDIA_SESSION) -> {
                parseMedia(notif, title, text, subText)
            }
            pkg == "com.kieronquinn.app.ambientmusicmod" || pkg.contains("intelligence.sense") -> {
                parseAmbientMusic(notif, title, text)
            }
            pkg == "com.google.android.calendar" || pkg.contains("calendar") -> {
                parseCalendar(notif, title, text)
            }
            pkg == "com.android.bluetooth" || pkg == "com.android.systemui" -> {
                parseSystemUi(notif, title, text, combined)
            }
            pkg == "ch.protonvpn.android" -> {
                parseProtonVpn(notif, title, text)
            }
            pkg == "com.strava" || pkg.contains("workout") || pkg.contains("fitness") -> {
                parseStrava(notif, combined, title, text)
            }
            pkg == "com.discord" || pkg.contains("discord") -> {
                parseDiscord(notif, combined, title, text)
            }
            pkg.contains("weather") || pkg == "com.google.android.googlequicksearchbox" -> {
                parseWeather(combined, title, text)
            }
            pkg.contains("messaging") || pkg.contains("sms") || pkg.contains("google.android.apps.messaging") -> {
                parseOtp(combined, title, text)
            }
            extras.containsKey(Notification.EXTRA_PROGRESS) -> {
                parseProgress(notif, title, text)
            }
            else -> null
        }
    }

    private fun cleanSongTitle(rawTitle: String, artist: String): String {
        var cleaned = rawTitle.replace(Regex("""\s*[\(\[].*?[\)\]]"""), "").trim()
        
        if (cleaned.contains("-") && artist.isNotBlank()) {
            val parts = cleaned.split("-").map { it.trim() }
            val artistLower = artist.lowercase()
            
            val songPart = parts.firstOrNull { part ->
                !part.lowercase().contains(artistLower) && !artistLower.contains(part.lowercase())
            }
            if (songPart != null) {
                cleaned = songPart
            }
        }
        return cleaned.ifBlank { rawTitle }.take(14)
    }

    private fun parseMedia(notif: Notification, title: String, text: String, subText: String): ParsedCapsule? {
        if (title.isBlank()) return null
        val artist = text.ifBlank { subText } 
        val songName = cleanSongTitle(title, artist)

        return ParsedCapsule(
            pillText = songName,
            title = title,
            content = text,
            iconName = "ic_capsule_music",
            timeoutSeconds = 6,
            actions = notif.actions?.toList() ?: emptyList()
        )
    }

    private fun parseAmbientMusic(notif: Notification, title: String, text: String): ParsedCapsule? {
        // Ignore the app's permanent foreground-service notification; it isn't a song.
        if (title.contains("Ambient Music Mod", ignoreCase = true) ||
            text.contains("running in the background", ignoreCase = true)
        ) return null

        val rawSong = title.replace("Now Playing", "", ignoreCase = true).trim().ifBlank { 
            text.split("-").firstOrNull()?.trim() ?: "Music" 
        }
        val songName = cleanSongTitle(rawSong, "")

        return ParsedCapsule(
            pillText = songName,
            title = "Now Playing",
            content = rawSong,
            iconName = "ic_capsule_music",
            timeoutSeconds = 8,
            actions = notif.actions?.toList() ?: emptyList()
        )
    }

    private fun parseCalendar(notif: Notification, title: String, text: String): ParsedCapsule? {
        val timeMatch = Regex("""\b(\d{1,2}):(\d{2})\s?(AM|PM|am|pm)?\b""").find(text)
        var targetMillis: Long? = null
        
        if (timeMatch != null) {
            val hours = timeMatch.groupValues[1].toInt()
            val minutes = timeMatch.groupValues[2].toInt()
            val ampm = timeMatch.groupValues.getOrNull(3)?.uppercase()
            
            val calendar = Calendar.getInstance()
            val now = calendar.timeInMillis
            
            var targetHourOfDay = hours
            if (ampm == "PM" && hours < 12) targetHourOfDay += 12
            if (ampm == "AM" && hours == 12) targetHourOfDay = 0
            
            calendar.set(Calendar.HOUR_OF_DAY, targetHourOfDay)
            calendar.set(Calendar.MINUTE, minutes)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            
            targetMillis = calendar.timeInMillis
            if (targetMillis < now) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
                targetMillis = calendar.timeInMillis
            }
        }

        return ParsedCapsule(
            pillText = if (targetMillis != null) "in {MINS}m" else "Event",
            title = title,
            content = text,
            iconName = "ic_capsule_calendar",
            timeoutSeconds = 0,
            chronometerTargetMillis = targetMillis,
            actions = notif.actions?.toList() ?: emptyList()
        )
    }

    private fun parseSystemUi(notif: Notification, title: String, text: String, combined: String): ParsedCapsule? {
        return when {
            combined.contains("battery", ignoreCase = true) || combined.contains("charging", ignoreCase = true) -> {
                val pct = Regex("""\d+%""").find(combined)?.value ?: "Power"
                ParsedCapsule(pct, title, text, "ic_capsule_bolt", timeoutSeconds = 5)
            }
            combined.contains("bluetooth", ignoreCase = true) || combined.contains("connected to", ignoreCase = true) -> {
                val device = title.replace("Connected to ", "", ignoreCase = true).take(10)
                ParsedCapsule(device, "Bluetooth", text, "ic_capsule_bluetooth", timeoutSeconds = 5, actions = notif.actions?.toList() ?: emptyList())
            }
            combined.contains("do not disturb", ignoreCase = true) || combined.contains("muted", ignoreCase = true) -> {
                ParsedCapsule("DND", title, text, "ic_capsule_bell_off", timeoutSeconds = 4)
            }
            else -> null
        }
    }

    private fun parseWeather(combined: String, title: String, text: String): ParsedCapsule? {
        val tempMatch = Regex("""-?\d{1,3}°[CFcf]?""").find(combined) ?: return null
        val cleanTemp = tempMatch.value.replace(Regex("""[CFcf]"""), "")

        return ParsedCapsule(
            pillText = cleanTemp,
            title = title.ifBlank { "Weather" },
            content = text,
            iconName = "ic_capsule_cloud",
            timeoutSeconds = 0
        )
    }

    // ---------------------------------------------------------------------
    // Strava
    //   recording        -> "Run" / "Ride" / ... / "Active"
    //   paused / auto    -> primary metric (distance, else elapsed time)
    // ---------------------------------------------------------------------
    private fun parseStrava(notif: Notification, combined: String, title: String, text: String): ParsedCapsule? {
        val actions = notif.actions?.toList() ?: emptyList()

        if (stravaPausedPattern.containsMatchIn(combined)) {
            val metric = stravaDistancePattern.find(combined)?.value?.replace(" ", "")
                ?: stravaTimePattern.find(combined)?.value
                ?: "Paused"

            return ParsedCapsule(
                pillText = metric,
                title = title.ifBlank { "Workout Paused" },
                content = text,
                iconName = "ic_capsule_run",
                timeoutSeconds = 0,
                actions = actions
            )
        }

        return ParsedCapsule(
            pillText = stravaActivityLabel(combined),
            title = title.ifBlank { "Strava" },
            content = text,
            iconName = "ic_capsule_run",
            timeoutSeconds = 0,
            actions = actions
        )
    }

    private fun stravaActivityLabel(combined: String): String {
        val lower = combined.lowercase()
        return when {
            Regex("""\b(run|running)\b""").containsMatchIn(lower) -> "Run"
            Regex("""\b(ride|riding|cycling|bike|biking)\b""").containsMatchIn(lower) -> "Ride"
            Regex("""\bwalk(ing)?\b""").containsMatchIn(lower) -> "Walk"
            Regex("""\bhik(e|ing)\b""").containsMatchIn(lower) -> "Hike"
            Regex("""\bswim(ming)?\b""").containsMatchIn(lower) -> "Swim"
            else -> "Active"
        }
    }

    // ---------------------------------------------------------------------
    // Discord
    //   voice session -> "On VC" / "On call" / "Muted" / "Deafened" / "Connecting"
    //   anything else -> mention / DM as before
    // ---------------------------------------------------------------------
    private fun parseDiscord(notif: Notification, combined: String, title: String, text: String): ParsedCapsule? {
        val actions = notif.actions?.toList() ?: emptyList()
        val actionTitles = actions.mapNotNull { it.title?.toString()?.lowercase() }

        val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notif.channelId?.trim().orEmpty()
        } else {
            ""
        }
        val isVoice = channelId.equals(DISCORD_VOICE_CHANNEL_ID, ignoreCase = true) ||
                discordVoiceTextPattern.containsMatchIn(combined)

        if (isVoice) {
            // Discord titles look like "Voice Connected – Tap to return to call".
            // Only the part before the dash is the state; the rest is boilerplate
            // (it contains "call", which is why everything used to read "On call").
            val status = title.substringBefore(" – ").substringBefore(" - ").trim()
            val isConnecting = discordConnectingPattern.containsMatchIn(status)

            // The action buttons show the *next* state, so "Unmute" means currently muted.
            val isDeafened = actionTitles.any { it.contains("undeafen") } ||
                    discordDeafenedPattern.containsMatchIn(status)
            val isMuted = actionTitles.any { it.contains("unmute") } ||
                    discordMutedPattern.containsMatchIn(status)

            val pill = when {
                isConnecting -> "Connecting"
                isDeafened -> "Deafened"
                isMuted -> "Muted"
                else -> "On VC"
            }

            return ParsedCapsule(
                pillText = pill,
                title = title.ifBlank { "Discord" },
                content = text,
                iconName = "ic_capsule_chat",
                timeoutSeconds = 0,
                actions = actions
            )
        }

        val hasMention = combined.contains("@") || combined.contains("mentioned", ignoreCase = true)
        val pill = if (hasMention) {
            val sender = title.split(" ").firstOrNull()?.take(8) ?: "Msg"
            "@$sender"
        } else {
            "DM"
        }

        return ParsedCapsule(
            pillText = pill,
            title = title.ifBlank { "Discord" },
            content = text,
            iconName = "ic_capsule_chat",
            timeoutSeconds = 8,
            actions = actions
        )
    }

    // ---------------------------------------------------------------------
    // ProtonVPN: "Connected to Japan" -> "Japan" with a VPN icon
    // ---------------------------------------------------------------------
    private fun parseProtonVpn(notif: Notification, title: String, text: String): ParsedCapsule? {
        val source = when {
            title.contains("Connected", ignoreCase = true) -> title
            text.contains("Connected", ignoreCase = true) -> text
            else -> return null
        }

        val rawPlace = protonCountryPattern.find(source)?.groupValues?.get(1).orEmpty()
        // Drop server suffixes like "Japan - JP#12", "Japan • JP#12", "Japan (P2P)"
        val place = rawPlace
            .split(" - ", " – ", " • ", " · ", "#", "(")
            .firstOrNull()
            ?.trim()
            ?.trimEnd('.', ',', ':')
            .orEmpty()
            .let { CountryCodes.shorten(it) }
            .ifBlank { "VPN" }

        return ParsedCapsule(
            pillText = place,
            title = title,
            content = text,
            iconName = "ic_capsule_vpn",
            timeoutSeconds = 0, 
            actions = notif.actions?.toList() ?: emptyList()
        )
    }

    private fun parseOtp(combined: String, title: String, text: String): ParsedCapsule? {
        val otpMatch = Regex("""(?i)(?:otp|code|pin|passcode|verification)[^\d]*\b(\d{4,8})\b""").find(combined)
            ?: Regex("""(?i)\b(?:is|:)\s*(\d{4,8})\b""").find(combined)
            ?: Regex("""\b(?!(?:19|20)\d{2}\b)(\d{4,8})\b""").find(combined) 

        val code = otpMatch?.groupValues?.lastOrNull { it.isNotBlank() } ?: return null

        return ParsedCapsule(
            pillText = code,
            title = "Verification Code",
            content = text.ifBlank { "Tap to copy: $code" },
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
