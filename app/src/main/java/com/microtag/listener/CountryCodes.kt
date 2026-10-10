package com.microtag.listener

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

/**
 * Shortens VPN server/location names for the status-bar pill.
 *
 *  - Overrides come from assets/vpn_country_codes.json (aliases, "UK" instead of "GB", Tor, P2P...)
 *    and always win.
 *  - Every other ISO country is resolved from the platform's own English country names.
 *  - Anything unknown is passed through, trimmed, so non-country servers still show something.
 */
object CountryCodes {
    private const val TAG = "CountryCodes"
    private const val ASSET_NAME = "vpn_country_codes.json"
    private const val MAX_FALLBACK_LENGTH = 10

    private val separators = Regex("""\s*(?:→|➝|->|>>|»|↔|\bvia\b)\s*""", RegexOption.IGNORE_CASE)
    private val diacritics = Regex("""\p{M}+""")
    private val nonAlphanumeric = Regex("""[^a-z0-9]+""")

    @Volatile
    private var overrides: Map<String, String> = emptyMap()

    private val isoNames: Map<String, String> by lazy {
        val map = HashMap<String, String>()
        for (code in Locale.getISOCountries()) {
            val name = Locale.Builder().setRegion(code).build().getDisplayCountry(Locale.ENGLISH)
            if (name.isNotBlank()) map[normalize(name)] = code
        }
        map
    }

    fun init(context: Context) {
        try {
            val raw = context.applicationContext.assets.open(ASSET_NAME)
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            val json = JSONObject(raw)
            val loaded = HashMap<String, String>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = json.optString(key).trim()
                if (value.isNotEmpty()) loaded[normalize(key)] = value
            }
            overrides = loaded
        } catch (e: Exception) {
            Log.w(TAG, "No override file loaded ($ASSET_NAME): ${e.message}")
        }
    }

    /** "Singapore" -> "SG", "Switzerland → Iceland" -> "CH→IS", unknown names pass through. */
    fun shorten(raw: String): String {
        val text = raw.trim()
        if (text.isEmpty()) return text

        // Secure Core style routes: entry and exit country
        val parts = text.split(separators).map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size in 2..3) {
            return parts.joinToString("→") { lookup(it) }
        }
        return lookup(text)
    }

    private fun lookup(name: String): String {
        // Already a short code such as "JP" or "USA"
        if (name.length in 2..3 && name.all { it.isUpperCase() }) return name

        val key = normalize(name)
        overrides[key]?.let { return it }
        isoNames[key]?.let { return it }
        return name.take(MAX_FALLBACK_LENGTH).trim()
    }

    private fun normalize(value: String): String {
        val stripped = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(diacritics, "")
            .replace("&", " and ")
        return stripped.replace(nonAlphanumeric, " ").trim()
    }
}
