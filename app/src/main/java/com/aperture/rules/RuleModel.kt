/*
 * Copyright (C) 2026 Aperture Project
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aperture.rules

data class ApertureRule(
    val id: String,
    val packageName: String,
    val enabled: Boolean = true,
    val titlePattern: String? = null,
    val contentPattern: String? = null,
    val pillFormat: String,
    val iconName: String? = "ic_capsule_search",
    val timeoutSeconds: Int = 12,
    val isProgress: Boolean = false,
    val priority: Int = 0
)

data class RuleMatchResult(
    val matched: Boolean,
    val pillText: String,
    val headline: String,
    val details: String,
    val iconName: String?,
    val isProgress: Boolean,
    val timeoutSeconds: Int
)
