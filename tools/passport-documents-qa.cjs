/* Browser fixtures only: real fragment, CSS and runtime; no working panel/database requests. */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const http = require('node:http');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const assets = path.join(root, 'spring-panel/src/main/resources/static');
const output = path.join(root, 'spring-panel/target/passport-documents-qa');
const fragment = fs.readFileSync(path.join(root, 'spring-panel/src/main/resources/templates/passports/fragments/detail-documents.html'), 'utf8')
  .replace(/<\/?th:block[^>]*>/g, '');
const image = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=', 'base64');
function pdf() {
  const parts = ['%PDF-1.4\n']; const offsets = [0];
  const body = ['<< /Type /Catalog /Pages 2 0 R >>', '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 400 200] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>',
    '<< /Length 52 >>\nstream\nBT /F1 20 Tf 30 100 Td (Equipment acceptance) Tj ET\nendstream',
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>'];
  body.forEach((text, index) => { offsets.push(Buffer.byteLength(parts.join(''))); parts.push(`${index + 1} 0 obj\n${text}\nendobj\n`); });
  const start = Buffer.byteLength(parts.join(''));
  parts.push(`xref\n0 6\n0000000000 65535 f \n${offsets.slice(1).map(n => String(n).padStart(10, '0') + ' 00000 n ').join('\n')}\ntrailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n${start}\n%%EOF`);
  return Buffer.from(parts.join(''));
}
const pdfBytes = pdf();
const fixture = `<!doctype html><html lang="ru"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="_csrf" content="fixture-token"><link rel="stylesheet" href="/vendor/bootstrap/5.3.3/bootstrap.min.css"><link rel="stylesheet" href="/css/passport-documents.css"></head>
<body><main class="container py-3"><h1 class="h3">Паспорт объекта · Касса</h1>
<button class="btn btn-outline-primary mb-3" id="documentsTab">Документы и история <span id="passportDocumentsTabCount">—</span></button>${fragment}</main>
<script src="/js/passport-documents-runtime.js"></script><script>const documentsRuntime=window.PassportDocumentsRuntime.mount({passportId:1,csrfToken:'fixture-token'});
document.getElementById('documentsTab').addEventListener('click',()=>documentsRuntime.load());</script></body></html>`;

function multipart(buffer, contentType) {
  const boundary = '--' + contentType.match(/boundary=(.+)/)[1];
  const parts = buffer.toString('latin1').split(boundary).slice(1, -1);
  let metadata, file;
  for (const part of parts) {
    const split = part.indexOf('\r\n\r\n'); const headers = part.slice(0, split), body = Buffer.from(part.slice(split + 4, -2), 'latin1');
    if (headers.includes('name="metadata"')) metadata = JSON.parse(body.toString('utf8'));
    if (headers.includes('name="file"')) {
      const name = Buffer.from(headers.match(/filename="([^"]*)"/)[1], 'latin1').toString('utf8');
      file = { name, size: body.length, preview_kind: name.endsWith('.pdf') ? 'pdf' : 'image', bytes: body };
    }
  }
  return { metadata, file };
}
function initial() {
  const data = new Map();
  ['Акт приёмки кассы', 'Исторические изменения', 'Инструкция PDF', 'Инструкция DOCX'].forEach((title, index) => {
    const id = `df01765d-81f7-4d64-9e6b-fb48dcb80b5${index}`;
    const file = index === 0 ? { name: 'Фото.png', preview_kind: 'image', size: image.length, bytes: image }
      : index === 2 ? { name: 'Приёмка.pdf', preview_kind: 'pdf', size: pdfBytes.length, bytes: pdfBytes }
      : index === 3 ? { name: 'Инструкция.docx', preview_kind: 'text', size: 300, preview_text: 'Касса настроена\nПроверка пройдена <script>window.xss=true</script>' } : null;
    data.set(id, [{ id, title, category: 'Приёмка', event_date: '2026-11-12', description: 'Оборудование настроено и принято в работу.',
      fields: { 'Серийный номер': 'ККТ-001' }, revision: 1, actor: 'Инженер', changed_at: '2026-11-12T12:00:00Z', action: 'create', archived: false, ...(file ? { file } : {}) }]);
  });
  return data;
}

async function main() {
  fs.mkdirSync(output, { recursive: true });
  const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
  let activeData;
  const server = http.createServer((req, res) => {
    const url = new URL(req.url, 'http://local');
    const binary = url.pathname.match(/^\/api\/object_passports\/1\/documents\/([^/]+)\/revisions\/(\d+)\/file$/);
    if (binary) {
      const item = activeData?.get(binary[1])?.find(item => item.revision === Number(binary[2]));
      if (!item?.file) { res.writeHead(404); res.end(); return; }
      const preview = url.searchParams.get('preview') === 'true', bytes = item.file.bytes || Buffer.from('fixture');
      res.writeHead(200, { 'Content-Type': item.file.preview_kind === 'pdf' ? 'application/pdf' : 'image/png',
        'Content-Length': bytes.length,
        'Content-Disposition': (preview ? 'inline' : 'attachment') + "; filename*=UTF-8''" + encodeURIComponent(item.file.name),
        'X-Frame-Options': preview ? 'SAMEORIGIN' : 'DENY',
        'Content-Security-Policy': preview ? "frame-ancestors 'self'" : "default-src 'none'" });
      res.end(bytes); return;
    }
    if (url.pathname === '/') { res.setHeader('Content-Type', 'text/html; charset=utf-8'); res.end(fixture); return; }
    const file = path.resolve(assets, url.pathname.slice(1));
    if (!file.startsWith(assets + path.sep) || !fs.existsSync(file)) { res.writeHead(404); res.end(); return; }
    res.setHeader('Content-Type', path.extname(file) === '.js' ? 'text/javascript' : 'text/css'); fs.createReadStream(file).pipe(res);
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  let browser;
  try {
    browser = await chromium.launch({ headless: true, ...(process.env.PLAYWRIGHT_BROWSER_EXECUTABLE ? { executablePath: process.env.PLAYWRIGHT_BROWSER_EXECUTABLE } : {}) });
    for (const width of [320, 1440]) for (const mode of ['viewer', 'editor']) {
      const page = await browser.newPage({ viewport: { width, height: 1000 }, acceptDownloads: true });
      const errors = [], consoleErrors = [], requests = [], writes = []; const data = initial(); activeData = data; let conflict = false;
      page.on('pageerror', ex => errors.push(ex.message)); page.on('console', message => { if (message.type() === 'error') consoleErrors.push(message.text()); });
      await page.route('**/api/**', async route => {
        const request = route.request(), address = new URL(request.url()); requests.push(address.pathname);
        const prefix = '/api/object_passports/1/documents'; assert(address.pathname.startsWith(prefix));
        const segments = address.pathname.slice(prefix.length).split('/').filter(Boolean), id = segments[0];
        if (request.method() !== 'GET') {
          assert.equal(mode, 'editor'); assert.equal(request.headers()['x-xsrf-token'], 'fixture-token');
          if (conflict) { conflict = false; return route.fulfill({ status: 409, json: { success: false } }); }
          if (segments[1] === 'archive') {
            const input = request.postDataJSON(), old = data.get(id).at(-1); assert.equal(input.base_revision, old.revision);
            data.get(id).push({ ...old, archived: input.archived, revision: old.revision + 1, action: input.archived ? 'archive' : 'unarchive' });
          } else {
            const { metadata, file } = multipart(request.postDataBuffer(), request.headers()['content-type']); writes.push(metadata);
            const key = id || 'df01765d-81f7-4d64-9e6b-fb48dcb80b59', versions = data.get(key) || [], old = versions.at(-1);
            assert.equal(metadata.base_revision, old?.revision || 0);
            versions.push({ ...old, ...metadata, ...(file ? { file } : {}), id: key, revision: versions.length + 1, archived: false,
              actor: 'Инженер', changed_at: '2026-11-12T13:00:00Z', action: old ? 'edit' : 'create' }); data.set(key, versions);
          }
          return route.fulfill({ json: { success: true } });
        }
        if (!id) {
          const items = [...data.values()].map(versions => versions.at(-1)).reverse().filter(item => address.searchParams.get('archived') === 'true' || !item.archived)
            .filter(item => (item.title + item.description).toLowerCase().includes((address.searchParams.get('q') || '').toLowerCase()));
          return route.fulfill({ json: { success: true, items, total: items.length, has_more: false, next_cursor: '', can_edit: mode === 'editor' } });
        }
        if (segments.length === 2) return route.fulfill({ json: { success: true, items: data.get(id).slice().reverse(), has_more: false, next_cursor: 0 } });
        const item = data.get(id).find(item => item.revision === Number(segments[2])); assert(item);
        if (segments.length === 4) {
          return route.continue();
        }
        return route.fulfill({ json: { success: true, document: item } });
      });
      await page.goto(`http://127.0.0.1:${server.address().port}`);
      assert.equal(requests.length, 0, 'documents load only when the tab is opened');
      await page.locator('#documentsTab').click(); await page.locator('.passport-document-card').first().waitFor();
      assert.equal(await page.locator('[data-document-create]').isVisible(), mode === 'editor');
      assert.equal(await page.locator('[data-action="edit"]').count(), mode === 'editor' ? 4 : 0);
      await page.locator('.passport-document-card').filter({ hasText: 'Инструкция DOCX' }).getByRole('button', { name: 'Просмотр', exact: true }).click();
      await page.locator('#passportDocumentViewer pre').waitFor();
      assert((await page.locator('#passportDocumentViewer pre').textContent()).includes('<script>'));
      assert.equal(await page.evaluate(() => window.xss), undefined);
      await page.locator('[data-document-viewer-close]').click();
      if (mode === 'editor') {
        await page.locator('[data-document-create]').click();
        const form = page.locator('#passportDocumentForm');
        await form.locator('[name="title"]').fill('Приёмка после настройки');
        await form.locator('[name="category"]').fill('Свой тип'); await form.locator('[name="event_date"]').fill('2026-11-12');
        await form.locator('[name="description"]').fill('Проверено, настройки сохранены.');
        await form.locator('[data-document-field-add]').click(); await form.locator('[data-extra-key]').fill('Серийный номер'); await form.locator('[data-extra-value]').fill('ККТ-42');
        await form.locator('[name="file"]').setInputFiles({ name: 'Фото.png', mimeType: 'image/png', buffer: image });
        await form.locator('[data-document-local-preview] img').waitFor();
        await page.screenshot({ path: path.join(output, `editor-${width}.png`), fullPage: true });
        await form.locator('[data-document-save]').click(); await page.locator('#passportDocumentEditor').waitFor({ state: 'hidden' });
        const card = page.locator('.passport-document-card').filter({ hasText: 'Приёмка после настройки' }); await card.waitFor();
        assert.deepEqual(writes[0].fields, { 'Серийный номер': 'ККТ-42' }); assert.equal(writes[0].category, 'Свой тип');
        await card.locator('[data-action="edit"]').click(); await form.locator('[name="description"]').fill('Дополнено после проверки');
        await form.locator('[name="file"]').setInputFiles({ name: 'Приёмка.pdf', mimeType: 'application/pdf', buffer: pdfBytes });
        await form.locator('[data-document-save]').click(); await page.locator('#passportDocumentEditor').waitFor({ state: 'hidden' });
        await card.getByRole('button', { name: 'Просмотр', exact: true }).click(); await page.locator('#passportDocumentViewer iframe').waitFor();
        await page.waitForTimeout(1500);
        await page.screenshot({ path: path.join(output, `pdf-${width}.png`), fullPage: true });
        await page.locator('[data-document-history-toggle]').click(); await page.getByRole('button', { name: 'Открыть версию 1', exact: true }).click();
        await page.locator('#passportDocumentViewer [data-document-preview] img').waitFor();
        const downloaded = page.waitForEvent('download'); await page.locator('[data-document-download]').click(); const download = await downloaded;
        const destination = path.join(output, `old-version-${width}.png`); await download.saveAs(destination); assert.deepEqual(fs.readFileSync(destination), image);
        await page.locator('[data-document-viewer-close]').click();
        await card.locator('[data-action="edit"]').click(); await form.locator('[name="description"]').fill('Несохранённые данные'); conflict = true;
        await form.locator('[data-document-save]').click(); await page.waitForFunction(() => document.querySelector('[data-document-error]').textContent.includes('уже изменён'));
        assert.equal(await form.locator('[name="description"]').inputValue(), 'Несохранённые данные');
        await page.locator('#passportDocumentEditor .btn-close').click();
        await card.locator('[data-action="archive"]').click(); await card.waitFor({ state: 'hidden' });
        await page.locator('[name="archived"]').check(); await card.waitFor(); assert.equal(await card.locator('[data-action="edit"]').count(), 0);
        await card.locator('[data-action="archive"]').click(); await card.locator('[data-action="edit"]').waitFor();
      }
      await page.screenshot({ path: path.join(output, `${mode}-${width}.png`), fullPage: true });
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false);
      assert.deepEqual(errors, []);
      const pdfErrors = consoleErrors.filter(message => /sandbox|refused|blocked/i.test(message));
      assert.deepEqual(pdfErrors, [], 'native PDF preview must not be blocked');
      await page.close();
    }
    console.log('Passport documents browser QA passed: viewer/editor at 320/1440, upload, free fields, replacement, PDF/DOCX/image previews, exact old-file download, conflict preservation, archive and restore.');
  } finally { await browser?.close(); await new Promise(resolve => server.close(resolve)); }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
