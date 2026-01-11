package ru.course.apitesting.report

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

object ReportWriter {
    private val json = Json { prettyPrint = true }

    fun writeJson(outDir: String, results: List<TestResult>) {
        val dir = File(outDir)
        dir.mkdirs()
        File(dir, "report.json").writeText(json.encodeToString(results), Charsets.UTF_8)
    }
}
