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
            ru.course.apitesting.web.WebReportServer.start(results)
        }


    } catch (t: Throwable) {
        System.err.println("FATAL: " + (t.message ?: t::class.qualifiedName))
        t.printStackTrace()
    }
}
