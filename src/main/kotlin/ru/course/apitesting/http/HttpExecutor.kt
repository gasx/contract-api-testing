package ru.course.apitesting.http

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import ru.course.apitesting.config.ApiTestCase

class HttpExecutor(
    private val client: HttpClient,
    private val baseUrl: String
) {
    suspend fun executeAsync(tc: ApiTestCase): HttpResult {
        return try {
            val url = baseUrl.trimEnd('/') + tc.path
            val resp: HttpResponse = client.request(url) {
                method = io.ktor.http.HttpMethod.parse(tc.method.uppercase())
                tc.headers.forEach { (k, v) -> header(k, v) }
                if (tc.body != null) {
                    setBody(tc.body.toString())
                }            }
            val body = resp.bodyAsText()
            HttpResult(ok = true, status = resp.status.value, bodyText = body, error = null)
        } catch (e: Exception) {
            HttpResult(ok = false, status = null, bodyText = null, error = e.message ?: (e::class.simpleName ?: "error"))
        }
    }

    fun execute(tc: ApiTestCase): HttpResult {
        return kotlinx.coroutines.runBlocking { executeAsync(tc) }
    }
}
