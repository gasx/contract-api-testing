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

    if (code.includes('ASSERTION')) {
        return 'Ошибка assert-проверки';
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

function formatIntegrationValue(value) {
    if (value === null || value === undefined) {
        return '-';
    }

    if (typeof value === 'object') {
        return JSON.stringify(value);
    }

    return String(value);
}

function displayTestTitle(result) {
    return safe(result.name || result.testId || 'Без названия');
}

function renderTags(result) {
    const tags = Array.isArray(result.tags) ? result.tags : [];

    if (tags.length === 0) {
        return '';
    }

    return (
        '<div class="test-tags">' +
        tags.map(tag => '<span class="test-tag">' + safeHtml(tag) + '</span>').join('') +
        '</div>'
    );
}

function renderJsonPreview(value) {
    if (value === null || value === undefined) {
        return '<span class="small">не указано</span>';
    }

    if (typeof value === 'object') {
        return '<pre class="json-preview">' + escapeHtml(JSON.stringify(value, null, 2)) + '</pre>';
    }

    return '<pre class="json-preview">' + escapeHtml(String(value)) + '</pre>';
}

function renderMapPreview(title, value) {
    const map = value && typeof value === 'object' ? value : {};
    const keys = Object.keys(map);

    if (keys.length === 0) {
        return (
            '<div class="request-part">' +
            '<div class="request-part-title">' + safeHtml(title) + '</div>' +
            '<span class="small">не указано</span>' +
            '</div>'
        );
    }

    const rows = keys.map(key => {
        return (
            '<div class="kv-row">' +
            '<span class="kv-key">' + safeHtml(key) + '</span>' +
            '<span class="kv-value">' + safeHtml(formatIntegrationValue(map[key])) + '</span>' +
            '</div>'
        );
    }).join('');

    return (
        '<div class="request-part">' +
        '<div class="request-part-title">' + safeHtml(title) + '</div>' +
        rows +
        '</div>'
    );
}

function renderRequestInfo(result) {
    const request = result.request || {};
    const method = request.method || result.method;
    const path = request.path || result.target;
    const query = request.query || {};
    const headers = request.headers || {};
    const body = request.body;

    const queryString = Object.keys(query).length === 0
        ? ''
        : '?' + Object.keys(query)
            .map(key => encodeURIComponent(key) + '=' + encodeURIComponent(String(query[key])))
            .join('&');

    return (
        '<div class="request-box">' +
        '<div class="request-line">' +
        '<span class="method">' + safeHtml(method) + '</span> ' +
        '<span>' + safeHtml(path + queryString) + '</span>' +
        '</div>' +
        '<div class="request-grid">' +
        renderMapPreview('Query', query) +
        renderMapPreview('Headers', headers) +
        '<div class="request-part">' +
        '<div class="request-part-title">Body</div>' +
        renderJsonPreview(body) +
        '</div>' +
        '</div>' +
        '</div>'
    );
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
        const contentType = escapeHtml(file.contentType || 'application/octet-stream');
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

function renderIntegrationObject(title, value) {
    if (!value || typeof value !== 'object') {
        return '';
    }

    const keys = Object.keys(value);

    if (keys.length === 0) {
        return '';
    }

    const rows = keys.map(key => {
        return (
            '<div class="integration-var-row">' +
            '<span class="integration-var-key">' + safeHtml(key) + '</span>' +
            '<span class="integration-var-value">' + safeHtml(formatIntegrationValue(value[key])) + '</span>' +
            '</div>'
        );
    }).join('');

    return (
        '<details class="integration-vars">' +
        '<summary>' + safeHtml(title) + '</summary>' +
        rows +
        '</details>'
    );
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

function renderOperationResponse(result) {
    const hasJsonBody = result.responseBody !== null && result.responseBody !== undefined;
    const hasTextBody = result.responseText !== null && result.responseText !== undefined && String(result.responseText).length > 0;
    const hasContentType = result.responseContentType !== null && result.responseContentType !== undefined && String(result.responseContentType).length > 0;

    if (!hasJsonBody && !hasTextBody && !hasContentType) {
        return '<span class="small">ответ не сохранён</span>';
    }

    const body = hasJsonBody
        ? JSON.stringify(result.responseBody, null, 2)
        : String(result.responseText || '');

    return (
        '<div class="operation-response-block">' +
        '<div class="operation-response-meta">Content-Type: ' + safeHtml(result.responseContentType || '-') + '</div>' +
        '<pre class="operation-response-body">' + escapeHtml(body) + '</pre>' +
        '</div>'
    );
}

function renderViolations(result) {
    const violations = Array.isArray(result.violations) ? result.violations : [];

    if (violations.length === 0) {
        return '<div class="ok-note">Нарушений не найдено</div>';
    }

    return violations.map(violation => {
        return (
            '<div class="error error-card">' +
            '<b>' + escapeHtml(classify(violation.code)) + '</b>' +
            '<div class="small">path: ' + safeHtml(violation.path) + '</div>' +
            '<div>' + escapeHtml(formatViolation(violation)) + '</div>' +
            '</div>'
        );
    }).join('');
}

function renderHttpSummary(result) {
    const ok = result.actualStatus === result.expectedStatus;
    const cls = ok ? 'http-ok' : 'http-bad';

    return (
        '<div class="http-summary ' + cls + '">' +
        '<div class="metric-label">HTTP</div>' +
        '<div class="metric-value">' +
        safeHtml(result.actualStatus) +
        ' / ' +
        safeHtml(result.expectedStatus) +
        '</div>' +
        '</div>'
    );
}

function renderTestCard(result) {
    const passed = !!result.passed;
    const badge = passed
        ? '<span class="badge badge-pass">PASS</span>'
        : '<span class="badge badge-fail">FAIL</span>';

    const description = result.description
        ? '<div class="test-description">' + safeHtml(result.description) + '</div>'
        : '';

    return (
        '<article class="test-card-row ' + (passed ? 'test-card-pass' : 'test-card-fail') + '">' +
        '<div class="test-card-header">' +
        '<div class="test-title-block">' +
        '<div class="test-title-line">' +
        badge +
        '<h3>' + safeHtml(displayTestTitle(result)) + '</h3>' +
        '</div>' +
        '<div class="test-id">' + safeHtml(result.testId) + '</div>' +
        renderTags(result) +
        '</div>' +
        '<div class="test-metrics">' +
        renderHttpSummary(result) +
        '<div class="metric-box">' +
        '<div class="metric-label">Время</div>' +
        '<div class="metric-value">' + safeHtml(result.durationMs) + ' мс</div>' +
        '</div>' +
        '</div>' +
        '</div>' +
        description +
        '<div class="test-main-grid">' +
        '<section class="test-section-card">' +
        '<div class="section-title">Проверяемый запрос</div>' +
        renderRequestInfo(result) +
        '</section>' +
        '<section class="test-section-card">' +
        '<div class="section-title">Результат проверки</div>' +
        '<div class="result-facts">' +
        '<div><span class="small">Контракт:</span><br>' + safeHtml(result.contractId) + '</div>' +
        '<div><span class="small">Метод:</span><br>' + safeHtml(result.method) + '</div>' +
        '<div><span class="small">Адрес:</span><br>' + safeHtml(result.target) + '</div>' +
        '</div>' +
        '</section>' +
        '</div>' +
        '<div class="test-details-grid">' +
        '<details class="test-detail" ' + (!passed ? 'open' : '') + '>' +
        '<summary>Ошибки и нарушения</summary>' +
        renderViolations(result) +
        '</details>' +
        '<details class="test-detail">' +
        '<summary>Интеграции</summary>' +
        renderIntegrations(result) +
        '</details>' +
        '<details class="test-detail">' +
        '<summary>Файлы</summary>' +
        renderFileTransfers(result) +
        '</details>' +
        '<details class="test-detail">' +
        '<summary>Ответ</summary>' +
        renderOperationResponse(result) +
        '</details>' +
        '</div>' +
        '</article>'
    );
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
                    backgroundColor: ['#8FBFA3', '#D99AA5'],
                    borderWidth: 0
                }
            ]
        },
        options: {
            plugins: {
                legend: {
                    labels: {
                        color: '#475569'
                    }
                }
            }
        }
    });

    const errorMap = {};

    allResults.forEach(result => {
        const violations = Array.isArray(result.violations) ? result.violations : [];

        violations.forEach(violation => {
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
                    backgroundColor: '#A7B7D9',
                    borderRadius: 8
                }
            ]
        },
        options: {
            scales: {
                x: {
                    ticks: { color: '#475569' },
                    grid: { color: '#E6E3DA' }
                },
                y: {
                    ticks: { color: '#475569' },
                    grid: { color: '#E6E3DA' }
                }
            },
            plugins: {
                legend: {
                    labels: {
                        color: '#475569'
                    }
                }
            }
        }
    });
}

function renderTable() {
    const query = document.getElementById('search').value.toLowerCase();
    const container = document.getElementById('testRows');

    const filtered = allResults.filter(result => {
        const statusMatches =
            currentFilter === 'ALL' ||
            (currentFilter === 'PASS' && result.passed) ||
            (currentFilter === 'FAIL' && !result.passed) ||
            currentFilter === result.method;

        const request = result.request || {};

        const text = [
            result.testId,
            result.name,
            result.description,
            Array.isArray(result.tags) ? result.tags.join(' ') : '',
            result.method,
            result.target,
            result.contractId,
            result.expectedStatus,
            result.actualStatus,
            JSON.stringify(request.query || {}),
            JSON.stringify(request.headers || {}),
            JSON.stringify(request.body || {}),
            JSON.stringify(result.integrations || {}),
            JSON.stringify(result.fileTransfers || {}),
            JSON.stringify(result.responseBody || {}),
            result.responseText,
            ...(Array.isArray(result.violations) ? result.violations.map(violationText) : [])
        ].join(' ').toLowerCase();

        return statusMatches && text.includes(query);
    });

    if (filtered.length === 0) {
        container.innerHTML = '<div class="empty-state">По текущему фильтру тесты не найдены</div>';
        return;
    }

    container.innerHTML = filtered.map(renderTestCard).join('');
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
        return sum + (Array.isArray(result.violations) ? result.violations.length : 0);
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
        '<div class="error error-card">' +
        escapeHtml(error.message) +
        '</div>';
});

/* Request curl component v1 */

function copyCurl(button) {
    const root = button.closest('.curl-component');
    const pre = root ? root.querySelector('pre') : null;
    const text = pre ? pre.innerText : '';

    if (!text) {
        return;
    }

    navigator.clipboard.writeText(text).then(() => {
        const oldText = button.innerText;
        button.innerText = 'Скопировано';

        setTimeout(() => {
            button.innerText = oldText;
        }, 1200);
    });
}

function renderCurlComponent(request) {
    const curl = request && request.curl
        ? String(request.curl)
        : '';

    if (!curl) {
        return (
            '<div class="curl-component">' +
            '<div class="request-part-title">curl запроса</div>' +
            '<span class="small">curl не сохранён для этого запуска</span>' +
            '</div>'
        );
    }

    return (
        '<details class="curl-component">' +
        '<summary>curl запроса</summary>' +
        '<button type="button" class="curl-copy-button" onclick="copyCurl(this); event.stopPropagation()">Скопировать curl</button>' +
        '<pre class="curl-body">' + escapeHtml(curl) + '</pre>' +
        '</details>'
    );
}

function renderRequestInfo(result) {
    const request = result.request || {};
    const method = request.method || result.method;
    const path = request.path || result.target;
    const query = request.query || {};
    const headers = request.headers || {};
    const body = request.body;

    const queryString = Object.keys(query).length === 0
        ? ''
        : '?' + Object.keys(query)
            .map(key => encodeURIComponent(key) + '=' + encodeURIComponent(String(query[key])))
            .join('&');

    const displayUrl = request.fullUrl || (path + queryString);

    return (
        '<div class="request-box">' +
        '<div class="request-line">' +
        '<span class="method">' + safeHtml(method) + '</span> ' +
        '<span>' + safeHtml(displayUrl) + '</span>' +
        '</div>' +
        renderCurlComponent(request) +
        '<div class="request-grid">' +
        renderMapPreview('Query', query) +
        renderMapPreview('Headers', headers) +
        '<div class="request-part">' +
        '<div class="request-part-title">Body</div>' +
        renderJsonPreview(body) +
        '</div>' +
        '</div>' +
        '</div>'
    );
}

/* End request curl component v1 */

/* Request curl component v2 */

function copyCurl(button) {
    const root = button.closest('.curl-component');
    const pre = root ? root.querySelector('pre') : null;
    const text = pre ? pre.innerText : '';

    if (!text) {
        return;
    }

    navigator.clipboard.writeText(text).then(() => {
        const oldText = button.innerText;
        button.innerText = 'Скопировано';

        setTimeout(() => {
            button.innerText = oldText;
        }, 1200);
    });
}

function shellQuoteForUi(value) {
    return "'" + String(value).replace(/'/g, "'\"'\"'") + "'";
}

function buildCurlFromRequest(method, url, headers, body) {
    const parts = [];

    parts.push('curl');
    parts.push('-i');
    parts.push('-X');
    parts.push(shellQuoteForUi(String(method || 'GET').toUpperCase()));
    parts.push(shellQuoteForUi(url));

    Object.keys(headers || {}).forEach(key => {
        parts.push('-H');
        parts.push(shellQuoteForUi(key + ': ' + String(headers[key])));
    });

    if (body !== null && body !== undefined) {
        const hasContentType = Object.keys(headers || {}).some(key => {
            return key.toLowerCase() === 'content-type';
        });

        if (!hasContentType) {
            parts.push('-H');
            parts.push(shellQuoteForUi('Content-Type: application/json'));
        }

        parts.push('--data-raw');
        parts.push(shellQuoteForUi(
            typeof body === 'object'
                ? JSON.stringify(body)
                : String(body)
        ));
    }

    return parts.join(' \\\n  ');
}

function renderCurlComponent(method, url, headers, body) {
    const curl = buildCurlFromRequest(method, url, headers, body);

    return (
        '<details class="curl-component">' +
        '<summary>curl запроса</summary>' +
        '<button type="button" class="curl-copy-button" onclick="copyCurl(this); event.stopPropagation()">Скопировать curl</button>' +
        '<pre class="curl-body">' + escapeHtml(curl) + '</pre>' +
        '</details>'
    );
}

function renderRequestInfo(result) {
    const request = result.request || {};
    const method = request.method || result.method;
    const path = request.path || result.target;
    const query = request.query || {};
    const headers = request.headers || {};
    const body = request.body;

    const queryString = Object.keys(query).length === 0
        ? ''
        : '?' + Object.keys(query)
            .map(key => encodeURIComponent(key) + '=' + encodeURIComponent(String(query[key])))
            .join('&');

    const displayUrl = path + queryString;

    return (
        '<div class="request-box">' +
        '<div class="request-line">' +
        '<span class="method">' + safeHtml(method) + '</span> ' +
        '<span>' + safeHtml(displayUrl) + '</span>' +
        '</div>' +
        renderCurlComponent(method, displayUrl, headers, body) +
        '<div class="request-grid">' +
        renderMapPreview('Query', query) +
        renderMapPreview('Headers', headers) +
        '<div class="request-part">' +
        '<div class="request-part-title">Body</div>' +
        renderJsonPreview(body) +
        '</div>' +
        '</div>' +
        '</div>'
    );
}

/* End request curl component v2 */
