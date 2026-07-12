package ru.course.apitesting.integration.kafka

import ru.course.apitesting.integration.avro.AvroJsonConverter
import ru.course.apitesting.integration.core.IntegrationContext
import ru.course.apitesting.integration.core.IntegrationExecutor
import ru.course.apitesting.integration.core.IntegrationResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import org.apache.avro.Schema
import org.apache.avro.generic.GenericDatumReader
import org.apache.avro.generic.GenericRecord
import org.apache.avro.io.DecoderFactory
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringDeserializer
import java.io.File
import java.time.Duration
import java.util.Properties
import java.util.UUID

class KafkaConsumeIntegrationExecutor(
    private val baseDir: File
) : IntegrationExecutor {
    override val type: String = "kafka-consume"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override fun execute(
        name: String,
        config: JsonObject,
        context: IntegrationContext
    ): IntegrationResult {
        val hosts = readStringList(config["hosts"], "hosts")

        val topic = config["topic"]
            ?.jsonPrimitive
            ?.contentOrNull
            ?: error("У Kafka consume-интеграции $name не указан topic")

        val partition = config["partition"]
            ?.jsonPrimitive
            ?.intOrNull

        val offset = config["offset"]
            ?.jsonPrimitive
            ?.longOrNull

        val expectedKey = config["key"]?.let {
            jsonToString(it)
        }

        val expectedHeaders = readStringMap(config["headers"] as? JsonObject)

        val timeoutMs = config["timeoutMs"]
            ?.jsonPrimitive
            ?.longOrNull
            ?: 10000L

        val messageFormat = config["messageFormat"]
            ?.jsonPrimitive
            ?.contentOrNull
            ?: "json"

        val groupId = config["groupId"]
            ?.jsonPrimitive
            ?.contentOrNull
            ?: "contract-api-testing-${UUID.randomUUID()}"

        if (offset != null && partition == null) {
            error("Kafka consume-интеграция $name: offset требует partition")
        }

        val properties = Properties()

        properties[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = hosts.joinToString(",")
        properties[ConsumerConfig.GROUP_ID_CONFIG] = groupId
        properties[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
        properties[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = "false"

        readStringMap(config["properties"] as? JsonObject).forEach { (key, value) ->
            properties[key] = value
        }

        properties[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java.name
        properties[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = ByteArrayDeserializer::class.java.name

        KafkaConsumer<String, ByteArray>(properties).use { consumer ->
            if (partition != null) {
                val topicPartition = TopicPartition(topic, partition)
                consumer.assign(listOf(topicPartition))

                if (offset != null) {
                    consumer.seek(topicPartition, offset)
                } else {
                    consumer.seekToBeginning(listOf(topicPartition))
                }
            } else {
                consumer.subscribe(listOf(topic))
            }

            val record = pollRecord(
                consumer = consumer,
                topic = topic,
                partition = partition,
                offset = offset,
                expectedKey = expectedKey,
                expectedHeaders = expectedHeaders,
                timeoutMs = timeoutMs
            ) ?: error("Kafka consume-интеграция $name не нашла сообщение topic=$topic partition=${partition ?: "-"} offset=${offset ?: "-"} за ${timeoutMs}ms")

            val valueJson = decodeValue(
                name = name,
                config = config,
                messageFormat = messageFormat,
                bytes = record.value()
            )

            return IntegrationResult(
                name = name,
                type = type,
                status = 200,
                response = buildJsonObject {
                    put("topic", record.topic())
                    put("partition", record.partition())
                    put("offset", record.offset())
                    put("timestamp", record.timestamp())
                    put("key", record.key())
                    put("messageFormat", messageFormat)
                    put("value", valueJson)
                }
            )
        }
    }

    private fun pollRecord(
        consumer: KafkaConsumer<String, ByteArray>,
        topic: String,
        partition: Int?,
        offset: Long?,
        expectedKey: String?,
        expectedHeaders: Map<String, String>,
        timeoutMs: Long
    ): ConsumerRecord<String, ByteArray>? {
        val deadline = System.currentTimeMillis() + timeoutMs

        while (System.currentTimeMillis() < deadline) {
            val remaining = deadline - System.currentTimeMillis()
            val pollMs = remaining.coerceAtMost(500L)

            val records = consumer.poll(Duration.ofMillis(pollMs))

            records.forEach { record ->
                if (record.topic() != topic) {
                    return@forEach
                }

                if (partition != null && record.partition() != partition) {
                    return@forEach
                }

                if (offset != null && record.offset() != offset) {
                    return@forEach
                }

                if (expectedKey != null && record.key() != expectedKey) {
                    return@forEach
                }

                if (!headersMatch(record, expectedHeaders)) {
                    return@forEach
                }

                return record
            }
        }

        return null
    }

    private fun headersMatch(
        record: ConsumerRecord<String, ByteArray>,
        expectedHeaders: Map<String, String>
    ): Boolean {
        expectedHeaders.forEach { (name, expectedValue) ->
            val header = record.headers().lastHeader(name)
                ?: return false

            val actualValue = String(header.value(), Charsets.UTF_8)

            if (actualValue != expectedValue) {
                return false
            }
        }

        return true
    }

    private fun decodeValue(
        name: String,
        config: JsonObject,
        messageFormat: String,
        bytes: ByteArray
    ): JsonElement {
        return when (messageFormat.lowercase()) {
            "json" -> decodeJson(bytes)
            "string" -> JsonPrimitive(String(bytes, Charsets.UTF_8))
            "avro" -> decodeAvro(name, config, bytes)
            else -> error("Kafka consume-интеграция $name имеет неизвестный messageFormat=$messageFormat")
        }
    }

    private fun decodeJson(bytes: ByteArray): JsonElement {
        val text = String(bytes, Charsets.UTF_8)

        return try {
            json.parseToJsonElement(text)
        } catch (e: Throwable) {
            JsonPrimitive(text)
        }
    }

    private fun decodeAvro(
        name: String,
        config: JsonObject,
        bytes: ByteArray
    ): JsonElement {
        val schemaPath = config["avroSchemaFile"]
            ?.jsonPrimitive
            ?.contentOrNull
            ?: error("У Kafka Avro consume-интеграции $name не указан avroSchemaFile")

        val schemaFile = resolve(schemaPath)

        if (!schemaFile.exists()) {
            error("Avro schema file не найден: ${schemaFile.path}")
        }

        val schema = Schema.Parser().parse(schemaFile)
        val reader = GenericDatumReader<GenericRecord>(schema)
        val decoder = DecoderFactory.get().binaryDecoder(bytes, null)
        val record = reader.read(null, decoder)

        return AvroJsonConverter.toJson(record, schema)
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