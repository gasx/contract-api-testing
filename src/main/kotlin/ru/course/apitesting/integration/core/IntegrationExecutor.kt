package ru.course.apitesting.integration.core

import kotlinx.serialization.json.JsonObject

interface IntegrationExecutor {
    val type: String

    fun execute(
        name: String,
        config: JsonObject,
        context: IntegrationContext
    ): IntegrationResult
}