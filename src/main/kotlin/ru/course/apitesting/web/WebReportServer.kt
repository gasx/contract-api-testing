package ru.course.apitesting.web

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.netty.Netty
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.course.apitesting.report.TestResult
import java.io.File

object WebReportServer {
    fun start(results: List<TestResult>, port: Int = 8080) {
        val json = Json {
            prettyPrint = true
            encodeDefaults = true
        }

        val server = embeddedServer(Netty, port = port) {
            routing {
                get("/api/results") {
                    call.respondText(
                        text = json.encodeToString(results),
                        contentType = ContentType.Application.Json
                    )
                }

                get("/api/files/{testId}/{index}") {
                    val testId = call.parameters["testId"]
                    val index = call.parameters["index"]?.toIntOrNull()

                    if (testId == null || index == null) {
                        call.respond(HttpStatusCode.BadRequest)
                        return@get
                    }

                    val transfer = results
                        .firstOrNull { it.testId == testId }
                        ?.fileTransfers
                        ?.getOrNull(index)

                    val localPath = transfer?.localPath

                    if (
                        transfer == null ||
                        transfer.direction != "RECEIVED" ||
                        localPath.isNullOrBlank()
                    ) {
                        call.respond(HttpStatusCode.NotFound)
                        return@get
                    }

                    val file = File(localPath)

                    if (!file.isFile) {
                        call.respond(HttpStatusCode.NotFound)
                        return@get
                    }

                    call.respondFile(file)
                }

                staticResources(
                    remotePath = "/",
                    basePackage = "web",
                    index = "index.html"
                )
            }
        }

        println()
        println("Веб-интерфейс отчёта: http://localhost:$port")
        println("Для остановки нажмите Ctrl+C")

        server.start(wait = true)
    }
}