package ru.course.apitesting

import ru.course.apitesting.cli.Args
import ru.course.apitesting.cli.ConsoleUi
import ru.course.apitesting.config.ConfigLoader
import ru.course.apitesting.exec.TestRunner
import ru.course.apitesting.http.HttpClientFactory
import ru.course.apitesting.http.HttpExecutor
import ru.course.apitesting.report.JUnitXmlWriter
import ru.course.apitesting.report.ReportWriter
import ru.course.apitesting.validate.ContractValidator
import kotlin.concurrent.thread
import java.io.File

fun main(rawArgs: Array<String>) {
    val ui = ConsoleUi()

    try {
        val args = Args.parse(rawArgs)
        if (args == null) {
            ui.printUsage()
            return
        }

        val loader = ConfigLoader()
        val runConfig = loader.loadRunConfig(args.runFile)

        val runFileDir = File(args.runFile).absoluteFile.parentFile ?: File(".").absoluteFile

        val httpClient = HttpClientFactory.create(runConfig.timeoutMs)
        val executor = HttpExecutor(httpClient, runConfig.baseUrl)
        val validator = ContractValidator()
        val runner = TestRunner(loader, executor, validator, runFileDir)

        val results = runner.runAll(runConfig.tests)

        ReportWriter.writeJson(args.outDir, results)
        JUnitXmlWriter.write(args.outDir, results)

        ui.printSummary(results)

        if (args.web) {
            println()
            println("Запуск Web UI...")
            println("После системных сообщений Ktor сводка будет выведена повторно.")
            println()

            kotlin.concurrent.thread {
                ru.course.apitesting.web.WebReportServer.start(results)
            }

            Thread.sleep(1200)

            println()
            println("============================================================")
            println("ИТОГОВАЯ СВОДКА ПОСЛЕ ЗАПУСКА WEB UI")
            println("============================================================")
            ui.printSummary(results)

            println()
            println("Web UI доступен по адресу: http://localhost:8080")
            println("Для остановки нажмите Ctrl+C")

            Thread.currentThread().join()
        }


    } catch (t: Throwable) {
        System.err.println("FATAL: " + (t.message ?: t::class.qualifiedName))
        t.printStackTrace()
    }
}
