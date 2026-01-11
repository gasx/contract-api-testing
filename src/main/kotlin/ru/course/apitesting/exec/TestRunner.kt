package ru.course.apitesting.exec

import ru.course.apitesting.config.ApiTestCase
import ru.course.apitesting.config.ConfigLoader
import ru.course.apitesting.http.HttpExecutor
import ru.course.apitesting.http.HttpResult
import ru.course.apitesting.report.TestResult
import ru.course.apitesting.validate.ContractValidator
import java.io.File

class TestRunner(
    private val loader: ConfigLoader,
    private val executor: HttpExecutor,
    private val validator: ContractValidator,
    private val baseDir: File
) {
    fun runAll(tests: List<ApiTestCase>): List<TestResult> {
        return tests.map { tc ->
            val contractPath = resolve(tc.contractFile)
            val contract = loader.loadContract(contractPath)

            val httpResult: HttpResult = if (!tc.responseFile.isNullOrBlank()) {
                val responsePath = resolve(tc.responseFile!!)
                val body = loader.readTextFile(responsePath)
                HttpResult(ok = true, status = 200, bodyText = body, error = null)
            } else {
                executor.execute(tc)
            }

            validator.validate(tc, contract, httpResult)
        }
    }

    private fun resolve(path: String): String {
        val f = File(path)
        return if (f.isAbsolute) f.path else File(baseDir, path).path
    }
}