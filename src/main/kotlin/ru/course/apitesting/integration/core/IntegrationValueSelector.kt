package ru.course.apitesting.integration.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

object IntegrationValueSelector {
    fun resolve(result: IntegrationResult, expression: String): JsonElement {
        return when {
            expression == "status" -> {
                result.status?.let { JsonPrimitive(it) } ?: JsonNull
            }

            expression == "type" -> {
                JsonPrimitive(result.type)
            }

            expression == "response" -> {
                result.response
            }

            expression.startsWith("response.") -> {
                val path = expression.removePrefix("response.")
                select(result.response, path)
                    ?: error("Не найден путь response.$path в интеграции ${result.name}")
            }

            expression == "vars" -> {
                JsonObject(result.vars)
            }

            expression.startsWith("vars.") -> {
                val path = expression.removePrefix("vars.")
                select(JsonObject(result.vars), path)
                    ?: error("Не найден путь vars.$path в интеграции ${result.name}")
            }

            expression.startsWith("headers.") -> {
                val headerName = expression.removePrefix("headers.")
                result.headers[headerName]?.let { JsonPrimitive(it) }
                    ?: error("Не найден header $headerName в интеграции ${result.name}")
            }

            else -> {
                error("Неизвестное выражение ${result.name}.$expression")
            }
        }
    }

    fun select(root: JsonElement, path: String): JsonElement? {
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
            val array = current as? JsonArray ?: return null
            current = array.getOrNull(index) ?: return null
        }

        return current
    }
}