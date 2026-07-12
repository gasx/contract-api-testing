package ru.course.apitesting.integration

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import java.util.Properties

class KafkaIntegrationExecutor : IntegrationExecutor {
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

        val value = jsonToString(message)
        val headers = readStringMap(config["headers"] as? JsonObject)
        val customProperties = readStringMap(config["properties"] as? JsonObject)
        val delayMs = config["delayMs"]
            ?.jsonPrimitive
            ?.longOrNull
            ?: 0L

        val properties = Properties()

        properties[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = hosts.joinToString(",")
        properties[ProducerConfig.ACKS_CONFIG] = "all"
        properties[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java.name
        properties[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java.name

        customProperties.forEach { (key, value) ->
            properties[key] = value
        }

        properties[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java.name
        properties[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java.name

        KafkaProducer<String, String>(properties).use { producer ->
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
                    put("delayMs", delayMs)
                }
            )
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