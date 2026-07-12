package ru.course.apitesting.exec

import ru.course.apitesting.config.ApiTestCase
import ru.course.apitesting.config.ConfigLoader
import ru.course.apitesting.http.HttpExecutor
import ru.course.apitesting.http.HttpResult
import ru.course.apitesting.report.TestResult
import ru.course.apitesting.schema.ExternalContractLoader
import ru.course.apitesting.validate.ContractValidator
import java.io.File
import kotlin.system.measureTimeMillis
import ru.course.apitesting.integration.IntegrationEngine

class TestRunner(
    private val loader: ConfigLoader,
    private val executor: HttpExecutor,
    private val validator: ContractValidator,
    private val baseDir: File,
    private val integrationEngine: IntegrationEngine
) {
    private val contractLoader = ExternalContractLoader(loader)

    fun runAll(tests: List<ApiTestCase>): List<TestResult> {
        return tests.map { tc ->
            var result: TestResult

            val durationMs = measureTimeMillis {
                val renderedTestCase = integrationEngine.prepareTest(tc)

                val preparedTestCase = renderedTestCase.copy(
                    multipart = renderedTestCase.multipart.map { part ->
                        part.copy(
                            filePath = part.filePath?.let { resolve(it) }
                        )
                    },
                    downloadTo = renderedTestCase.downloadTo?.let { resolve(it) }
                )

                val contractPath = resolve(preparedTestCase.contractFile)

                val contract = contractLoader.load(
                    path = contractPath,
                    testCase = preparedTestCase
                )

                val httpResult: HttpResult =
                    if (!preparedTestCase.responseFile.isNullOrBlank()) {
                        val responsePath = resolve(preparedTestCase.responseFile!!)
                        val body = loader.readTextFile(responsePath)

                        HttpResult(
                            ok = true,
                            status = preparedTestCase.expectedStatus,
                            bodyText = body
                        )
                    } else {
                        executor.execute(preparedTestCase)
                    }

                result = validator.validate(
                    tc = preparedTestCase,
                    contract = contract,
                    http = httpResult
                )
            }

            result.copy(durationMs = durationMs)
        }
    }

    private fun resolve(path: String): String {
        val file = File(path)

        return if (file.isAbsolute) {
            file.path
        } else {
            File(baseDir, path).path
        }
    }
}