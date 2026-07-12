package ru.course.apitesting.integration.kitqat

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import ru.course.apitesting.integration.core.IntegrationContext
import ru.course.apitesting.integration.core.IntegrationExecutor
import ru.course.apitesting.integration.core.IntegrationResult

class KitqatIntegrationExecutor(
    private val httpClient: HttpClient
) : IntegrationExecutor {
    override val type: String = "kitqat"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    override fun execute(
        name: String,
        config: JsonObject,
        context: IntegrationContext
    ): IntegrationResult {
        return runBlocking {
            val baseUrl = readString(config, "baseUrl")
                ?: readEnv(config, "baseUrlEnv")
                ?: error("У Kitqat-интеграции $name не указан baseUrl или baseUrlEnv")

            val token = readString(config, "token")
                ?: readEnv(config, "tokenEnv")
                ?: error("У Kitqat-интеграции $name не указан token или tokenEnv")

            val endpoint = readString(config, "endpoint")
                ?: "/pool/templates/data/take"

            val templateName = readString(config, "templateName")
                ?: readString(config, "pool")?.let { KitqatPool.fromCode(it).templateName }
                ?: error("У Kitqat-интеграции $name не указан pool или templateName")

            val productType = readString(config, "product")
                ?.let { KitqatProductType.fromCode(it) }

            val count = readInt(config, "count") ?: 1
            val clientIndex = readInt(config, "clientIndex") ?: 0
            val productIndex = readInt(config, "productIndex") ?: 0
            val deleteFromPool = readBoolean(config, "deleteFromPool", false)
            val expectedStatus = readInt(config, "expectedStatus") ?: 200
            val failOnStatus = readBoolean(config, "failOnStatus", true)

            val url = baseUrl.trimEnd('/') + "/" + endpoint.trimStart('/')

            val requestBody = buildJsonObject {
                put("templateName", templateName)
                put("count", count)
                put("deleteFromPool", deleteFromPool)
            }

            val response = httpClient.request(url) {
                method = HttpMethod.Post
                header(HttpHeaders.Accept, "*/*")
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(requestBody.toString())
            }

            val status = response.status.value
            val responseText = response.bodyAsText()
            val statusOk = status == expectedStatus

            if (!statusOk) {
                return@runBlocking IntegrationResult(
                    name = name,
                    type = type,
                    status = status,
                    response = parseJson(responseText),
                    error = if (failOnStatus) {
                        "Kitqat expected status $expectedStatus but got $status"
                    } else {
                        null
                    }
                )
            }

            val responseDto = json.decodeFromString(
                CreditAccountsKitqatPoolResponse.serializer(),
                responseText
            )

            val client = responseDto.data
                .getOrNull(clientIndex)
                ?.client
                ?: error("Kitqat не вернул клиента с clientIndex=$clientIndex")

            val selectedProduct = productType?.selectRequired(
                client = client,
                index = productIndex
            )

            val vars = buildVars(
                client = client,
                selectedProduct = selectedProduct
            )

            val normalizedResponse = buildJsonObject {
                put(
                    "raw",
                    json.encodeToJsonElement(
                        CreditAccountsKitqatPoolResponse.serializer(),
                        responseDto
                    )
                )

                put(
                    "client",
                    json.encodeToJsonElement(
                        CreditAccountsKitqatClientData.serializer(),
                        client
                    )
                )

                if (selectedProduct != null) {
                    put(
                        "product",
                        json.encodeToJsonElement(
                            KitqatSelectedProductDto.serializer(),
                            selectedProduct
                        )
                    )
                }
            }

            IntegrationResult(
                name = name,
                type = type,
                status = status,
                response = normalizedResponse,
                vars = vars
            )
        }
    }

    private fun buildVars(
        client: CreditAccountsKitqatClientData,
        selectedProduct: KitqatSelectedProductDto?
    ): Map<String, JsonElement> {
        val vars = linkedMapOf<String, JsonElement>()

        vars["siebelId"] = JsonPrimitive(client.siebelId)
        vars["phone"] = JsonPrimitive(client.phone)

        if (selectedProduct != null) {
            vars["productType"] = JsonPrimitive(selectedProduct.type)
            vars["productIndex"] = JsonPrimitive(selectedProduct.index)

            selectedProduct.productName?.let {
                vars["productName"] = JsonPrimitive(it)
            }

            selectedProduct.contractNumber?.let {
                vars["contractNumber"] = JsonPrimitive(it)
            }

            selectedProduct.creditContractNumber?.let {
                vars["creditContractNumber"] = JsonPrimitive(it)
            }

            selectedProduct.currentContractNumber?.let {
                vars["currentContractNumber"] = JsonPrimitive(it)
            }

            selectedProduct.card?.cardNum?.let {
                vars["cardNum"] = JsonPrimitive(it)
            }

            selectedProduct.card?.ucid?.let {
                vars["ucid"] = JsonPrimitive(it)
            }

            selectedProduct.card?.cvv?.let {
                vars["cvv"] = JsonPrimitive(it)
            }

            selectedProduct.card?.expdate?.let {
                vars["expdate"] = JsonPrimitive(it)
            }

            selectedProduct.loanAccount?.contractNumber?.let {
                vars["loanAccountContractNumber"] = JsonPrimitive(it)
            }

            selectedProduct.loanAccount?.card?.cardNum?.let {
                vars["loanAccountCardNum"] = JsonPrimitive(it)
            }

            selectedProduct.loanAccount?.card?.ucid?.let {
                vars["loanAccountUcid"] = JsonPrimitive(it)
            }

            selectedProduct.roleId?.let {
                vars["roleId"] = JsonPrimitive(it)
            }

            selectedProduct.status?.let {
                vars["status"] = JsonPrimitive(it)
            }

            selectedProduct.accountType?.let {
                vars["accountType"] = JsonPrimitive(it)
            }

            if (selectedProduct.scopes.isNotEmpty()) {
                vars["scopes"] = kotlinx.serialization.json.JsonArray(
                    selectedProduct.scopes.map { JsonPrimitive(it) }
                )
            }

            selectedProduct.ownerSiebelId?.let {
                vars["ownerSiebelId"] = JsonPrimitive(it)
            }

            selectedProduct.ownerPhone?.let {
                vars["ownerPhone"] = JsonPrimitive(it)
            }

            selectedProduct.recipientSiebelId?.let {
                vars["recipientSiebelId"] = JsonPrimitive(it)
            }

            selectedProduct.recipientPhone?.let {
                vars["recipientPhone"] = JsonPrimitive(it)
            }
        }

        return vars
    }

    private fun parseJson(text: String): JsonElement {
        return try {
            json.parseToJsonElement(text)
        } catch (e: Throwable) {
            JsonPrimitive(text)
        }
    }

    private fun readString(
        config: JsonObject,
        field: String
    ): String? {
        return config[field]
            ?.jsonPrimitive
            ?.contentOrNull
    }

    private fun readEnv(
        config: JsonObject,
        field: String
    ): String? {
        val envName = readString(config, field) ?: return null
        return System.getenv(envName)
            ?: error("Переменная окружения не найдена: $envName")
    }

    private fun readInt(
        config: JsonObject,
        field: String
    ): Int? {
        return config[field]
            ?.jsonPrimitive
            ?.intOrNull
    }

    private fun readBoolean(
        config: JsonObject,
        field: String,
        defaultValue: Boolean
    ): Boolean {
        val primitive = config[field]?.jsonPrimitive ?: return defaultValue

        primitive.booleanOrNull?.let {
            return it
        }

        return when (primitive.contentOrNull?.lowercase()) {
            "true" -> true
            "false" -> false
            else -> defaultValue
        }
    }
}