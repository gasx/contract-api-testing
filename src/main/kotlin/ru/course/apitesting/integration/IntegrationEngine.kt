package ru.course.apitesting.integration

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import ru.course.apitesting.config.ApiTestCase
import kotlin.system.measureTimeMillis

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

    fun prepareTest(testCase: ApiTestCase): PreparedTestCase {
        val context = IntegrationContext()
        val testJson = json.encodeToJsonElement(ApiTestCase.serializer(), testCase)

        val required = linkedSetOf<String>()
        required += testCase.beforeTest
        required += renderer.findIntegrationNames(testJson)

        required.forEach { name ->
            executeIntegration(name, context, linkedSetOf())
        }

        val renderedTestCase = testCase.copy(
            path = renderer.renderString(testCase.path, context),
            headers = testCase.headers.mapValues { (_, value) ->
                renderer.renderString(value, context)
            },
            query = testCase.query.mapValues { (_, value) ->
                renderer.renderString(value, context)
            },
            body = testCase.body?.let {
                renderer.render(it, context)
            },
            contractFile = renderer.renderString(testCase.contractFile, context),
            multipart = testCase.multipart.map { part ->
                part.copy(
                    value = part.value?.let { renderer.renderString(it, context) },
                    filePath = part.filePath?.let { renderer.renderString(it, context) },
                    fileName = part.fileName?.let { renderer.renderString(it, context) },
                    contentType = part.contentType?.let { renderer.renderString(it, context) }
                )
            },
            downloadTo = testCase.downloadTo?.let {
                renderer.renderString(it, context)
            },
            expectedContentType = testCase.expectedContentType?.let {
                renderer.renderString(it, context)
            },
            responseFile = testCase.responseFile?.let {
                renderer.renderString(it, context)
            }
        )

        return PreparedTestCase(
            testCase = renderedTestCase,
            integrations = context.all()
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

        val rawType = rawConfig["type"]
            ?.jsonPrimitive
            ?.contentOrNull
            ?: "unknown"

        stack += name

        try {
            val dependencies = renderer.findIntegrationNames(rawConfig)
                .filter { it != name }

            dependencies.forEach { dependency ->
                executeIntegration(dependency, context, stack)
            }

            val renderedConfig = renderer.render(rawConfig, context) as JsonObject

            val type = renderedConfig["type"]
                ?.jsonPrimitive
                ?.contentOrNull
                ?: error("У интеграции $name не указан type")

            val executor = executorsByType[type]
                ?: error("Исполнитель интеграции type=$type не зарегистрирован")

            var rawResult: IntegrationResult? = null
            var thrown: Throwable? = null

            val durationMs = measureTimeMillis {
                try {
                    rawResult = executor.execute(
                        name = name,
                        config = renderedConfig,
                        context = context
                    )
                } catch (t: Throwable) {
                    thrown = t
                    rawResult = IntegrationResult(
                        name = name,
                        type = type,
                        error = t.message ?: t::class.simpleName ?: "error"
                    )
                }
            }

            val result = rawResult!!.copy(durationMs = durationMs)

            context.put(result)

            println(
                "Integration executed: " +
                        "$name type=$type status=${result.status ?: "-"} " +
                        "durationMs=$durationMs error=${result.error ?: "-"}"
            )

            if (result.error != null) {
                throw IntegrationFailedException(
                    integrationName = name,
                    integrationType = type,
                    integrationResults = context.all(),
                    message = "Ошибка интеграции $name type=$type: ${result.error}",
                    cause = thrown
                )
            }

            return result
        } catch (e: IntegrationFailedException) {
            throw e
        } catch (t: Throwable) {
            val result = IntegrationResult(
                name = name,
                type = rawType,
                error = t.message ?: t::class.simpleName ?: "error"
            )

            context.put(result)

            throw IntegrationFailedException(
                integrationName = name,
                integrationType = rawType,
                integrationResults = context.all(),
                message = "Ошибка интеграции $name type=$rawType: ${result.error}",
                cause = t
            )
        } finally {
            stack -= name
        }
    }
}