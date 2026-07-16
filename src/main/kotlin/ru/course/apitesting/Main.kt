package ru.course.apitesting

import ru.course.apitesting.cli.Args
import ru.course.apitesting.cli.ConsoleUi
import ru.course.apitesting.config.ConfigLoader
import ru.course.apitesting.config.RunConfigReader
import ru.course.apitesting.exec.TestRunner
import ru.course.apitesting.http.HttpClientFactory
import ru.course.apitesting.http.HttpExecutor
import ru.course.apitesting.report.JUnitXmlWriter
import ru.course.apitesting.report.ReportWriter
import ru.course.apitesting.validate.ContractValidator
import ru.course.apitesting.integration.core.IntegrationEngine
import ru.course.apitesting.integration.http.HttpIntegrationExecutor
import ru.course.apitesting.integration.kafka.KafkaConsumeIntegrationExecutor
import ru.course.apitesting.integration.kafka.KafkaIntegrationExecutor
import ru.course.apitesting.integration.mock.MockIntegrationExecutor
import java.io.File

fun main(rawArgs: Array<String>) {
    val ui = ConsoleUi()

    try {
        val profileName = argValue(rawArgs, "--profile")

        val profilesFile = argValue(rawArgs, "--profiles")
            ?.let { File(it) }

        val argsForExistingParser = removeOptionsWithValues(
            args = rawArgs,
            optionNames = setOf("--profile", "--profiles")
        )

        val args = Args.parse(argsForExistingParser)
        if (args == null) {
            ui.printUsage()
            return
        }

        val loader = ConfigLoader()

        val runFile = File(args.runFile).absoluteFile

        val runConfig = RunConfigReader().read(
            runFile = runFile,
            profileName = profileName,
            profilesFile = profilesFile
        )

        val runFileDir = runFile.parentFile ?: File(".").absoluteFile

        val httpClient = HttpClientFactory.create(runConfig.timeoutMs)
        val executor = HttpExecutor(httpClient, runConfig.baseUrl)
        val validator = ContractValidator()
        val integrationEngine = IntegrationEngine(
            integrations = runConfig.integrations,
            executors = listOf(
                MockIntegrationExecutor(),
                HttpIntegrationExecutor(httpClient),
                KafkaIntegrationExecutor(runFileDir),
                KafkaConsumeIntegrationExecutor(runFileDir),
            )
        )
        val runner = TestRunner(loader, executor, validator, runFileDir, integrationEngine)

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

private fun argValue(
    args: Array<String>,
    name: String
): String? {
    val index = args.indexOf(name)

    if (index < 0) {
        return null
    }

    return args.getOrNull(index + 1)
        ?: error("После $name нужно указать значение")
}

private fun removeOptionsWithValues(
    args: Array<String>,
    optionNames: Set<String>
): Array<String> {
    val result = mutableListOf<String>()
    var index = 0

    while (index < args.size) {
        val value = args[index]

        if (value in optionNames) {
            if (index + 1 >= args.size) {
                error("После $value нужно указать значение")
            }

            index += 2
        } else {
            result += value
            index += 1
        }
    }

    return result.toTypedArray()
}