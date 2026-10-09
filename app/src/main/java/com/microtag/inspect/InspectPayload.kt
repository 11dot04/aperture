package com.microtag.inspect

import java.io.Serializable

data class InspectPayload(
    val domain: String = "",
    val title: String = "",
    val subtitle: String = "",
    val fullContent: String = "",
    val copyText: String = "",
    // Fallback/compat properties
    val primaryText: String = "",
    val secondaryText: String = "",
    val actionType: String = "COPY",
    val copyContent: String = "",
    val extraData: String = ""
) : Serializable
