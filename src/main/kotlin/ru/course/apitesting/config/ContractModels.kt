package ru.course.apitesting.config

import kotlinx.serialization.Serializable

@Serializable
data class ContractConfig(
    val contractId: String,
    val description: String = "",
    val requiredPaths: List<String> = emptyList(),
    val optionalPaths: List<String> = emptyList()
)
