/* Three task panes and all file input paths against real Thymeleaf markup. API writes are fixtures. */
const fs = require('node:fs'), path = require('node:path'), http = require('node:http'), crypto = require('node:crypto'), assert = require('node:assert/strict');
const root = path.resolve(__dirname, '..'), assets = path.join(root, 'spring-panel/src/main/resources/static');
const capture = path.join(root, 'spring-panel/target/task-card-rendered/tasks.html'), output = path.join(root, 'spring-panel/target/task-layout-files-qa');
const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+yXioAAAAASUVORK5CYII=', 'base64');
const escape = value => String(value).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
async function main() {
  assert(fs.existsSync(capture), 'Run TaskCardRenderTest first'); fs.mkdirSync(output, { recursive: true });
  const server = http.createServer((req, res) => {
    const url = new URL(req.url, 'http://localhost');
    if (url.pathname === '/tasks') { res.setHeader('Content-Type', 'text/html; charset=utf-8'); return res.end(fs.readFileSync(capture)); }
    if (url.pathname === '/fixture/screen.png') { res.setHeader('Content-Type', 'image/png'); return res.end(png); }
    const file = path.resolve(assets, '.' + url.pathname);
    if (!file.startsWith(assets + path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) { res.writeHead(404); return res.end('{}'); }
    res.setHeader('Content-Type', ({ '.css': 'text/css', '.js': 'text/javascript', '.svg': 'image/svg+xml', '.png': 'image/png', '.woff2': 'font/woff2', '.woff': 'font/woff' })[path.extname(file)] || 'application/octet-stream'); fs.createReadStream(file).pipe(res);
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const origin = `http://127.0.0.1:${server.address().port}`, { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
  const browser = await chromium.launch({ headless: true, ...(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {}) }), report = [];
  try {
    for (const theme of (process.env.QA_THEME ? [process.env.QA_THEME] : ['light', 'dark'])) for (const width of (process.env.QA_WIDTH ? [Number(process.env.QA_WIDTH)] : [320, 768, 1440])) {
      const context = await browser.newContext({ viewport: { width, height: 900 }, colorScheme: theme, reducedMotion: 'reduce' });
      await context.addInitScript(theme => { localStorage.setItem('iguana:theme', theme); localStorage.setItem('sidebarPinned', '1'); }, theme);
      let readonly = false, failTask = false, failComment = false;
      let task = { id: '41', display_no: 'DL_41', title: 'Запуск обновлённого рабочего пространства', body_html: '<p>Согласовать этапы и проверить вложения перед запуском.</p>',
        creator: 'anna', assignee: 'anna', co: ['victor'], watchers: [], status: 'В работе', projects: [], tags: [],
        comments: Array.from({ length: 35 }, (_, index) => ({ id: index + 1, author: index % 2 ? 'anna' : 'victor', created_at: `2026-11-24T07:${String(index).padStart(2, '0')}:00Z`, html: `<p>Этап ${index + 1}: проверка выполнена.</p>` })), events: [], history: [] };
      const writes = [], binaries = new Map(), errors = [], missing = [];
      async function readForm(req) { return new Request(req.url(), { method: 'POST', headers: req.headers(), body: req.postDataBuffer() }).formData(); }
      async function registerFiles(form, taskId) {
        let html = '';
        for (const file of form.getAll('files')) {
          const href = `/api/tasks/${taskId}/attachments/${crypto.randomUUID()}_${encodeURIComponent(file.name)}`;
          binaries.set(href, { bytes: Buffer.from(await file.arrayBuffer()), type: file.type || 'application/octet-stream', name: file.name });
          const attributes = `data-task-attachment="true" data-attachment-name="${escape(file.name)}" data-attachment-size="${file.size}"`;
          html += file.type.startsWith('image/') ? `<figure ${attributes}><a href="${href}" download="${escape(file.name)}"><img src="${href}?preview=true" alt="${escape(file.name)}"></a><figcaption>${escape(file.name)}</figcaption></figure>`
            : `<p><a ${attributes} href="${href}" download="${escape(file.name)}">${escape(file.name)}</a></p>`;
        }
        return html;
      }
      await context.route('**/*', async route => {
        const req = route.request(), url = new URL(req.url()); if (url.origin !== origin) return route.abort();
        if (!url.pathname.startsWith('/api/')) return route.continue();
        if (binaries.has(url.pathname)) { const file = binaries.get(url.pathname); return route.fulfill({ body: file.bytes, contentType: file.type }); }
        if (url.pathname === '/api/panel-users/profile') { const username = url.searchParams.get('username'); return route.fulfill({ json: { username, fullName: username === 'anna' ? 'Анна Смирнова' : 'Виктор Крюков', department: 'Поддержка', photo: '' } }); }
        if (url.pathname === '/api/shared-work/context') return route.fulfill({ json: { canCreateTask: true, canCreateProject: true, systemAdmin: true } });
        if (/^\/api\/shared-work\/TASK\/\d+\/access$/.test(url.pathname)) return route.fulfill({ json: { kind: 'TASK', id: url.pathname.split('/')[4], canWrite: !readonly, canManage: false, members: [] } });
        if (url.pathname === '/api/projects') return route.fulfill({ json: { items: [] } });
        if (/^\/api\/tasks\/\d+$/.test(url.pathname)) return route.fulfill({ json: task });
        if (url.pathname.endsWith('/comments') && req.method() === 'POST') {
          const form = await readForm(req); writes.push({ kind: 'comment', names: form.getAll('files').map(file => file.name), html: form.get('html') });
          if (failComment) return route.fulfill({ status: 503, json: { error: 'Хранилище временно недоступно' } });
          const item = { id: task.comments.length + 1, author: 'anna', created_at: '2026-11-24T08:00:00Z', html: form.get('html') + await registerFiles(form, task.id) };
          task.comments.push(item); task.events.push({ event_type: 'COMMENT_ADDED', actor: 'anna', occurred_at: item.created_at });
          return route.fulfill({ json: { ok: true, item } });
        }
        if (url.pathname === '/api/tasks' && req.method() === 'POST') {
          const form = await readForm(req); writes.push({ kind: 'task', names: form.getAll('files').map(file => file.name), id: form.get('id') });
          if (failTask) return route.fulfill({ status: 503, json: { error: 'Хранилище временно недоступно' } });
          const id = form.get('id') || '42';
          task = { ...task, id, title: form.get('title'), body_html: form.get('body_html') + await registerFiles(form, id), assignee: form.get('assignee'), status: form.get('status'), ...(form.get('id') ? {} : { comments: [], events: [] }) };
          return route.fulfill({ json: { ok: true, id, body_html: task.body_html } });
        }
        if (url.pathname === '/api/tasks') return route.fulfill({ json: { items: [task], total: 1, page: 1, page_size: 20 } });
        assert.equal(req.method(), 'GET', 'Unexpected write ' + req.method() + ' ' + url.pathname); return route.fulfill({ json: {} });
      });
      const page = await context.newPage(); page.on('pageerror', error => errors.push(error.message));
      page.on('response', response => { if (response.status() === 404 && /\.(js|css)(\?|$)/.test(response.url())) missing.push(response.url()); });
      const pane = async name => { if (width < 1100) await page.locator(`[data-task-pane-tab="${name}"]`).click(); };
      const dismissError = async () => {
        await page.waitForFunction(() => { const node = document.getElementById('appMessageModal'); return node?.classList.contains('show') && !bootstrap.Modal.getInstance(node)?._isTransitioning; });
        await page.locator('#appMessageModal [data-bs-dismiss="modal"]').click(); await page.locator('#appMessageModal').waitFor({ state: 'hidden' });
      };
      const transfer = async (selector, eventType, name, bytes, type) => page.locator(selector).evaluate((node, data) => {
        const transfer = new DataTransfer(); transfer.items.add(new File([new Uint8Array(data.bytes)], data.name, { type: data.type }));
        const event = data.eventType === 'paste' ? new ClipboardEvent('paste', { clipboardData: transfer, bubbles: true, cancelable: true }) : new DragEvent('drop', { dataTransfer: transfer, bubbles: true, cancelable: true });
        node.dispatchEvent(event); return event.defaultPrevented;
      }, { eventType, name, bytes: Array.from(bytes), type });
      await page.goto(origin + '/tasks#task=41', { waitUntil: 'networkidle' }); await page.locator('#taskModal.show').waitFor(); await page.locator('#saveTaskBtn:enabled').waitFor();
      try {
        const panes = await page.locator('#taskForm > [data-task-pane]').evaluateAll(nodes => nodes.map(node => node.dataset.taskPane)); assert.deepEqual(panes, ['description', 'chat', 'properties']);
        assert.equal(await page.locator('.tasks-history-panel').evaluate(node => node.closest('[data-task-pane]').dataset.taskPane), 'properties');
        await pane('chat');
        const before = await page.locator('.tasks-chat-composer').boundingBox(); await page.locator('#comments').evaluate(node => { node.scrollTop = 0; });
        const after = await page.locator('.tasks-chat-composer').boundingBox(); assert(Math.abs(before.y - after.y) < 1, 'Composer stays fixed while comments scroll');
        const scrollable = await page.locator('#comments').evaluate(node => node.scrollHeight > node.clientHeight); assert(scrollable, 'Long chat scrolls independently');
        const rowHeight = await page.locator('.tasks-comment').first().evaluate(node => node.getBoundingClientRect().height); assert(rowHeight < 70, 'Ordinary comment stays compact');
        const composerBottom = after.y + after.height, bodyBottom = await page.locator('#taskModal .modal-body').evaluate(node => node.getBoundingClientRect().bottom); assert(Math.abs(composerBottom - bodyBottom) < 3, 'Composer is at bottom of available task area');
        await pane('description');
        await page.locator('#taskFileInput').setInputFiles([{ name: 'План из файла.txt', mimeType: 'text/plain', buffer: Buffer.from('file picker bytes') }]);
        assert(await transfer('[data-task-pane="description"]', 'drop', 'drag-and-drop.txt', Buffer.from('drag and drop bytes'), 'text/plain'));
        assert.equal(await page.locator('#taskAttachments [data-remove-task-file]').count(), 2);
        failTask = true; await page.locator('#saveTaskBtn').click(); await page.locator('#saveTaskBtn:enabled').waitFor(); await dismissError();
        assert.equal(await page.locator('#taskAttachments [data-remove-task-file]').count(), 2, 'Failed task save preserves pending files');
        failTask = false; await page.locator('#saveTaskBtn').click(); await page.locator('#taskModal').waitFor({ state: 'hidden' });
        assert.deepEqual(writes.at(-1).names, ['План из файла.txt', 'drag-and-drop.txt']);
        await page.locator('[data-open-task="41"]').first().click(); await page.locator('#taskModal.show').waitFor(); await page.locator('#saveTaskBtn:enabled').waitFor();
        assert.equal(await page.locator('#taskAttachments .tasks-attachment').count(), 2);
        const bytes = await page.locator('#taskAttachments .tasks-attachment__copy a').first().evaluate(async node => (await fetch(node.href)).text()); assert.equal(bytes, 'file picker bytes', 'Download uses persisted file bytes');
        await pane('chat');
        await page.locator('#commentFileInput').setInputFiles({ name: 'Комментарий.txt', mimeType: 'text/plain', buffer: Buffer.from('comment picker') });
        assert(await transfer('.tasks-chat-composer', 'drop', 'comment-drop.txt', Buffer.from('comment drop'), 'text/plain'));
        assert(await transfer('#commentEditor', 'paste', 'Скриншот.png', png, 'image/png'));
        assert.equal(await page.locator('#taskCommentFiles .tasks-file-chip').count(), 3);
        assert.equal(await page.locator('#taskSaveState').innerText(), '', 'Comment files do not mark task properties dirty');
        failComment = true; await page.locator('#sendCommentBtn').click(); await page.locator('#sendCommentBtn:enabled').waitFor(); await dismissError();
        assert.equal(await page.locator('#taskCommentFiles .tasks-file-chip').count(), 3, 'Failed comment upload preserves file-only draft');
        failComment = false; await page.locator('#sendCommentBtn').click(); await page.waitForFunction(() => !document.querySelector('#taskCommentFiles .tasks-file-chip')); await page.locator('#sendCommentBtn:enabled').waitFor();
        assert.deepEqual(writes.at(-1).names, ['Комментарий.txt', 'comment-drop.txt', 'Скриншот.png']);
        assert.equal(await page.locator('.tasks-comment').last().locator('.tasks-file-chip').count(), 3); assert.equal(await page.locator('.tasks-comment-body img').count(), 0);
        await pane('description'); assert.equal(await page.locator('#taskAttachments .tasks-attachment').count(), 5, 'Left panel includes description and comment files');
        await page.locator('[data-task-image-preview]').first().click(); await page.locator('.tasks-image-viewer[open]').waitFor(); await page.keyboard.press('Escape'); assert(await page.locator('#taskModal').isVisible());
        if (width < 1100) await pane('chat'); await page.screenshot({ path: path.join(output, `layout-${theme}-${width}.png`) });
        await pane('properties'); await page.locator('.tasks-history-panel > summary').click(); assert.match(await page.locator('#taskEvents').innerText(), /Добавлен комментарий/);
        readonly = true; await page.evaluate(() => SharedWorkAccess.openObject('TASK', '41')); await page.locator('#taskStatus:disabled').waitFor();
        await pane('description'); assert(await page.locator('#attachTaskFileBtn').isDisabled());
        await transfer('[data-task-pane="description"]', 'drop', 'forbidden.txt', Buffer.from('denied'), 'text/plain'); assert.equal(await page.locator('#taskAttachments [data-remove-task-file]').count(), 0);
        const overflow = await page.locator('#taskModal').evaluate(node => node.scrollWidth > node.clientWidth + 2 || node.querySelector('.modal-body').scrollWidth > node.querySelector('.modal-body').clientWidth + 2); assert.equal(overflow, false);
        // Files selected before creation are sent with the create request, after native required fields are filled.
        await page.locator('[data-task-close]').last().click(); await page.locator('#taskModal').waitFor({ state: 'hidden' }); readonly = false;
        await page.locator('#createTaskBtn').click(); await page.locator('#saveTaskBtn:enabled').waitFor();
        await page.locator('#taskTitle').fill('Новая задача с файлом'); await page.locator('#bodyEditor').fill('Описание новой задачи');
        await page.locator('#taskFileInput').setInputFiles({ name: 'new-task.txt', mimeType: 'text/plain', buffer: Buffer.from('new task bytes') });
        await pane('properties'); await page.locator('#taskAssignee').locator('..').locator('.employee-picker__trigger').click(); await page.locator('#taskAssignee').locator('..').locator('[data-employee-value="anna"]').click();
        await page.locator('#saveTaskBtn').click(); await page.locator('#taskModal').waitFor({ state: 'hidden' }); assert.equal(writes.at(-1).id, ''); assert.deepEqual(writes.at(-1).names, ['new-task.txt']); assert.match(task.body_html, /new-task.txt/);
        assert.deepEqual(errors, []); assert.deepEqual(missing, []); report.push({ theme, width, writes, scrollable, commentHeight: rowHeight, overflow, errors, missing });
      } catch (error) { await page.screenshot({ path: path.join(output, `failure-${theme}-${width}.png`) }); throw error; }
      finally { await context.close(); }
    }
    fs.writeFileSync(path.join(output, 'report.json'), JSON.stringify(report, null, 2)); console.log(`GREEN: ${report.length} layout/file scenarios; independent scroll, fixed composer, file picker, drag/drop, paste, upload retry, download, file-only comments, new tasks and read-only.`);
  } finally { await browser.close(); await new Promise(resolve => server.close(resolve)); }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
