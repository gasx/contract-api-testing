package ru.course.apitesting.integration.core

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class IntegrationContext {
    private val results = linkedMapOf<String, IntegrationResult>()
    private val globalVars = linkedMapOf<String, JsonElement>()

    fun has(name: String): Boolean {
        return results.containsKey(name)
    }

    fun put(result: IntegrationResult) {
        results[result.name] = result
    }

    fun get(name: String): IntegrationResult? {
        return results[name]
    }

    fun all(): List<IntegrationResult> {
        return results.values.toList()
    }

    fun putVar(name: String, value: JsonElement) {
        globalVars[name] = value
    }

    fun putVars(values: Map<String, JsonElement>) {
        values.forEach { (name, value) ->
            putVar(name, value)
        }
    }

    fun vars(): Map<String, JsonElement> {
        return globalVars.toMap()
    }

    fun resolve(expression: String): JsonElement {
        if (expression.startsWith("env.")) {
            val key = expression.removePrefix("env.")
            val value = System.getenv(key)
                ?: error("Переменная окружения не найдена: $key")

            return JsonPrimitive(value)
        }

        if (expression == "vars") {
            return JsonObject(globalVars)
        }

        if (expression.startsWith("vars.")) {
            val path = expression.removePrefix("vars.")

            return IntegrationValueSelector.select(JsonObject(globalVars), path)
                ?: error("Глобальная переменная не найдена: vars.$path")
        }

        val integrationName = expression.substringBefore(".")
        val rest = expression.substringAfter(".", "")

        val result = results[integrationName]
            ?: error("Интеграция ещё не выполнена: $integrationName")

        return IntegrationValueSelector.resolve(result, rest)
    }
}