package ru.course.apitesting.validate

import kotlinx.serialization.json.Json
import ru.course.apitesting.config.ApiTestCase
import ru.course.apitesting.http.HttpResult
import ru.course.apitesting.report.TestResult
import ru.course.apitesting.report.Violation
import ru.course.apitesting.schema.JsonSchemaValidator
import ru.course.apitesting.schema.LoadedContract

class ContractValidator {

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    fun validate(
        tc: ApiTestCase,
        contract: LoadedContract,
        http: HttpResult
    ): TestResult {
        val violations = mutableListOf<Violation>()
        val actualStatus = http.status
        val expectedStatus = contract.expectedStatus

        if (!http.ok) {
            violations += Violation(
                code = "HTTP_ERROR",
                path = "",
                details = http.error ?: "HTTP execution failed"
            )

            return buildResult(
                tc = tc,
                contractId = contract.contractId,
                expectedStatus = expectedStatus,
                actualStatus = actualStatus,
                violations = violations
            )
        }

        if (actualStatus != expectedStatus) {
            violations += Violation(
                code = "STATUS_MISMATCH",
                path = "",
                details = "Expected HTTP status $expectedStatus but got $actualStatus"
            )
        }

        val responseText = http.bodyText ?: ""

        val responseJson = try {
            json.parseToJsonElement(responseText)
        } catch (e: Throwable) {
            violations += Violation(
                code = "JSON_PARSE_ERROR",
                path = "",
                details = "Response body is not valid JSON: ${e.message}"
            )

            return buildResult(
                tc = tc,
                contractId = contract.contractId,
                expectedStatus = expectedStatus,
                actualStatus = actualStatus,
                violations = violations
            )
        }

        when (contract) {
            is LoadedContract.Legacy -> {
                contract.contract.requiredPaths.forEach { path ->
                    if (!JsonPathExtensions.exists(responseJson, path)) {
                        violations += Violation(
                            code = "REQUIRED_PATH_MISSING",
                            path = path,
                            details = "Required path not found"
                        )
                    }
                }

                contract.contract.optionalPaths.forEach { path ->
                    if (!JsonPathExtensions.exists(responseJson, path)) {
                        violations += Violation(
                            code = "OPTIONAL_PATH_MISSING",
                            path = path,
                            details = "Optional path not found"
                        )
                    }
                }
            }

            is LoadedContract.JsonSchema -> {
                violations += JsonSchemaValidator.validate(
                    response = responseJson,
                    schema = contract.schema,
                    document = contract.document
                )
            }
        }

        return buildResult(
            tc = tc,
            contractId = contract.contractId,
            expectedStatus = expectedStatus,
            actualStatus = actualStatus,
            violations = violations
        )
    }

    private fun buildResult(
        tc: ApiTestCase,
        contractId: String,
        expectedStatus: Int,
        actualStatus: Int?,
        violations: List<Violation>
    ): TestResult {
        val failCodes = setOf(
            "HTTP_ERROR",
            "JSON_PARSE_ERROR",
            "STATUS_MISMATCH",
            "REQUIRED_PATH_MISSING",
            "TYPE_MISMATCH",
            "ENUM_MISMATCH",
            "UNEXPECTED_PROPERTY"
        )

        val failed = violations.any { it.code in failCodes }

        return TestResult(
            testId = tc.testId,
            contractId = contractId,
            target = tc.path,
            method = tc.method.uppercase(),
            expectedStatus = expectedStatus,
            actualStatus = actualStatus,
            passed = !failed,
            violations = violations
        )
    }
}