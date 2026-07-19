package ru.course.apitesting.web

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.netty.Netty
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.course.apitesting.config.ConfigLoader
import ru.course.apitesting.config.RunConfig
import ru.course.apitesting.exec.TestRunner
import ru.course.apitesting.http.HttpClientFactory
import ru.course.apitesting.http.HttpExecutor
import ru.course.apitesting.integration.core.IntegrationEngine
import ru.course.apitesting.integration.http.HttpIntegrationExecutor
import ru.course.apitesting.integration.kafka.KafkaConsumeIntegrationExecutor
import ru.course.apitesting.integration.kafka.KafkaIntegrationExecutor
import ru.course.apitesting.integration.mock.MockIntegrationExecutor
import ru.course.apitesting.report.JUnitXmlWriter
import ru.course.apitesting.report.ReportWriter
import ru.course.apitesting.report.TestResult
import ru.course.apitesting.validate.ContractValidator
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object WebReportServer {
    fun start(results: List<TestResult>, port: Int = 8080) {
        val json = Json {
            prettyPrint = true
            encodeDefaults = true
            ignoreUnknownKeys = true
            isLenient = true
        }

        var currentResults = results

        val server = embeddedServer(Netty, port = port) {
            routing {
                get("/api/results") {
                    call.respondText(
                        text = json.encodeToString(currentResults),
                        contentType = ContentType.Application.Json
                    )
                }

                post("/api/run") {
                    val body = call.receiveText()

                    val runConfig = try {
                        json.decodeFromString(
                            RunConfig.serializer(),
                            body
                        )
                    } catch (e: Throwable) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            "JSON конфига невалидный: ${e.message}"
                        )
                        return@post
                    }

                    val runBaseDir = File("configs").absoluteFile
                    val outDir = nextUiRunOutDir()

                    try {
                        val httpClient = HttpClientFactory.create(runConfig.timeoutMs)

                        val executor = HttpExecutor(
                            client = httpClient,
                            baseUrl = runConfig.baseUrl
                        )

                        val integrationEngine = IntegrationEngine(
                            integrations = runConfig.integrations,
                            executors = listOf(
                                MockIntegrationExecutor(),
                                HttpIntegrationExecutor(httpClient),
                                KafkaIntegrationExecutor(runBaseDir),
                                KafkaConsumeIntegrationExecutor(runBaseDir)
                            )
                        )

                        val runner = TestRunner(
                            loader = ConfigLoader(),
                            executor = executor,
                            validator = ContractValidator(),
                            baseDir = runBaseDir,
                            integrationEngine = integrationEngine
                        )

                        val runResults = runner.runAll(runConfig.tests)

                        ReportWriter.writeJson(outDir, runResults)
                        JUnitXmlWriter.write(outDir, runResults)

                        currentResults = runResults

                        val total = runResults.size
                        val passed = runResults.count { it.passed }
                        val failed = total - passed

                        val responseText = """
                            {
                              "ok": true,
                              "outDir": ${json.encodeToString(outDir)},
                              "total": $total,
                              "passed": $passed,
                              "failed": $failed,
                              "results": ${json.encodeToString(runResults)}
                            }
                        """.trimIndent()

                        call.respondText(
                            text = responseText,
                            contentType = ContentType.Application.Json
                        )
                    } catch (e: Throwable) {
                        val responseText = """
                            {
                              "ok": false,
                              "error": ${json.encodeToString(e.message ?: e::class.simpleName ?: "Ошибка запуска")}
                            }
                        """.trimIndent()

                        call.respondText(
                            status = HttpStatusCode.InternalServerError,
                            text = responseText,
                            contentType = ContentType.Application.Json
                        )
                    }
                }

                get("/api/files/{testId}/{index}") {
                    val testId = call.parameters["testId"]
                    val index = call.parameters["index"]?.toIntOrNull()

                    if (testId == null || index == null) {
                        call.respond(HttpStatusCode.BadRequest)
                        return@get
                    }

                    val transfer = currentResults
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

    private fun nextUiRunOutDir(): String {
        val timestamp = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))

        return File("build/reports/ui-runs/$timestamp").path
    }
}
