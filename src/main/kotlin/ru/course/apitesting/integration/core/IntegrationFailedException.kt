package ru.course.apitesting.integration.core

class IntegrationFailedException(
    val integrationName: String,
    val integrationType: String,
    val integrationResults: List<IntegrationResult>,
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)