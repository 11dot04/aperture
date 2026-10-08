package com.microtag.rules

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
                val content = InputStreamReader(stream).readText()
                val jsonArr = JSONArray(content)
                for (i in 0 until jsonArr.length()) {
                    val rule = ApertureRule.fromJson(jsonArr.getJSONObject(i))
                    if (rule != null) {
                        rules.add(rule)
                    }
                }
            }
            Log.d(TAG, "Successfully loaded ${rules.size} rules from custom_rules.json")
        } catch (e: Exception) {
            Log.e(TAG, "Failed reading custom_rules.json: ${e.message}")
        }
    }

    fun getActiveRules(): List<ApertureRule> = rules

    fun evaluate(packageName: String, title: String, content: String): MatchResult? {
        val candidateRules = rules.filter { it.packageName.equals(packageName, ignoreCase = true) }
        val targetText = "$title $content".trim()

        for (rule in candidateRules) {
            val match = rule.regex.find(targetText)
            if (match != null) {
                var outputPill = rule.pillText
                match.groupValues.forEachIndexed { idx, value ->
                    outputPill = outputPill.replace("$$idx", value)
                }

                // If template didn't use $0/$1 group tokens, fallback to the first capture group or match
                if (outputPill == rule.pillText && match.groupValues.size > 1) {
                    outputPill = match.groupValues[1]
                }

                return MatchResult(
                    pillText = outputPill.trim(),
                    iconName = rule.iconName,
                    timeoutSeconds = rule.timeoutSeconds
                )
            }
        }
        return null
    }
}
