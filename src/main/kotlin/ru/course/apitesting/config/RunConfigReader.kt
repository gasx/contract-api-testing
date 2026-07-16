package ru.course.apitesting.config

import kotlinx.serialization.json.Json
import java.io.File

class RunConfigReader {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    fun read(
        runFile: File,
        profileName: String?,
        profilesFile: File?
    ): RunConfig {
        val runText = runFile.readText()

        if (profileName == null) {
            if (runText.contains("\${profile.")) {
                error("В run config используются \${profile.*}, но не указан --profile")
            }

            return json.decodeFromString(
                RunConfig.serializer(),
                runText
            )
        }

        val actualProfilesFile = profilesFile
            ?: File("configs/profiles.json")

        if (!actualProfilesFile.exists()) {
            error("Файл профилей не найден: ${actualProfilesFile.path}")
        }

        val profiles = json.decodeFromString(
            ProfilesFile.serializer(),
            actualProfilesFile.readText()
        )

        val profile = profiles.profiles[profileName]
            ?: error("Профиль не найден: $profileName. Доступные профили: ${profiles.profiles.keys.joinToString()}")

        val runJson = json.parseToJsonElement(runText)

        val renderedRunJson = ProfileRenderer(
            profileName = profileName,
            profile = profile
        ).render(runJson)

        return json.decodeFromJsonElement(
            RunConfig.serializer(),
            renderedRunJson
        )
    }
}