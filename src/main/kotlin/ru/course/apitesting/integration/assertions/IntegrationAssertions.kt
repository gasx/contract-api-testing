package ru.course.apitesting.integration.assertions

import ru.course.apitesting.integration.core.IntegrationResult
import ru.course.apitesting.integration.core.IntegrationValueSelector

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

object IntegrationAssertions {
    private val json = Json {
        prettyPrint = false
        encodeDefaults = true
    }

    private val operatorKeys = setOf(
        "eq",
        "notEq",
        "exists",
        "isNull",
        "notNull",
        "gt",
        "gte",
        "lt",
        "lte",
        "in",
        "contains",
        "matches",
        "startsWith",
        "endsWith",
        "type"
    )

    fun validate(
        name: String,
        result: IntegrationResult,
        assertConfig: JsonObject?
    ): List<String> {
        if (assertConfig == null) {
            return emptyList()
        }

        return assertConfig.flatMap { (path, expected) ->
            validatePath(result, path, expected)
        }
    }

    private fun validatePath(
        result: IntegrationResult,
        path: String,
        expected: JsonElement
    ): List<String> {
        val actual = resolveActual(result, path)

        if (isOperatorObject(expected)) {
            val operators = expected as JsonObject

            return operators.mapNotNull { (operator, operatorExpected) ->
                validateOperator(path, actual, operator, operatorExpected)
            }
        }

        if (!actual.exists) {
            return listOf("assert.$path: actual=<missing>, expected=${toText(expected)}")
        }

        return if (actual.value == expected) {
            emptyList()
        } else {
            listOf("assert.$path: expected=${toText(expected)}, actual=${toText(actual.value)}")
        }
    }

    private fun validateOperator(
        path: String,
        actual: ActualValue,
        operator: String,
        expected: JsonElement
    ): String? {
        return when (operator) {
            "eq" -> validateEq(path, actual, expected)
            "notEq" -> validateNotEq(path, actual, expected)
            "exists" -> validateExists(path, actual, expected)
            "isNull" -> validateIsNull(path, actual, expected)
            "notNull" -> validateNotNull(path, actual, expected)
            "gt" -> validateNumber(path, actual, expected, operator) { a, b -> a > b }
            "gte" -> validateNumber(path, actual, expected, operator) { a, b -> a >= b }
            "lt" -> validateNumber(path, actual, expected, operator) { a, b -> a < b }
            "lte" -> validateNumber(path, actual, expected, operator) { a, b -> a <= b }
            "in" -> validateIn(path, actual, expected)
            "contains" -> validateContains(path, actual, expected)
            "matches" -> validateMatches(path, actual, expected)
            "startsWith" -> validateStartsWith(path, actual, expected)
            "endsWith" -> validateEndsWith(path, actual, expected)
            "type" -> validateType(path, actual, expected)
            else -> "assert.$path: неизвестный оператор $operator"
        }
    }

    private fun validateEq(
        path: String,
        actual: ActualValue,
        expected: JsonElement
    ): String? {
        if (!actual.exists) {
            return "assert.$path.eq: actual=<missing>, expected=${toText(expected)}"
        }

        return if (actual.value == expected) {
            null
        } else {
            "assert.$path.eq: expected=${toText(expected)}, actual=${toText(actual.value)}"
        }
    }

    private fun validateNotEq(
        path: String,
        actual: ActualValue,
        expected: JsonElement
    ): String? {
        if (!actual.exists) {
            return null
        }

        return if (actual.value != expected) {
            null
        } else {
            "assert.$path.notEq: actual не должен быть равен ${toText(expected)}"
        }
    }

    private fun validateExists(
        path: String,
        actual: ActualValue,
        expected: JsonElement
    ): String? {
        val expectedValue = expectedBoolean(expected)
        val ok = actual.exists == expectedValue

        return if (ok) {
            null
        } else {
            "assert.$path.exists: expected=$expectedValue, actual=${actual.exists}"
        }
    }

    private fun validateIsNull(
        path: String,
        actual: ActualValue,
        expected: JsonElement
    ): String? {
        val expectedValue = expectedBoolean(expected)
        val actualValue = actual.exists && actual.value is JsonNull
        val ok = actualValue == expectedValue

        return if (ok) {
            null
        } else {
            "assert.$path.isNull: expected=$expectedValue, actual=$actualValue"
        }
    }

    private fun validateNotNull(
        path: String,
        actual: ActualValue,
        expected: JsonElement
    ): String? {
        val expectedValue = expectedBoolean(expected)
        val actualValue = actual.exists && actual.value !is JsonNull
        val ok = actualValue == expectedValue

        return if (ok) {
            null
        } else {
            "assert.$path.notNull: expected=$expectedValue, actual=$actualValue"
        }
    }

    private fun validateNumber(
        path: String,
        actual: ActualValue,
        expected: JsonElement,
        operator: String,
        predicate: (Double, Double) -> Boolean
    ): String? {
        if (!actual.exists) {
            return "assert.$path.$operator: actual=<missing>, expected=${toText(expected)}"
        }

        val actualNumber = actual.value?.let { toNumber(it) }
            ?: return "assert.$path.$operator: actual не является числом: ${toText(actual.value)}"

        val expectedNumber = toNumber(expected)
            ?: return "assert.$path.$operator: expected не является числом: ${toText(expected)}"

        return if (predicate(actualNumber, expectedNumber)) {
            null
        } else {
            "assert.$path.$operator: expected=${toText(expected)}, actual=${toText(actual.value)}"
        }
    }

    private fun validateIn(
        path: String,
        actual: ActualValue,
        expected: JsonElement
    ): String? {
        if (!actual.exists) {
            return "assert.$path.in: actual=<missing>, expected=${toText(expected)}"
        }

        val array = expected as? JsonArray
            ?: return "assert.$path.in: expected должен быть массивом"

        val ok = array.any { it == actual.value }

        return if (ok) {
            null
        } else {
            "assert.$path.in: actual=${toText(actual.value)}, expected one of=${toText(expected)}"
        }
    }

    private fun validateContains(
        path: String,
        actual: ActualValue,
        expected: JsonElement
    ): String? {
        if (!actual.exists) {
            return "assert.$path.contains: actual=<missing>, expected=${toText(expected)}"
        }

        val actualValue = actual.value

        val ok = when (actualValue) {
            is JsonPrimitive -> {
                val actualText = actualValue.contentOrNull ?: actualValue.toString()
                val expectedText = expectedPrimitiveText(expected)
                expectedText != null && actualText.contains(expectedText)
            }

            is JsonArray -> {
                actualValue.any { it == expected }
            }

            is JsonObject -> {
                val expectedKey = expectedPrimitiveText(expected)
                expectedKey != null && actualValue.containsKey(expectedKey)
            }

            else -> false
        }

        return if (ok) {
            null
        } else {
            "assert.$path.contains: expected=${toText(expected)}, actual=${toText(actual.value)}"
        }
    }

    private fun validateMatches(
        path: String,
        actual: ActualValue,
        expected: JsonElement
    ): String? {
        if (!actual.exists) {
            return "assert.$path.matches: actual=<missing>, expected=${toText(expected)}"
        }

        val actualText = actual.value?.let { primitiveText(it) }
            ?: return "assert.$path.matches: actual не является строкой: ${toText(actual.value)}"

        val pattern = expectedPrimitiveText(expected)
            ?: return "assert.$path.matches: expected должен быть строкой"

        val ok = Regex(pattern).containsMatchIn(actualText)

        return if (ok) {
            null
        } else {
            "assert.$path.matches: pattern=$pattern, actual=$actualText"
        }
    }

    private fun validateStartsWith(
        path: String,
        actual: ActualValue,
        expected: JsonElement
    ): String? {
        if (!actual.exists) {
            return "assert.$path.startsWith: actual=<missing>, expected=${toText(expected)}"
        }

        val actualText = actual.value?.let { primitiveText(it) }
            ?: return "assert.$path.startsWith: actual не является строкой: ${toText(actual.value)}"

        val expectedText = expectedPrimitiveText(expected)
            ?: return "assert.$path.startsWith: expected должен быть строкой"

        return if (actualText.startsWith(expectedText)) {
            null
        } else {
            "assert.$path.startsWith: expected=$expectedText, actual=$actualText"
        }
    }

    private fun validateEndsWith(
        path: String,
        actual: ActualValue,
        expected: JsonElement
    ): String? {
        if (!actual.exists) {
            return "assert.$path.endsWith: actual=<missing>, expected=${toText(expected)}"
        }

        val actualText = actual.value?.let { primitiveText(it) }
            ?: return "assert.$path.endsWith: actual не является строкой: ${toText(actual.value)}"

        val expectedText = expectedPrimitiveText(expected)
            ?: return "assert.$path.endsWith: expected должен быть строкой"

        return if (actualText.endsWith(expectedText)) {
            null
        } else {
            "assert.$path.endsWith: expected=$expectedText, actual=$actualText"
        }
    }

    private fun validateType(
        path: String,
        actual: ActualValue,
        expected: JsonElement
    ): String? {
        if (!actual.exists) {
            return "assert.$path.type: actual=<missing>, expected=${toText(expected)}"
        }

        val expectedType = expectedPrimitiveText(expected)
            ?: return "assert.$path.type: expected должен быть строкой"

        val actualType = typeOf(actual.value)

        return if (actualType == expectedType) {
            null
        } else {
            "assert.$path.type: expected=$expectedType, actual=$actualType"
        }
    }

    private fun resolveActual(
        result: IntegrationResult,
        path: String
    ): ActualValue {
        return try {
            ActualValue(
                exists = true,
                value = IntegrationValueSelector.resolve(result, path)
            )
        } catch (e: Throwable) {
            ActualValue(
                exists = false,
                value = null
            )
        }
    }

    private fun isOperatorObject(element: JsonElement): Boolean {
        val obj = element as? JsonObject ?: return false

        if (obj.isEmpty()) {
            return false
        }

        return obj.keys.all { it in operatorKeys }
    }

    private fun expectedBoolean(element: JsonElement): Boolean {
        val primitive = element as? JsonPrimitive
            ?: error("expected должен быть boolean")

        return primitive.booleanOrNull
            ?: primitive.contentOrNull?.toBooleanStrictOrNull()
            ?: error("expected должен быть boolean")
    }

    private fun toNumber(element: JsonElement): Double? {
        val primitive = element as? JsonPrimitive ?: return null

        return primitive.doubleOrNull
            ?: primitive.longOrNull?.toDouble()
            ?: primitive.intOrNull?.toDouble()
            ?: primitive.contentOrNull?.toDoubleOrNull()
    }

    private fun primitiveText(element: JsonElement): String? {
        val primitive = element as? JsonPrimitive ?: return null

        return primitive.contentOrNull ?: primitive.toString()
    }

    private fun expectedPrimitiveText(element: JsonElement): String? {
        return primitiveText(element)
    }

    private fun typeOf(element: JsonElement?): String {
        return when (element) {
            null -> "missing"
            JsonNull -> "null"
            is JsonObject -> "object"
            is JsonArray -> "array"
            is JsonPrimitive -> {
                when {
                    element.booleanOrNull != null -> "boolean"
                    element.intOrNull != null -> "int"
                    element.longOrNull != null -> "long"
                    element.doubleOrNull != null -> "number"
                    else -> "string"
                }
            }
        }
    }

    private fun toText(element: JsonElement?): String {
        if (element == null) {
            return "<missing>"
        }

        return json.encodeToString(JsonElement.serializer(), element)
    }

    private data class ActualValue(
        val exists: Boolean,
        val value: JsonElement?
    )
}