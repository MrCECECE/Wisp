package com.wisp.model
import java.util.Date

data class ConfigItem(
    var name: String,
    var url: String,
    var lastUpdate: Date? = null,
    var rawContent: String? = null
)