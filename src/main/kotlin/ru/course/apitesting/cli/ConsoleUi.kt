package ru.course.apitesting.cli

import ru.course.apitesting.report.TestResult
import kotlin.system.measureTimeMillis

class ConsoleUi {

    fun printStart(total: Int) {
        println("RUN  contract-api-tests | total: $total")
        println("------------------------------------------------------------")
    }

    fun printProgressLine(
        done: Int,
        total: Int,
        passed: Int,
        failed: Int,
        elapsedMs: Long
    ) {
        val sec = "%.2f".format(elapsedMs / 1000.0)
        println("RUN  tests: $done/$total  passed:$passed  failed:$failed  time:${sec}s")
    }

    fun printTestLine(index: Int, total: Int, r: TestResult) {
        val status = if (r.passed) "PASS" else "FAIL"
        val vioCount = r.violations.size
        println("[$index/$total] ${r.testId} ${r.method} ${r.target} -> $status (violations: $vioCount)")
    }

    fun printFailDetails(r: TestResult) {
        if (r.passed) return
        // одна строка с максимумом смысла
        val brief = r.violations
            .take(3)
            .joinToString("; ") { it.path }
        val more = if (r.violations.size > 3) " (+${r.violations.size - 3} more)" else ""
        println("details: missing/mismatch -> $brief$more")
    }

    fun printFinish(passed: Int, failed: Int, total: Int, elapsedMs: Long, outDir: String) {
        val sec = "%.2f".format(elapsedMs / 1000.0)
        val status = if (failed == 0) "BUILD PASS" else "BUILD FAIL"
        println("------------------------------------------------------------")
        println("$status | total:$total passed:$passed failed:$failed time:${sec}s")
        println("report: $outDir")
    }

    fun printUsage() {
        println("Использование:")
        println("  ./gradlew run --args=\"--run <путь_к_run.json> --out <каталог_отчетов>\" --console=plain")
        println()
        println("Пример:")
        println("  ./gradlew run --args=\"--run configs/run_demo.json --out build/reports\" --console=plain")
    }

    fun printSummary(results: List<TestResult>) {
        val total = results.size
        val passed = results.count { it.passed }
        val failed = total - passed
        val status = if (failed == 0) "BUILD PASS" else "BUILD FAIL"

        val successRate = if (total == 0) 0 else (passed * 100 / total)
        val totalDuration = results.sumOf { it.durationMs }
        val avgDuration = if (total == 0) 0 else totalDuration / total

        val slowest = results.maxByOrNull { it.durationMs }
        val fastest = results.minByOrNull { it.durationMs }

        val allViolations = results.flatMap { it.violations }
        val httpStatusErrors = allViolations.count { it.code == "STATUS_MISMATCH" }
        val requiredMissing = allViolations.count { it.code == "REQUIRED_PATH_MISSING" }
        val optionalMissing = allViolations.count { it.code == "OPTIONAL_PATH_MISSING" }
        val jsonErrors = allViolations.count { it.code == "JSON_PARSE_ERROR" }
        val httpErrors = allViolations.count { it.code == "HTTP_ERROR" }

        println("------------------------------------------------------------")
        println("$status | total:$total passed:$passed failed:$failed")
        println()

        println("PASSED TESTS")
        println("------------")
        results.filter { it.passed }.forEach {
            println("[PASS] ${it.testId} | ${it.method} ${it.target} | ${it.durationMs} ms")
        }

        println()

        println("FAILED TESTS")
        println("------------")

        val failedTests = results.filter { !it.passed }

        if (failedTests.isEmpty()) {
            println("No failed tests")
        } else {
            failedTests.forEach { test ->
                println("[FAIL] ${test.testId} | ${test.method} ${test.target} | ${test.durationMs} ms")
                println("       expected status: ${test.expectedStatus}")
                println("       actual status  : ${test.actualStatus ?: "-"}")

                if (test.violations.isEmpty()) {
                    println("       reason         : unknown error")
                } else {
                    println("       errors:")
                    test.violations.forEach { violation ->
                        val path = if (violation.path.isBlank()) "-" else violation.path
                        println("       - ${violation.code} | path: $path | ${violation.details}")
                    }
                }

                println()
            }
        }

        println("METRICS")
        println("-------")
        println("Success rate       : $successRate%")
        println("Total duration     : $totalDuration ms")
        println("Average test time  : $avgDuration ms")
        println("Slowest test       : ${slowest?.testId ?: "-"} (${slowest?.durationMs ?: 0} ms)")
        println("Fastest test       : ${fastest?.testId ?: "-"} (${fastest?.durationMs ?: 0} ms)")
        println("Total violations   : ${allViolations.size}")
        println("HTTP status errors : $httpStatusErrors")
        println("Required missing   : $requiredMissing")
        println("Optional missing   : $optionalMissing")
        println("JSON errors        : $jsonErrors")
        println("HTTP errors        : $httpErrors")
    }

}