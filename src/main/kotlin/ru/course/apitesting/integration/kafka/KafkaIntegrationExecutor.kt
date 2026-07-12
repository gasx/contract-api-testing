package ru.course.apitesting.integration.kafka

import ru.course.apitesting.integration.avro.AvroJsonConverter
import ru.course.apitesting.integration.core.IntegrationContext
import ru.course.apitesting.integration.core.IntegrationExecutor
import ru.course.apitesting.integration.core.IntegrationResult

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import org.apache.avro.Schema
import org.apache.avro.generic.GenericDatumWriter
import org.apache.avro.generic.GenericRecord
import org.apache.avro.io.EncoderFactory
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringSerializer
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Properties

class KafkaIntegrationExecutor(
    private val baseDir: File
) : IntegrationExecutor {
    override val type: String = "kafka"

    override fun execute(
        name: String,
        config: JsonObject,
        context: IntegrationContext
    ): IntegrationResult {
        val hosts = readStringList(config["hosts"], "hosts")
        val topic = config["topic"]
            ?.jsonPrimitive
            ?.contentOrNull
            ?: error("У Kafka-интеграции $name не указан topic")

        val key = config["key"]?.let {
            jsonToString(it)
        }

        val message = config["message"]
            ?: error("У Kafka-интеграции $name не указан message")

        val messageFormat = config["messageFormat"]
            ?.jsonPrimitive
            ?.contentOrNull
            ?: "json"

        val value = when (messageFormat.lowercase()) {
            "json" -> jsonToString(message).toByteArray(Charsets.UTF_8)
            "string" -> jsonToString(message).toByteArray(Charsets.UTF_8)
            "avro" -> encodeAvro(name, config, message)
            else -> error("Kafka-интеграция $name имеет неизвестный messageFormat=$messageFormat")
        }

        val headers = readStringMap(config["headers"] as? JsonObject)
        val customProperties = readStringMap(config["properties"] as? JsonObject)
        val delayMs = config["delayMs"]
            ?.jsonPrimitive
            ?.longOrNull
            ?: 0L

        val properties = Properties()

        properties[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = hosts.joinToString(",")
        properties[ProducerConfig.ACKS_CONFIG] = "all"

        customProperties.forEach { (key, value) ->
            properties[key] = value
        }

        properties[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java.name
        properties[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = ByteArraySerializer::class.java.name

        KafkaProducer<String, ByteArray>(properties).use { producer ->
            val record = ProducerRecord(
                topic,
                key,
                value
            )

            headers.forEach { (headerName, headerValue) ->
                record.headers().add(
                    headerName,
                    headerValue.toByteArray(Charsets.UTF_8)
                )
            }

            val metadata = producer.send(record).get()

            producer.flush()

            if (delayMs > 0) {
                Thread.sleep(delayMs)
            }

            return IntegrationResult(
                name = name,
                type = type,
                status = 200,
                response = buildJsonObject {
                    put("topic", metadata.topic())
                    put("partition", metadata.partition())
                    put("offset", metadata.offset())
                    put("timestamp", metadata.timestamp())
                    put("serializedKeySize", metadata.serializedKeySize())
                    put("serializedValueSize", metadata.serializedValueSize())
                    put("messageFormat", messageFormat)
                    put("delayMs", delayMs)
                }
            )
        }
    }

    private fun encodeAvro(
        name: String,
        config: JsonObject,
        message: JsonElement
    ): ByteArray {
        val schemaPath = config["avroSchemaFile"]
            ?.jsonPrimitive
            ?.contentOrNull
            ?: error("У Kafka Avro-интеграции $name не указан avroSchemaFile")

        val schemaFile = resolve(schemaPath)

        if (!schemaFile.exists()) {
            error("Avro schema file не найден: ${schemaFile.path}")
        }

        val schema = Schema.Parser().parse(schemaFile)
        val value = AvroJsonConverter.convert(message, schema) as? GenericRecord
            ?: error("Avro message для $name должен быть RECORD")

        val output = ByteArrayOutputStream()
        val writer = GenericDatumWriter<GenericRecord>(schema)
        val encoder = EncoderFactory.get().binaryEncoder(output, null)

        writer.write(value, encoder)
        encoder.flush()

        return output.toByteArray()
    }

    private fun resolve(path: String): File {
        val file = File(path)

        return if (file.isAbsolute) {
            file
        } else {
            File(baseDir, path)
        }
    }

    private fun readStringList(
        element: JsonElement?,
        fieldName: String
    ): List<String> {
        val array = element as? JsonArray
            ?: error("Поле $fieldName должно быть массивом строк")

        val values = array.map {
            it.jsonPrimitive.contentOrNull
                ?: error("Поле $fieldName должно быть массивом строк")
        }

        if (values.isEmpty()) {
            error("Поле $fieldName не должно быть пустым")
        }

        return values
    }

    private fun readStringMap(obj: JsonObject?): Map<String, String> {
        if (obj == null) {
            return emptyMap()
        }

        return obj.mapValues { (_, value) ->
            jsonToString(value)
        }
    }

    private fun jsonToString(element: JsonElement): String {
        return when (element) {
            is JsonPrimitive -> element.contentOrNull ?: element.toString()
            else -> element.toString()
        }
    }
}