package ru.course.apitesting.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class RunConfig(
    val baseUrl: String,
    val timeoutMs: Long = 8000,
    val tests: List<ApiTestCase> = emptyList()
)

@Serializable
data class ApiTestCase(
    val testId: String,
    val method: String,
    val path: String,
    val expectedStatus: Int = 200,
    val headers: Map<String, String> = emptyMap(),
    val body: JsonElement? = null,
    val contractFile: String,
    val responseFile: String? = null
)
