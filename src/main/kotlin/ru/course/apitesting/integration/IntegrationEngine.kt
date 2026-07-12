package ru.course.apitesting.integration

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive
import ru.course.apitesting.config.ApiTestCase

class IntegrationEngine(
    private val integrations: Map<String, JsonObject>,
    executors: List<IntegrationExecutor>
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val executorsByType = executors.associateBy { it.type }
    private val renderer = TemplateRenderer(integrations.keys)

    fun prepareTest(testCase: ApiTestCase): ApiTestCase {
        val context = IntegrationContext()
        val testJson = json.encodeToJsonElement(ApiTestCase.serializer(), testCase)

        val requiredByTemplates = renderer.findIntegrationNames(testJson)
        val required = linkedSetOf<String>()

        required += testCase.beforeTest
        required += requiredByTemplates

        required.forEach { name ->
            executeIntegration(name, context, linkedSetOf())
        }

        val renderedTestJson = renderer.render(testJson, context)

        return json.decodeFromJsonElement(
            ApiTestCase.serializer(),
            renderedTestJson
        )
    }

    private fun executeIntegration(
        name: String,
        context: IntegrationContext,
        stack: MutableSet<String>
    ): IntegrationResult {
        if (context.has(name)) {
            return context.get(name)!!
        }

        if (name in stack) {
            error("Циклическая зависимость интеграций: ${(stack + name).joinToString(" -> ")}")
        }

        val rawConfig = integrations[name]
            ?: error("Интеграция не найдена: $name")

        stack += name

        val dependencies = renderer.findIntegrationNames(rawConfig)
            .filter { it != name }

        dependencies.forEach { dependency ->
            executeIntegration(dependency, context, stack)
        }

        val renderedConfig = renderer.render(rawConfig, context) as JsonObject
        val type = renderedConfig["type"]
            ?.jsonPrimitive
            ?.content
            ?: error("У интеграции $name не указан type")

        val executor = executorsByType[type]
            ?: error("Исполнитель интеграции type=$type не зарегистрирован")

        val result = executor.execute(
            name = name,
            config = renderedConfig,
            context = context
        )

        context.put(result)
        stack -= name

        return result
    }
}