/*
 * Copyright (C) 2026 Aperture Project
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aperture.rules

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class RuleEngine(private val context: Context) {

    private val rules = mutableListOf<ApertureRule>()

    init {
        loadRules()
    }

    fun loadRules() {
        rules.clear()
        val jsonString = readRulesJson() ?: return
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                rules.add(
                    ApertureRule(
                        id = obj.getString("id"),
                        packageName = obj.getString("packageName"),
                        enabled = obj.optBoolean("enabled", true),
                        titlePattern = obj.optString("titlePattern").takeIf { it.isNotBlank() },
                        contentPattern = obj.optString("contentPattern").takeIf { it.isNotBlank() },
                        pillFormat = obj.getString("pillFormat"),
                        iconName = obj.optString("iconName", "ic_capsule_search"),
                        timeoutSeconds = obj.optInt("timeoutSeconds", 12),
                        isProgress = obj.optBoolean("isProgress", false),
                        priority = obj.optInt("priority", 0)
                    )
                )
            }
            rules.sortByDescending { it.priority }
        } catch (_: Exception) {}
    }

    private fun readRulesJson(): String? {
        val customFile = File(context.filesDir, "custom_rules.json")
        if (customFile.exists()) {
            return customFile.readText()
        }
        return try {
            context.assets.open("default_rules.json").bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        }
    }

    fun evaluate(packageName: String, title: String, content: String): RuleMatchResult? {
        val targetRules = rules.filter { it.enabled && it.packageName == packageName }

        for (rule in targetRules) {
            var titleMatched = true
            var contentMatched = true
            var resolvedPill = rule.pillFormat

            if (rule.titlePattern != null) {
                val match = Regex(rule.titlePattern).find(title)
                if (match != null) {
                    resolvedPill = formatTemplate(resolvedPill, match.groupValues)
                } else {
                    titleMatched = false
                }
            }

            if (rule.contentPattern != null) {
                val match = Regex(rule.contentPattern).find(content)
                if (match != null) {
                    resolvedPill = formatTemplate(resolvedPill, match.groupValues)
                } else {
                    contentMatched = false
                }
            }

            if (titleMatched && contentMatched) {
                return RuleMatchResult(
                    matched = true,
                    pillText = resolvedPill,
                    headline = title,
                    details = content,
                    iconName = rule.iconName,
                    isProgress = rule.isProgress,
                    timeoutSeconds = rule.timeoutSeconds
                )
            }
        }
        return null
    }

    private fun formatTemplate(template: String, groups: List<String>): String {
        var out = template
        for (i in groups.indices) {
            out = out.replace("{$i}", groups[i])
        }
        return out
    }
}
