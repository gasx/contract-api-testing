package ru.course.apitesting.web

import io.ktor.http.ContentType
import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.course.apitesting.report.TestResult
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import java.io.File

object WebReportServer {

    fun start(results: List<TestResult>, port: Int = 8080) {
        val json = Json {
            prettyPrint = true
            encodeDefaults = true
        }

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

                get("/api/files/{testId}/{index}") {
                    val testId = call.parameters["testId"]
                    val index = call.parameters["index"]?.toIntOrNull()

                    if (testId == null || index == null) {
                        call.respond(HttpStatusCode.BadRequest)
                        return@get
                    }

                    val transfer = results
                        .firstOrNull { it.testId == testId }
                        ?.fileTransfers
                        ?.getOrNull(index)

                    val localPath = transfer?.localPath

                    if (
                        transfer == null ||
                        transfer.direction != "RECEIVED" ||
                        localPath.isNullOrBlank()
                    ) {
                        call.respond(HttpStatusCode.NotFound)
                        return@get
                    }

                    val file = File(localPath)

                    if (!file.isFile) {
                        call.respond(HttpStatusCode.NotFound)
                        return@get
                    }

                    val fileName = file.name.replace("\"", "")

                    call.response.headers.append(
                        HttpHeaders.ContentDisposition,
                        "inline; filename=\"$fileName\""
                    )

                    call.respondFile(file)
                }
            }
        }

        println()
        println("Веб-интерфейс отчёта: http://localhost:$port")
        println("Для остановки нажмите Ctrl+C")
        server.start(wait = true)
    }

    private fun buildHtml(): String = """
<!doctype html>
<html lang="ru">
<head>
<meta charset="utf-8">
<title>Панель результатов тестирования API</title>
<script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
<style>
:root {
  --bg: #0f172a;
  --panel: #111827;
  --panel2: #1f2937;
  --text: #e5e7eb;
  --muted: #94a3b8;
  --green: #22c55e;
  --red: #ef4444;
  --yellow: #f59e0b;
  --blue: #38bdf8;
  --border: #263244;
}
* { box-sizing: border-box; }
body {
  margin: 0;
  padding: 20px;
  font-family: Arial, sans-serif;
  background: radial-gradient(circle at top, #1e293b 0, #0f172a 45%);
  color: var(--text);
}
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.title {
  font-size: 26px;
  font-weight: 800;
}
.subtitle {
  color: var(--muted);
  margin-top: 5px;
  font-size: 13px;
}
.status-pill {
  padding: 8px 14px;
  border-radius: 999px;
  font-weight: 800;
  letter-spacing: .4px;
  font-size: 13px;
}
.status-pass {
  background: rgba(34,197,94,.15);
  color: var(--green);
  border: 1px solid rgba(34,197,94,.45);
}
.status-fail {
  background: rgba(239,68,68,.15);
  color: var(--red);
  border: 1px solid rgba(239,68,68,.45);
}
.cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
  margin-bottom: 14px;
}
.card {
  background: rgba(17,24,39,.92);
  border: 1px solid var(--border);
  border-radius: 14px;
  padding: 12px 14px;
  box-shadow: 0 12px 28px rgba(0,0,0,.18);
}
.card-label {
  color: var(--muted);
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: .7px;
}
.card-value {
  margin-top: 6px;
  font-size: 26px;
  font-weight: 900;
}
.green { color: var(--green); }
.red { color: var(--red); }
.yellow { color: var(--yellow); }
.blue { color: var(--blue); }
.grid {
  display: grid;
  grid-template-columns: 360px 1fr;
  gap: 12px;
  margin-bottom: 14px;
}
.panel {
  background: rgba(17,24,39,.92);
  border: 1px solid var(--border);
  border-radius: 14px;
  padding: 14px;
  box-shadow: 0 12px 28px rgba(0,0,0,.18);
}
.panel h2 {
  font-size: 16px;
  margin: 0 0 10px 0;
}
.health {
  margin-top: 12px;
}
.bar {
  height: 12px;
  background: #334155;
  border-radius: 999px;
  overflow: hidden;
}
.bar-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--green), var(--blue));
  width: 0%;
}
.filters {
  display: flex;
  gap: 6px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}
button {
  border: 1px solid var(--border);
  background: var(--panel2);
  color: var(--text);
  padding: 7px 10px;
  border-radius: 9px;
  cursor: pointer;
  font-size: 12px;
}
button.active {
  border-color: var(--blue);
  color: var(--blue);
}
input {
  width: 100%;
  background: #0b1220;
  border: 1px solid var(--border);
  color: var(--text);
  padding: 9px 10px;
  border-radius: 10px;
  margin-bottom: 10px;
  font-size: 13px;
}
table {
  width: 100%;
  border-collapse: collapse;
  overflow: hidden;
}
th, td {
  padding: 8px 10px;
  border-bottom: 1px solid var(--border);
  text-align: left;
  vertical-align: top;
  font-size: 13px;
}
th {
  color: var(--muted);
  font-size: 11px;
  text-transform: uppercase;
}
.badge {
  display: inline-block;
  padding: 4px 7px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 800;
}
.badge-pass {
  background: rgba(34,197,94,.16);
  color: var(--green);
}
.badge-fail {
  background: rgba(239,68,68,.16);
  color: var(--red);
}
.method {
  color: var(--blue);
  font-weight: 800;
}
.error {
  color: #fecaca;
  margin-bottom: 4px;
}

.small {
  color: var(--muted);
  font-size: 11px;
}

.file-transfer {
  margin-top: 6px;
  padding: 6px 8px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: #0b1220;
}

.file-kind {
  display: inline-block;
  margin-bottom: 4px;
  font-size: 10px;
  font-weight: 800;
  color: var(--blue);
  text-transform: uppercase;
}

.file-link {
  display: inline-block;
  margin-top: 5px;
  color: var(--blue);
  text-decoration: none;
  font-size: 11px;
  font-weight: 700;
}

.file-preview {
  display: block;
  max-width: 130px;
  max-height: 76px;
  margin-top: 5px;
  border: 1px solid var(--border);
  border-radius: 6px;
}

@media (max-width: 1000px) {
  .cards { grid-template-columns: repeat(2, 1fr); }
  .grid { grid-template-columns: 1fr; }
}
</style>
</head>
<body>

<div class="header">
  <div>
    <div class="title">Результаты тестирования</div>
  </div>
  <div id="statusPill" class="status-pill">ЗАГРУЗКА</div>
</div>

<div class="cards">
  <div class="card">
    <div class="card-label">Всего тестов</div>
    <div class="card-value" id="total">0</div>
  </div>
  <div class="card">
    <div class="card-label">Успешно</div>
    <div class="card-value green" id="passed">0</div>
  </div>
  <div class="card">
    <div class="card-label">С ошибками</div>
    <div class="card-value red" id="failed">0</div>
  </div>
  <div class="card">
    <div class="card-label">Процент успеха</div>
    <div class="card-value blue" id="rate">0%</div>
  </div>
  <div class="card">
    <div class="card-label">Нарушения</div>
    <div class="card-value yellow" id="violations">0</div>
  </div>
  <div class="card">
    <div class="card-label">Общее время</div>
    <div class="card-value blue" id="totalTime">0 мс</div>
  </div>
  <div class="card">
    <div class="card-label">Среднее время</div>
    <div class="card-value blue" id="avgTime">0 мс</div>
  </div>
  <div class="card">
    <div class="card-label">Самый долгий тест</div>
    <div class="card-value yellow" id="slowestTime">0 мс</div>
  </div>
</div>

<div class="grid">
  <div class="panel">
    <h2>Успешные и неуспешные тесты</h2>
    <canvas id="resultChart"></canvas>
    <div class="health">
      <div class="small">Общая оценка состояния API</div>
      <div class="bar"><div class="bar-fill" id="healthBar"></div></div>
    </div>
  </div>

  <div class="panel">
    <h2>Типы выявленных ошибок</h2>
    <canvas id="errorChart"></canvas>
  </div>
</div>

<div class="panel">
  <h2>Результаты тестов</h2>

  <input id="search" placeholder="Поиск по названию теста, методу, адресу или ошибке...">

  <div class="filters">
    <button class="active" onclick="setFilter('ALL', this)">Все</button>
    <button onclick="setFilter('PASS', this)">Успешные</button>
    <button onclick="setFilter('FAIL', this)">С ошибками</button>
    <button onclick="setFilter('GET', this)">GET</button>
    <button onclick="setFilter('POST', this)">POST</button>
    <button onclick="setFilter('PUT', this)">PUT</button>
    <button onclick="setFilter('PATCH', this)">PATCH</button>
  </div>

  <table>
    <thead>
      <tr>
        <th>Статус</th>
        <th>Тест</th>
        <th>Запрос</th>
        <th>HTTP</th>
        <th>Время</th>
        <th>Ошибки</th>
      </tr>
    </thead>
    <tbody id="testRows"></tbody>
  </table>
</div>

<script>
let allResults = [];
let currentFilter = 'ALL';

function setFilter(filter, button) {
  currentFilter = filter;
  document.querySelectorAll('button').forEach(b => b.classList.remove('active'));
  button.classList.add('active');
  renderTable();
}

function safe(v) {
  return v === null || v === undefined || v === '' ? '-' : String(v);
}

function escapeHtml(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

function formatBytes(value) {
  const bytes = Number(value) || 0;

  if (bytes < 1024) {
    return bytes + ' B';
  }

  if (bytes < 1024 * 1024) {
    return (bytes / 1024).toFixed(1) + ' KB';
  }

  return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
}

function renderFileTransfers(test) {
  const transfers = test.fileTransfers || [];

  return transfers.map((file, index) => {
    const received = file.direction === 'RECEIVED';
    const kind = received ? 'Получен файл' : 'Отправлен файл';
    const name = escapeHtml(file.fileName || 'Файл');
    const type = escapeHtml(file.contentType || 'application/octet-stream');
    const details = name + ' · ' + type + ' · ' + formatBytes(file.sizeBytes);
    const href = '/api/files/' + encodeURIComponent(test.testId) + '/' + index;
    const image = String(file.contentType || '')
      .toLowerCase()
      .startsWith('image/');

    const preview = received && image
      ? '<a href="' + href + '" target="_blank" onclick="event.stopPropagation()">' +
          '<img class="file-preview" src="' + href + '" alt="' + name + '">' +
        '</a>'
      : '';

    const link = received
      ? '<a class="file-link" href="' + href + '" target="_blank" onclick="event.stopPropagation()">Открыть файл</a>'
      : '';

    return '<div class="file-transfer">' +
      '<div class="file-kind">' + kind + '</div><br>' +
      '<span class="small">' + details + '</span>' +
      preview +
      link +
      '</div>';
  }).join('');
}

function violationText(v) {
  return safe(v.code) + ' ' + safe(v.path) + ' ' + safe(v.details);
}

function classify(code) {
  if (!code) return 'ДРУГАЯ ОШИБКА';
  if (code.includes('STATUS')) return 'Ошибка HTTP-статуса';
  if (code.includes('REQUIRED')) return 'Нет обязательного поля';
  if (code.includes('OPTIONAL')) return 'Нет необязательного поля';
  if (code.includes('HTTP')) return 'Ошибка HTTP-запроса';
  if (code.includes('JSON')) return 'Ошибка JSON';
  return code;
}

function formatViolation(v) {
  if (v.code === 'STATUS_MISMATCH') {
    return 'HTTP-статус ответа не совпадает с ожидаемым';
  }
  if (v.code === 'REQUIRED_PATH_MISSING') {
    return 'Отсутствует обязательное поле в ответе';
  }
  if (v.code === 'OPTIONAL_PATH_MISSING') {
    return 'Отсутствует необязательное поле в ответе';
  }
  if (v.code === 'JSON_PARSE_ERROR') {
    return 'Ответ сервера не является корректным JSON';
  }
  if (v.code === 'HTTP_ERROR') {
    return 'Ошибка при выполнении HTTP-запроса';
  }
  return safe(v.details);
}

async function load() {
  const res = await fetch('/api/results');
  allResults = await res.json();

  const total = allResults.length;
  const passed = allResults.filter(r => r.passed).length;
  const failed = total - passed;
  const rate = total === 0 ? 0 : Math.round((passed / total) * 100);
  const violations = allResults.reduce((s, r) => s + r.violations.length, 0);

  const totalTime = allResults.reduce((s, r) => s + (r.durationMs || 0), 0);
  const avgTime = total === 0 ? 0 : Math.round(totalTime / total);
  const slowest = allResults.length === 0
    ? null
    : allResults.reduce((a, b) => (a.durationMs || 0) > (b.durationMs || 0) ? a : b);

  document.getElementById('total').innerText = total;
  document.getElementById('passed').innerText = passed;
  document.getElementById('failed').innerText = failed;
  document.getElementById('rate').innerText = rate + '%';
  document.getElementById('violations').innerText = violations;
  document.getElementById('totalTime').innerText = totalTime + ' мс';
  document.getElementById('avgTime').innerText = avgTime + ' мс';
  document.getElementById('slowestTime').innerText = slowest ? (slowest.durationMs || 0) + ' мс' : '0 мс';
  document.getElementById('healthBar').style.width = rate + '%';

  const pill = document.getElementById('statusPill');
  pill.innerText = failed === 0 ? 'ПРОГОН УСПЕШЕН' : 'ОБНАРУЖЕНЫ ОШИБКИ';
  pill.className = 'status-pill ' + (failed === 0 ? 'status-pass' : 'status-fail');

  renderCharts(passed, failed);
  renderTable();
}

function renderCharts(passed, failed) {
  new Chart(document.getElementById('resultChart'), {
    type: 'doughnut',
    data: {
      labels: ['Успешные', 'С ошибками'],
      datasets: [{
        data: [passed, failed],
        backgroundColor: ['#22c55e', '#ef4444'],
        borderWidth: 0
      }]
    },
    options: {
      plugins: {
        legend: { labels: { color: '#e5e7eb' } }
      }
    }
  });

  const map = {};
  allResults.forEach(r => {
    r.violations.forEach(v => {
      const key = classify(v.code);
      map[key] = (map[key] || 0) + 1;
    });
  });

  const labels = Object.keys(map);
  const values = labels.map(k => map[k]);

  new Chart(document.getElementById('errorChart'), {
    type: 'bar',
    data: {
      labels,
      datasets: [{
        label: 'Количество ошибок',
        data: values,
        backgroundColor: '#38bdf8',
        borderRadius: 8
      }]
    },
    options: {
      scales: {
        x: { ticks: { color: '#e5e7eb' }, grid: { color: '#263244' } },
        y: { ticks: { color: '#e5e7eb' }, grid: { color: '#263244' } }
      },
      plugins: {
        legend: { labels: { color: '#e5e7eb' } }
      }
    }
  });
}

function renderTable() {
  const q = document.getElementById('search').value.toLowerCase();
  const tbody = document.getElementById('testRows');
  tbody.innerHTML = '';

  const filtered = allResults.filter(r => {
    const statusMatch =
      currentFilter === 'ALL' ||
      (currentFilter === 'PASS' && r.passed) ||
      (currentFilter === 'FAIL' && !r.passed) ||
      currentFilter === r.method;

    const text = [
  r.testId,
  r.method,
  r.target,
  r.expectedStatus,
  r.actualStatus,
  ...r.violations.map(violationText),
  ...(r.fileTransfers || []).map(file =>
    [file.direction, file.fileName, file.contentType].join(' ')
  )
].join(' ').toLowerCase();

    return statusMatch && text.includes(q);
  });

  filtered.forEach(r => {
    const badge = r.passed
      ? '<span class="badge badge-pass">УСПЕШНО</span>'
      : '<span class="badge badge-fail">ОШИБКА</span>';

    const errors = r.violations.length === 0
      ? '<span class="small">нарушений не найдено</span>'
      : r.violations.map(v =>
          '<div class="error"><b>' + classify(v.code) + '</b> · ' +
          safe(v.path) + '<br><span class="small">' + formatViolation(v) + '</span></div>'
        ).join('');

    const tr = document.createElement('tr');
    tr.innerHTML =
      '<td>' + badge + '</td>' +
    '<td><b>' + safe(r.testId) + '</b>' +
    renderFileTransfers(r) +
    '<br><span class="small">' + safe(r.contractId) + '</span></td>' +      '<td><span class="method">' + safe(r.method) + '</span> ' + safe(r.target) + '</td>' +
      '<td>ожидалось: <b>' + safe(r.expectedStatus) + '</b><br>получено: <b>' + safe(r.actualStatus) + '</b></td>' +
      '<td><b>' + safe(r.durationMs) + ' мс</b></td>' +
      '<td>' + errors + '</td>';

    tr.style.cursor = 'pointer';
    tr.onclick = () => alert(
      'Тест: ' + safe(r.testId) + '\n' +
      'Метод: ' + safe(r.method) + '\n' +
      'Адрес: ' + safe(r.target) + '\n' +
      'Ожидался HTTP-статус: ' + safe(r.expectedStatus) + '\n' +
      'Получен HTTP-статус: ' + safe(r.actualStatus) + '\n' +
      'Время выполнения: ' + safe(r.durationMs) + ' мс\n\n' +
      'Нарушения:\n' +
      (r.violations.length === 0
        ? 'Нарушения не обнаружены'
        : r.violations.map(v =>
            '- ' + classify(v.code) + ' | поле: ' + safe(v.path) + ' | ' + formatViolation(v)
          ).join('\n'))
    );

    tbody.appendChild(tr);
  });
}

document.getElementById('search').addEventListener('input', renderTable);
load();
</script>

</body>
</html>
""".trimIndent()
}