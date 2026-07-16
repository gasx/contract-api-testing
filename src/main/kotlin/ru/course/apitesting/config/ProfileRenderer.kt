package ru.course.apitesting.config

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

class ProfileRenderer(
    private val profileName: String,
    private val profile: ProfileConfig
) {
    private val placeholderRegex = Regex("\\$\\{profile\\.([^}]+)}")
    private val fullPlaceholderRegex = Regex("^\\$\\{profile\\.([^}]+)}$")

    private val values: JsonObject = buildProfileValues()

    fun render(element: JsonElement): JsonElement {
        return when (element) {
            is JsonObject -> JsonObject(
                element.mapValues { (_, value) ->
                    render(value)
                }
            )

            is JsonArray -> JsonArray(
                element.map {
                    render(it)
                }
            )

            is JsonPrimitive -> {
                if (element.isString) {
                    renderStringElement(element.contentOrNull ?: element.toString().trim('"'))
                } else {
                    element
                }
            }

            JsonNull -> JsonNull
        }
    }

    private fun renderStringElement(value: String): JsonElement {
        val fullMatch = fullPlaceholderRegex.matchEntire(value)

        if (fullMatch != null) {
            return resolve(fullMatch.groupValues[1])
        }

        val rendered = placeholderRegex.replace(value) { match ->
            jsonToString(resolve(match.groupValues[1]))
        }

        return JsonPrimitive(rendered)
    }

    private fun resolve(path: String): JsonElement {
        return select(values, path)
            ?: error("Profile value не найден: profile.$path для профиля $profileName")
    }

    private fun buildProfileValues(): JsonObject {
        val result = linkedMapOf<String, JsonElement>()

        result["name"] = JsonPrimitive(profileName)

        profile.baseUrl?.let {
            result["baseUrl"] = JsonPrimitive(it)
        }

        profile.timeoutMs?.let {
            result["timeoutMs"] = JsonPrimitive(it)
        }

        profile.variables.forEach { (key, value) ->
            result[key] = value
        }

        return JsonObject(result)
    }

    private fun select(
        root: JsonElement,
        path: String
    ): JsonElement? {
        var current: JsonElement = root

        path.split(".")
            .filter { it.isNotBlank() }
            .forEach { segment ->
                current = (current as? JsonObject)?.get(segment) ?: return null
            }

        return current
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