package ru.course.apitesting.integration.core

import ru.course.apitesting.config.ApiTestCase

data class PreparedTestCase(
    val testCase: ApiTestCase,
    val integrations: List<IntegrationResult>
)