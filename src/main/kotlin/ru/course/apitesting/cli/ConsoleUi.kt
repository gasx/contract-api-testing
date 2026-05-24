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
        println("------------------------------------------------------------")
        println("$status | total:$total passed:$passed failed:$failed")
    }

}