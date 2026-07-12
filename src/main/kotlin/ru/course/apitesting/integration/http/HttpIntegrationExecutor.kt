package ru.course.apitesting.integration.http

import ru.course.apitesting.integration.core.IntegrationContext
import ru.course.apitesting.integration.core.IntegrationExecutor
import ru.course.apitesting.integration.core.IntegrationResult

import io.ktor.client.HttpClient
import io.ktor.client.request.basicAuth
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

class HttpIntegrationExecutor(
    private val httpClient: HttpClient
) : IntegrationExecutor {
    override val type: String = "http"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override fun execute(
        name: String,
        config: JsonObject,
        context: IntegrationContext
    ): IntegrationResult {
        return runBlocking {
            val url = config["url"]
                ?.jsonPrimitive
                ?.contentOrNull
                ?: error("У HTTP-интеграции $name не указан url")

            val methodName = config["method"]
                ?.jsonPrimitive
                ?.contentOrNull
                ?: "GET"

            val expectedStatus = config["expectedStatus"]
                ?.jsonPrimitive
                ?.intOrNull

            val failOnStatus = readBoolean(
                config = config,
                field = "failOnStatus",
                defaultValue = true
            )

            val headers = readStringMap(config["headers"] as? JsonObject)
            val query = readStringMap(config["query"] as? JsonObject)
            val body = config["body"]

            val response = httpClient.request(url) {
                method = HttpMethod.parse(methodName.uppercase())

                headers.forEach { (key, value) ->
                    header(key, value)
                }

                query.forEach { (key, value) ->
                    parameter(key, value)
                }

                applyAuth(config["auth"] as? JsonObject)

                if (body != null && body !is JsonNull) {
                    if (headers.keys.none { it.equals(HttpHeaders.ContentType, ignoreCase = true) }) {
                        contentType(ContentType.Application.Json)
                    }

                    setBody(body.toString())
                }
            }

            val status = response.status.value
            val responseText = response.bodyAsText()
            val responseJson = parseResponse(responseText)
            val responseHeaders = response.headers.entries()
                .associate { entry ->
                    entry.key to entry.value.joinToString(",")
                }

            val statusOk = if (expectedStatus != null) {
                status == expectedStatus
            } else {
                status in 200..299
            }

            val error = when {
                statusOk -> null
                !failOnStatus -> null
                expectedStatus != null -> "HTTP integration expected status $expectedStatus but got $status"
                else -> "HTTP integration expected 2xx status but got $status"
            }

            IntegrationResult(
                name = name,
                type = type,
                status = status,
                headers = responseHeaders,
                response = responseJson,
                error = error
            )
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.applyAuth(auth: JsonObject?) {
        if (auth == null) {
            return
        }

        val authType = auth["type"]
            ?.jsonPrimitive
            ?.contentOrNull
            ?: return

        when (authType) {
            "bearer" -> {
                val token = auth["token"]
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?: auth["tokenEnv"]
                        ?.jsonPrimitive
                        ?.contentOrNull
                        ?.let { System.getenv(it) }
                    ?: error("Не задан bearer token")

                header(HttpHeaders.Authorization, "Bearer $token")
            }

            "basic" -> {
                val username = auth["username"]
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?: auth["usernameEnv"]
                        ?.jsonPrimitive
                        ?.contentOrNull
                        ?.let { System.getenv(it) }
                    ?: error("Не задан username для basic auth")

                val password = auth["password"]
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?: auth["passwordEnv"]
                        ?.jsonPrimitive
                        ?.contentOrNull
                        ?.let { System.getenv(it) }
                    ?: error("Не задан password для basic auth")

                basicAuth(username, password)
            }
        }
    }

    private fun readStringMap(obj: JsonObject?): Map<String, String> {
        if (obj == null) {
            return emptyMap()
        }

        return obj.mapValues { (_, value) ->
            jsonToString(value)
        }
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

    private fun parseResponse(text: String): JsonElement {
        return try {
            json.parseToJsonElement(text)
        } catch (e: Throwable) {
            JsonPrimitive(text)
        }
    }

    private fun jsonToString(element: JsonElement): String {
        return when (element) {
            is JsonPrimitive -> element.contentOrNull ?: element.toString()
            else -> element.toString()
        }
    }
}