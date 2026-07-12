package ru.course.apitesting.integration.core

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

data class IntegrationResult(
    val name: String,
    val type: String,
    val status: Int? = null,
    val headers: Map<String, String> = emptyMap(),
    val response: JsonElement = JsonObject(emptyMap()),
    val vars: Map<String, JsonElement> = emptyMap(),
    val savedVars: Map<String, JsonElement> = emptyMap(),
    val attempts: Int = 1,
    val durationMs: Long = 0,
    val error: String? = null
)