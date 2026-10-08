package com.microtag.rules

import org.json.JSONObject

data class ApertureRule(
    val packageName: String,
    val regex: Regex,
    val pillText: String,
    val iconName: String,
    val timeoutSeconds: Int
) {
    companion object {
        fun fromJson(json: JSONObject): ApertureRule? {
            val pkg = json.optString("package_name").takeIf { it.isNotBlank() } ?: return null
            val pattern = json.optString("regex").takeIf { it.isNotBlank() } ?: return null
            val pill = json.optString("pill_text", "")
            val icon = json.optString("icon_name", "ic_capsule_search")
            val timeout = json.optInt("timeout_seconds", 15)

            return try {
                ApertureRule(
                    packageName = pkg,
                    regex = Regex(pattern),
                    pillText = pill,
                    iconName = icon,
                    timeoutSeconds = timeout
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}

data class MatchResult(
    val pillText: String,
    val iconName: String,
    val timeoutSeconds: Int
)
