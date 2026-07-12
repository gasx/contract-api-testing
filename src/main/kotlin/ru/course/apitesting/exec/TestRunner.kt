package ru.course.apitesting.exec

import ru.course.apitesting.config.ApiTestCase
import ru.course.apitesting.config.ConfigLoader
import ru.course.apitesting.http.HttpExecutor
import ru.course.apitesting.http.HttpResult
import ru.course.apitesting.integration.IntegrationEngine
import ru.course.apitesting.integration.IntegrationFailedException
import ru.course.apitesting.integration.IntegrationResult
import ru.course.apitesting.report.IntegrationRunInfo
import ru.course.apitesting.report.TestResult
import ru.course.apitesting.report.Violation
import ru.course.apitesting.schema.ExternalContractLoader
import ru.course.apitesting.validate.ContractValidator
import java.io.File
import kotlin.system.measureTimeMillis

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
            lateinit var result: TestResult

            val durationMs = measureTimeMillis {
                result = try {
                    runOne(tc)
                } catch (e: IntegrationFailedException) {
                    buildIntegrationFailedResult(tc, e)
                } catch (t: Throwable) {
                    buildTestExecutionFailedResult(tc, t)
                }
            }

            result.copy(durationMs = durationMs)
        }
    }

    private fun runOne(tc: ApiTestCase): TestResult {
        val prepared = integrationEngine.prepareTest(tc)
        val renderedTestCase = prepared.testCase

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

        return validator.validate(
            tc = preparedTestCase,
            contract = contract,
            http = httpResult
        ).copy(
            integrations = prepared.integrations.toRunInfo()
        )
    }

    private fun buildIntegrationFailedResult(
        tc: ApiTestCase,
        exception: IntegrationFailedException
    ): TestResult {
        return TestResult(
            testId = tc.testId,
            contractId = tc.contractFile,
            target = tc.path,
            method = tc.method.uppercase(),
            expectedStatus = tc.expectedStatus,
            actualStatus = null,
            passed = false,
            violations = listOf(
                Violation(
                    code = "INTEGRATION_ERROR",
                    path = exception.integrationName,
                    details = exception.message ?: "Integration failed"
                )
            ),
            integrations = exception.integrationResults.toRunInfo()
        )
    }

    private fun buildTestExecutionFailedResult(
        tc: ApiTestCase,
        throwable: Throwable
    ): TestResult {
        return TestResult(
            testId = tc.testId,
            contractId = tc.contractFile,
            target = tc.path,
            method = tc.method.uppercase(),
            expectedStatus = tc.expectedStatus,
            actualStatus = null,
            passed = false,
            violations = listOf(
                Violation(
                    code = "TEST_EXECUTION_ERROR",
                    path = "",
                    details = throwable.message ?: throwable::class.simpleName ?: "Test execution failed"
                )
            )
        )
    }

    private fun List<IntegrationResult>.toRunInfo(): List<IntegrationRunInfo> {
        return map {
            IntegrationRunInfo(
                name = it.name,
                type = it.type,
                status = it.status,
                durationMs = it.durationMs,
                error = it.error
            )
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