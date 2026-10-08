package com.microtag.inspect

import java.io.Serializable

data class InspectPayload(
    val title: String = "",
    val primaryText: String = "",
    val secondaryText: String = "",
    val actionType: String = "COPY",
    val copyContent: String = "",
    val extraData: String = ""
) : Serializable
