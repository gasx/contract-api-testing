package ru.course.apitesting.config

import kotlinx.serialization.json.Json
import java.io.File

class ConfigLoader {
    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = true
        prettyPrint = false
    }

    fun loadRunConfig(path: String): RunConfig {
        val text = File(path).readText(Charsets.UTF_8)
        return json.decodeFromString(text)
    }

    fun loadContract(path: String): ContractConfig {
        val text = File(path).readText(Charsets.UTF_8)
        return json.decodeFromString(text)
    }

    fun readTextFile(path: String): String {
        return File(path).readText(Charsets.UTF_8)
    }
}
