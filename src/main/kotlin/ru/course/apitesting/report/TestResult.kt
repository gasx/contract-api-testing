package ru.course.apitesting.report

import kotlinx.serialization.Serializable

@Serializable
data class Violation(
    val code: String,
    val path: String,
    val details: String
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
    val durationMs: Long = 0
)