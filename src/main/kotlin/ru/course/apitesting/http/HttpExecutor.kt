package ru.course.apitesting.http

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import ru.course.apitesting.config.ApiTestCase
import ru.course.apitesting.report.FileTransferInfo
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class HttpExecutor(
    private val client: HttpClient,
    private val baseUrl: String
) {
    suspend fun executeAsync(tc: ApiTestCase): HttpResult {
        val requestUrl = baseUrl.trimEnd('/') + tc.path
        val fullUrl = buildFullUrl(tc)
        val curl = buildCurl(tc, fullUrl)

        printRequestLog(
            testId = tc.testId,
            method = tc.method,
            fullUrl = fullUrl,
            curl = curl
        )

        return try {
            val response: HttpResponse = client.request(requestUrl) {
                method = HttpMethod.parse(tc.method.uppercase())

                tc.query.forEach { (key, value) ->
                    parameter(key, value)
                }

                tc.headers.forEach { (key, value) ->
                    if (
                        tc.multipart.isEmpty() ||
                        !key.equals(HttpHeaders.ContentType, ignoreCase = true)
                    ) {
                        header(key, value)
                    }
                }

                when {
                    tc.multipart.isNotEmpty() -> {
                        setBody(
                            MultiPartFormDataContent(
                                formData {
                                    tc.multipart.forEach { part ->
                                        when {
                                            !part.filePath.isNullOrBlank() -> {
                                                val file = File(part.filePath)

                                                require(file.isFile) {
                                                    "Файл не найден: ${file.absolutePath}"
                                                }

                                                append(
                                                    key = part.name,
                                                    value = file.readBytes(),
                                                    headers = Headers.build {
                                                        append(
                                                            HttpHeaders.ContentType,
                                                            part.contentType ?: "application/octet-stream"
                                                        )
                                                        append(
                                                            HttpHeaders.ContentDisposition,
                                                            "filename=\"${part.fileName ?: file.name}\""
                                                        )
                                                    }
                                                )
                                            }

                                            part.value != null -> {
                                                append(part.name, part.value)
                                            }

                                            else -> {
                                                error(
                                                    "Для multipart-поля '${part.name}' " +
                                                            "нужно указать value или filePath"
                                                )
                                            }
                                        }
                                    }
                                }
                            )
                        )
                    }

                    tc.body != null -> {
                        if (tc.headers.keys.none { it.equals(HttpHeaders.ContentType, ignoreCase = true) }) {
                            contentType(ContentType.Application.Json)
                        }

                        setBody(tc.body.toString())
                    }
                }
            }

            val contentType = response.headers[HttpHeaders.ContentType]

            if (tc.downloadTo != null) {
                val bytes: ByteArray = response.body()
                val outputFile = File(tc.downloadTo)

                val downloadedFile =
                    if (response.status.value == tc.expectedStatus) {
                        outputFile.parentFile?.mkdirs()
                        outputFile.writeBytes(bytes)

                        FileTransferInfo(
                            direction = "RECEIVED",
                            fileName = outputFile.name,
                            contentType = contentType,
                            sizeBytes = outputFile.length(),
                            localPath = outputFile.absolutePath
                        )
                    } else {
                        null
                    }

                HttpResult(
                    ok = true,
                    status = response.status.value,
                    bodyBytes = bytes,
                    contentType = contentType,
                    downloadedFile = downloadedFile
                )
            } else {
                HttpResult(
                    ok = true,
                    status = response.status.value,
                    bodyText = response.bodyAsText(),
                    contentType = contentType
                )
            }
        } catch (e: Exception) {
            HttpResult(
                ok = false,
                status = null,
                error = e.message ?: (e::class.simpleName ?: "error")
            )
        }
    }

    fun execute(tc: ApiTestCase): HttpResult {
        return kotlinx.coroutines.runBlocking {
            executeAsync(tc)
        }
    }

    private fun buildFullUrl(tc: ApiTestCase): String {
        val url = baseUrl.trimEnd('/') + tc.path

        if (tc.query.isEmpty()) {
            return url
        }

        val queryString = tc.query.entries.joinToString("&") { (key, value) ->
            encodeQuery(key) + "=" + encodeQuery(value)
        }

        return "$url?$queryString"
    }

    private fun encodeQuery(value: String): String {
        return URLEncoder
            .encode(value, StandardCharsets.UTF_8)
            .replace("+", "%20")
    }

    private fun buildCurl(
        tc: ApiTestCase,
        fullUrl: String
    ): String {
        val parts = mutableListOf<String>()

        parts += "curl"
        parts += "-i"
        parts += "-X"
        parts += shellQuote(tc.method.uppercase())
        parts += shellQuote(fullUrl)

        val headers = linkedMapOf<String, String>()
        headers += tc.headers

        if (
            tc.body != null &&
            headers.keys.none { it.equals(HttpHeaders.ContentType, ignoreCase = true) }
        ) {
            headers[HttpHeaders.ContentType] = "application/json"
        }

        headers.forEach { (key, value) ->
            parts += "-H"
            parts += shellQuote("$key: $value")
        }

        tc.multipart.forEach { part ->
            when {
                !part.filePath.isNullOrBlank() -> {
                    val contentType = part.contentType ?: "application/octet-stream"
                    val fileName = part.fileName ?: File(part.filePath).name

                    parts += "-F"
                    parts += shellQuote("${part.name}=@${part.filePath};filename=$fileName;type=$contentType")
                }

                part.value != null -> {
                    parts += "-F"
                    parts += shellQuote("${part.name}=${part.value}")
                }
            }
        }

        if (tc.multipart.isEmpty() && tc.body != null) {
            parts += "--data-raw"
            parts += shellQuote(tc.body.toString())
        }

        return parts.joinToString(" \\\n  ")
    }

    private fun shellQuote(value: String): String {
        return "'" + value.replace("'", "'\"'\"'") + "'"
    }

    private fun printRequestLog(
        testId: String,
        method: String,
        fullUrl: String,
        curl: String
    ) {
        println()
        println("============================================================")
        println("ЗАПРОС ПРОВЕРЯЕМОЙ РУЧКИ: $testId")
        println("${method.uppercase()} $fullUrl")
        println("curl:")
        println(curl)
        println("============================================================")
        println()
    }
}
