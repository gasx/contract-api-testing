package ru.course.apitesting.schema

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import kotlinx.serialization.json.*
import ru.course.apitesting.config.ApiTestCase
import ru.course.apitesting.config.ConfigLoader
import ru.course.apitesting.config.ContractConfig
import ru.course.apitesting.report.Violation
import java.io.File

sealed class LoadedContract {
    abstract val contractId: String
    abstract val expectedStatus: Int

    data class Legacy(
        override val contractId: String,
        override val expectedStatus: Int,
        val contract: ContractConfig
    ) : LoadedContract()

    data class JsonSchema(
        override val contractId: String,
        override val expectedStatus: Int,
        val schema: JsonElement,
        val document: JsonObject
    ) : LoadedContract()
}

class ExternalContractLoader(
    private val configLoader: ConfigLoader
) {
    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    private val yamlMapper = ObjectMapper(YAMLFactory())

    fun load(path: String, testCase: ApiTestCase): LoadedContract {
        val document = readDocument(path)

        return when {
            document.containsKey("openapi") -> loadOpenApi(document, testCase)

            document.containsKey("contractId") &&
                    document.containsKey("requiredPaths") -> {
                val legacy = configLoader.loadContract(path)

                LoadedContract.Legacy(
                    contractId = legacy.contractId,
                    expectedStatus = testCase.expectedStatus,
                    contract = legacy
                )
            }

            else -> {
                LoadedContract.JsonSchema(
                    contractId = document["title"]
                        ?.jsonPrimitive
                        ?.contentOrNull
                        ?: File(path).nameWithoutExtension,
                    expectedStatus = testCase.expectedStatus,
                    schema = document,
                    document = document
                )
            }
        }
    }

    private fun loadOpenApi(
        openApi: JsonObject,
        testCase: ApiTestCase
    ): LoadedContract.JsonSchema {
        val paths = openApi["paths"]
            ?.jsonObject
            ?: error("OpenAPI: отсутствует раздел paths")

        val pathEntry = paths.entries.firstOrNull { entry ->
            pathMatches(entry.key, testCase.path)
        } ?: error(
            "OpenAPI: путь ${testCase.path} не найден в разделе paths"
        )

        val operation = pathEntry.value
            .jsonObject[testCase.method.lowercase()]
            ?.jsonObject
            ?: error(
                "OpenAPI: метод ${testCase.method.uppercase()} " +
                        "не найден для пути ${pathEntry.key}"
            )

        val responses = operation["responses"]
            ?.jsonObject
            ?: error(
                "OpenAPI: отсутствует responses для " +
                        "${testCase.method.uppercase()} ${pathEntry.key}"
            )

        val response = responses[testCase.expectedStatus.toString()]
            ?.jsonObject
            ?: responses["default"]
                ?.jsonObject
            ?: error(
                "OpenAPI: не найден ответ ${testCase.expectedStatus} " +
                        "для ${testCase.method.uppercase()} ${pathEntry.key}"
            )

        val content = response["content"]
            ?.jsonObject
            ?: error(
                "OpenAPI: у ответа ${testCase.expectedStatus} " +
                        "нет раздела content"
            )

        val mediaType = content["application/json"]
            ?.jsonObject
            ?: content.values
                .firstOrNull()
                ?.jsonObject
            ?: error(
                "OpenAPI: не найдена схема ответа application/json"
            )

        val schema = mediaType["schema"]
            ?: error(
                "OpenAPI: в content нет schema для " +
                        "${testCase.method.uppercase()} ${pathEntry.key}"
            )

        val apiTitle = openApi["info"]
            ?.jsonObject
            ?.get("title")
            ?.jsonPrimitive
            ?.contentOrNull
            ?: "OpenAPI"

        return LoadedContract.JsonSchema(
            contractId = "$apiTitle: ${testCase.method.uppercase()} ${pathEntry.key}",
            expectedStatus = testCase.expectedStatus,
            schema = schema,
            document = openApi
        )
    }

    private fun pathMatches(template: String, actualPath: String): Boolean {
        val actual = actualPath
            .substringBefore("?")
            .trimEnd('/')
            .ifBlank { "/" }

        val expected = template
            .trimEnd('/')
            .ifBlank { "/" }

        val expectedParts = expected
            .trim('/')
            .split('/')
            .filter { it.isNotBlank() }

        val actualParts = actual
            .trim('/')
            .split('/')
            .filter { it.isNotBlank() }

        if (expectedParts.size != actualParts.size) {
            return false
        }

        return expectedParts.zip(actualParts).all { (expectedPart, actualPart) ->
            expectedPart.startsWith("{") && expectedPart.endsWith("}") ||
                    expectedPart == actualPart
        }
    }

    private fun readDocument(path: String): JsonObject {
        val file = File(path)

        require(file.exists()) {
            "Файл контракта не найден: ${file.absolutePath}"
        }

        val extension = file.extension.lowercase()

        val element = when (extension) {
            "yaml", "yml" -> {
                yamlMapper.readTree(file).toJsonElement()
            }

            else -> {
                json.parseToJsonElement(
                    file.readText(Charsets.UTF_8)
                )
            }
        }

        return element as? JsonObject
            ?: error("Контракт должен содержать JSON/YAML объект верхнего уровня")
    }

    private fun JsonNode.toJsonElement(): JsonElement {
        return when {
            isObject -> {
                val result = linkedMapOf<String, JsonElement>()
                val iterator = fields()

                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    result[entry.key] = entry.value.toJsonElement()
                }

                JsonObject(result)
            }

            isArray -> {
                JsonArray(
                    elements().asSequence()
                        .map { it.toJsonElement() }
                        .toList()
                )
            }

            isTextual -> JsonPrimitive(asText())
            isBoolean -> JsonPrimitive(asBoolean())
            isIntegralNumber -> JsonPrimitive(longValue())
            isFloatingPointNumber -> JsonPrimitive(doubleValue())
            isNull -> JsonNull
            else -> JsonPrimitive(asText())
        }
    }
}

object JsonSchemaValidator {

    fun validate(
        response: JsonElement,
        schema: JsonElement,
        document: JsonObject
    ): List<Violation> {
        val violations = mutableListOf<Violation>()

        validateNode(
            value = response,
            schema = schema,
            rootDocument = document,
            path = "",
            violations = violations
        )

        return violations
    }

    private fun validateNode(
        value: JsonElement,
        schema: JsonElement,
        rootDocument: JsonObject,
        path: String,
        violations: MutableList<Violation>
    ) {
        val resolvedSchema = resolveReference(schema, rootDocument)
        val schemaObject = resolvedSchema as? JsonObject ?: return

        val allOf = schemaObject["allOf"] as? JsonArray
        allOf?.forEach { childSchema ->
            validateNode(
                value = value,
                schema = childSchema,
                rootDocument = rootDocument,
                path = path,
                violations = violations
            )
        }

        val expectedTypes = schemaTypes(schemaObject)

        if (expectedTypes.isNotEmpty() &&
            expectedTypes.none { typeMatches(value, it, schemaObject) }
        ) {
            violations += Violation(
                code = "TYPE_MISMATCH",
                path = displayPath(path),
                details = "Expected ${expectedTypes.joinToString(" or ")}"
            )
            return
        }

        validateEnum(value, schemaObject, path, violations)
        validateObject(value, schemaObject, rootDocument, path, violations)
        validateArray(value, schemaObject, rootDocument, path, violations)
    }

    private fun validateEnum(
        value: JsonElement,
        schema: JsonObject,
        path: String,
        violations: MutableList<Violation>
    ) {
        val enumValues = schema["enum"] as? JsonArray ?: return

        if (enumValues.none { it == value }) {
            violations += Violation(
                code = "ENUM_MISMATCH",
                path = displayPath(path),
                details = "Value is not included in enum"
            )
        }
    }

    private fun validateObject(
        value: JsonElement,
        schema: JsonObject,
        rootDocument: JsonObject,
        path: String,
        violations: MutableList<Violation>
    ) {
        val responseObject = value as? JsonObject ?: return

        val requiredFields = (schema["required"] as? JsonArray)
            ?.mapNotNull {
                (it as? JsonPrimitive)?.contentOrNull
            }
            .orEmpty()

        requiredFields.forEach { field ->
            if (!responseObject.containsKey(field)) {
                violations += Violation(
                    code = "REQUIRED_PATH_MISSING",
                    path = childPath(path, field),
                    details = "Required field not found"
                )
            }
        }

        val properties = schema["properties"] as? JsonObject ?: JsonObject(emptyMap())

        properties.forEach { (field, fieldSchema) ->
            val actualValue = responseObject[field] ?: return@forEach

            validateNode(
                value = actualValue,
                schema = fieldSchema,
                rootDocument = rootDocument,
                path = childPath(path, field),
                violations = violations
            )
        }

        val additionalPropertiesForbidden =
            schema["additionalProperties"]
                ?.jsonPrimitive
                ?.booleanOrNull == false

        if (additionalPropertiesForbidden) {
            responseObject.keys
                .filter { it !in properties.keys }
                .forEach { unexpectedField ->
                    violations += Violation(
                        code = "UNEXPECTED_PROPERTY",
                        path = childPath(path, unexpectedField),
                        details = "Property is not allowed by schema"
                    )
                }
        }
    }

    private fun validateArray(
        value: JsonElement,
        schema: JsonObject,
        rootDocument: JsonObject,
        path: String,
        violations: MutableList<Violation>
    ) {
        val responseArray = value as? JsonArray ?: return
        val itemSchema = schema["items"] ?: return

        responseArray.forEachIndexed { index, item ->
            validateNode(
                value = item,
                schema = itemSchema,
                rootDocument = rootDocument,
                path = "$path[$index]",
                violations = violations
            )
        }
    }

    private fun resolveReference(
        schema: JsonElement,
        rootDocument: JsonObject
    ): JsonElement {
        val schemaObject = schema as? JsonObject ?: return schema

        val reference = schemaObject["\$ref"]
            ?.jsonPrimitive
            ?.contentOrNull
            ?: return schema

        if (!reference.startsWith("#/")) {
            return schema
        }

        return resolvePointer(rootDocument, reference.removePrefix("#/")) ?: schema
    }

    private fun resolvePointer(
        rootDocument: JsonObject,
        pointer: String
    ): JsonElement? {
        var current: JsonElement = rootDocument

        pointer.split("/").forEach { rawToken ->
            val token = rawToken
                .replace("~1", "/")
                .replace("~0", "~")

            current = (current as? JsonObject)
                ?.get(token)
                ?: return null
        }

        return current
    }

    private fun schemaTypes(schema: JsonObject): List<String> {
        return when (val type = schema["type"]) {
            is JsonPrimitive -> {
                type.contentOrNull?.let { listOf(it) }.orEmpty()
            }

            is JsonArray -> {
                type.mapNotNull {
                    (it as? JsonPrimitive)?.contentOrNull
                }
            }

            else -> emptyList()
        }
    }

    private fun typeMatches(
        value: JsonElement,
        expectedType: String,
        schema: JsonObject
    ): Boolean {
        if (value is JsonNull) {
            return expectedType == "null" ||
                    schema["nullable"]?.jsonPrimitive?.booleanOrNull == true
        }

        return when (expectedType) {
            "object" -> value is JsonObject
            "array" -> value is JsonArray

            "string" -> {
                value is JsonPrimitive && value.isString
            }

            "boolean" -> {
                value is JsonPrimitive &&
                        !value.isString &&
                        value.booleanOrNull != null
            }

            "integer" -> {
                value is JsonPrimitive &&
                        !value.isString &&
                        value.intOrNull != null
            }

            "number" -> {
                value is JsonPrimitive &&
                        !value.isString &&
                        value.doubleOrNull != null
            }

            "null" -> value is JsonNull
            else -> true
        }
    }

    private fun childPath(parent: String, field: String): String {
        return if (parent.isBlank()) field else "$parent.$field"
    }

    private fun displayPath(path: String): String {
        return if (path.isBlank()) "$" else path
    }
}