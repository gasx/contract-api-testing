package ru.course.apitesting.integration

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import org.apache.commons.text.StringSubstitutor

class TemplateRenderer(
    private val integrationNames: Set<String>
) {
    private val placeholderRegex = Regex("\\$\\{([^}]+)}")
    private val fullPlaceholderRegex = Regex("^\\$\\{([^}]+)}$")

    fun findIntegrationNames(element: JsonElement): Set<String> {
        val result = linkedSetOf<String>()
        scan(element, result)
        return result
    }

    fun render(element: JsonElement, context: IntegrationContext): JsonElement {
        return when (element) {
            is JsonObject -> {
                JsonObject(
                    element.mapValues { (_, value) ->
                        render(value, context)
                    }
                )
            }

            is JsonArray -> {
                JsonArray(
                    element.map {
                        render(it, context)
                    }
                )
            }

            is JsonPrimitive -> {
                if (element.isString) {
                    renderStringElement(element.content, context)
                } else {
                    element
                }
            }

            JsonNull -> JsonNull
        }
    }

    fun renderString(value: String, context: IntegrationContext): String {
        val substitutor = StringSubstitutor { key ->
            jsonToString(context.resolve(key))
        }

        substitutor.setEnableUndefinedVariableException(true)
        return substitutor.replace(value)
    }

    private fun renderStringElement(value: String, context: IntegrationContext): JsonElement {
        val fullMatch = fullPlaceholderRegex.matchEntire(value)

        if (fullMatch != null) {
            return context.resolve(fullMatch.groupValues[1])
        }

        return JsonPrimitive(renderString(value, context))
    }

    private fun scan(element: JsonElement, result: MutableSet<String>) {
        when (element) {
            is JsonObject -> {
                element.values.forEach {
                    scan(it, result)
                }
            }

            is JsonArray -> {
                element.forEach {
                    scan(it, result)
                }
            }

            is JsonPrimitive -> {
                if (element.isString) {
                    placeholderRegex.findAll(element.content).forEach { match ->
                        val rootName = match.groupValues[1].substringBefore(".")
                        if (rootName in integrationNames) {
                            result += rootName
                        }
                    }
                }
            }

            JsonNull -> Unit
        }
    }

    private fun jsonToString(element: JsonElement): String {
        return when (element) {
            is JsonPrimitive -> {
                element.contentOrNull
                    ?: element.booleanOrNull?.toString()
                    ?: element.longOrNull?.toString()
                    ?: element.intOrNull?.toString()
                    ?: element.doubleOrNull?.toString()
                    ?: element.toString()
            }

            else -> Json.encodeToString(JsonElement.serializer(), element)
        }
    }
}