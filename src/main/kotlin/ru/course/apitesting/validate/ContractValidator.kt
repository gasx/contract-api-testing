package ru.course.apitesting.validate

import kotlinx.serialization.json.Json
import ru.course.apitesting.config.ApiTestCase
import ru.course.apitesting.config.ContractConfig
import ru.course.apitesting.http.HttpResult
import ru.course.apitesting.report.TestResult
import ru.course.apitesting.report.Violation

class ContractValidator {

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    fun validate(tc: ApiTestCase, contract: ContractConfig, http: HttpResult): TestResult {
        val violations = mutableListOf<Violation>()

        val actualStatus = http.status
        val expectedStatus = 200 // для демо: ожидаем 200; можно вынести в конфиг при необходимости

        if (!http.ok) {
            violations += Violation("HTTP_ERROR", "", http.error ?: "HTTP execution failed")
            return buildResult(tc, contract, expectedStatus, actualStatus, violations)
        }

        if (actualStatus != null && actualStatus != expectedStatus) {
            violations += Violation("STATUS_MISMATCH", "", "Expected HTTP status $expectedStatus but got $actualStatus")
        }

        val body = http.bodyText ?: ""
        val root = try {
            json.parseToJsonElement(body)
        } catch (e: Throwable) {
            violations += Violation("JSON_PARSE_ERROR", "", "Response body is not valid JSON: ${e.message}")
            return buildResult(tc, contract, expectedStatus, actualStatus, violations)
        }

        contract.requiredPaths.forEach { p ->
            if (!JsonPathExtensions.exists(root, p)) {
                violations += Violation("REQUIRED_PATH_MISSING", p, "Required path not found")
            }
        }

        contract.optionalPaths.forEach { p ->
            if (!JsonPathExtensions.exists(root, p)) {
                violations += Violation("OPTIONAL_PATH_MISSING", p, "Optional path not found")
            }
        }

        return buildResult(tc, contract, expectedStatus, actualStatus, violations)
    }

    private fun buildResult(
        tc: ApiTestCase,
        contract: ContractConfig,
        expectedStatus: Int,
        actualStatus: Int?,
        violations: List<Violation>
    ): TestResult {
        val failed = violations.any { it.code in setOf("HTTP_ERROR", "JSON_PARSE_ERROR", "STATUS_MISMATCH", "REQUIRED_PATH_MISSING") }
        return TestResult(
            testId = tc.testId,
            contractId = contract.contractId,
            target = tc.path,
            method = tc.method.uppercase(),
            expectedStatus = expectedStatus,
            actualStatus = actualStatus,
            passed = !failed,
            violations = violations
        )
    }
}
