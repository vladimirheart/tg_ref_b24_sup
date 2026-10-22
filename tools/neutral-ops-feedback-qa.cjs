/* Заполненная визуальная приёмка 01-281. Только fixture HTML и заблокированные записи. */
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const assert = require('node:assert/strict');
const root = path.resolve(__dirname, '..');
const resources = path.join(root, 'spring-panel/src/main/resources');
const captures = path.join(root, 'spring-panel/target/neutral-ops-rendered');
const output = path.join(root, 'spring-panel/target/neutral-ops-feedback');
const activity = {
  business_hours_start: 9, business_hours_end: 18, max_count: 7,
  hour_labels: Array.from({ length: 24 }, (_, hour) => String(hour).padStart(2, '0') + ':00'),
  matrix: ['Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб', 'Вс'].map((day_label, day) => ({ day_label, values: Array.from({ length: 24 }, (_, hour) => hour >= 8 && hour <= 21 ? ((day * 5 + hour * 3) % 7) + 1 + [2, 4, 1, 5, 7, 0, 0][day] : 0) }))
};
const payload = { stats: { current: { total: 128 }, previous: { total: 112 } }, time_stats: { formatted_total: '24 ч 15 мин', formatted_avg: '11 мин', resolved_count: 116 },
  activity_stats: activity, available_restaurants: ['Центральный'], staff_time_stats: [],
  charts: { channel: { Telegram: 72, VK: 36, MAX: 20 }, business: { Поддержка: 86, Доставка: 42 }, network: { Корпоративные: 78, Франчайзи: 50 }, category: { Поддержка: 62, Заказы: 40, Прочее: 26 }, bot_product: { Панель: 72, Приложение: 56 }, city: { Москва: 86, Казань: 42 }, restaurant: { Центральный: 78, Северный: 50 } } };

async function main() {
  fs.mkdirSync(output, { recursive: true });
  const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
  const server = http.createServer((req, res) => {
    const pathname = new URL(req.url, 'http://localhost').pathname;
    const base = pathname.startsWith('/screens/') ? captures : path.join(resources, 'static');
    const name = pathname.startsWith('/screens/') ? pathname.slice(9) + '.html' : pathname.slice(1);
    const file = path.resolve(base, name);
    if (!file.startsWith(base + path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) { res.writeHead(404); res.end('{}'); return; }
    const mime = { '.html': 'text/html; charset=utf-8', '.css': 'text/css', '.js': 'text/javascript', '.svg': 'image/svg+xml', '.png': 'image/png', '.woff2': 'font/woff2' };
    res.setHeader('Content-Type', mime[path.extname(file)] || 'application/octet-stream');
    fs.createReadStream(file).pipe(res);
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const origin = `http://127.0.0.1:${server.address().port}`;
  const browser = await chromium.launch({ headless: true });
  const report = [];
  try {
    for (const theme of ['light', 'dark']) for (const width of [320, 390, 768, 1440, 1920]) {
      const touch = width <= 390;
      const context = await browser.newContext({ viewport: { width, height: 1000 }, colorScheme: theme, hasTouch: touch, isMobile: touch, reducedMotion: 'reduce' });
      await context.addInitScript(theme => { localStorage.setItem('iguana:theme', theme); localStorage.setItem('iguana:theme-palette', 'neo'); localStorage.setItem('sidebarPinned', '1'); }, theme);
      await context.route('**/*', route => {
        if (!route.request().url().startsWith(origin + '/')) return route.abort();
        if (new URL(route.request().url()).pathname === '/api/dashboard/data') return route.fulfill({ json: payload });
        if (new URL(route.request().url()).pathname === '/api/dashboard/restaurant-options') return route.fulfill({ json: { restaurants: ['Центральный'] } });
        if (!['GET', 'HEAD'].includes(route.request().method())) return route.fulfill({ status: 409, body: '{}' });
        return route.continue();
      });
      const page = await context.newPage();
      page.on('pageerror', error => console.error('PAGE:', error.message));
      await page.goto(origin + '/screens/dashboard/index', { waitUntil: 'load' });
      await page.waitForFunction(() => window.Chart?.getChart('activityTrendChart'));
      const chart = await page.evaluate(() => {
        const chart = Chart.getChart('activityTrendChart');
        return { type: chart.config.type, total: chart.data.datasets[0].data, working: chart.data.datasets[1].data, overflow: document.documentElement.scrollWidth > innerWidth + 1 };
      });
      assert.equal(chart.type, 'line');
      assert.deepEqual(chart.total, activity.matrix.map(row => row.values.reduce((a, b) => a + b, 0)));
      assert.deepEqual(chart.working, activity.matrix.map(row => row.values.slice(9, 18).reduce((a, b) => a + b, 0)));
      assert(!chart.overflow);
      await page.locator('[data-panel-id="activity-trend"]').screenshot({ path: path.join(output, `reports-${theme}-${width}.png`) });
      await page.evaluate(() => { updateActivityTrend(null); });
      assert(await page.locator('[data-trend-empty]').isVisible(), 'Пустой ответ API имеет отдельное честное состояние');
      await page.evaluate(stats => updateActivityTrend(stats), activity);

      await page.goto(origin + '/screens/settings/index', { waitUntil: 'load' });
      await page.evaluate(() => {
        window.__settingsLocationsTreeRuntime = null;
        const runtime = SettingsLocationsTreeRuntime.mount({ initialLocations: { 'Тестовый бизнес': { Корпоративные: { Москва: ['Центральная', 'Северная'] } }, 'Второй бизнес': { Франчайзи: { Казань: ['Центральная'] } } } });
        runtime.buildLocationsTree();
        bootstrap.Modal.getOrCreateInstance(document.getElementById('locationsModal')).show();
      });
      const toggle = page.locator('#locationsModal .location-tree-toggle').first();
      await toggle.waitFor({ state: 'visible' });
      const size = await toggle.boundingBox();
      assert.equal(Math.round(size.height), touch ? 44 : 24);
      assert.equal(Math.round(size.width), touch ? 44 : 24);
      const expanded = await toggle.getAttribute('aria-expanded');
      await toggle.click();
      assert.notEqual(await toggle.getAttribute('aria-expanded'), expanded, 'Работает настоящее раскрытие ветки');
      await toggle.focus();
      assert(await toggle.evaluate(el => el === document.activeElement));
      await page.screenshot({ path: path.join(output, `locations-${theme}-${width}.png`) });

      await page.goto(origin + '/screens/dialogs/index', { waitUntil: 'load' });
      const actionSize = await page.evaluate(() => {
        let button = document.querySelector('.dialog-table .dialog-actions-toggle');
        if (!button) {
          const row = document.querySelector('.dialog-table tbody').insertRow();
          row.insertCell().innerHTML = '<button class="btn btn-sm btn-outline-secondary dialog-actions-toggle" aria-label="Действия">⋯</button>';
          button = row.querySelector('button');
        }
        const rect = button.getBoundingClientRect(); return { width: rect.width, height: rect.height };
      });
      assert.equal(Math.round(actionSize.height), touch ? 44 : 24);
      assert.equal(Math.round(actionSize.width), touch ? 44 : 24);
      await page.evaluate(() => {
        const history = document.getElementById('dialogDetailsHistory');
        const runtime = DialogsDetailsHistoryRuntime.createRuntime({
          elements: { detailsHistory: history },
          normalizeMessageSenderByType: (type, sender) => sender,
          resolveSenderLabel: message => ({ user: 'Анна · клиент', support: 'Оператор', system: 'Система' }[message.sender]),
          renderMessageAvatar: () => '<span class="chat-message-avatar">А</span>',
          escapeHtml: text => { const el = document.createElement('span'); el.textContent = text; return el.innerHTML; }
        });
        history.innerHTML = [
          { sender: 'user', message: 'Добрый день! Не получается войти в личный кабинет. После ввода кода страница возвращается к авторизации.', timestamp: '10:42', telegramMessageId: 101 },
          { sender: 'support', message: 'Здравствуйте, Анна! Проверю доступ. Подскажите, пожалуйста, на каком устройстве возникает ошибка?', timestamp: '10:43', telegramMessageId: 102 },
          { sender: 'system', message: 'Обращение назначено оператору · статус «В работе»', timestamp: '10:43' },
          { sender: 'user', message: 'На рабочем компьютере, браузер Chrome. На телефоне всё открывается.', timestamp: '10:44', telegramMessageId: 103 },
          { sender: 'support', message: 'Доступ восстановлен. Попробуйте обновить страницу и войти ещё раз.', timestamp: '10:46', telegramMessageId: 104, replyToTelegramMessageId: 103, replyPreview: 'На рабочем компьютере, браузер Chrome.' }
        ].map(message => runtime.messageToHtml(message)).join('');
        document.getElementById('dialogDetailsClientName').textContent = 'Анна Смирнова';
        document.getElementById('dialogDetailsMeta').textContent = '№ 20261022-001';
        document.getElementById('dialogDetailsLocation').textContent = 'Центральная';
        document.getElementById('dialogDetailsProblem').textContent = 'Не получается войти в личный кабинет';
        document.getElementById('dialogDetailsBusiness').textContent = 'Поддержка';
        document.getElementById('dialogDetailsAiState').textContent = 'Нет новых рекомендаций';
        document.getElementById('dialogMyDialogsEmpty').classList.remove('d-none');
        bootstrap.Modal.getOrCreateInstance(document.getElementById('dialogDetailsModal')).show();
      });
      await page.locator('#dialogDetailsModal.show').waitFor({ state: 'visible' });
      await page.waitForTimeout(250);
      const geometry = await page.evaluate(() => {
        const modal = document.querySelector('#dialogDetailsModal .modal-dialog').getBoundingClientRect();
        const incoming = document.querySelector('#dialogDetailsHistory .user .chat-message').getBoundingClientRect();
        const outgoing = document.querySelector('#dialogDetailsHistory .support .chat-message').getBoundingClientRect();
        const row = document.querySelector('#dialogDetailsHistory .chat-message-row.support').getBoundingClientRect();
        return { outside: modal.left < 0 || modal.right > innerWidth + 1, outgoingRight: outgoing.right > incoming.right, outgoingAtEdge: Math.abs(outgoing.right - row.right) < 2, incomingRight: incoming.right, outgoingEdge: outgoing.right, historyOverflow: document.getElementById('dialogDetailsHistory').scrollWidth > document.getElementById('dialogDetailsHistory').clientWidth + 1 };
      });
      await page.screenshot({ path: path.join(output, `dialog-${theme}-${width}.png`) });
      assert(!geometry.outside && !geometry.historyOverflow);
      assert(geometry.outgoingRight, 'Ответы оператора визуально справа: ' + JSON.stringify(geometry));
      assert(geometry.outgoingAtEdge, 'Исходящие выровнены по правому краю, независимо от длины текста');
      await page.locator('#dialogReplyText').fill('Проверка переноса длинного ответа без перекрытия инструментами. '.repeat(8));
      const composer = await page.evaluate(() => {
        const textarea = document.getElementById('dialogReplyText').getBoundingClientRect();
        const tools = document.querySelector('#dialogDetailsModal .dialog-composer-overlay').getBoundingClientRect();
        return { inside: tools.left >= textarea.left && tools.right <= textarea.right && tools.bottom <= textarea.bottom, padding: parseFloat(getComputedStyle(document.getElementById('dialogReplyText')).paddingBottom), toolHeight: tools.height };
      });
      assert(composer.inside && composer.padding > composer.toolHeight, 'Инструменты занимают зарезервированную нижнюю строку: ' + JSON.stringify(composer));
      await page.locator('#dialogReplyText').fill('');
      await page.screenshot({ path: path.join(output, `dialog-${theme}-${width}.png`) });
      report.push({ theme, width, touch, chart, toggle: size, actions: actionSize, dialog: geometry, composer });
      console.log(`GREEN: ${theme} ${width}px`);
      await context.close();
    }
  } finally { await browser.close(); await new Promise(resolve => server.close(resolve)); }
  fs.writeFileSync(path.join(output, 'report.json'), JSON.stringify(report, null, 2));
  console.log(`Проверено ${report.length} комбинаций: отчёты, локации, диалог и пустой график. Записи в рабочие системы запрещены.`);
}
main().catch(error => { console.error(error); process.exitCode = 1; });
