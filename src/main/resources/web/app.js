let allResults = [];
let currentFilter = 'ALL';

function setFilter(filter, button) {
    currentFilter = filter;

    document.querySelectorAll('.filters button').forEach(item => {
        item.classList.remove('active');
    });

    button.classList.add('active');
    renderTable();
}

function safe(value) {
    return value === null || value === undefined || value === ''
        ? '-'
        : String(value);
}

function escapeHtml(value) {
    return String(value ?? '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

function safeHtml(value) {
    return escapeHtml(safe(value));
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

function violationText(violation) {
    return [
        safe(violation.code),
        safe(violation.path),
        safe(violation.details)
    ].join(' ');
}

function classify(code) {
    if (!code) {
        return 'Другая ошибка';
    }

    if (code.includes('INTEGRATION')) {
        return 'Ошибка интеграции';
    }

    if (code.includes('STATUS')) {
        return 'Ошибка HTTP-статуса';
    }

    if (code.includes('REQUIRED')) {
        return 'Нет обязательного поля';
    }

    if (code.includes('OPTIONAL')) {
        return 'Нет необязательного поля';
    }

    if (code.includes('CONTENT_TYPE')) {
        return 'Неверный тип файла';
    }

    if (code.includes('FILE_EMPTY')) {
        return 'Пустой файл';
    }

    if (code.includes('HTTP')) {
        return 'Ошибка HTTP-запроса';
    }

    if (code.includes('JSON')) {
        return 'Ошибка JSON';
    }

    return code;
}

function formatViolation(violation) {
    if (violation.code === 'INTEGRATION_ERROR') {
        return 'Ошибка при выполнении интеграции перед тестом';
    }

    if (violation.code === 'STATUS_MISMATCH') {
        return 'HTTP-статус ответа не совпадает с ожидаемым';
    }

    if (violation.code === 'REQUIRED_PATH_MISSING') {
        return 'Отсутствует обязательное поле в ответе';
    }

    if (violation.code === 'OPTIONAL_PATH_MISSING') {
        return 'Отсутствует необязательное поле в ответе';
    }

    if (violation.code === 'JSON_PARSE_ERROR') {
        return 'Ответ сервера не является корректным JSON';
    }

    if (violation.code === 'HTTP_ERROR') {
        return 'Ошибка при выполнении HTTP-запроса';
    }

    return safe(violation.details);
}

function renderFileTransfers(test) {
    const transfers = Array.isArray(test.fileTransfers)
        ? test.fileTransfers
        : [];

    if (transfers.length === 0) {
        return '<span class="small">файлы не используются</span>';
    }

    return transfers.map((file, index) => {
        const received = file.direction === 'RECEIVED';
        const kind = received ? 'Получен файл' : 'Отправлен файл';
        const fileName = escapeHtml(file.fileName || 'Файл');
        const contentType = escapeHtml(
            file.contentType || 'application/octet-stream'
        );
        const fileUrl =
            '/api/files/' +
            encodeURIComponent(test.testId) +
            '/' +
            index;

        const isImage = String(file.contentType || '')
            .toLowerCase()
            .startsWith('image/');

        const preview = received && isImage
            ? '<a href="' + fileUrl + '" target="_blank" onclick="event.stopPropagation()">' +
            '<img class="file-preview" src="' + fileUrl + '" alt="' + fileName + '">' +
            '</a>'
            : '';

        const openLink = received
            ? '<a class="file-link" href="' + fileUrl + '" target="_blank" onclick="event.stopPropagation()">Открыть файл</a>'
            : '';

        return (
            '<div class="file-transfer">' +
            '<div class="file-kind">' + kind + '</div><br>' +
            '<span class="small">' +
            fileName +
            ' · ' +
            contentType +
            ' · ' +
            formatBytes(file.sizeBytes) +
            '</span>' +
            preview +
            openLink +
            '</div>'
        );
    }).join('');
}

function renderIntegrations(test) {
    const integrations = Array.isArray(test.integrations)
        ? test.integrations
        : [];

    if (integrations.length === 0) {
        return '<span class="small">интеграции не использовались</span>';
    }

    return integrations.map(item => {
        const hasError = item.error !== null && item.error !== undefined && item.error !== '';
        const status = item.status === null || item.status === undefined
            ? '-'
            : item.status;

        const attempts = item.attempts || 1;

        const statusClass = !hasError
            ? 'integration-status-ok'
            : 'integration-status-bad';

        const retryBadge = attempts > 1
            ? '<span class="integration-retry">retry x' + safeHtml(attempts) + '</span>'
            : '';

        const errorBlock = hasError
            ? '<div class="integration-error">' + safeHtml(item.error) + '</div>'
            : '';

        const varsBlock = renderIntegrationObject('vars', item.vars);
        const savedVarsBlock = renderIntegrationObject('saved', item.savedVars);

        return (
            '<div class="integration-run ' + (hasError ? 'integration-run-error' : '') + '">' +
            '<div class="integration-head">' +
            '<span class="integration-name">' + safeHtml(item.name) + '</span>' +
            ' <span class="integration-type">' + safeHtml(item.type) + '</span>' +
            retryBadge +
            '</div>' +
            '<div class="small">' +
            'status: <span class="' + statusClass + '">' + safeHtml(status) + '</span>' +
            ' · ' +
            safeHtml(item.durationMs || 0) +
            ' мс' +
            ' · attempts: ' +
            safeHtml(attempts) +
            '</div>' +
            errorBlock +
            varsBlock +
            savedVarsBlock +
            '</div>'
        );
    }).join('');
}

function renderIntegrationObject(title, value) {
    if (!value || typeof value !== 'object') {
        return '';
    }

    const keys = Object.keys(value);

    if (keys.length === 0) {
        return '';
    }

    const rows = keys.slice(0, 5).map(key => {
        return (
            '<div class="integration-var-row">' +
            '<span class="integration-var-key">' + safeHtml(key) + '</span>' +
            '<span class="integration-var-value">' + safeHtml(formatIntegrationValue(value[key])) + '</span>' +
            '</div>'
        );
    }).join('');

    const more = keys.length > 5
        ? '<div class="integration-var-more">+' + safeHtml(keys.length - 5) + ' ещё</div>'
        : '';

    return (
        '<details class="integration-vars">' +
        '<summary>' + safeHtml(title) + '</summary>' +
        rows +
        more +
        '</details>'
    );
}

function formatIntegrationValue(value) {
    if (value === null || value === undefined) {
        return '-';
    }

    if (typeof value === 'object') {
        return JSON.stringify(value);
    }

    return String(value);
}

function renderCharts(passed, failed) {
    if (typeof Chart === 'undefined') {
        return;
    }

    new Chart(document.getElementById('resultChart'), {
        type: 'doughnut',
        data: {
            labels: ['Успешные', 'С ошибками'],
            datasets: [
                {
                    data: [passed, failed],
                    backgroundColor: ['#22c55e', '#ef4444'],
                    borderWidth: 0
                }
            ]
        },
        options: {
            plugins: {
                legend: {
                    labels: {
                        color: '#e5e7eb'
                    }
                }
            }
        }
    });

    const errorMap = {};

    allResults.forEach(result => {
        result.violations.forEach(violation => {
            const key = classify(violation.code);
            errorMap[key] = (errorMap[key] || 0) + 1;
        });
    });

    new Chart(document.getElementById('errorChart'), {
        type: 'bar',
        data: {
            labels: Object.keys(errorMap),
            datasets: [
                {
                    label: 'Количество ошибок',
                    data: Object.values(errorMap),
                    backgroundColor: '#38bdf8',
                    borderRadius: 8
                }
            ]
        },
        options: {
            scales: {
                x: {
                    ticks: {
                        color: '#e5e7eb'
                    },
                    grid: {
                        color: '#263244'
                    }
                },
                y: {
                    ticks: {
                        color: '#e5e7eb'
                    },
                    grid: {
                        color: '#263244'
                    }
                }
            },
            plugins: {
                legend: {
                    labels: {
                        color: '#e5e7eb'
                    }
                }
            }
        }
    });
}

function renderTable() {
    const query = document.getElementById('search').value.toLowerCase();
    const tbody = document.getElementById('testRows');

    tbody.innerHTML = '';

    const filtered = allResults.filter(result => {
        const statusMatches =
            currentFilter === 'ALL' ||
            (currentFilter === 'PASS' && result.passed) ||
            (currentFilter === 'FAIL' && !result.passed) ||
            currentFilter === result.method;

        const fileText = (result.fileTransfers || [])
            .map(file => {
                return [
                    file.direction,
                    file.fileName,
                    file.contentType
                ].join(' ');
            })
            .join(' ');

        const integrationText = (result.integrations || [])
            .map(item => {
                return [
                    item.name,
                    item.type,
                    item.status,
                    item.durationMs,
                    item.attempts,
                    item.error,
                    JSON.stringify(item.vars || {}),
                    JSON.stringify(item.savedVars || {})
                ].join(' ');
            })
            .join(' ');

        const text = [
            result.testId,
            result.method,
            result.target,
            result.expectedStatus,
            result.actualStatus,
            fileText,
            integrationText,
            ...result.violations.map(violationText)
        ].join(' ').toLowerCase();

        return statusMatches && text.includes(query);
    });

    filtered.forEach(result => {
        const badge = result.passed
            ? '<span class="badge badge-pass">УСПЕШНО</span>'
            : '<span class="badge badge-fail">ОШИБКА</span>';

        const errors = result.violations.length === 0
            ? '<span class="small">нарушений не найдено</span>'
            : result.violations.map(violation => {
                return (
                    '<div class="error">' +
                    '<b>' + escapeHtml(classify(violation.code)) + '</b> · ' +
                    safeHtml(violation.path) +
                    '<br>' +
                    '<span class="small">' +
                    escapeHtml(formatViolation(violation)) +
                    '</span>' +
                    '</div>'
                );
            }).join('');

        const row = document.createElement('tr');

        row.innerHTML =
            '<td>' + badge + '</td>' +
            '<td><b>' + safeHtml(result.testId) + '</b><br>' +
            '<span class="small">' + safeHtml(result.contractId) + '</span></td>' +
            '<td>' + renderFileTransfers(result) + '</td>' +
            '<td>' + renderIntegrations(result) + '</td>' +
            '<td><span class="method">' + safeHtml(result.method) + '</span> ' +
            safeHtml(result.target) + '</td>' +
            '<td>ожидалось: <b>' + safeHtml(result.expectedStatus) + '</b><br>' +
            'получено: <b>' + safeHtml(result.actualStatus) + '</b></td>' +
            '<td><b>' + safeHtml(result.durationMs) + ' мс</b></td>' +
            '<td>' + errors + '</td>';

        row.onclick = () => {
            const violations = result.violations.length === 0
                ? 'Нарушения не обнаружены'
                : result.violations.map(violation => {
                    return (
                        '- ' +
                        classify(violation.code) +
                        ' | поле: ' +
                        safe(violation.path) +
                        ' | ' +
                        formatViolation(violation)
                    );
                }).join('\n');

            const integrations = Array.isArray(result.integrations) && result.integrations.length > 0
                ? result.integrations.map(item => {
                    const error = item.error
                        ? ' | error: ' + safe(item.error)
                        : '';

                    const vars = item.vars && Object.keys(item.vars).length > 0
                        ? '\n  vars: ' + JSON.stringify(item.vars)
                        : '';

                    const savedVars = item.savedVars && Object.keys(item.savedVars).length > 0
                        ? '\n  savedVars: ' + JSON.stringify(item.savedVars)
                        : '';

                    return (
                        '- ' +
                        safe(item.name) +
                        ' | type: ' +
                        safe(item.type) +
                        ' | status: ' +
                        safe(item.status) +
                        ' | ' +
                        safe(item.durationMs) +
                        ' мс' +
                        ' | attempts: ' +
                        safe(item.attempts || 1) +
                        error +
                        vars +
                        savedVars
                    );
                }).join('\n')
                : 'Интеграции не использовались';

            alert(
                'Тест: ' + safe(result.testId) + '\n' +
                'Метод: ' + safe(result.method) + '\n' +
                'Адрес: ' + safe(result.target) + '\n' +
                'Ожидался HTTP-статус: ' + safe(result.expectedStatus) + '\n' +
                'Получен HTTP-статус: ' + safe(result.actualStatus) + '\n' +
                'Время выполнения: ' + safe(result.durationMs) + ' мс\n\n' +
                'Интеграции:\n' +
                integrations +
                '\n\n' +
                'Нарушения:\n' +
                violations
            );
        };

        tbody.appendChild(row);
    });
}

async function load() {
    const response = await fetch('/api/results');

    if (!response.ok) {
        throw new Error('Не удалось получить результаты тестов');
    }

    allResults = await response.json();

    const total = allResults.length;
    const passed = allResults.filter(result => result.passed).length;
    const failed = total - passed;
    const rate = total === 0
        ? 0
        : Math.round((passed / total) * 100);

    const violations = allResults.reduce((sum, result) => {
        return sum + result.violations.length;
    }, 0);

    const totalTime = allResults.reduce((sum, result) => {
        return sum + (result.durationMs || 0);
    }, 0);

    const averageTime = total === 0
        ? 0
        : Math.round(totalTime / total);

    const slowest = total === 0
        ? null
        : allResults.reduce((first, second) => {
            return (first.durationMs || 0) > (second.durationMs || 0)
                ? first
                : second;
        });

    document.getElementById('total').innerText = total;
    document.getElementById('passed').innerText = passed;
    document.getElementById('failed').innerText = failed;
    document.getElementById('rate').innerText = rate + '%';
    document.getElementById('violations').innerText = violations;
    document.getElementById('totalTime').innerText = totalTime + ' мс';
    document.getElementById('avgTime').innerText = averageTime + ' мс';
    document.getElementById('slowestTime').innerText =
        slowest
            ? (slowest.durationMs || 0) + ' мс'
            : '0 мс';

    document.getElementById('healthBar').style.width = rate + '%';

    const statusPill = document.getElementById('statusPill');

    statusPill.innerText =
        failed === 0
            ? 'ПРОГОН УСПЕШЕН'
            : 'ОБНАРУЖЕНЫ ОШИБКИ';

    statusPill.className =
        'status-pill ' +
        (failed === 0 ? 'status-pass' : 'status-fail');

    renderCharts(passed, failed);
    renderTable();
}

document.getElementById('search').addEventListener('input', renderTable);

load().catch(error => {
    document.getElementById('statusPill').innerText = 'ОШИБКА ЗАГРУЗКИ';
    document.getElementById('statusPill').className =
        'status-pill status-fail';

    document.getElementById('testRows').innerHTML =
        '<tr><td colspan="8"><div class="error">' +
        escapeHtml(error.message) +
        '</div></td></tr>';
});