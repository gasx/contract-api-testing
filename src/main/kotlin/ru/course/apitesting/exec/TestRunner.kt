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

class TestRunner(
    private val loader: ConfigLoader,
    private val executor: HttpExecutor,
    private val validator: ContractValidator,
    private val baseDir: File
) {
    private val contractLoader = ExternalContractLoader(loader)

    fun runAll(tests: List<ApiTestCase>): List<TestResult> {
        return tests.map { tc ->
            var result: TestResult

            val durationMs = measureTimeMillis {
                val contractPath = resolve(tc.contractFile)

                val contract = contractLoader.load(
                    path = contractPath,
                    testCase = tc
                )

                val httpResult: HttpResult =
                    if (!tc.responseFile.isNullOrBlank()) {
                        val responsePath = resolve(tc.responseFile!!)
                        val body = loader.readTextFile(responsePath)

                        HttpResult(
                            ok = true,
                            status = tc.expectedStatus,
                            bodyText = body,
                            error = null
                        )
                    } else {
                        executor.execute(tc)
                    }

                result = validator.validate(
                    tc = tc,
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