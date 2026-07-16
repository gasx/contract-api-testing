package ru.course.apitesting.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class RunConfig(
    val baseUrl: String,
    val timeoutMs: Long = 10000,
    val integrations: Map<String, JsonObject> = emptyMap(),
    val tests: List<ApiTestCase>
)

@Serializable
data class ApiTestCase(
    val testId: String,
    val beforeTest: List<String> = emptyList(),
    val method: String,
    val path: String,
    val expectedStatus: Int = 200,
    val headers: Map<String, String> = emptyMap(),
    val query: Map<String, String> = emptyMap(),
    val body: JsonElement? = null,
    val contractFile: String,
    val multipart: List<MultipartPart> = emptyList(),
    val downloadTo: String? = null,
    val expectedContentType: String? = null,
    val responseFile: String? = null,
    val assert: JsonObject? = null
)

@Serializable
data class MultipartPart(
    val name: String,
    val value: String? = null,
    val filePath: String? = null,
    val fileName: String? = null,
    val contentType: String? = null
)