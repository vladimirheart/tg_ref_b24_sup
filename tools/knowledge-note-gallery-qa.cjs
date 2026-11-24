/* Native photo viewer with production JS/CSS and loopback fixtures. Node 22+, Chromium/Edge. */
const fs = require('node:fs'), path = require('node:path'), http = require('node:http');
const cp = require('node:child_process'), crypto = require('node:crypto'), assert = require('node:assert/strict');
const root = path.resolve(__dirname, '..'), resources = path.join(root, 'spring-panel/src/main/resources/static');
const output = path.join(root, 'spring-panel/target/knowledge-note-gallery-qa');
const image = fs.readFileSync(path.join(resources, 'images/iguana-brand.png'));
const photo = i => `![Фото ${i}](/api/attachments/knowledge-base/photo-${i}.png)`;
const body = 'Текст заметки\n' + Array.from({ length: 10 }, (_, i) => photo(i + 1)).join('\n');
const untrustedCaption = '<img src=x onerror="globalThis.qaInjected=true">';
const single = `![${untrustedCaption}](/api/attachments/knowledge-base/photo-11.png)`;
let uploads = 0;
const fixture = `<!doctype html><html lang="ru"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="_csrf" content="fixture-csrf"><meta name="_csrf_header" content="X-CSRF-TOKEN">
  <link rel="stylesheet" href="/vendor/bootstrap/5.3.3/bootstrap.min.css"><link rel="stylesheet" href="/css/style.css"><link rel="stylesheet" href="/css/app.css">
  <script src="/js/theme.js"></script></head><body data-ui-page="knowledge"><main class="container py-3">
  <h1>Заметка</h1><div id="first-note"></div><div id="second-note"></div><div id="broken-note"></div>
  <form data-knowledge-note-form><textarea name="body" id="noteBody"></textarea><button type="button" data-note-add-photo>Добавить фото</button>
  <input type="file" data-note-photo-input><div data-note-photo-state></div><div data-note-body-preview></div><button type="submit">Сохранить</button></form>
  <div class="modal" id="dialogDetailsModal" tabindex="-1"><div class="modal-dialog"><div class="modal-content"><div class="modal-body" id="modal-note"></div></div></div></div>
  <dialog id="native-host"><div id="native-note"></div></dialog></main>
  <script src="/vendor/bootstrap/5.3.3/bootstrap.bundle.min.js"></script><script src="/js/knowledge-note-content.js"></script><script src="/js/knowledge-notes.js"></script>
  <script>
    window.qaErrors=[];window.addEventListener('error',e=>{if(e.message)qaErrors.push(e.message)});
    window.qaFormSubmits=0;document.querySelector('form').addEventListener('submit',e=>{e.preventDefault();qaFormSubmits++});
    IguanaNoteContent.render(document.getElementById('first-note'),${JSON.stringify(body)},20);
    IguanaNoteContent.render(document.getElementById('second-note'),${JSON.stringify(single)});
    IguanaNoteContent.render(document.getElementById('broken-note'),${JSON.stringify(photo(12))});
    IguanaNoteContent.render(document.getElementById('modal-note'),${JSON.stringify(single)});
    IguanaNoteContent.render(document.getElementById('native-note'),${JSON.stringify(photo(2))});
    window.qaModal=new bootstrap.Modal(document.getElementById('dialogDetailsModal'));
  </script></body></html>`;

class Cdp {
  constructor(ws) {
    this.ws = ws; this.next = 0; this.pending = new Map(); this.downloads = 0; this.windows = 0;
    ws.addEventListener('message', event => {
      const m = JSON.parse(String(event.data)), pending = this.pending.get(m.id);
      if (m.method === 'Browser.downloadWillBegin') this.downloads++;
      if (m.method === 'Page.windowOpen') this.windows++;
      if (!pending) return;
      clearTimeout(pending.timer); this.pending.delete(m.id);
      if (m.error) pending.reject(Error(JSON.stringify(m.error))); else pending.resolve(m.result);
    });
  }
  send(method, params = {}, sessionId) {
    return new Promise((resolve, reject) => {
      const id = ++this.next, timer = setTimeout(() => { this.pending.delete(id); reject(Error('CDP timeout: ' + method)); }, 20000);
      this.pending.set(id, { resolve, reject, timer }); this.ws.send(JSON.stringify({ id, method, params, sessionId }));
    });
  }
}
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));

async function main() {
  fs.mkdirSync(output, { recursive: true });
  const server = http.createServer(async (req, res) => {
    const url = new URL(req.url, 'http://fixture');
    const send = (data, type, status = 200) => { res.writeHead(status, { 'Content-Type': type,
      'Content-Security-Policy': "default-src 'self' data: 'unsafe-inline'; connect-src 'self'" }); res.end(data); };
    if (url.pathname === '/') return send(fixture.replace('<script src="/js/theme.js">',
      `<script>localStorage.setItem('iguana:theme',${JSON.stringify(url.searchParams.get('theme') || 'light')});</script><script src="/js/theme.js">`), 'text/html; charset=utf-8');
    if (url.pathname === '/api/attachments/knowledge-base/note-photos' && req.method === 'POST') {
      assert.equal(req.headers['x-csrf-token'], 'fixture-csrf');
      const chunks = []; for await (const chunk of req) chunks.push(chunk); assert(Buffer.concat(chunks).includes(image)); uploads++;
      return send(JSON.stringify({ storedName: 'uploaded.png' }), 'application/json');
    }
    if (url.pathname.startsWith('/api/attachments/knowledge-base/')) {
      if (url.pathname.endsWith('photo-12.png')) return send('', 'text/plain', 404);
      // Preserve the real download header: viewer must display an image without navigating to it.
      res.setHeader('Content-Disposition', 'attachment; filename="photo.png"'); return send(image, 'image/png');
    }
    const file = path.resolve(resources, '.' + url.pathname);
    if (!file.startsWith(resources + path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) return send('', 'text/plain', 404);
    const mime = { '.js': 'text/javascript', '.css': 'text/css', '.png': 'image/png', '.svg': 'image/svg+xml', '.woff2': 'font/woff2' };
    send(fs.readFileSync(file), mime[path.extname(file)] || 'application/octet-stream');
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const origin = `http://127.0.0.1:${server.address().port}`;
  const binary = process.env.BROWSER_BINARY || ['C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
    'C:/Program Files/Google/Chrome/Application/chrome.exe', '/usr/bin/chromium'].find(fs.existsSync);
  assert(binary, 'Set BROWSER_BINARY');
  const profile = path.join(output, 'browser-' + crypto.randomUUID()); fs.mkdirSync(profile);
  const log = fs.openSync(profile + '.log', 'wx');
  const child = cp.spawn(binary, ['--headless=new', '--disable-gpu', '--no-first-run', '--disable-background-networking',
    '--remote-debugging-port=0', '--remote-debugging-address=127.0.0.1', '--user-data-dir=' + profile, 'about:blank'],
    { windowsHide: true, stdio: ['ignore', log, log] });
  let cdp;
  try {
    const active = path.join(profile, 'DevToolsActivePort');
    for (let i = 0; i < 150 && !fs.existsSync(active); i++) await pause(100);
    assert(fs.existsSync(active), 'Browser did not start');
    const [port, endpoint] = fs.readFileSync(active, 'utf8').trim().split(/\r?\n/), ws = new WebSocket('ws://127.0.0.1:' + port + endpoint);
    await new Promise((resolve, reject) => { ws.addEventListener('open', resolve, { once: true }); ws.addEventListener('error', reject, { once: true }); });
    cdp = new Cdp(ws);
    const { targetId } = await cdp.send('Target.createTarget', { url: 'about:blank' });
    const { sessionId } = await cdp.send('Target.attachToTarget', { targetId, flatten: true });
    await cdp.send('Page.enable', {}, sessionId);
    await cdp.send('Browser.setDownloadBehavior', { behavior: 'deny', eventsEnabled: true });
    const evaluate = async expression => {
      const r = await cdp.send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true }, sessionId);
      assert(!r.exceptionDetails, JSON.stringify(r.exceptionDetails)); return r.result.value;
    };
    const wait = async expression => {
      for (let i = 0; i < 100; i++) { if (await evaluate(expression)) return; await pause(50); }
      const diagnostic = await evaluate('({url:location.href,focus:document.activeElement?.outerHTML?.slice(0,400),open:document.querySelector(".note-photo-viewer")?.open,src:document.querySelector("[data-note-photo-image]")?.src,status:document.querySelector("[data-note-photo-status]")?.textContent,errors:window.qaErrors})');
      throw Error('Not ready: ' + expression + '\n' + JSON.stringify(diagnostic));
    };
    const key = async (key, code = key, modifiers = 0) => {
      const windowsVirtualKeyCode = { Enter: 13, Escape: 27, ArrowRight: 39, ArrowLeft: 37, Tab: 9 }[key];
      for (const type of ['keyDown', 'keyUp']) await cdp.send('Input.dispatchKeyEvent', {
        type, key, code, modifiers, windowsVirtualKeyCode,
        text: type === 'keyDown' && key === 'Enter' ? '\r' : undefined,
      }, sessionId);
    };
    const closed = () => wait('!document.querySelector(".note-photo-viewer")?.open');
    const loaded = () => wait('document.querySelector("[data-note-photo-image]")?.naturalWidth>0 && document.querySelector("[data-note-photo-status]").hidden');
    const counter = async expected => assert.equal(await evaluate('document.querySelector("[data-note-photo-counter]").textContent'), expected);
    const cases = [];
    for (const theme of ['light', 'dark']) for (const width of [320, 390, 1024, 1440]) {
      await cdp.send('Emulation.setDeviceMetricsOverride', { width, height: 1000, deviceScaleFactor: 1, mobile: false }, sessionId);
      const search = `?theme=${theme}&width=${width}`;
      await cdp.send('Page.navigate', { url: origin + '/' + search }, sessionId);
      await wait(`location.search===${JSON.stringify(search)} && typeof qaModal!=='undefined' && document.documentElement.dataset.theme===${JSON.stringify(theme)}`);
      assert.equal(await evaluate('document.querySelectorAll("#first-note [data-note-photo-open]").length'), 9);
      assert.equal(await evaluate('document.querySelectorAll("a.note-photo-link").length'), 0);
      await evaluate('window.qaTrigger=document.querySelectorAll("#first-note [data-note-photo-open]")[1];qaTrigger.focus()'); await key('Enter'); await loaded(); await counter('2 / 10');
      await key('ArrowRight'); await loaded(); await counter('3 / 10');
      assert(await evaluate('document.querySelector("[data-note-photo-prev]").getBoundingClientRect().height>=44'));
      await evaluate('document.querySelector("[data-note-photo-prev]").click()'); await counter('2 / 10');
      await key('ArrowLeft'); await key('ArrowLeft'); await loaded(); await counter('10 / 10');
      await evaluate('document.querySelector("[data-note-photo-next]").click()'); await loaded(); await counter('1 / 10');
      await evaluate('document.querySelector("[data-note-photo-zoom]").click()');
      assert(await evaluate('document.querySelector(".note-photo-viewer").classList.contains("is-zoomed") && document.querySelector("[data-note-photo-zoom]").getAttribute("aria-pressed")==="true"'));
      assert(await evaluate('document.querySelector("[data-note-photo-image]").getBoundingClientRect().width===document.querySelector("[data-note-photo-image]").naturalWidth'));
      await key('ArrowRight'); await loaded(); assert(await evaluate('!document.querySelector(".note-photo-viewer").classList.contains("is-zoomed")'));
      const bounds = await evaluate('(()=>{const r=document.querySelector(".note-photo-viewer").getBoundingClientRect();return {x:r.x,y:r.y,right:r.right,bottom:r.bottom,width:innerWidth,height:innerHeight,overflow:document.documentElement.scrollWidth>innerWidth+1};})()');
      assert(bounds.x >= 0 && bounds.y >= 0 && bounds.right <= bounds.width && bounds.bottom <= bounds.height && !bounds.overflow);
      const screen = await cdp.send('Page.captureScreenshot', { format: 'png' }, sessionId); fs.writeFileSync(path.join(output, `${theme}-${width}.png`), Buffer.from(screen.data, 'base64'));
      await key('Escape'); await closed(); assert(await evaluate('document.activeElement===qaTrigger && !document.documentElement.classList.contains("note-photo-viewer-open")'));
      await evaluate('document.querySelector("#first-note .note-photo-more").click()'); await loaded(); await counter('9 / 10');
      const point = await evaluate('(()=>{const r=document.querySelector(".note-photo-viewer__stage").getBoundingClientRect();return {x:r.x+r.width*.8,y:r.y+r.height*.5,to:r.x+r.width*.2};})()');
      for (const [type, x] of [['touchStart', point.x], ['touchMove', point.to], ['touchEnd', point.to]]) await cdp.send('Input.dispatchTouchEvent', { type, touchPoints: type === 'touchEnd' ? [] : [{ x, y: point.y, id: 0 }] }, sessionId);
      await wait('document.querySelector("[data-note-photo-counter]").textContent==="10 / 10"');
      await evaluate('document.querySelector("[data-note-photo-close]").click()'); await closed();
      await evaluate('document.querySelector("#second-note [data-note-photo-open]").click()'); await loaded(); await counter('1 / 1');
      assert.equal(await evaluate('document.querySelector("[data-note-photo-caption]").textContent'), untrustedCaption);
      assert(await evaluate('!window.qaInjected && !document.querySelector(".note-photo-viewer [onerror]")'));
      for (const type of ['mousePressed', 'mouseReleased']) await cdp.send('Input.dispatchMouseEvent', { type, x: 0, y: 0, button: 'left', clickCount: 1 }, sessionId);
      await closed();
      await evaluate('qaModal.show()'); await wait('document.getElementById("dialogDetailsModal").classList.contains("show")');
      await evaluate('document.querySelector("#modal-note [data-note-photo-open]").click()'); await loaded(); await counter('1 / 1');
      assert(await evaluate('document.querySelector("[data-note-photo-prev]").hidden&&document.querySelector("[data-note-photo-next]").hidden'));
      await key('Tab', 'Tab', 8); assert(await evaluate('document.activeElement.hasAttribute("data-note-photo-zoom")'));
      for (let i = 0; i < 5; i++) { await key('Tab'); assert(await evaluate('document.querySelector(".note-photo-viewer").contains(document.activeElement)')); }
      await key('Escape'); await closed(); assert(await evaluate('document.getElementById("dialogDetailsModal").classList.contains("show")'));
      await evaluate('qaModal.hide();document.getElementById("native-host").showModal();document.querySelector("#native-note [data-note-photo-open]").click()'); await loaded();
      await key('Escape'); await closed(); assert(await evaluate('document.getElementById("native-host").open')); await evaluate('document.getElementById("native-host").close()');
      await evaluate('document.querySelector("#broken-note [data-note-photo-open]").click()');
      await wait('document.querySelector("[data-note-photo-status]").textContent.includes("Не удалось")'); await key('Escape'); await closed();
      await evaluate(`(()=>{const dt=new DataTransfer();dt.items.add(new File([Uint8Array.from(atob(${JSON.stringify(image.toString('base64'))}),c=>c.charCodeAt(0))],'photo.png',{type:'image/png'}));const input=document.querySelector('[data-note-photo-input]');input.files=dt.files;input.dispatchEvent(new Event('change',{bubbles:true}));})()`);
      await wait('document.querySelector("[data-note-photo-state]").textContent.includes("Добавлено фото: 1")');
      await evaluate('document.querySelector("[data-note-body-preview] [data-note-photo-open]").click()'); await loaded(); await counter('1 / 1'); await key('Escape'); await closed();
      assert.equal(await evaluate('qaFormSubmits'), 0); assert.equal(await evaluate('document.querySelectorAll(".note-photo-viewer").length'), 1); assert.deepEqual(await evaluate('qaErrors'), []);
      cases.push({ theme, width, gallery: 10, keyboard: true, swipe: true, zoom: true, bootstrapFocusTrap: true, nativeParent: true, uploadPreview: true, captionXssBlocked: true, backdrop: true });
    }
    assert.equal(cdp.downloads, 0); assert.equal(cdp.windows, 0);
    const report = { status: 'GREEN', cases, downloads: cdp.downloads, newWindows: cdp.windows, uploads };
    fs.writeFileSync(path.join(output, 'report.json'), JSON.stringify(report, null, 2)); console.log(JSON.stringify(report));
  } finally {
    if (cdp) { try { await cdp.send('Browser.close'); } catch (_) {} cdp.ws.close(); }
    if (child.exitCode === null) child.kill(); fs.closeSync(log); server.closeAllConnections(); server.close();
  }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
