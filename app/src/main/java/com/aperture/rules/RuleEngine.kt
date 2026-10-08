package com.aperture.rules

import android.content.Context
import android.util.Log
import org.json.JSONArray
import java.io.InputStreamReader

object RuleEngine {
    private const val TAG = "ApertureRuleEngine"
    private val rules = mutableListOf<ApertureRule>()

    fun init(context: Context) {
        rules.clear()
        try {
            context.assets.open("custom_rules.json").use { stream ->
                val text = InputStreamReader(stream).readText()
                val array = JSONArray(text)
                for (i in 0 until array.length()) {
                    rules.add(ApertureRule.fromJson(array.getJSONObject(i)))
                }
            }
            Log.d(TAG, "Loaded ${rules.size} custom rules.")
        } catch (e: Exception) {
            Log.w(TAG, "No custom_rules.json found or failed parsing; proceeding with defaults: ${e.message}")
        }
    }

    fun evaluate(packageName: String, title: String, content: String): MatchResult? {
        val candidateRules = rules.filter { it.enabled && it.packageName == packageName }

        for (rule in candidateRules) {
            var matched = true
            var resolvedPill = rule.pillTemplate

            rule.titleRegex?.let { regex ->
                val match = regex.find(title)
                if (match != null) {
                    resolvedPill = resolveTemplates(resolvedPill, match)
                } else {
                    matched = false
                }
            }

            if (!matched) continue

            rule.contentRegex?.let { regex ->
                val match = regex.find(content)
                if (match != null) {
                    resolvedPill = resolveTemplates(resolvedPill, match)
                } else {
                    matched = false
                }
            }

            if (matched) {
                return MatchResult(
                    pillText = resolvedPill.trim(),
                    headline = title,
                    details = content,
                    iconName = rule.iconName,
                    timeoutSeconds = rule.timeoutSeconds,
                    isProgress = rule.isProgress
                )
            }
        }
        return null
    }

    private fun resolveTemplates(template: String, match: kotlin.text.MatchResult): String {
        var result = template
        match.groupValues.forEachIndexed { idx, value ->
            result = result.replace("$$idx", value)
        }
        return result
    }
}
