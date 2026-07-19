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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.course.apitesting.config.ConfigLoader
import ru.course.apitesting.config.RunConfigReader
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
import java.time.Instant

object WebReportServer {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun start(results: List<TestResult>, port: Int = 8080) {
        var currentResults = results

        val server = embeddedServer(Netty, port = port) {
            routing {
                get("/api/results") {
                    call.respondText(
                        text = json.encodeToString(
                            serializer = kotlinx.serialization.builtins.ListSerializer(TestResult.serializer()),
                            value = currentResults
                        ),
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

                get("/api/contracts") {
                    val response = try {
                        ContractListResponse(
                            ok = true,
                            contracts = listContracts()
                        )
                    } catch (e: Throwable) {
                        ContractListResponse(
                            ok = false,
                            contracts = emptyList(),
                            error = e.message ?: e::class.simpleName
                        )
                    }

                    call.respondText(
                        text = json.encodeToString(ContractListResponse.serializer(), response),
                        contentType = ContentType.Application.Json
                    )
                }

                get("/api/contracts/content") {
                    val path = call.request.queryParameters["path"]

                    if (path.isNullOrBlank()) {
                        call.respond(HttpStatusCode.BadRequest, "Не указан path")
                        return@get
                    }

                    val file = contractFileByPath(path)

                    if (!file.isFile) {
                        call.respond(HttpStatusCode.NotFound, "Контракт не найден")
                        return@get
                    }

                    call.respondText(
                        text = file.readText(),
                        contentType = ContentType.Application.Json
                    )
                }

                post("/api/contracts") {
                    val body = call.receiveText()

                    val response = try {
                        val request = json.decodeFromString(
                            ContractSaveRequest.serializer(),
                            body
                        )

                        val savedPath = saveContract(request)

                        ContractSaveResponse(
                            ok = true,
                            path = savedPath
                        )
                    } catch (e: Throwable) {
                        ContractSaveResponse(
                            ok = false,
                            error = e.message ?: e::class.simpleName
                        )
                    }

                    val status = if (response.ok) {
                        HttpStatusCode.OK
                    } else {
                        HttpStatusCode.BadRequest
                    }

                    call.respondText(
                        text = json.encodeToString(ContractSaveResponse.serializer(), response),
                        contentType = ContentType.Application.Json,
                        status = status
                    )
                }

                post("/api/run") {
                    val body = call.receiveText()

                    val response = try {
                        val runFile = File("configs/_ui_run_from_builder.json").absoluteFile
                        runFile.parentFile?.mkdirs()
                        runFile.writeText(body)

                        val runConfig = RunConfigReader().read(
                            runFile = runFile,
                            profileName = null,
                            profilesFile = null
                        )

                        val runFileDir = runFile.parentFile ?: File(".").absoluteFile

                        val httpClient = HttpClientFactory.create(runConfig.timeoutMs)
                        val executor = HttpExecutor(httpClient, runConfig.baseUrl)
                        val validator = ContractValidator()

                        val integrationEngine = IntegrationEngine(
                            integrations = runConfig.integrations,
                            executors = listOf(
                                MockIntegrationExecutor(),
                                HttpIntegrationExecutor(httpClient),
                                KafkaIntegrationExecutor(runFileDir),
                                KafkaConsumeIntegrationExecutor(runFileDir)
                            )
                        )

                        val runner = TestRunner(
                            loader = ConfigLoader(),
                            executor = executor,
                            validator = validator,
                            baseDir = runFileDir,
                            integrationEngine = integrationEngine
                        )

                        val newResults = runner.runAll(runConfig.tests)
                        currentResults = newResults

                        val outDir = "build/reports"
                        ReportWriter.writeJson(outDir, newResults)
                        JUnitXmlWriter.write(outDir, newResults)

                        UiRunResponse(
                            ok = true,
                            total = newResults.size,
                            passed = newResults.count { it.passed },
                            failed = newResults.count { !it.passed },
                            outDir = outDir
                        )
                    } catch (e: Throwable) {
                        UiRunResponse(
                            ok = false,
                            error = e.message ?: e::class.simpleName
                        )
                    }

                    val status = if (response.ok) {
                        HttpStatusCode.OK
                    } else {
                        HttpStatusCode.BadRequest
                    }

                    call.respondText(
                        text = json.encodeToString(UiRunResponse.serializer(), response),
                        contentType = ContentType.Application.Json,
                        status = status
                    )
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
        println("Конструктор: http://localhost:$port/builder.html")
        println("Для остановки нажмите Ctrl+C")

        server.start(wait = true)
    }

    private fun listContracts(): List<ContractInfo> {
        val configsDir = File("configs").canonicalFile
        val schemasDir = File(configsDir, "schemas").canonicalFile

        if (!schemasDir.exists()) {
            schemasDir.mkdirs()
        }

        return schemasDir
            .walkTopDown()
            .filter { it.isFile }
            .filter { it.extension.equals("json", ignoreCase = true) }
            .map { file ->
                val canonicalFile = file.canonicalFile
                val relativePath = configsDir
                    .toPath()
                    .relativize(canonicalFile.toPath())
                    .toString()
                    .replace(File.separatorChar, '/')

                ContractInfo(
                    path = relativePath,
                    fileName = canonicalFile.name,
                    sizeBytes = canonicalFile.length(),
                    modifiedAt = Instant.ofEpochMilli(canonicalFile.lastModified()).toString()
                )
            }
            .sortedBy { it.path }
            .toList()
    }

    private fun saveContract(request: ContractSaveRequest): String {
        val content = request.content.trim()

        if (content.isBlank()) {
            error("Файл контракта пустой")
        }

        json.parseToJsonElement(content)

        val targetPath = if (!request.path.isNullOrBlank()) {
            normalizeContractPath(request.path)
        } else {
            val safeFileName = sanitizeFileName(
                request.fileName ?: "contract.schema.json"
            )

            "schemas/uploaded/$safeFileName"
        }

        val targetFile = contractFileByPath(targetPath)
        targetFile.parentFile?.mkdirs()
        targetFile.writeText(content)

        return targetPath
    }

    private fun contractFileByPath(path: String): File {
        val configsDir = File("configs").canonicalFile
        val normalizedPath = normalizeContractPath(path)
        val targetFile = File(configsDir, normalizedPath).canonicalFile

        val configsPath = configsDir.path + File.separator

        if (!targetFile.path.startsWith(configsPath)) {
            error("Недопустимый путь контракта")
        }

        return targetFile
    }

    private fun normalizeContractPath(path: String): String {
        val normalized = path
            .trim()
            .replace('\\', '/')
            .removePrefix("/")
            .removePrefix("configs/")

        if (!normalized.startsWith("schemas/")) {
            error("Контракт должен лежать внутри configs/schemas")
        }

        if (normalized.split("/").any { it == ".." }) {
            error("Недопустимый путь контракта")
        }

        if (!normalized.endsWith(".json", ignoreCase = true)) {
            error("Контракт должен быть JSON-файлом")
        }

        return normalized
    }

    private fun sanitizeFileName(fileName: String): String {
        val rawName = fileName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .ifBlank { "contract.schema.json" }

        val safeName = rawName.replace(
            Regex("[^A-Za-z0-9._-]"),
            "_"
        )

        if (!safeName.endsWith(".json", ignoreCase = true)) {
            return "$safeName.json"
        }

        return safeName
    }
}

@Serializable
private data class ContractInfo(
    val path: String,
    val fileName: String,
    val sizeBytes: Long,
    val modifiedAt: String
)

@Serializable
private data class ContractListResponse(
    val ok: Boolean,
    val contracts: List<ContractInfo>,
    val error: String? = null
)

@Serializable
private data class ContractSaveRequest(
    val fileName: String? = null,
    val path: String? = null,
    val content: String
)

@Serializable
private data class ContractSaveResponse(
    val ok: Boolean,
    val path: String? = null,
    val error: String? = null
)

@Serializable
private data class UiRunResponse(
    val ok: Boolean,
    val total: Int = 0,
    val passed: Int = 0,
    val failed: Int = 0,
    val outDir: String? = null,
    val error: String? = null
)
