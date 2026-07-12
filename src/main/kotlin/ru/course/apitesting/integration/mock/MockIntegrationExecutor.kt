package ru.course.apitesting.integration.mock

import ru.course.apitesting.integration.core.IntegrationContext
import ru.course.apitesting.integration.core.IntegrationExecutor
import ru.course.apitesting.integration.core.IntegrationResult

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

class MockIntegrationExecutor : IntegrationExecutor {
    override val type: String = "mock"

    override fun execute(
        name: String,
        config: JsonObject,
        context: IntegrationContext
    ): IntegrationResult {
        return IntegrationResult(
            name = name,
            type = type,
            status = config["status"]?.jsonPrimitive?.intOrNull ?: 200,
            response = config["data"] ?: JsonObject(emptyMap()),
            error = config["error"]?.jsonPrimitive?.contentOrNull
        )
    }
}