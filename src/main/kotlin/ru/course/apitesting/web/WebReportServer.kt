package ru.course.apitesting.web

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.course.apitesting.report.TestResult

object WebReportServer {

    fun start(results: List<TestResult>, port: Int = 8081) {
        val json = Json { prettyPrint = true }

        val server = embeddedServer(Netty, port = port) {
            routing {
                get("/") {
                    call.respondText(buildHtml(), ContentType.Text.Html)
                }

                get("/api/results") {
                    call.respondText(
                        json.encodeToString(results),
                        ContentType.Application.Json
                    )
                }

                get("/health") {
                    call.respondText("OK", status = HttpStatusCode.OK)
                }
            }
        }

        println()
        println("Web UI started: http://localhost:$port")
        println("Press Ctrl+C to stop")

        server.start(wait = true)
    }

    private fun buildHtml(): String {
        return """
            <!doctype html>
            <html lang="ru">
            <head>
                <meta charset="utf-8">
                <title>Contract API Test Report</title>
                <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
                <style>
                    body {
                        font-family: Arial, sans-serif;
                        background: #f4f6f8;
                        margin: 0;
                        padding: 24px;
                        color: #222;
                    }
                    h1 {
                        margin-bottom: 8px;
                    }
                    .cards {
                        display: flex;
                        gap: 16px;
                        margin: 24px 0;
                    }
                    .card {
                        background: white;
                        border-radius: 12px;
                        padding: 18px;
                        box-shadow: 0 2px 8px rgba(0,0,0,0.08);
                        flex: 1;
                    }
                    .number {
                        font-size: 32px;
                        font-weight: bold;
                    }
                    .pass { color: #1f8f4d; }
                    .fail { color: #c62828; }
                    .chart-box {
                        width: 360px;
                        background: white;
                        border-radius: 12px;
                        padding: 18px;
                        box-shadow: 0 2px 8px rgba(0,0,0,0.08);
                        margin-bottom: 24px;
                    }
                    table {
                        width: 100%;
                        border-collapse: collapse;
                        background: white;
                        border-radius: 12px;
                        overflow: hidden;
                        box-shadow: 0 2px 8px rgba(0,0,0,0.08);
                    }
                    th, td {
                        padding: 12px;
                        border-bottom: 1px solid #eee;
                        text-align: left;
                        vertical-align: top;
                    }
                    th {
                        background: #eef1f5;
                    }
                    .badge {
                        padding: 4px 8px;
                        border-radius: 8px;
                        font-weight: bold;
                        font-size: 12px;
                    }
                    .badge-pass {
                        background: #dff5e8;
                        color: #1f8f4d;
                    }
                    .badge-fail {
                        background: #fde0e0;
                        color: #c62828;
                    }
                    .error {
                        font-size: 13px;
                        color: #444;
                    }
                </style>
            </head>
            <body>
                <h1>Contract API Test Report</h1>
                <div id="subtitle">Загрузка результатов...</div>

                <div class="cards">
                    <div class="card">
                        <div>Total</div>
                        <div class="number" id="total">0</div>
                    </div>
                    <div class="card">
                        <div>Passed</div>
                        <div class="number pass" id="passed">0</div>
                    </div>
                    <div class="card">
                        <div>Failed</div>
                        <div class="number fail" id="failed">0</div>
                    </div>
                    <div class="card">
                        <div>Pass rate</div>
                        <div class="number" id="percent">0%</div>
                    </div>
                </div>

                <div class="chart-box">
                    <canvas id="resultChart"></canvas>
                </div>

                <h2>Список тестов</h2>
                <table>
                    <thead>
                        <tr>
                            <th>Статус</th>
                            <th>Тест</th>
                            <th>Запрос</th>
                            <th>HTTP</th>
                            <th>Ошибки</th>
                        </tr>
                    </thead>
                    <tbody id="tests"></tbody>
                </table>

                <script>
                    async function loadResults() {
                        const response = await fetch('/api/results');
                        const results = await response.json();

                        const total = results.length;
                        const passed = results.filter(r => r.passed).length;
                        const failed = total - passed;
                        const percent = total === 0 ? 0 : Math.round((passed / total) * 100);

                        document.getElementById('subtitle').innerText =
                            'Итог тестового прогона: ' + (failed === 0 ? 'PASS' : 'FAIL');

                        document.getElementById('total').innerText = total;
                        document.getElementById('passed').innerText = passed;
                        document.getElementById('failed').innerText = failed;
                        document.getElementById('percent').innerText = percent + '%';

                        new Chart(document.getElementById('resultChart'), {
                            type: 'doughnut',
                            data: {
                                labels: ['Passed', 'Failed'],
                                datasets: [{
                                    data: [passed, failed],
                                    backgroundColor: ['#1f8f4d', '#c62828']
                                }]
                            }
                        });

                        const tbody = document.getElementById('tests');
                        tbody.innerHTML = '';

                        results.forEach(r => {
                            const errors = r.violations.length === 0
                                ? '-'
                                : r.violations.map(v =>
                                    '<div class="error"><b>' + v.code + '</b>: ' +
                                    (v.path || '-') + ' — ' + v.details + '</div>'
                                  ).join('');

                            const status = r.passed
                                ? '<span class="badge badge-pass">PASS</span>'
                                : '<span class="badge badge-fail">FAIL</span>';

                            const row = document.createElement('tr');
                            row.innerHTML =
                                '<td>' + status + '</td>' +
                                '<td>' + r.testId + '</td>' +
                                '<td>' + r.method + ' ' + r.target + '</td>' +
                                '<td>' + r.expectedStatus + ' / ' + (r.actualStatus ?? '-') + '</td>' +
                                '<td>' + errors + '</td>';

                            tbody.appendChild(row);
                        });
                    }

                    loadResults();
                </script>
            </body>
            </html>
        """.trimIndent()
    }
}