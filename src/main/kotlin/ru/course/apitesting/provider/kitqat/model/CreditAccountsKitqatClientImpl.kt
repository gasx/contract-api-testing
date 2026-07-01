package ru.course.apitesting.provider.kitqat.model

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import ru.course.apitesting.provider.kitqat.model.CreditAccountsKitqatPoolResponse
import ru.course.apitesting.provider.kitqat.model.KitqatPoolRequest

class CreditAccountsKitqatClientImpl(
    private val baseUrl: String,
    private val qaUtilsToken: String,
    private val httpClient: HttpClient,
    private val json: Json,
) : CreditAccountsKitqatClient {

    override fun getDataFromPool(kitqatPoolRequest: KitqatPoolRequest): CreditAccountsKitqatPoolResponse {
        return runBlocking {
            val url = "$baseUrl/$POOL_TEMPLATE_DATA_TAKE"

            val requestBody = json.encodeToString(KitqatPoolRequest.serializer(), kitqatPoolRequest)

            val response = httpClient.post(url) {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $qaUtilsToken")
                setBody(requestBody)
            }

            if (!response.status.isSuccess()) {
                throw IllegalStateException("Failed to get data from Kitqat pool: ${response.status}")
            }

            val responseBody: String = response.body()
            val parsedResponse = json.decodeFromString<CreditAccountsKitqatPoolResponse>(responseBody)

            println("Kitqat user: $parsedResponse")
            parsedResponse
        }
    }

    companion object {
        private const val POOL_TEMPLATE_DATA_TAKE = "pool/templates/data/take"
    }
}