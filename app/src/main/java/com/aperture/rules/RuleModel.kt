package com.aperture.rules

import org.json.JSONObject

data class ApertureRule(
    val id: String,
    val packageName: String,
    val enabled: Boolean = true,
    val titleRegex: Regex? = null,
    val contentRegex: Regex? = null,
    val pillTemplate: String,
    val iconName: String = "ic_capsule_search",
    val timeoutSeconds: Int = 12,
    val isProgress: Boolean = false
) {
    companion object {
        fun fromJson(json: JSONObject): ApertureRule {
            val titlePattern = json.optString("titlePattern", "").takeIf { it.isNotBlank() }
            val contentPattern = json.optString("contentPattern", "").takeIf { it.isNotBlank() }

            return ApertureRule(
                id = json.getString("id"),
                packageName = json.getString("packageName"),
                enabled = json.optBoolean("enabled", true),
                titleRegex = titlePattern?.let { Regex(it, RegexOption.IGNORE_CASE) },
                contentRegex = contentPattern?.let { Regex(it, RegexOption.IGNORE_CASE) },
                pillTemplate = json.getString("pillTemplate"),
                iconName = json.optString("iconName", "ic_capsule_search"),
                timeoutSeconds = json.optInt("timeoutSeconds", 12),
                isProgress = json.optBoolean("isProgress", false)
            )
        }
    }
}

data class MatchResult(
    val pillText: String,
    val headline: String,
    val details: String,
    val iconName: String,
    val timeoutSeconds: Int,
    val isProgress: Boolean
)
