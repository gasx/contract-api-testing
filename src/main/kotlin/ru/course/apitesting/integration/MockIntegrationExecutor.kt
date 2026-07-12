package ru.course.apitesting.integration

import kotlinx.serialization.json.JsonObject

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
            status = 200,
            response = config["data"] ?: JsonObject(emptyMap())
        )
    }
}