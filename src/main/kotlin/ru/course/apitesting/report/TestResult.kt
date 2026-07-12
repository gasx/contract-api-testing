package ru.course.apitesting.report

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonObject

@Serializable
data class Violation(
    val code: String,
    val path: String,
    val details: String
)

@Serializable
data class FileTransferInfo(
    val direction: String,
    val fileName: String,
    val contentType: String? = null,
    val sizeBytes: Long,
    @Transient
    val localPath: String? = null
)

@Serializable
data class IntegrationRunInfo(
    val name: String,
    val type: String,
    val status: Int? = null,
    val durationMs: Long = 0,
    val attempts: Int = 1,
    val error: String? = null,
    val vars: JsonObject = JsonObject(emptyMap()),
    val savedVars: JsonObject = JsonObject(emptyMap())
)

@Serializable
data class TestResult(
    val testId: String,
    val contractId: String,
    val target: String,
    val method: String,
    val expectedStatus: Int,
    val actualStatus: Int?,
    val passed: Boolean,
    val violations: List<Violation>,
    val durationMs: Long = 0,
    val fileTransfers: List<FileTransferInfo> = emptyList(),
    val integrations: List<IntegrationRunInfo> = emptyList()
)