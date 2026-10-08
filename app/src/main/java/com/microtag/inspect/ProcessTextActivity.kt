package com.microtag.inspect

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Icon
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.aperture.core.ApertureReminder
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class ProcessTextActivity : Activity() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val INSPECT_NOTIFICATION_ID = 9003

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rawText = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.trim()
        if (rawText.isNullOrBlank()) {
            finish()
            return
        }

        // 1. Reading Speed (>= 25 words)
        val wordList = rawText.split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (wordList.size >= 25) {
            val minutes = wordList.size / 230.0
            val totalSeconds = (minutes * 60).toInt()
            val m = totalSeconds / 60
            val s = totalSeconds % 60
            val pill = if (m == 0) "${s}s read" else "~${m}m read"

            ApertureReminder.showCapsule(
                context = applicationContext,
                pillText = pill,
                title = "Reading Estimate",
                content = "${wordList.size} words • ${rawText.length} characters",
                notificationId = INSPECT_NOTIFICATION_ID,
                timeoutSeconds = 12,
                detailPayload = ApertureReminder.InspectPayload(
                    domain = "LXCN",
                    title = "Reading Estimate",
                    subtitle = "${wordList.size} words",
                    fullContent = "Reading pace: 230 words per minute.\n\nSample:\n${rawText.take(300)}...",
                    copyText = rawText
                )
            )
            finish()
            return
        }

        // 2. Color Swatch (#HEX)
        val hexMatch = Regex("(?i)^#?([0-9a-f]{6}|[0-9a-f]{3})$").find(rawText)
        if (hexMatch != null) {
            val hex = hexMatch.groupValues[1]
            val fullHex = if (hex.length == 3) "${hex[0]}${hex[0]}${hex[1]}${hex[1]}${hex[2]}${hex[2]}" else hex
            try {
                val parsedColor = Color.parseColor("#$fullHex")
                val r = Color.red(parsedColor)
                val g = Color.green(parsedColor)
                val b = Color.blue(parsedColor)

                val bmp = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
                Canvas(bmp).drawCircle(24f, 24f, 24f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = parsedColor })

                ApertureReminder.showCapsule(
                    context = applicationContext,
                    pillText = "#$fullHex",
                    title = "Color Swatch",
                    content = "RGB ($r, $g, $b)",
                    notificationId = INSPECT_NOTIFICATION_ID,
                    customIcon = Icon.createWithBitmap(bmp),
                    timeoutSeconds = 15,
                    detailPayload = ApertureReminder.InspectPayload(
                        domain = "SWTCH",
                        title = "#${fullHex.uppercase()}",
                        subtitle = "RGB ($r, $g, $b)",
                        fullContent = "Hex: #${fullHex.uppercase()}\nRed: $r  Green: $g  Blue: $b",
                        copyText = "#${fullHex.uppercase()}"
                    )
                )
            } catch (_: Exception) {}
            finish()
            return
        }

        // 3. Time Zone Teleport
        val tzMatch = Regex("""(?i)^\s*(\d{1,2})(?::(\d{2}))?\s*(am|pm)?\s*(est|edt|pst|pdt|cst|cdt|gmt|utc|ist|jst|bst)\s*$""").find(rawText)
        if (tzMatch != null) {
            var hour = tzMatch.groupValues[1].toInt()
            val min = tzMatch.groupValues[2].toIntOrNull() ?: 0
            val ampm = tzMatch.groupValues[3].lowercase()
            val tzStr = tzMatch.groupValues[4].uppercase()

            if (ampm == "pm" && hour < 12) hour += 12
            if (ampm == "am" && hour == 12) hour = 0

            val zoneMap = mapOf(
                "EST" to "America/New_York", "EDT" to "America/New_York",
                "PST" to "America/Los_Angeles", "PDT" to "America/Los_Angeles",
                "CST" to "America/Chicago", "CDT" to "America/Chicago",
                "GMT" to "UTC", "UTC" to "UTC", "BST" to "Europe/London",
                "IST" to "Asia/Kolkata", "JST" to "Asia/Tokyo"
            )
            val cal = Calendar.getInstance(TimeZone.getTimeZone(zoneMap[tzStr] ?: "UTC")).apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, min)
            }
            val localTime = SimpleDateFormat("h:mm a z", Locale.getDefault()).apply {
                timeZone = TimeZone.getDefault()
            }.format(cal.time)

            ApertureReminder.showCapsule(
                context = applicationContext,
                pillText = SimpleDateFormat("h:mm a", Locale.getDefault()).format(cal.time),
                title = "Time Conversion",
                content = "$rawText = $localTime",
                notificationId = INSPECT_NOTIFICATION_ID,
                timeoutSeconds = 12,
                detailPayload = ApertureReminder.InspectPayload(
                    domain = "TIME",
                    title = localTime,
                    subtitle = "From $rawText ($tzStr)",
                    fullContent = "Converted to local: $localTime\nSource: $rawText",
                    copyText = localTime
                )
            )
            finish()
            return
        }

        // 4. Inline Arithmetic
        val mathMatch = Regex("""^\s*(-?\d+(?:\.\d+)?)\s*([\+\-\*\/xX×÷])\s*(-?\d+(?:\.\d+)?)\s*$""").find(rawText)
        if (mathMatch != null) {
            val a = mathMatch.groupValues[1].toDoubleOrNull()
            val op = mathMatch.groupValues[2]
            val b = mathMatch.groupValues[3].toDoubleOrNull()

            if (a != null && b != null) {
                val res = when (op) {
                    "+", "plus" -> a + b
                    "-", "minus" -> a - b
                    "*", "x", "X", "×" -> a * b
                    "/", "÷" -> if (b != 0.0) a / b else null
                    else -> null
                }
                if (res != null) {
                    val formatted = if (res % 1.0 == 0.0) res.toLong().toString() else String.format(Locale.ROOT, "%.3f", res).trimEnd('0').trimEnd('.')
                    ApertureReminder.showCapsule(
                        context = applicationContext,
                        pillText = formatted,
                        title = "Calculation",
                        content = "$rawText = $formatted",
                        notificationId = INSPECT_NOTIFICATION_ID,
                        timeoutSeconds = 15,
                        detailPayload = ApertureReminder.InspectPayload(
                            domain = "CALC",
                            title = formatted,
                            subtitle = rawText,
                            fullContent = "$rawText = $formatted",
                            copyText = formatted
                        )
                    )
                    finish()
                    return
                }
            }
        }

        // 5. Deterministic Unit Conversions
        val unitPattern = Regex("""(?i)^\s*(\d+(?:\.\d+)?)\s*(lbs?|pounds?|kg|kilograms?|mi|miles?|km|kilometers?|f|fahrenheit|c|celsius|psi|bar)\s*$""")
        val unitMatch = unitPattern.find(rawText)
        if (unitMatch != null) {
            val value = unitMatch.groupValues[1].toDoubleOrNull()
            val unit = unitMatch.groupValues[2].lowercase(Locale.ROOT)
            if (value != null) {
                var convertedVal = 0.0
                var targetUnit = ""

                when {
                    unit.startsWith("lb") || unit.startsWith("pound") -> {
                        convertedVal = value * 0.45359237
                        targetUnit = "kg"
                    }
                    unit == "kg" || unit.startsWith("kilogram") -> {
                        convertedVal = value / 0.45359237
                        targetUnit = "lbs"
                    }
                    unit == "mi" || unit.startsWith("mile") -> {
                        convertedVal = value * 1.609344
                        targetUnit = "km"
                    }
                    unit == "km" || unit.startsWith("kilometer") -> {
                        convertedVal = value / 1.609344
                        targetUnit = "mi"
                    }
                    unit == "f" || unit.startsWith("fahrenheit") -> {
                        convertedVal = (value - 32.0) * (5.0 / 9.0)
                        targetUnit = "°C"
                    }
                    unit == "c" || unit.startsWith("celsius") -> {
                        convertedVal = (value * (9.0 / 5.0)) + 32.0
                        targetUnit = "°F"
                    }
                    unit == "psi" -> {
                        convertedVal = value * 0.0689476
                        targetUnit = "bar"
                    }
                    unit == "bar" -> {
                        convertedVal = value / 0.0689476
                        targetUnit = "psi"
                    }
                }

                val formatted = if (convertedVal % 1.0 == 0.0) {
                    convertedVal.toLong().toString()
                } else {
                    String.format(Locale.ROOT, "%.2f", convertedVal)
                }

                val resultPill = "$formatted $targetUnit"
                ApertureReminder.showCapsule(
                    context = applicationContext,
                    pillText = resultPill,
                    title = "Unit Conversion",
                    content = "$rawText = $resultPill",
                    notificationId = INSPECT_NOTIFICATION_ID,
                    timeoutSeconds = 15,
                    detailPayload = ApertureReminder.InspectPayload(
                        domain = "CONV",
                        title = resultPill,
                        subtitle = rawText,
                        fullContent = "$rawText is equal to $resultPill",
                        copyText = resultPill
                    )
                )
                finish()
                return
            }
        }

        // 6. Currency Lookup
        val currMatch = Regex("(?i)^([$€£¥₹])?\\s*(\\d+(?:\\.\\d+)?)\\s*([a-z]{3})?$").find(rawText)
        if (currMatch != null) {
            val sym = currMatch.groupValues[1]
            val amt = currMatch.groupValues[2].toDoubleOrNull()
            val code = currMatch.groupValues[3].uppercase()
            if (amt != null && (sym.isNotBlank() || code.isNotBlank())) {
                val base = when {
                    sym == "$" || code == "USD" -> "USD"
                    sym == "€" || code == "EUR" -> "EUR"
                    sym == "£" || code == "GBP" -> "GBP"
                    sym == "₹" || code == "INR" -> "INR"
                    code.isNotBlank() -> code
                    else -> "USD"
                }
                thread {
                    fetchCurrency(amt, base)
                }
                return
            }
        }

        // 7. Network / Dictionary or URL Redirect Follow (Generic Fallback)
        thread {
            if (rawText.startsWith("http://") || rawText.startsWith("https://") || (rawText.contains(".") && !rawText.contains(" "))) {
                resolveUrl(rawText)
            } else {
                resolveDictionary(rawText)
            }
        }
    }

    private fun resolveDictionary(rawText: String) {
        val word = rawText.replace(Regex("[^a-zA-Z0-9\\s-]"), "").trim().split("\\s+".toRegex()).firstOrNull() ?: ""
        if (word.isBlank()) { finish(); return }

        var resolvedTitle = word.replaceFirstChar { it.uppercase() }
        var resolvedSubtitle = ""
        var resolvedBody = ""

        try {
            val encoded = URLEncoder.encode(word.lowercase(), "UTF-8")
            val conn = (URL("https://api.dictionaryapi.dev/api/v2/entries/en/$encoded").openConnection() as HttpURLConnection).apply {
                connectTimeout = 2500
                readTimeout = 2500
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
            }

            if (conn.responseCode == 200) {
                val array = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
                val entry = array.getJSONObject(0)
                resolvedSubtitle = entry.optString("phonetic", "")
                val meanings = entry.optJSONArray("meanings")
                val sb = StringBuilder()

                if (meanings != null) {
                    for (i in 0 until meanings.length()) {
                        val m = meanings.getJSONObject(i)
                        sb.append(m.optString("partOfSpeech", "")).append("\n")
                        val defs = m.optJSONArray("definitions")
                        if (defs != null) {
                            for (d in 0 until minOf(defs.length(), 2)) {
                                sb.append("  ${d + 1}. ").append(defs.getJSONObject(d).optString("definition", "")).append("\n")
                            }
                        }
                        sb.append("\n")
                    }
                }
                resolvedBody = sb.toString().trim()
            }
        } catch (_: Exception) {}

        // Datamuse Fallback
        if (resolvedBody.isBlank()) {
            try {
                val encoded = URLEncoder.encode(word.lowercase(), "UTF-8")
                val conn = (URL("https://api.datamuse.com/words?sp=$encoded&md=d&max=1").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 2500
                    readTimeout = 2500
                }
                if (conn.responseCode == 200) {
                    val array = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
                    if (array.length() > 0) {
                        val defs = array.getJSONObject(0).optJSONArray("defs")
                        if (defs != null) {
                            val sb = StringBuilder()
                            for (i in 0 until minOf(defs.length(), 3)) {
                                val line = defs.getString(i).split("\t", limit = 2)
                                val pos = if (line.size > 1) line[0] else ""
                                val defText = if (line.size > 1) line[1] else line[0]
                                sb.append("$pos\n  1. $defText\n\n")
                            }
                            resolvedBody = sb.toString().trim()
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        mainHandler.post {
            if (resolvedBody.isNotBlank()) {
                ApertureReminder.showCapsule(
                    context = applicationContext,
                    pillText = word,
                    title = resolvedTitle,
                    content = resolvedSubtitle.ifBlank { "Definition" },
                    notificationId = INSPECT_NOTIFICATION_ID,
                    timeoutSeconds = 25,
                    detailPayload = ApertureReminder.InspectPayload(
                        domain = "LXCN",
                        title = resolvedTitle,
                        subtitle = resolvedSubtitle,
                        fullContent = resolvedBody,
                        copyText = "$resolvedTitle $resolvedSubtitle\n\n$resolvedBody"
                    )
                )
            } else {
                Toast.makeText(applicationContext, "No definition found", Toast.LENGTH_SHORT).show()
            }
            finish()
        }
    }

    private fun resolveUrl(rawUrl: String) {
        val target = if (!rawUrl.startsWith("http")) "https://$rawUrl" else rawUrl
        try {
            var current = URL(target)
            var conn = current.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 3000
            conn.readTimeout = 3000

            var hops = 0
            while (hops < 5) {
                conn.connect()
                val code = conn.responseCode
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location") ?: break
                    current = URL(current, loc)
                    conn.disconnect()
                    conn = current.openConnection() as HttpURLConnection
                    conn.instanceFollowRedirects = false
                    hops++
                } else break
            }
            val destination = current.toString()
            val host = current.host.removePrefix("www.")
            conn.disconnect()

            mainHandler.post {
                ApertureReminder.showCapsule(
                    context = applicationContext,
                    pillText = host,
                    title = host,
                    content = destination,
                    notificationId = INSPECT_NOTIFICATION_ID,
                    timeoutSeconds = 15,
                    detailPayload = ApertureReminder.InspectPayload(
                        domain = "URL",
                        title = host,
                        subtitle = "Redirect Resolved",
                        fullContent = "Resolved Destination:\n$destination\n\nSource:\n$rawUrl",
                        copyText = destination
                    )
                )
                finish()
            }
        } catch (_: Exception) {
            mainHandler.post { finish() }
        }
    }

    private fun fetchCurrency(amt: Double, base: String) {
        try {
            val conn = (URL("https://open.er-api.com/v6/latest/$base").openConnection() as HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 3000
            }
            if (conn.responseCode == 200) {
                val rates = JSONObject(conn.inputStream.bufferedReader().use { it.readText() }).getJSONObject("rates")
                val isUsdBase = base == "USD"
                val targetCode = if (isUsdBase) "EUR" else "USD"
                val rate = rates.optDouble(targetCode, 1.0)
                val converted = amt * rate
                val formatted = String.format(Locale.ROOT, "%.2f %s", converted, targetCode)

                mainHandler.post {
                    ApertureReminder.showCapsule(
                        context = applicationContext,
                        pillText = formatted,
                        title = "$amt $base Conversion",
                        content = "$amt $base = $formatted",
                        notificationId = INSPECT_NOTIFICATION_ID,
                        timeoutSeconds = 15,
                        detailPayload = ApertureReminder.InspectPayload(
                            domain = "CALC",
                            title = formatted,
                            subtitle = "$amt $base",
                            fullContent = "$amt $base = $formatted",
                            copyText = formatted
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        mainHandler.post { finish() }
    }
}
