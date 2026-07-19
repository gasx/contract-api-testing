let tests = [];
let activeTestIndex = 0;

function escapeHtml(value) {
    return String(value ?? '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

function value(id) {
    const el = document.getElementById(id);
    return el ? el.value.trim() : '';
}

function setInput(id, val) {
    const el = document.getElementById(id);
    if (el) el.value = val ?? '';
}

function numberValue(id, fallback) {
    const n = Number(value(id));
    return Number.isFinite(n) ? n : fallback;
}

function listValue(id) {
    return value(id)
        .split(',')
        .map(x => x.trim())
        .filter(Boolean);
}

function parseJsonOrNull(text) {
    const raw = String(text || '').trim();
    if (!raw) return null;
    return JSON.parse(raw);
}

function parseExpectedValue(text) {
    const raw = String(text || '').trim();

    if (raw === '') return '';

    try {
        return JSON.parse(raw);
    } catch (e) {
        return raw;
    }
}

function objectToKeyValue(obj) {
    if (!obj || typeof obj !== 'object') return '';

    return Object.entries(obj)
        .map(([key, val]) => key + '=' + val)
        .join(',');
}

function parseKeyValueText(text) {
    const result = {};
    const raw = String(text || '').trim();

    if (!raw) return result;

    raw.split(',')
        .map(x => x.trim())
        .filter(Boolean)
        .forEach(item => {
            const parts = item.split('=');
            const key = String(parts[0] || '').trim();
            const val = String(parts.slice(1).join('=') || '').trim();

            if (key) result[key] = val;
        });

    return result;
}

function showMainTab(name, button) {
    document.querySelectorAll('.builder-lite-tab-content').forEach(tab => {
        tab.classList.remove('builder-lite-tab-content-active');
    });

    document.querySelectorAll('.builder-lite-tabs button').forEach(tab => {
        tab.classList.remove('builder-lite-tab-active');
    });

    const target = document.getElementById('mainTab-' + name);
    if (target) target.classList.add('builder-lite-tab-content-active');

    if (button) button.classList.add('builder-lite-tab-active');

    buildConfig();
}

/* rows */

function clearRows(id) {
    const el = document.getElementById(id);
    if (el) el.innerHTML = '';
}

function addKeyValueRow(containerId, keyPlaceholder, valuePlaceholder, key = '', val = '') {
    const container = document.getElementById(containerId);
    const row = document.createElement('div');

    row.className = 'builder-lite-row';
    row.innerHTML =
        '<input class="row-key" placeholder="' + escapeHtml(keyPlaceholder) + '">' +
        '<input class="row-value" placeholder="' + escapeHtml(valuePlaceholder) + '">' +
        '<button type="button" onclick="this.parentElement.remove(); buildConfig()">×</button>';

    container.appendChild(row);

    row.querySelector('.row-key').value = key;
    row.querySelector('.row-value').value = val;

    row.querySelectorAll('input').forEach(input => {
        input.addEventListener('input', buildConfig);
    });

    return row;
}

function addQueryRow(key = '', val = '') {
    addKeyValueRow('queryRows', 'name', 'value', key, val);
    buildConfig();
}

function addHeaderRow(key = '', val = '') {
    addKeyValueRow('headerRows', 'Header-Name', 'value', key, val);
    buildConfig();
}

function readMap(containerId) {
    const result = {};

    document.querySelectorAll('#' + containerId + ' .builder-lite-row').forEach(row => {
        const key = row.querySelector('.row-key').value.trim();
        const val = row.querySelector('.row-value').value.trim();

        if (key) result[key] = val;
    });

    return result;
}

/* asserts */

function addAssertRow(path = '', operator = 'eq', expected = '') {
    const container = document.getElementById('assertRows');
    const row = document.createElement('div');

    row.className = 'builder-lite-row builder-lite-assert-row';
    row.innerHTML =
        '<input class="assert-path" placeholder="response.id">' +
        '<select class="assert-operator">' +
        '<option>eq</option>' +
        '<option>notEq</option>' +
        '<option>exists</option>' +
        '<option>isNull</option>' +
        '<option>notNull</option>' +
        '<option>gt</option>' +
        '<option>gte</option>' +
        '<option>lt</option>' +
        '<option>lte</option>' +
        '<option>in</option>' +
        '<option>contains</option>' +
        '<option>matches</option>' +
        '<option>startsWith</option>' +
        '<option>endsWith</option>' +
        '<option>type</option>' +
        '</select>' +
        '<input class="assert-expected" placeholder="1 или text или true">' +
        '<button type="button" onclick="this.parentElement.remove(); buildConfig()">×</button>';

    container.appendChild(row);

    row.querySelector('.assert-path').value = path;
    row.querySelector('.assert-operator').value = operator;
    row.querySelector('.assert-expected').value =
        typeof expected === 'object'
            ? JSON.stringify(expected)
            : String(expected ?? '');

    row.querySelectorAll('input, select').forEach(input => {
        input.addEventListener('input', buildConfig);
        input.addEventListener('change', buildConfig);
    });

    buildConfig();
}

function readAssertConfig() {
    const result = {};

    document.querySelectorAll('#assertRows .builder-lite-assert-row').forEach(row => {
        const path = row.querySelector('.assert-path').value.trim();
        const operator = row.querySelector('.assert-operator').value.trim();
        const expectedRaw = row.querySelector('.assert-expected').value.trim();

        if (!path || !operator) return;

        result[path] = {
            [operator]: parseExpectedValue(expectedRaw)
        };
    });

    return result;
}

/* integration auth */

function toggleIntegrationAuthFields(select) {
    const card = select.closest('.builder-lite-integration');
    const type = select.value;

    const bearerBlock = card.querySelector('.integration-auth-bearer');
    const basicBlock = card.querySelector('.integration-auth-basic');

    if (bearerBlock) {
        bearerBlock.style.display = type === 'bearer' ? 'block' : 'none';
    }

    if (basicBlock) {
        basicBlock.style.display = type === 'basic' ? 'block' : 'none';
    }
}

function readIntegrationAuth(row) {
    const type = row.querySelector('.integration-auth-type')?.value || 'none';

    if (type === 'none') {
        return null;
    }

    if (type === 'bearer') {
        const token = row.querySelector('.integration-bearer-token').value.trim();
        const tokenEnv = row.querySelector('.integration-bearer-token-env').value.trim();

        const auth = { type: 'bearer' };

        if (token) auth.token = token;
        if (tokenEnv) auth.tokenEnv = tokenEnv;

        return auth;
    }

    if (type === 'basic') {
        const username = row.querySelector('.integration-basic-username').value.trim();
        const password = row.querySelector('.integration-basic-password').value.trim();
        const usernameEnv = row.querySelector('.integration-basic-username-env').value.trim();
        const passwordEnv = row.querySelector('.integration-basic-password-env').value.trim();

        const auth = { type: 'basic' };

        if (username) auth.username = username;
        if (password) auth.password = password;
        if (usernameEnv) auth.usernameEnv = usernameEnv;
        if (passwordEnv) auth.passwordEnv = passwordEnv;

        return auth;
    }

    return null;
}

/* integrations */

function addIntegrationRow(config = null, name = '') {
    const container = document.getElementById('integrationRows');
    const row = document.createElement('div');

    const type = config?.type || 'http';

    row.className = 'builder-lite-integration';
    row.innerHTML =
        '<div class="builder-lite-grid-2">' +
        '<div>' +
        '<label>Имя действия</label>' +
        '<input class="integration-name" placeholder="loadUser1">' +
        '</div>' +
        '<div>' +
        '<label>Тип</label>' +
        '<select class="integration-type" onchange="toggleIntegrationFields(this); buildConfig()">' +
        '<option value="http">HTTP request</option>' +
        '<option value="mock">Mock</option>' +
        '<option value="kafka">Kafka produce</option>' +
        '<option value="kafka-consume">Kafka consume</option>' +
        '</select>' +
        '</div>' +
        '</div>' +

        '<div class="integration-fields integration-http">' +
        '<div class="builder-lite-grid-2">' +
        '<div><label>Method</label><select class="integration-http-method"><option>GET</option><option>POST</option><option>PUT</option><option>PATCH</option><option>DELETE</option></select></div>' +
        '<div><label>Status</label><input class="integration-http-status" placeholder="200"></div>' +
        '</div>' +
        '<label>URL</label>' +
        '<input class="integration-http-url" placeholder="https://api.example.test/auth">' +
        '</div>' +

        '<div class="integration-fields integration-mock">' +
        '<label>Status</label>' +
        '<input class="integration-mock-status" placeholder="200">' +
        '<label>Data JSON</label>' +
        '<textarea class="integration-mock-data builder-lite-code-input" placeholder="{&quot;id&quot;:1}"></textarea>' +
        '</div>' +

        '<div class="integration-fields integration-kafka">' +
        '<label>Hosts</label>' +
        '<input class="integration-kafka-hosts" placeholder="localhost:9092">' +
        '<label>Topic</label>' +
        '<input class="integration-kafka-topic" placeholder="events.topic">' +
        '<label>Message JSON</label>' +
        '<textarea class="integration-kafka-message builder-lite-code-input" placeholder="{&quot;event&quot;:&quot;CREATED&quot;}"></textarea>' +
        '</div>' +

        '<div class="integration-fields integration-kafka-consume">' +
        '<label>Hosts</label>' +
        '<input class="integration-consume-hosts" placeholder="localhost:9092">' +
        '<label>Topic</label>' +
        '<input class="integration-consume-topic" placeholder="events.topic">' +
        '<label>Timeout, ms</label>' +
        '<input class="integration-consume-timeout" placeholder="10000">' +
        '</div>' +

        '<details class="builder-lite-details">' +
        '<summary>Авторизация, переменные и retry</summary>' +

        '<div class="integration-http-extra">' +
        '<label>Auth type</label>' +
        '<select class="integration-auth-type" onchange="toggleIntegrationAuthFields(this); buildConfig()">' +
        '<option value="none">No auth</option>' +
        '<option value="bearer">Bearer token</option>' +
        '<option value="basic">Basic auth</option>' +
        '</select>' +

        '<div class="integration-auth-bearer" style="display:none">' +
        '<div class="builder-lite-note">Лучше использовать Token ENV, чтобы не хранить секрет в JSON.</div>' +
        '<div class="builder-lite-grid-2">' +
        '<div><label>Token</label><input class="integration-bearer-token" placeholder="eyJhbGciOi..."></div>' +
        '<div><label>Token ENV</label><input class="integration-bearer-token-env" placeholder="TEST_API_TOKEN"></div>' +
        '</div>' +
        '</div>' +

        '<div class="integration-auth-basic" style="display:none">' +
        '<div class="builder-lite-note">Лучше использовать ENV-поля вместо username/password.</div>' +
        '<div class="builder-lite-grid-2">' +
        '<div><label>Username</label><input class="integration-basic-username" placeholder="user"></div>' +
        '<div><label>Password</label><input class="integration-basic-password" type="password" placeholder="password"></div>' +
        '</div>' +
        '<div class="builder-lite-grid-2">' +
        '<div><label>Username ENV</label><input class="integration-basic-username-env" placeholder="TEST_USERNAME"></div>' +
        '<div><label>Password ENV</label><input class="integration-basic-password-env" placeholder="TEST_PASSWORD"></div>' +
        '</div>' +
        '</div>' +
        '</div>' +

        '<label>Save as global vars</label>' +
        '<input class="integration-save-as" placeholder="userId=response.id,username=response.username">' +
        '<label>Extract vars</label>' +
        '<input class="integration-extract" placeholder="token=response.token">' +
        '<div class="builder-lite-grid-2">' +
        '<div><label>Retry attempts</label><input class="integration-retry-attempts" placeholder="1"></div>' +
        '<div><label>Retry delay, ms</label><input class="integration-retry-delay" placeholder="0"></div>' +
        '</div>' +
        '</details>' +

        '<button type="button" class="builder-lite-remove" onclick="this.closest(\'.builder-lite-integration\').remove(); buildConfig()">Удалить действие</button>';

    container.appendChild(row);

    row.querySelector('.integration-name').value = name || '';
    row.querySelector('.integration-type').value = type;

    fillIntegrationRow(row, config || { type });

    row.querySelectorAll('input, textarea, select').forEach(input => {
        input.addEventListener('input', buildConfig);
        input.addEventListener('change', buildConfig);
    });

    toggleIntegrationFields(row.querySelector('.integration-type'));
    toggleIntegrationAuthFields(row.querySelector('.integration-auth-type'));

    buildConfig();
}

function fillIntegrationRow(row, config) {
    const type = config.type || 'http';

    if (type === 'http') {
        row.querySelector('.integration-http-method').value = config.method || 'GET';
        row.querySelector('.integration-http-status').value = config.expectedStatus || 200;
        row.querySelector('.integration-http-url').value = config.url || '';

        const auth = config.auth || null;

        row.querySelector('.integration-auth-type').value = auth?.type || 'none';
        row.querySelector('.integration-bearer-token').value = auth?.token || '';
        row.querySelector('.integration-bearer-token-env').value = auth?.tokenEnv || '';
        row.querySelector('.integration-basic-username').value = auth?.username || '';
        row.querySelector('.integration-basic-password').value = auth?.password || '';
        row.querySelector('.integration-basic-username-env').value = auth?.usernameEnv || '';
        row.querySelector('.integration-basic-password-env').value = auth?.passwordEnv || '';
    }

    if (type === 'mock') {
        row.querySelector('.integration-mock-status').value = config.status || 200;
        row.querySelector('.integration-mock-data').value = config.data ? JSON.stringify(config.data, null, 2) : '{}';
    }

    if (type === 'kafka') {
        row.querySelector('.integration-kafka-hosts').value = Array.isArray(config.hosts) ? config.hosts.join(',') : '';
        row.querySelector('.integration-kafka-topic').value = config.topic || '';
        row.querySelector('.integration-kafka-message').value = config.message ? JSON.stringify(config.message, null, 2) : '{}';
    }

    if (type === 'kafka-consume') {
        row.querySelector('.integration-consume-hosts').value = Array.isArray(config.hosts) ? config.hosts.join(',') : '';
        row.querySelector('.integration-consume-topic').value = config.topic || '';
        row.querySelector('.integration-consume-timeout').value = config.timeoutMs || '';
    }

    row.querySelector('.integration-save-as').value = objectToKeyValue(config.saveAs);
    row.querySelector('.integration-extract').value = objectToKeyValue(config.extract);

    if (config.retry) {
        row.querySelector('.integration-retry-attempts').value = config.retry.attempts || '';
        row.querySelector('.integration-retry-delay').value = config.retry.delayMs || '';
    }
}

function toggleIntegrationFields(select) {
    const card = select.closest('.builder-lite-integration');
    const type = select.value;

    card.querySelectorAll('.integration-fields').forEach(block => {
        block.style.display = 'none';
    });

    const target = card.querySelector('.integration-' + type);

    if (target) {
        target.style.display = 'block';
    }

    const authExtra = card.querySelector('.integration-http-extra');

    if (authExtra) {
        authExtra.style.display = type === 'http' ? 'block' : 'none';
    }
}

function readIntegrations() {
    const integrations = {};

    document.querySelectorAll('#integrationRows .builder-lite-integration').forEach(row => {
        const name = row.querySelector('.integration-name').value.trim();
        const type = row.querySelector('.integration-type').value;

        if (!name) return;

        const integration = { type };

        if (type === 'http') {
            integration.method = row.querySelector('.integration-http-method').value.trim() || 'GET';
            integration.url = row.querySelector('.integration-http-url').value.trim();
            integration.expectedStatus = Number(row.querySelector('.integration-http-status').value.trim()) || 200;

            const auth = readIntegrationAuth(row);

            if (!integration.url) return;

            if (auth) {
                integration.auth = auth;
            }
        }

        if (type === 'mock') {
            integration.status = Number(row.querySelector('.integration-mock-status').value.trim()) || 200;

            const data = parseJsonOrNull(row.querySelector('.integration-mock-data').value);
            if (data !== null) integration.data = data;
        }

        if (type === 'kafka') {
            integration.hosts = row.querySelector('.integration-kafka-hosts').value.split(',').map(x => x.trim()).filter(Boolean);
            integration.topic = row.querySelector('.integration-kafka-topic').value.trim();
            integration.messageFormat = 'json';
            integration.message = parseJsonOrNull(row.querySelector('.integration-kafka-message').value) || {};

            if (integration.hosts.length === 0 || !integration.topic) return;
        }

        if (type === 'kafka-consume') {
            integration.hosts = row.querySelector('.integration-consume-hosts').value.split(',').map(x => x.trim()).filter(Boolean);
            integration.topic = row.querySelector('.integration-consume-topic').value.trim();
            integration.messageFormat = 'json';

            const timeoutMs = Number(row.querySelector('.integration-consume-timeout').value.trim());
            if (Number.isFinite(timeoutMs) && timeoutMs > 0) integration.timeoutMs = timeoutMs;

            if (integration.hosts.length === 0 || !integration.topic) return;
        }

        const saveAs = parseKeyValueText(row.querySelector('.integration-save-as').value);
        const extract = parseKeyValueText(row.querySelector('.integration-extract').value);
        const retryAttempts = Number(row.querySelector('.integration-retry-attempts').value.trim());
        const retryDelay = Number(row.querySelector('.integration-retry-delay').value.trim());

        if (Object.keys(saveAs).length > 0) integration.saveAs = saveAs;
        if (Object.keys(extract).length > 0) integration.extract = extract;

        if (
            (Number.isFinite(retryAttempts) && retryAttempts > 1) ||
            (Number.isFinite(retryDelay) && retryDelay > 0)
        ) {
            integration.retry = {
                attempts: Number.isFinite(retryAttempts) && retryAttempts > 0 ? retryAttempts : 1,
                delayMs: Number.isFinite(retryDelay) && retryDelay > 0 ? retryDelay : 0
            };
        }

        integrations[name] = integration;
    });

    return integrations;
}

function renderIntegrations(integrations) {
    clearRows('integrationRows');

    Object.entries(integrations || {}).forEach(([name, config]) => {
        addIntegrationRow(config, name);
    });
}

/* tests */

function readCurrentTestFromForm() {
    const test = {
        testId: value('testId') || makeTestId(value('testName')) || 'NEW_API_TEST',
        method: value('method') || 'GET',
        path: value('path') || '/',
        expectedStatus: numberValue('expectedStatus', 200),
        contractFile: value('contractFile') || 'schemas/jsonplaceholder-simple/post.schema.json'
    };

    const name = value('testName');
    const description = value('testDescription');
    const tags = listValue('tags');
    const beforeTest = listValue('beforeTest');
    const query = readMap('queryRows');
    const headers = readMap('headerRows');
    const body = parseJsonOrNull(value('bodyJson'));
    const assertConfig = readAssertConfig();

    if (name) test.name = name;
    if (description) test.description = description;
    if (tags.length > 0) test.tags = tags;
    if (beforeTest.length > 0) test.beforeTest = beforeTest;
    if (Object.keys(query).length > 0) test.query = query;
    if (Object.keys(headers).length > 0) test.headers = headers;
    if (body !== null) test.body = body;
    if (Object.keys(assertConfig).length > 0) test.assert = assertConfig;

    return test;
}

function makeTestId(title) {
    const raw = String(title || '')
        .toUpperCase()
        .replace(/[^A-ZА-Я0-9]+/g, '_')
        .replace(/^_+|_+$/g, '');

    return raw || '';
}

function saveActiveTest() {
    if (tests.length === 0) return;
    tests[activeTestIndex] = readCurrentTestFromForm();
}

function renderActiveTestToForm() {
    const test = tests[activeTestIndex];
    if (!test) return;

    setInput('testId', test.testId || '');
    setInput('testName', test.name || '');
    setInput('testDescription', test.description || '');
    setInput('tags', Array.isArray(test.tags) ? test.tags.join(',') : '');
    setInput('beforeTest', Array.isArray(test.beforeTest) ? test.beforeTest.join(',') : '');
    setInput('method', test.method || 'GET');
    setInput('expectedStatus', test.expectedStatus || 200);
    setInput('path', test.path || '/');
    setInput('contractFile', test.contractFile || 'schemas/jsonplaceholder-simple/post.schema.json');
    setInput('bodyJson', test.body ? JSON.stringify(test.body, null, 2) : '');

    clearRows('queryRows');
    clearRows('headerRows');
    clearRows('assertRows');

    Object.entries(test.query || {}).forEach(([key, val]) => {
        addKeyValueRow('queryRows', 'name', 'value', key, val);
    });

    Object.entries(test.headers || {}).forEach(([key, val]) => {
        addKeyValueRow('headerRows', 'Header-Name', 'value', key, val);
    });

    Object.entries(test.assert || {}).forEach(([path, assertion]) => {
        const operator = Object.keys(assertion || {})[0] || 'eq';
        addAssertRow(path, operator, assertion[operator]);
    });

    renderTestsList();
    buildConfig(false);
}

function renderTestsList() {
    const container = document.getElementById('testsList');

    container.innerHTML = tests.map((test, index) => {
        const active = index === activeTestIndex ? ' builder-lite-test-active' : '';
        const title = test.name || test.testId || 'Без названия';
        const meta = (test.method || 'GET') + ' ' + (test.path || '/');

        return (
            '<button class="builder-lite-test' + active + '" onclick="selectTest(' + index + ')">' +
            '<b>' + escapeHtml(title) + '</b>' +
            '<small>' + escapeHtml(meta) + '</small>' +
            '</button>'
        );
    }).join('');
}

function selectTest(index) {
    try {
        saveActiveTest();
    } catch (e) {
        alert('Исправь текущий тест: ' + e.message);
        return;
    }

    activeTestIndex = index;
    renderActiveTestToForm();
}

function createDefaultTest(index) {
    return {
        testId: 'GET_POST_' + index,
        name: 'Новый GET-тест ' + index,
        method: 'GET',
        path: '/posts/' + index,
        expectedStatus: 200,
        contractFile: 'schemas/jsonplaceholder-simple/post.schema.json',
        assert: {
            status: { eq: 200 },
            'response.id': { eq: index }
        }
    };
}

function addTest() {
    try {
        if (tests.length > 0) saveActiveTest();
    } catch (e) {
        alert('Исправь текущий тест: ' + e.message);
        return;
    }

    tests.push(createDefaultTest(tests.length + 1));
    activeTestIndex = tests.length - 1;
    renderActiveTestToForm();
}

function duplicateTest() {
    try {
        saveActiveTest();
    } catch (e) {
        alert('Исправь текущий тест: ' + e.message);
        return;
    }

    const current = tests[activeTestIndex];
    const copy = JSON.parse(JSON.stringify(current));

    copy.testId = current.testId + '_COPY';
    copy.name = (current.name || current.testId) + ' copy';

    tests.splice(activeTestIndex + 1, 0, copy);
    activeTestIndex += 1;

    renderActiveTestToForm();
}

function deleteTest() {
    if (tests.length <= 1) {
        alert('Должен остаться хотя бы один тест');
        return;
    }

    tests.splice(activeTestIndex, 1);
    activeTestIndex = Math.max(0, activeTestIndex - 1);
    renderActiveTestToForm();
}

/* config */

function buildConfig(shouldSaveActive = true) {
    try {
        if (shouldSaveActive && tests.length > 0) saveActiveTest();

        renderTestsList();

        const config = {
            baseUrl: value('baseUrl') || 'https://jsonplaceholder.typicode.com',
            timeoutMs: numberValue('timeoutMs', 15000)
        };

        const integrations = readIntegrations();

        if (Object.keys(integrations).length > 0) {
            config.integrations = integrations;
        }

        config.tests = tests;

        const text = JSON.stringify(config, null, 2);
        document.getElementById('jsonPreview').innerText = text;

        return text;
    } catch (e) {
        document.getElementById('jsonPreview').innerText = 'Ошибка: ' + e.message;
        return null;
    }
}

async function runScenarioFromUi() {
    const jsonText = buildConfig();

    if (!jsonText) return;

    showMainTab('result', document.querySelector('.builder-lite-tabs button:nth-child(3)'));

    const status = document.getElementById('runStatus');
    status.innerHTML = 'Запуск...';

    try {
        const response = await fetch('/api/run', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: jsonText
        });

        const text = await response.text();
        let data;

        try {
            data = JSON.parse(text);
        } catch (e) {
            data = { ok: false, error: text };
        }

        if (!response.ok || !data.ok) {
            status.innerHTML =
                '<div class="builder-lite-run-fail">Ошибка запуска</div>' +
                '<pre>' + escapeHtml(data.error || text) + '</pre>';
            return;
        }

        const passed = Number(data.passed || 0);
        const failed = Number(data.failed || 0);
        const total = Number(data.total || 0);

        status.innerHTML =
            '<div class="' + (failed === 0 ? 'builder-lite-run-pass' : 'builder-lite-run-fail') + '">' +
            (failed === 0 ? 'Успешно' : 'Есть ошибки') +
            '</div>' +
            '<div>Всего: ' + total + '</div>' +
            '<div>Успешно: ' + passed + '</div>' +
            '<div>Ошибок: ' + failed + '</div>' +
            '<div>Отчёты: ' + escapeHtml(data.outDir || '-') + '</div>' +
            '<div class="builder-lite-note">Открой вкладку “Отчёт”, чтобы посмотреть карточки тестов.</div>';
    } catch (e) {
        status.innerHTML =
            '<div class="builder-lite-run-fail">Ошибка запуска</div>' +
            '<pre>' + escapeHtml(e.message) + '</pre>';
    }
}

async function copyConfig() {
    const text = buildConfig();
    if (!text) return;

    try {
        await navigator.clipboard.writeText(text);
        alert('JSON скопирован');
    } catch (e) {
        alert('Не удалось скопировать. Открой вкладку JSON и скопируй вручную.');
    }
}

function downloadConfig() {
    const text = buildConfig();
    if (!text) return;

    const blob = new Blob([text], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');

    link.href = url;
    link.download = 'run_config.json';
    link.click();

    URL.revokeObjectURL(url);
}

function init() {
    [
        'baseUrl',
        'timeoutMs',
        'testId',
        'testName',
        'testDescription',
        'tags',
        'beforeTest',
        'method',
        'expectedStatus',
        'path',
        'contractFile',
        'bodyJson'
    ].forEach(id => {
        const el = document.getElementById(id);

        if (el) {
            el.addEventListener('input', buildConfig);
            el.addEventListener('change', buildConfig);
        }
    });

    tests = [
        {
            testId: 'GET_POST_1_RETURNS_POST',
            name: 'Получение поста №1 возвращает данные поста',
            method: 'GET',
            path: '/posts/1',
            expectedStatus: 200,
            contractFile: 'schemas/jsonplaceholder-simple/post.schema.json',
            assert: {
                status: { eq: 200 },
                'response.id': { eq: 1 },
                'response.userId': { eq: 1 }
            }
        }
    ];

    activeTestIndex = 0;
    renderActiveTestToForm();
    buildConfig(false);
}

document.addEventListener('DOMContentLoaded', init);

/* Contract storage UI */

document.addEventListener('DOMContentLoaded', function () {
    setTimeout(initContractStorageUi, 0);
});

function initContractStorageUi() {
    const tabs = document.querySelector('.builder-lite-tabs') || document.querySelector('.tabs');
    const main = document.querySelector('.main') || document.querySelector('.builder-lite-main');

    if (!tabs || !main || document.getElementById('mainTab-contracts')) {
        return;
    }

    const contractsButton = document.createElement('button');
    contractsButton.type = 'button';
    contractsButton.innerText = 'Контракты';
    contractsButton.onclick = function () {
        showMainTab('contracts', contractsButton);
        loadContracts();
    };

    tabs.appendChild(contractsButton);

    const section = document.createElement('section');
    section.id = 'mainTab-contracts';
    section.className = 'builder-lite-tab-content';

    section.innerHTML =
        '<section class="panel">' +
        '<div class="panel-head">' +
        '<div>' +
        '<div class="panel-title" style="margin:0">Хранилище контрактов</div>' +
        '<div class="note">Контракты сохраняются в configs/schemas и потом выбираются в поле Contract file.</div>' +
        '</div>' +
        '<button type="button" onclick="loadContracts()">Обновить список</button>' +
        '</div>' +

        '<label>Файл контракта с компьютера</label>' +
        '<input id="contractFileInput" type="file" accept=".json,application/json">' +

        '<label>Сохранить как</label>' +
        '<input id="contractStoragePath" placeholder="schemas/uploaded/user.schema.json">' +

        '<div class="row-actions">' +
        '<button type="button" class="primary-button" onclick="uploadContractFromUi()">Загрузить / заменить</button>' +
        '<button type="button" onclick="clearContractUploadForm()">Очистить</button>' +
        '</div>' +

        '<div id="contractUploadStatus" class="note"></div>' +
        '</section>' +

        '<section class="panel">' +
        '<div class="panel-title">Список контрактов</div>' +
        '<div id="contractsList" class="dynamic-list"></div>' +
        '</section>';

    main.appendChild(section);

    loadContracts();
}

async function loadContracts() {
    const list = document.getElementById('contractsList');

    if (!list) {
        return;
    }

    list.innerHTML = '<div class="note">Загружаю список контрактов...</div>';

    try {
        const response = await fetch('/api/contracts');
        const data = await response.json();

        if (!data.ok) {
            list.innerHTML = '<div class="builder-lite-run-fail">Ошибка: ' + contractEscapeHtml(data.error || 'unknown') + '</div>';
            return;
        }

        renderContractsList(data.contracts || []);
    } catch (e) {
        list.innerHTML = '<div class="builder-lite-run-fail">Ошибка загрузки контрактов: ' + contractEscapeHtml(e.message) + '</div>';
    }
}

function renderContractsList(contracts) {
    const list = document.getElementById('contractsList');

    if (!list) {
        return;
    }

    if (!contracts.length) {
        list.innerHTML = '<div class="note">Контрактов пока нет. Загрузи JSON-файл выше.</div>';
        return;
    }

    list.innerHTML = '';

    contracts.forEach(function (item) {
        const row = document.createElement('div');
        row.className = 'integration-card';
        row.style.display = 'grid';
        row.style.gridTemplateColumns = 'minmax(0, 1fr) auto auto';
        row.style.gap = '10px';
        row.style.alignItems = 'center';

        const info = document.createElement('div');

        const title = document.createElement('div');
        title.style.fontWeight = '700';
        title.innerText = item.path;

        const meta = document.createElement('div');
        meta.className = 'note';
        meta.style.margin = '4px 0 0';
        meta.innerText = formatContractSize(item.sizeBytes) + ' · ' + formatContractDate(item.modifiedAt);

        info.appendChild(title);
        info.appendChild(meta);

        const useButton = document.createElement('button');
        useButton.type = 'button';
        useButton.innerText = 'Выбрать';
        useButton.onclick = function () {
            selectStoredContract(item.path);
        };

        const replaceButton = document.createElement('button');
        replaceButton.type = 'button';
        replaceButton.innerText = 'Заменить';
        replaceButton.onclick = function () {
            prepareReplaceContract(item.path);
        };

        row.appendChild(info);
        row.appendChild(useButton);
        row.appendChild(replaceButton);

        list.appendChild(row);
    });
}

function selectStoredContract(path) {
    const input = document.getElementById('contractFile');

    if (input) {
        input.value = path;
        input.dispatchEvent(new Event('input', { bubbles: true }));
    }

    if (typeof buildConfig === 'function') {
        buildConfig();
    }

    const scenarioButton = document.querySelector('.builder-lite-tabs button');
    if (typeof showMainTab === 'function' && scenarioButton) {
        showMainTab('scenario', scenarioButton);
    }
}

function prepareReplaceContract(path) {
    const pathInput = document.getElementById('contractStoragePath');
    const fileInput = document.getElementById('contractFileInput');
    const status = document.getElementById('contractUploadStatus');

    if (pathInput) {
        pathInput.value = path;
    }

    if (status) {
        status.innerHTML = 'Выбран контракт для замены: <b>' + contractEscapeHtml(path) + '</b>. Теперь выбери новый JSON-файл и нажми “Загрузить / заменить”.';
    }

    if (fileInput) {
        fileInput.focus();
    }
}

async function uploadContractFromUi() {
    const fileInput = document.getElementById('contractFileInput');
    const pathInput = document.getElementById('contractStoragePath');
    const status = document.getElementById('contractUploadStatus');

    if (!fileInput || !fileInput.files || !fileInput.files[0]) {
        if (status) {
            status.innerText = 'Сначала выбери JSON-файл с компьютера.';
        }
        return;
    }

    const file = fileInput.files[0];
    const content = await file.text();
    const targetPath = pathInput ? pathInput.value.trim() : '';

    if (status) {
        status.innerText = 'Загружаю контракт...';
    }

    try {
        const response = await fetch('/api/contracts', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                fileName: file.name,
                path: targetPath || null,
                content: content
            })
        });

        const data = await response.json();

        if (!response.ok || !data.ok) {
            if (status) {
                status.innerHTML = '<span class="builder-lite-run-fail">Ошибка: ' + contractEscapeHtml(data.error || 'unknown') + '</span>';
            }
            return;
        }

        if (status) {
            status.innerHTML = '<span class="builder-lite-run-pass">Контракт сохранён: ' + contractEscapeHtml(data.path) + '</span>';
        }

        if (pathInput) {
            pathInput.value = data.path || '';
        }

        selectStoredContract(data.path);
        loadContracts();
    } catch (e) {
        if (status) {
            status.innerHTML = '<span class="builder-lite-run-fail">Ошибка: ' + contractEscapeHtml(e.message) + '</span>';
        }
    }
}

function clearContractUploadForm() {
    const fileInput = document.getElementById('contractFileInput');
    const pathInput = document.getElementById('contractStoragePath');
    const status = document.getElementById('contractUploadStatus');

    if (fileInput) {
        fileInput.value = '';
    }

    if (pathInput) {
        pathInput.value = '';
    }

    if (status) {
        status.innerText = '';
    }
}

function formatContractSize(sizeBytes) {
    const n = Number(sizeBytes || 0);

    if (n < 1024) {
        return n + ' B';
    }

    if (n < 1024 * 1024) {
        return Math.round(n / 1024) + ' KB';
    }

    return (n / 1024 / 1024).toFixed(1) + ' MB';
}

function formatContractDate(value) {
    try {
        return new Date(value).toLocaleString();
    } catch (e) {
        return value || '';
    }
}

function contractEscapeHtml(value) {
    return String(value ?? '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

/* End contract storage UI */
