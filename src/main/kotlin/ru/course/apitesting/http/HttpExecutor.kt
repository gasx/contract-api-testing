package ru.course.apitesting.http

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import ru.course.apitesting.config.ApiTestCase
import ru.course.apitesting.report.FileTransferInfo
import java.io.File

class HttpExecutor(
    private val client: HttpClient,
    private val baseUrl: String
) {
    suspend fun executeAsync(tc: ApiTestCase): HttpResult {
        return try {
            val url = baseUrl.trimEnd('/') + tc.path

            val response: HttpResponse = client.request(url) {
                method = HttpMethod.parse(tc.method.uppercase())

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
                                                            part.contentType
                                                                ?: "application/octet-stream"
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
}