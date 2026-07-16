package ru.course.apitesting.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ProfilesFile(
    val profiles: Map<String, ProfileConfig> = emptyMap()
)

@Serializable
data class ProfileConfig(
    val baseUrl: String? = null,
    val timeoutMs: Long? = null,
    val variables: JsonObject = JsonObject(emptyMap())
)