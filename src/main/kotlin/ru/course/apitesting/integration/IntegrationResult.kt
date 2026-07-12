package ru.course.apitesting.integration

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

data class IntegrationResult(
    val name: String,
    val type: String,
    val status: Int? = null,
    val headers: Map<String, String> = emptyMap(),
    val response: JsonElement = JsonObject(emptyMap())
)