package ru.course.apitesting.integration.avro

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import org.apache.avro.Schema
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericRecord
import java.nio.ByteBuffer
import java.util.Base64

object AvroJsonConverter {
    fun convert(element: JsonElement, schema: Schema): Any? {
        return when (schema.type) {
            Schema.Type.RECORD -> toRecord(element, schema)
            Schema.Type.ARRAY -> toArray(element, schema)
            Schema.Type.MAP -> toMap(element, schema)
            Schema.Type.UNION -> toUnion(element, schema)
            Schema.Type.ENUM -> toEnum(element, schema)
            Schema.Type.STRING -> toStringValue(element)
            Schema.Type.INT -> toIntValue(element)
            Schema.Type.LONG -> toLongValue(element)
            Schema.Type.FLOAT -> toDoubleValue(element).toFloat()
            Schema.Type.DOUBLE -> toDoubleValue(element)
            Schema.Type.BOOLEAN -> toBooleanValue(element)
            Schema.Type.NULL -> null
            Schema.Type.BYTES -> toBytes(element)
            Schema.Type.FIXED -> toFixed(element, schema)
        }
    }

    fun toJson(value: Any?, schema: Schema): JsonElement {
        if (value == null) {
            return JsonNull
        }

        return when (schema.type) {
            Schema.Type.RECORD -> recordToJson(value, schema)
            Schema.Type.ARRAY -> arrayToJson(value, schema)
            Schema.Type.MAP -> mapToJson(value, schema)
            Schema.Type.UNION -> unionToJson(value, schema)
            Schema.Type.ENUM -> JsonPrimitive(value.toString())
            Schema.Type.STRING -> JsonPrimitive(value.toString())
            Schema.Type.INT -> JsonPrimitive((value as Number).toInt())
            Schema.Type.LONG -> JsonPrimitive((value as Number).toLong())
            Schema.Type.FLOAT -> JsonPrimitive((value as Number).toFloat())
            Schema.Type.DOUBLE -> JsonPrimitive((value as Number).toDouble())
            Schema.Type.BOOLEAN -> JsonPrimitive(value as Boolean)
            Schema.Type.NULL -> JsonNull
            Schema.Type.BYTES -> bytesToJson(value)
            Schema.Type.FIXED -> fixedToJson(value)
        }
    }

    private fun toRecord(element: JsonElement, schema: Schema): GenericData.Record {
        val obj = element as? JsonObject
            ?: error("Avro RECORD ожидает JSON object")

        val record = GenericData.Record(schema)

        schema.fields.forEach { field ->
            val value = obj[field.name()]

            val converted = if (value == null || value is JsonNull) {
                if (allowsNull(field.schema())) {
                    null
                } else {
                    error("В Avro message отсутствует обязательное поле: ${field.name()}")
                }
            } else {
                convert(value, field.schema())
            }

            record.put(field.name(), converted)
        }

        return record
    }

    private fun toArray(element: JsonElement, schema: Schema): List<Any?> {
        val array = element as? JsonArray
            ?: error("Avro ARRAY ожидает JSON array")

        return array.map {
            convert(it, schema.elementType)
        }
    }

    private fun toMap(element: JsonElement, schema: Schema): Map<String, Any?> {
        val obj = element as? JsonObject
            ?: error("Avro MAP ожидает JSON object")

        return obj.mapValues { (_, value) ->
            convert(value, schema.valueType)
        }
    }

    private fun toUnion(element: JsonElement, schema: Schema): Any? {
        if (element is JsonNull) {
            if (allowsNull(schema)) {
                return null
            }

            error("Avro UNION не допускает null")
        }

        val targetSchema = schema.types.firstOrNull { it.type != Schema.Type.NULL }
            ?: error("Avro UNION не содержит рабочий тип")

        return convert(element, targetSchema)
    }

    private fun toEnum(element: JsonElement, schema: Schema): GenericData.EnumSymbol {
        val value = toStringValue(element)

        if (!schema.enumSymbols.contains(value)) {
            error("Avro ENUM ${schema.fullName} не содержит значение $value")
        }

        return GenericData.EnumSymbol(schema, value)
    }

    private fun toStringValue(element: JsonElement): String {
        return when (element) {
            is JsonPrimitive -> element.contentOrNull ?: element.toString()
            else -> element.toString()
        }
    }

    private fun toIntValue(element: JsonElement): Int {
        val primitive = element as? JsonPrimitive
            ?: error("Avro INT ожидает primitive")

        return primitive.intOrNull
            ?: primitive.contentOrNull?.toIntOrNull()
            ?: error("Avro INT не может быть получен из $element")
    }

    private fun toLongValue(element: JsonElement): Long {
        val primitive = element as? JsonPrimitive
            ?: error("Avro LONG ожидает primitive")

        return primitive.longOrNull
            ?: primitive.contentOrNull?.toLongOrNull()
            ?: error("Avro LONG не может быть получен из $element")
    }

    private fun toDoubleValue(element: JsonElement): Double {
        val primitive = element as? JsonPrimitive
            ?: error("Avro DOUBLE/FLOAT ожидает primitive")

        return primitive.doubleOrNull
            ?: primitive.contentOrNull?.toDoubleOrNull()
            ?: error("Avro DOUBLE/FLOAT не может быть получен из $element")
    }

    private fun toBooleanValue(element: JsonElement): Boolean {
        val primitive = element as? JsonPrimitive
            ?: error("Avro BOOLEAN ожидает primitive")

        return primitive.booleanOrNull
            ?: primitive.contentOrNull?.toBooleanStrictOrNull()
            ?: error("Avro BOOLEAN не может быть получен из $element")
    }

    private fun toBytes(element: JsonElement): ByteBuffer {
        val value = toStringValue(element)
        return ByteBuffer.wrap(value.toByteArray(Charsets.UTF_8))
    }

    private fun toFixed(element: JsonElement, schema: Schema): GenericData.Fixed {
        val value = toStringValue(element).toByteArray(Charsets.UTF_8)

        if (value.size != schema.fixedSize) {
            error("Avro FIXED ${schema.fullName} ожидает ${schema.fixedSize} байт, получено ${value.size}")
        }

        return GenericData.Fixed(schema, value)
    }

    private fun recordToJson(value: Any, schema: Schema): JsonObject {
        val record = value as? GenericRecord
            ?: error("Avro RECORD decode ожидает GenericRecord")

        return buildJsonObject {
            schema.fields.forEach { field ->
                put(field.name(), toJson(record.get(field.name()), field.schema()))
            }
        }
    }

    private fun arrayToJson(value: Any, schema: Schema): JsonElement {
        val collection = value as? Collection<*>
            ?: error("Avro ARRAY decode ожидает Collection")

        return buildJsonArray {
            collection.forEach {
                add(toJson(it, schema.elementType))
            }
        }
    }

    private fun mapToJson(value: Any, schema: Schema): JsonElement {
        val map = value as? Map<*, *>
            ?: error("Avro MAP decode ожидает Map")

        return buildJsonObject {
            map.forEach { (key, itemValue) ->
                put(key.toString(), toJson(itemValue, schema.valueType))
            }
        }
    }

    private fun unionToJson(value: Any?, schema: Schema): JsonElement {
        if (value == null) {
            return JsonNull
        }

        val index = GenericData.get().resolveUnion(schema, value)
        val targetSchema = schema.types[index]

        return toJson(value, targetSchema)
    }

    private fun bytesToJson(value: Any): JsonPrimitive {
        val bytes = when (value) {
            is ByteBuffer -> {
                val copy = value.duplicate()
                val result = ByteArray(copy.remaining())
                copy.get(result)
                result
            }

            is ByteArray -> value

            else -> error("Avro BYTES decode ожидает ByteBuffer или ByteArray")
        }

        return JsonPrimitive(Base64.getEncoder().encodeToString(bytes))
    }

    private fun fixedToJson(value: Any): JsonPrimitive {
        val fixed = value as? GenericData.Fixed
            ?: error("Avro FIXED decode ожидает GenericData.Fixed")

        return JsonPrimitive(Base64.getEncoder().encodeToString(fixed.bytes()))
    }

    private fun allowsNull(schema: Schema): Boolean {
        return schema.type == Schema.Type.NULL ||
                schema.type == Schema.Type.UNION && schema.types.any { it.type == Schema.Type.NULL }
    }
}