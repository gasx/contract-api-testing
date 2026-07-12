package ru.course.apitesting.integration.template

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
import org.apache.commons.text.lookup.StringLookup
import ru.course.apitesting.integration.core.IntegrationContext

class TemplateRenderer(
    private val integrationNames: Set<String>
) {
    private val placeholderRegex = Regex("\\$\\{([^}]+)}")
    private val fullPlaceholderRegex = Regex("^\\$\\{([^}]+)}$")

    fun findIntegrationNames(element: JsonElement): Set<String> {
        val result = linkedSetOf<String>()
        scanIntegrationNames(element, result)
        return result
    }

    fun findGlobalVarNames(element: JsonElement): Set<String> {
        val result = linkedSetOf<String>()
        scanGlobalVarNames(element, result)
        return result
    }

    fun render(element: JsonElement, context: IntegrationContext): JsonElement {
        return when (element) {
            is JsonObject -> JsonObject(element.mapValues { (_, value) -> render(value, context) })
            is JsonArray -> JsonArray(element.map { render(it, context) })
            is JsonPrimitive -> {
                if (element.isString) {
                    renderStringElement(element.contentOrNull ?: element.toString().trim('"'), context)
                } else {
                    element
                }
            }
            JsonNull -> JsonNull
        }
    }

    fun renderString(value: String, context: IntegrationContext): String {
        val lookup = StringLookup { key ->
            jsonToString(context.resolve(key))
        }

        val substitutor = StringSubstitutor(lookup)
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

    private fun scanIntegrationNames(
        element: JsonElement,
        result: MutableSet<String>
    ) {
        when (element) {
            is JsonObject -> element.values.forEach { scanIntegrationNames(it, result) }
            is JsonArray -> element.forEach { scanIntegrationNames(it, result) }
            is JsonPrimitive -> {
                if (element.isString) {
                    placeholderRegex.findAll(element.contentOrNull ?: element.toString().trim('"')).forEach { match ->
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

    private fun scanGlobalVarNames(
        element: JsonElement,
        result: MutableSet<String>
    ) {
        when (element) {
            is JsonObject -> element.values.forEach { scanGlobalVarNames(it, result) }
            is JsonArray -> element.forEach { scanGlobalVarNames(it, result) }
            is JsonPrimitive -> {
                if (element.isString) {
                    placeholderRegex.findAll(element.contentOrNull ?: element.toString().trim('"')).forEach { match ->
                        val expression = match.groupValues[1]

                        if (expression.startsWith("vars.")) {
                            val varName = expression
                                .removePrefix("vars.")
                                .substringBefore(".")

                            if (varName.isNotBlank()) {
                                result += varName
                            }
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