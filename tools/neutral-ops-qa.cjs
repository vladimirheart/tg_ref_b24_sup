/* Инвентарь и визуальный smoke настоящих MVC-render. Артефакты остаются в target/. */
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const assert = require('node:assert/strict');

const root = path.resolve(__dirname, '..');
const resources = path.join(root, 'spring-panel/src/main/resources');
const output = path.resolve(process.env.IGUANA_UI_QA_OUTPUT || path.join(root, 'spring-panel/target/neutral-ops-qa'));
const captures = path.resolve(process.env.IGUANA_UI_CAPTURES || path.join(root, 'spring-panel/target/neutral-ops-rendered'));
function files(directory) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry =>
    entry.isDirectory() ? files(path.join(directory, entry.name)) : [path.join(directory, entry.name)]);
}
const templates = path.join(resources, 'templates');
const inventory = files(templates).filter(f => f.endsWith('.html')).map(file => {
  const html = fs.readFileSync(file, 'utf8');
  const body = html.match(/<body\b[^>]*>/);
  const view = path.relative(templates, file).replaceAll('\\', '/').replace(/\.html$/, '');
  return { view, standalone: !!body, page: body?.[0].match(/data-ui-page="([^"]+)"/)?.[1],
    theme: html.includes('/css/style.css'), app: html.includes('/css/app.css'),
    rendered: fs.existsSync(path.join(captures, view + '.html')) };
});
const pages = inventory.filter(s => s.standalone);
assert.equal(pages.length, 46, 'Изменился инвентарь самостоятельных страниц');
assert(pages.every(s => s.page && s.theme && s.app), 'У каждой страницы должны быть theme tokens и общий CSS');
fs.mkdirSync(output, { recursive: true });
fs.writeFileSync(path.join(output, 'inventory.json'), JSON.stringify(inventory, null, 2));
console.log(`Инвентарь: ${pages.length} страниц, ${inventory.length - pages.length} фрагментов; MVC-render: ${pages.filter(s => s.rendered).length}`);
if (process.argv.includes('--inventory')) process.exit(0);

async function main() {
  const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
  const mime = { '.css': 'text/css', '.js': 'text/javascript', '.svg': 'image/svg+xml', '.png': 'image/png', '.woff2': 'font/woff2' };
  const server = http.createServer((req, res) => {
    const pathname = decodeURIComponent(new URL(req.url, 'http://localhost').pathname);
    const directory = pathname.startsWith('/screens/') ? captures : path.join(resources, 'static');
    const relative = pathname.startsWith('/screens/') ? pathname.slice(9) + '.html' : pathname.slice(1);
    const file = path.resolve(directory, relative);
    if (!file.startsWith(directory + path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) {
      res.writeHead(404, { 'Content-Type': 'application/json' }); res.end('{}'); return;
    }
    res.writeHead(200, { 'Content-Type': file.endsWith('.html') ? 'text/html; charset=utf-8' : (mime[path.extname(file)] || 'application/octet-stream') });
    fs.createReadStream(file).pipe(res);
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const origin = `http://127.0.0.1:${server.address().port}`;
  const browser = await chromium.launch({ headless: true });
  const report = [];
  const modalReport = [];
  const statesOnly = process.argv.includes('--states');
  const scenarios = statesOnly
    ? ['neo', 'catppuccin', 'amber-minimal'].flatMap(palette => ['light', 'dark', 'auto'].map(theme => ({ palette, theme })))
    : ['light', 'dark'].map(theme => ({ palette: 'neo', theme }));
  try {
    for (const { theme, palette } of scenarios) {
      for (const width of (statesOnly ? [390, 1440] : [320, 390, 768, 1440, 1920])) {
        console.log(`Проверка: ${theme}, ${width}px`);
        const context = await browser.newContext({ viewport: { width, height: 1000 }, colorScheme: theme === 'auto' ? 'dark' : theme, reducedMotion: 'reduce', hasTouch: statesOnly && width === 390, isMobile: statesOnly && width === 390 });
        await context.addInitScript(({ theme, palette }) => {
          localStorage.setItem('iguana:theme', theme);
          localStorage.setItem('iguana:theme-palette', palette);
          localStorage.setItem('sidebarPinned', '1');
        }, { theme, palette });
        await context.route('**/*', route => {
          if (!route.request().url().startsWith(origin + '/')) return route.abort();
          if (new URL(route.request().url()).pathname === '/api/dashboard/data') return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({
            available_restaurants: ['Центральный', 'Северный'], stats: { current: { total: 128 }, previous: { total: 112 } },
            time_stats: { formatted_total: '24 ч 15 мин', formatted_avg: '11 мин', resolved_count: 116 },
            staff_time_stats: [{ name: 'Оператор 1', total_minutes: 120, resolved_count: 12, formatted_total: '2 ч', formatted_avg: '10 мин', formatted_avg_response: '3 мин' }, { name: 'Оператор 2', total_minutes: 90, resolved_count: 9, formatted_total: '1 ч 30 мин', formatted_avg: '10 мин', formatted_avg_response: '4 мин' }], activity_stats: null,
            charts: { channel: { Telegram: 72, VK: 36, MAX: 20 }, business: { Поддержка: 86, Доставка: 42 }, network: { Центральный: 78, Северный: 50 }, category: { 'Техническая поддержка': 62, 'Вопрос по заказу': 40, Прочее: 26 }, bot_product: { Панель: 72, Приложение: 56 }, city: { Москва: 86, Казань: 42 }, restaurant: { Центральный: 78, Северный: 50 } }
          }) });
          if (!['GET', 'HEAD'].includes(route.request().method())) return route.fulfill({ status: 409, contentType: 'application/json', body: '{}' });
          return route.continue();
        });
        const page = await context.newPage();
        for (const screen of pages.filter(s => s.rendered && (!statesOnly || ['ui-kit/index', 'dialogs/index', 'settings/index', 'auth/login'].includes(s.view)))) {
          const label = `${screen.view.replaceAll('/', '-')}-${statesOnly ? palette + '-' : ''}${theme}-${width}`;
          await page.goto(`${origin}/screens/${screen.view}`, { waitUntil: 'load' });
          if (!statesOnly) await page.evaluate(theme => {
            document.documentElement.dataset.theme = theme;
            document.documentElement.classList.toggle('dark', theme === 'dark');
          }, theme);
          await page.waitForTimeout(180);
          const result = await page.evaluate(() => {
            const body = getComputedStyle(document.body);
            const main = document.querySelector('main');
            const rect = main?.getBoundingClientRect();
            const transparent = c => c === 'rgba(0, 0, 0, 0)' || c === 'transparent';
            const rgb = c => c.match(/[\d.]+/g)?.slice(0, 3).map(Number);
            const luminance = c => rgb(c)?.map(v => { v /= 255; return v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4; }).reduce((a,v,i) => a + v * [0.2126, 0.7152, 0.0722][i], 0);
            const contrast = (a,b) => { const x = luminance(a), y = luminance(b); return (Math.max(x,y) + 0.05) / (Math.min(x,y) + 0.05); };
            const labels = [...document.querySelectorAll('h1, .metric-value, .metric-label, .metric-mini-value, .metrics-headline__chip')].filter(el => el.checkVisibility()).map(el => {
              const style = getComputedStyle(el); let node = el, bg;
              while (node) { bg = getComputedStyle(node).backgroundColor; if (!transparent(bg)) break; node = node.parentElement; }
              return { text: el.textContent.trim().slice(0,60), ratio: Math.round(contrast(style.color, bg || body.backgroundColor) * 100) / 100 };
            });
            return { page: document.body.dataset.uiPage, background: body.backgroundColor,
              surfaceSoft: body.getPropertyValue('--surface-soft').trim(),
              viewportOverflow: document.documentElement.scrollWidth > innerWidth + 1,
              smallTouchTargets: matchMedia('(pointer: coarse)').matches ? [...document.querySelectorAll('.btn:not(.btn-link), .form-control:not(textarea), .form-select')].filter(el => el.checkVisibility() && el.getBoundingClientRect().height < 43.5).map(el => el.className) : [],
              mainOutsideViewport: rect ? rect.right > innerWidth + 1 || rect.left < -1 : false,
              contrast: labels,
              visualOverrides: [...document.querySelectorAll('[class*="card"], [class*="panel"], [class*="toolbar"], [class*="surface"], [class*="shell"], .staff-time-item, .staff-time-avatar, .staff-time-detail')].filter(el => el.checkVisibility() && getComputedStyle(el).visibility !== 'hidden' && getComputedStyle(el).opacity !== '0').flatMap(el => {
                const style = getComputedStyle(el);
                const gradient = /gradient\(/.test(style.backgroundImage);
                const shadow = style.boxShadow !== 'none' && !el.closest('.modal, .dropdown-menu, .popover, .offcanvas') && !el.contains(document.activeElement);
                const blur = style.backdropFilter !== 'none';
                return gradient || shadow || blur ? [{ tag: el.tagName, class: el.className, id: el.id, gradient: gradient ? style.backgroundImage : null, shadow: shadow ? style.boxShadow : null, blur: blur ? style.backdropFilter : null }] : [];
              }) };
          });
          await page.screenshot({ path: path.join(output, label + '.png'), fullPage: false });
          if (statesOnly) {
            const preferences = await page.evaluate(() => ({ palette: document.documentElement.dataset.themePalette, theme: document.documentElement.dataset.theme }));
            assert.equal(preferences.palette, palette, 'Сохраняется выбранная палитра');
            assert.equal(preferences.theme, theme === 'auto' ? 'dark' : theme, 'Системная яркость соответствует настройке ОС');
            if (screen.view === 'ui-kit/index') {
              const semanticStates = await page.evaluate(() => {
                const probe = document.createElement('div');
                probe.innerHTML = '<input class="form-control"><input class="form-control is-invalid"><table class="table"><tr><td>Обычная</td></tr></table><table class="table table-danger"><tr><td>Ошибка</td></tr></table>';
                document.body.append(probe);
                const fields = probe.querySelectorAll('input'); const cells = probe.querySelectorAll('td');
                const result = { invalidDistinct: getComputedStyle(fields[0]).borderColor !== getComputedStyle(fields[1]).borderColor, dangerDistinct: getComputedStyle(cells[0]).backgroundColor !== getComputedStyle(cells[1]).backgroundColor,
                  borders: [...fields].map(el => getComputedStyle(el).borderColor), backgrounds: [...cells].map(el => getComputedStyle(el).backgroundColor) };
                probe.remove(); return result;
              });
              assert(semanticStates.invalidDistinct && semanticStates.dangerDistinct, 'Семантические ошибки должны отличаться от обычных поверхностей: ' + JSON.stringify(semanticStates));
            }
            if (width === 390 && screen.view === 'dialogs/index') {
              const toggle = page.locator('#sidebarMobileToggle');
              await toggle.click();
              assert.equal(await toggle.getAttribute('aria-expanded'), 'true', 'Мобильное меню открывается');
              await toggle.click();
              assert.equal(await toggle.getAttribute('aria-expanded'), 'false', 'Мобильное меню закрывается');
            }
          }
          report.push({ view: screen.view, palette, theme, width, ...result });
          if (width === 1440) {
            const triggers = await page.locator('[data-bs-toggle="modal"][data-bs-target], [data-settings-overview-target]').all();
            for (const trigger of triggers.slice(0, 3)) {
              if (!await trigger.isVisible()) continue;
              const target = await trigger.getAttribute('data-bs-target') || '#' + await trigger.getAttribute('data-settings-overview-target');
              try {
                await trigger.click({ timeout: 1500 });
                const modal = page.locator(target);
                await modal.waitFor({ state: 'visible', timeout: 1500 });
                await page.waitForFunction(selector => {
                  const dialog = document.querySelector(selector);
                  return dialog?.classList.contains('show') && dialog.contains(document.activeElement);
                }, target, { timeout: 2500 });
                await page.keyboard.press('Tab');
                const focusWithin = await modal.evaluate(el => el.contains(document.activeElement));
                await page.screenshot({ path: path.join(output, `${label}-modal-${modalReport.length}.png`) });
                modalReport.push({ view: screen.view, palette, theme, target, visible: true, focusWithin });
                await page.keyboard.press('Escape');
                await page.evaluate(() => document.querySelectorAll('.modal.show').forEach(el => window.bootstrap?.Modal.getInstance(el)?.hide()));
              } catch (error) { modalReport.push({ view: screen.view, theme, target, visible: false, reason: error.message.split('\n')[0] }); }
            }
          }
        }
        await context.close();
      }
    }
  } finally { await browser.close(); await new Promise(resolve => server.close(resolve)); }
  fs.writeFileSync(path.join(output, 'browser-report.json'), JSON.stringify({ screens: report, modals: modalReport }, null, 2));
  console.log(`Снимков: ${report.length}; модальных проверок: ${modalReport.length}`);
  console.log(JSON.stringify({ overflow: report.filter(r => r.viewportOverflow || r.mainOutsideViewport).map(r => `${r.view}:${r.theme}:${r.width}`),
    missingTokens: report.filter(r => !r.surfaceSoft).map(r => r.view),
    lowContrast: report.flatMap(r => r.contrast.filter(c => c.ratio < 4.5).map(c => ({ view:r.view, theme:r.theme, text:c.text, ratio:c.ratio }))) }, null, 2));
  assert(report.every(r => !r.viewportOverflow && !r.mainOutsideViewport && r.surfaceSoft), 'Не должно быть viewport overflow и отсутствующих токенов');
  assert(report.every(r => !r.visualOverrides.length), 'Карточки и панели не должны сохранять отдельные градиенты, blur и тени вне overlay/focus состояний');
  assert(report.every(r => !r.smallTouchTargets.length), 'Сенсорные контролы должны быть не ниже 44px');
  assert(report.every(r => r.contrast.every(c => c.ratio >= 4.5)), 'Контраст заголовков и KPI должен быть не ниже 4.5:1');
  assert(modalReport.every(r => r.visible && r.focusWithin), 'Модальные окна открываются и удерживают клавиатурный фокус');
}
main().catch(error => { console.error(error.message); process.exitCode = 1; });
