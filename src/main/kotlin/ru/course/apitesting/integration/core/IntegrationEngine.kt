package ru.course.apitesting.integration.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import ru.course.apitesting.config.ApiTestCase
import ru.course.apitesting.integration.assertions.IntegrationAssertions
import ru.course.apitesting.integration.template.TemplateRenderer
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
    private val varProducers = buildVarProducers()

    fun prepareTest(testCase: ApiTestCase): PreparedTestCase {
        val context = IntegrationContext()
        val testJson = json.encodeToJsonElement(ApiTestCase.serializer(), testCase)

        val required = linkedSetOf<String>()
        required += testCase.beforeTest
        required += renderer.findIntegrationNames(testJson)
        required += producerIntegrationsForVars(renderer.findGlobalVarNames(testJson))

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

        var failOnError = readBoolean(
            config = rawConfig,
            field = "failOnError",
            defaultValue = true
        )

        stack += name

        try {
            val dependencies = linkedSetOf<String>()

            dependencies += renderer.findIntegrationNames(rawConfig)
                .filter { it != name }

            dependencies += producerIntegrationsForVars(renderer.findGlobalVarNames(rawConfig))
                .filter { it != name }

            dependencies.forEach { dependency ->
                executeIntegration(dependency, context, stack)
            }

            val renderedConfig = renderer.render(rawConfig, context) as JsonObject

            failOnError = readBoolean(
                config = renderedConfig,
                field = "failOnError",
                defaultValue = failOnError
            )

            val retryConfig = readRetryConfig(renderedConfig)

            val type = renderedConfig["type"]
                ?.jsonPrimitive
                ?.contentOrNull
                ?: error("У интеграции $name не указан type")

            val executor = executorsByType[type]
                ?: error("Исполнитель интеграции type=$type не зарегистрирован")

            var rawResult: IntegrationResult? = null
            var thrown: Throwable? = null
            var attemptsUsed = 1

            val durationMs = measureTimeMillis {
                var attempt = 1

                while (attempt <= retryConfig.attempts) {
                    attemptsUsed = attempt
                    thrown = null

                    rawResult = try {
                        executor.execute(
                            name = name,
                            config = renderedConfig,
                            context = context
                        )
                    } catch (t: Throwable) {
                        thrown = t

                        IntegrationResult(
                            name = name,
                            type = type,
                            error = t.message ?: t::class.simpleName ?: "error"
                        )
                    }.copy(
                        attempts = attempt
                    )

                    val shouldRetry = !rawResult!!.error.isNullOrBlank() &&
                            attempt < retryConfig.attempts

                    if (!shouldRetry) {
                        break
                    }

                    println(
                        "Integration retry: " +
                                "$name type=$type attempt=$attempt/${retryConfig.attempts} " +
                                "error=${rawResult!!.error ?: "-"}"
                    )

                    if (retryConfig.delayMs > 0) {
                        Thread.sleep(retryConfig.delayMs)
                    }

                    attempt++
                }
            }

            val resultWithDuration = rawResult!!.copy(
                durationMs = durationMs,
                attempts = attemptsUsed
            )

            val resultWithVars = if (resultWithDuration.error == null) {
                val extractedVars = extractVars(
                    name = name,
                    result = resultWithDuration,
                    extractConfig = renderedConfig["extract"] as? JsonObject
                )

                resultWithDuration.copy(
                    vars = resultWithDuration.vars + extractedVars
                )
            } else {
                resultWithDuration
            }

            val resultWithSavedVars = if (resultWithVars.error == null) {
                val savedVars = saveVars(
                    name = name,
                    result = resultWithVars,
                    saveAsConfig = renderedConfig["saveAs"] as? JsonObject,
                    context = context
                )

                resultWithVars.copy(
                    savedVars = savedVars
                )
            } else {
                resultWithVars
            }

            val assertionErrors = if (resultWithSavedVars.error == null) {
                IntegrationAssertions.validate(
                    name = name,
                    result = resultWithSavedVars,
                    assertConfig = renderedConfig["assert"] as? JsonObject
                )
            } else {
                emptyList()
            }

            val result = if (assertionErrors.isEmpty()) {
                resultWithSavedVars
            } else {
                resultWithSavedVars.copy(
                    error = assertionErrors.joinToString("; ")
                )
            }

            context.put(result)

            println(
                "Integration executed: " +
                        "$name type=$type status=${result.status ?: "-"} " +
                        "durationMs=$durationMs attempts=${result.attempts} " +
                        "error=${result.error ?: "-"} failOnError=$failOnError"
            )

            if (!result.error.isNullOrBlank() && failOnError) {
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

            if (failOnError) {
                throw IntegrationFailedException(
                    integrationName = name,
                    integrationType = rawType,
                    integrationResults = context.all(),
                    message = "Ошибка интеграции $name type=$rawType: ${result.error}",
                    cause = t
                )
            }

            return result
        } finally {
            stack -= name
        }
    }

    private fun extractVars(
        name: String,
        result: IntegrationResult,
        extractConfig: JsonObject?
    ): Map<String, JsonElement> {
        if (extractConfig == null) {
            return emptyMap()
        }

        return extractConfig.mapValues { (varName, pathElement) ->
            val path = pathElement
                .jsonPrimitive
                .contentOrNull
                ?: error("extract.$varName у интеграции $name должен быть строкой")

            IntegrationValueSelector.resolve(result, path)
        }
    }

    private fun saveVars(
        name: String,
        result: IntegrationResult,
        saveAsConfig: JsonObject?,
        context: IntegrationContext
    ): Map<String, JsonElement> {
        if (saveAsConfig == null) {
            return emptyMap()
        }

        val savedVars = saveAsConfig.mapValues { (varName, pathElement) ->
            val path = pathElement
                .jsonPrimitive
                .contentOrNull
                ?: error("saveAs.$varName у интеграции $name должен быть строкой")

            IntegrationValueSelector.resolve(result, path)
        }

        context.putVars(savedVars)

        return savedVars
    }

    private fun buildVarProducers(): Map<String, String> {
        val entries = integrations.flatMap { (integrationName, config) ->
            val saveAs = config["saveAs"] as? JsonObject
                ?: return@flatMap emptyList<Pair<String, String>>()

            saveAs.keys.map { varName ->
                varName to integrationName
            }
        }

        val duplicates = entries
            .groupBy { it.first }
            .filter { (_, values) ->
                values.map { it.second }.toSet().size > 1
            }

        if (duplicates.isNotEmpty()) {
            val text = duplicates.map { (varName, values) ->
                val producers = values
                    .map { it.second }
                    .distinct()
                    .joinToString(",")

                "$varName=$producers"
            }.joinToString("; ")

            error("Одна global var сохраняется несколькими интеграциями: $text")
        }

        return entries.associate { (varName, integrationName) ->
            varName to integrationName
        }
    }

    private fun producerIntegrationsForVars(varNames: Set<String>): List<String> {
        return varNames
            .mapNotNull { varName ->
                varProducers[varName]
            }
            .distinct()
    }

    private fun readRetryConfig(config: JsonObject): RetryConfig {
        val retry = config["retry"] as? JsonObject
            ?: return RetryConfig()

        val attempts = retry["attempts"]
            ?.jsonPrimitive
            ?.intOrNull
            ?: 1

        val delayMs = retry["delayMs"]
            ?.jsonPrimitive
            ?.longOrNull
            ?: 0L

        return RetryConfig(
            attempts = attempts.coerceAtLeast(1),
            delayMs = delayMs.coerceAtLeast(0)
        )
    }

    private fun readBoolean(
        config: JsonObject,
        field: String,
        defaultValue: Boolean
    ): Boolean {
        val primitive = config[field]?.jsonPrimitive ?: return defaultValue

        primitive.booleanOrNull?.let {
            return it
        }

        return when (primitive.contentOrNull?.lowercase()) {
            "true" -> true
            "false" -> false
            else -> defaultValue
        }
    }

    private data class RetryConfig(
        val attempts: Int = 1,
        val delayMs: Long = 0
    )
}