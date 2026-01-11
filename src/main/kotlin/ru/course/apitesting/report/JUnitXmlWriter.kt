package ru.course.apitesting.report

import java.io.File

object JUnitXmlWriter {
    fun write(outDir: String, results: List<TestResult>) {
        val dir = File(outDir)
        dir.mkdirs()

        val total = results.size
        val failures = results.count { !it.passed }

        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<testsuite name=\"contract-api-testing\" tests=\"$total\" failures=\"$failures\">\n")

        for (r in results) {
            sb.append("  <testcase name=\"${escape(r.testId)}\">\n")

            if (!r.passed) {
                val msg = buildFailureMessage(r)
                sb.append("    <failure message=\"FAILED\">")
                sb.append(escape(msg))
                sb.append("</failure>\n")
            }

            sb.append("  </testcase>\n")
        }

        sb.append("</testsuite>\n")

        File(dir, "junit.xml").writeText(sb.toString(), Charsets.UTF_8)
    }

    private fun buildFailureMessage(r: TestResult): String {
        val lines = mutableListOf<String>()
        lines += "${r.method} ${r.target}"
        lines += "expectedStatus=${r.expectedStatus}, actualStatus=${r.actualStatus ?: "-"}"
        r.violations.forEach { v ->
            lines += "${v.code} path=${v.path} details=${v.details}"
        }
        return lines.joinToString("\n")
    }

    private fun escape(s: String): String =
        s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
}
