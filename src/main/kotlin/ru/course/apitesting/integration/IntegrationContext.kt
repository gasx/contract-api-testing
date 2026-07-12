package ru.course.apitesting.integration

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class IntegrationContext {
    private val results = linkedMapOf<String, IntegrationResult>()

    fun has(name: String): Boolean {
        return results.containsKey(name)
    }

    fun put(result: IntegrationResult) {
        results[result.name] = result
    }

    fun get(name: String): IntegrationResult? {
        return results[name]
    }

    fun resolve(expression: String): JsonElement {
        if (expression.startsWith("env.")) {
            val key = expression.removePrefix("env.")
            val value = System.getenv(key)
                ?: error("Переменная окружения не найдена: $key")
            return JsonPrimitive(value)
        }

        val integrationName = expression.substringBefore(".")
        val rest = expression.substringAfter(".", "")

        val result = results[integrationName]
            ?: error("Интеграция ещё не выполнена: $integrationName")

        return when {
            rest == "status" -> JsonPrimitive(result.status)
            rest == "type" -> JsonPrimitive(result.type)
            rest.startsWith("headers.") -> {
                val headerName = rest.removePrefix("headers.")
                JsonPrimitive(result.headers[headerName])
            }
            rest == "response" -> result.response
            rest.startsWith("response.") -> {
                val path = rest.removePrefix("response.")
                select(result.response, path)
                    ?: error("Не найден путь $path в response интеграции $integrationName")
            }
            else -> error("Неизвестное выражение: $expression")
        }
    }

    private fun select(root: JsonElement, path: String): JsonElement? {
        var current: JsonElement = root

        path.split(".")
            .filter { it.isNotBlank() }
            .forEach { segment ->
                current = selectSegment(current, segment) ?: return null
            }

        return current
    }

    private fun selectSegment(root: JsonElement, segment: String): JsonElement? {
        var current: JsonElement = root
        val key = segment.substringBefore("[")
        val indexes = Regex("\\[(\\d+)]")
            .findAll(segment)
            .map { it.groupValues[1].toInt() }
            .toList()

        if (key.isNotBlank()) {
            current = (current as? JsonObject)?.get(key) ?: return null
        }

        indexes.forEach { index ->
            val array = current as? kotlinx.serialization.json.JsonArray ?: return null
            current = array.getOrNull(index) ?: return null
        }

        return current
    }
}