/* Real editor fragment and scripts, local fixture data; never contacts the working panel. */
'use strict';
const fs = require('node:fs'), path = require('node:path'), http = require('node:http'), assert = require('node:assert/strict');
const root = path.resolve(__dirname, '..'), assets = path.join(root, 'spring-panel/src/main/resources/static');
const output = path.join(root, 'spring-panel/target/question-editor-qa'); fs.mkdirSync(output, { recursive: true });
const fragment = fs.readFileSync(path.join(root, 'spring-panel/src/main/resources/templates/settings/fragments/channels-templates.html'), 'utf8');
const parts = fragment.split(/<th:block[^>]*th:fragment="templateEditorModals"[^>]*>/);
assert.equal(parts.length, 2);
const clean = html => html.replace(/<\/?th:block[^>]*>/g, '');
const initial = { active_template_id: 'standard', question_templates: [{ id: 'standard', name: 'Стандартный вопросник',
  description: 'Сохранённое описание', start_message: 'Здравствуйте! Ответьте на несколько вопросов.',
  first_response_timeout_minutes: 5, first_response_timeout_message: 'Обращение закрыто.', question_flow: [
    { id: 'direction', type: 'select', text: 'С чем нужна помощь?', order: 1, required: true,
      options: [{ id: 'setup', label: 'Настройка' }, { id: 'repair', label: 'Ремонт' }],
      binding_key: 'direction', include_in_dashboard: true, routes: [{ value_id: 'setup', next_question_id: 'description' }] },
    { id: 'city', type: 'preset', text: 'Город', order: 2, required: true, preset: { group: 'locations', field: 'city' }, excluded_options: ['Казань'] },
    { id: 'description', type: 'custom', text: 'Опишите проблему', order: 3, required: false, binding_key: 'problem' },
  ] }], active_rating_template_id: 'rating', rating_templates: [{ id: 'rating', name: 'Оценка', prompt_text: 'Оцените помощь',
    scale_size: 1, responses: [{ value: 1, text: 'Спасибо' }] }] };
const presets = { locations: { label: 'Локации', fields: { city: { label: 'Город', options: ['Москва', 'Казань'] } } } };
const html = `<!doctype html><html lang="ru"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<link rel="stylesheet" href="/vendor/bootstrap/5.3.3/bootstrap.min.css"><link rel="stylesheet" href="/vendor/bootstrap-icons/1.10.5/bootstrap-icons.css">
<link rel="stylesheet" href="/css/style.css"><link rel="stylesheet" href="/css/app.css"><link rel="stylesheet" href="/css/settings.css"></head>
<body data-ui-page="settings"><div id="channelsModal" class="modal show" style="display:block"><div class="modal-dialog modal-xl"><div class="modal-content p-3">
<div data-bot-settings-modal>${clean(parts[0]).replace('class="tab-pane fade"', 'class="tab-pane fade show active"')}</div></div></div></div>
${clean(parts[1])}<script src="/vendor/bootstrap/5.3.3/bootstrap.bundle.min.js"></script>
<script>const initial=${JSON.stringify(initial)}, presets=${JSON.stringify(presets)};
window.SettingsRuntimeAccess={resolvePageConfigRuntime:()=>({getBotSettingsInitial:()=>initial,getBotPresetDefinitions:()=>presets}),
fetchPageDataSection:async()=>({botSettings:initial,botPresetDefinitions:presets})};
window.SettingsPageShell={showModal:el=>bootstrap.Modal.getOrCreateInstance(el).show(),hideModal:el=>bootstrap.Modal.getOrCreateInstance(el).hide()};
</script><script src="/js/business-questionnaire-presets.js"></script><script src="/js/bot-settings.js"></script></body></html>`;

async function main() {
  const server = http.createServer((req, res) => {
    const pathname = new URL(req.url, 'http://localhost').pathname;
    if (pathname === '/editor') { res.setHeader('Content-Type', 'text/html; charset=utf-8'); res.end(html); return; }
    const file = path.resolve(assets, pathname.slice(1));
    if (!file.startsWith(assets + path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) { res.writeHead(404); res.end('{}'); return; }
    res.setHeader('Content-Type', ({ '.js': 'text/javascript', '.css': 'text/css', '.svg': 'image/svg+xml' })[path.extname(file)] || 'application/octet-stream');
    if (/\.(js|css)$/.test(file)) res.end(fs.readFileSync(file, 'utf8').replace(/\r\n/g, '\n'));
    else fs.createReadStream(file).pipe(res);
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
  const browser = await chromium.launch({ headless: true, ...(process.env.PLAYWRIGHT_BROWSER_EXECUTABLE ? { executablePath: process.env.PLAYWRIGHT_BROWSER_EXECUTABLE } : {}) });
  const origin = `http://127.0.0.1:${server.address().port}`, results = [];
  try {
    for (const theme of ['light', 'dark']) for (const width of [390, 1440]) {
      const context = await browser.newContext({ viewport: { width, height: 1000 }, colorScheme: theme, reducedMotion: 'reduce' });
      await context.addInitScript(theme => { document.addEventListener('DOMContentLoaded', () => {
        document.documentElement.dataset.theme = theme;
      }); }, theme);
      const errors = [], writes = [];
      await context.route('**/*', async route => {
        const req = route.request(), url = new URL(req.url()); assert.equal(url.origin, origin);
        if (url.pathname === '/settings' && req.method() === 'POST') {
          writes.push(req.postDataJSON()); return route.fulfill({ json: { success: true } });
        }
        if (url.pathname === '/api/business-admin/overview') return route.fulfill({ json: { businesses: [
          { id: 1, displayName: 'СушиВёсла', status: 'active' }, { id: 2, displayName: 'Блинбери', status: 'active' }] } });
        assert.equal(req.method(), 'GET'); return route.continue();
      });
      const page = await context.newPage(); page.on('pageerror', error => errors.push(error.message));
      await page.goto(origin + '/editor');
      await page.locator('[data-bot-template-edit]').first().click();
      const modal = page.locator('#botTemplateEditorModal'); await modal.waitFor({ state: 'visible' });
      const cards = modal.locator('.bot-question-card'); assert.equal(await cards.count(), 3);
      assert.equal(await modal.locator('details[open]').count(), 0, 'Secondary settings start collapsed');
      assert.deepEqual(await modal.locator('[data-bot-preset-business] option').allTextContents(),
        ['Общий справочник (прежние каналы)', 'Без бизнеса', 'СушиВёсла', 'Блинбери']);
      assert.equal(await modal.evaluate(el => el.scrollWidth <= el.clientWidth + 1), true, 'No horizontal modal overflow');
      assert.equal(await cards.first().evaluate(el => getComputedStyle(el).boxShadow), 'none');
      await page.screenshot({ path: path.join(output, `compact-${theme}-${width}.png`), fullPage: true });

      const first = modal.locator('.bot-question-card[data-question-id="direction"]');
      await first.locator('[data-question-section="analytics"] > summary').click();
      const binding = first.locator('[data-bot-question-binding]'); assert.equal(await binding.inputValue(), 'direction');
      await first.locator('[data-question-section="routes"] > summary').click();
      assert.equal(await first.locator('[data-bot-question-route-target="setup"]').inputValue(), 'description');
      await first.locator('[data-question-section="preview"] > summary').click();
      assert((await first.locator('[data-bot-question-preview-title]').textContent()).includes('С чем нужна помощь?'));
      await first.locator('[data-bot-question-select-options]').fill('Настройка\nРемонт\nДругое');
      assert(await first.locator('[data-bot-question-select-options]').evaluate(el => document.activeElement === el), 'Typing retains focus');
      await first.locator('[data-bot-question-select-options]').blur();
      assert.equal(await first.locator('[data-question-section="analytics"]').getAttribute('open'), '');
      assert.equal(await first.locator('[data-question-section="routes"]').getAttribute('open'), '');
      await first.locator('[data-bot-question-action="move-down"]').click();
      assert.equal(await first.locator('[data-question-section="analytics"]').getAttribute('open'), '', 'Disclosure follows question ID through reorder');
      await first.locator('[data-bot-question-action="move-up"]').click();
      const city = modal.locator('.bot-question-card[data-question-id="city"]');
      await city.locator('[data-question-section="options"] > summary').click();
      assert.equal(await city.locator('[data-bot-question-option][value="Казань"]').isChecked(), false);
      await city.locator('[data-question-section="options"] > summary').click();
      await modal.locator('[data-bot-template-save]').click();
      await page.waitForFunction(() => !document.getElementById('botTemplateEditorModal').classList.contains('show'));
      assert.equal(writes.length, 1);
      const saved = writes[0].bot_settings.question_templates.find(template => template.id === 'standard');
      assert.equal(saved.description, initial.question_templates[0].description);
      assert.equal(saved.first_response_timeout_minutes, 5);
      const savedFirst = saved.question_flow.find(question => question.id === 'direction');
      assert.equal(savedFirst.binding_key, 'direction'); assert.equal(savedFirst.include_in_dashboard, true);
      assert.equal(savedFirst.options.length, 3); assert.equal(savedFirst.routes[0].next_question_id, 'description');
      assert.deepEqual(saved.question_flow.find(question => question.id === 'city').excluded_options, ['Казань']);
      assert.equal(saved.question_flow.find(question => question.id === 'description').required, false);
      assert.deepEqual(errors, []);
      results.push({ theme, width, questions: 3, writes: writes.length, errors }); await context.close();
    }
    fs.writeFileSync(path.join(output, 'browser-report.json'), JSON.stringify({ status: 'GREEN', results }, null, 2));
    console.log('GREEN: 4 browser contexts; compact editor, disclosure state/focus/reorder, preset exclusions, hidden analytics/routes/timeouts retained on save.');
  } finally { await browser.close(); await new Promise(resolve => server.close(resolve)); }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
