/* Native photo viewer with production JS/CSS and loopback fixtures. Node 22+, Chromium/Edge. */
const fs = require('node:fs'), path = require('node:path'), http = require('node:http');
const cp = require('node:child_process'), crypto = require('node:crypto'), assert = require('node:assert/strict');
const root = path.resolve(__dirname, '..'), resources = path.join(root, 'spring-panel/src/main/resources/static');
const output = path.join(root, 'spring-panel/target/knowledge-note-gallery-qa');
const image = fs.readFileSync(path.join(resources, 'images/iguana-brand.png'));
function testPng(width, height) {
  const crc = data => { let c = -1; for (const b of data) { c ^= b; for (let i=0;i<8;i++) c=(c>>>1)^((c&1)?0xedb88320:0); } return (c^-1)>>>0; };
  const chunk = (type, data) => { const name=Buffer.from(type),size=Buffer.alloc(4),checksum=Buffer.alloc(4);size.writeUInt32BE(data.length);checksum.writeUInt32BE(crc(Buffer.concat([name,data])));return Buffer.concat([size,name,data,checksum]); };
  const pixels=Buffer.alloc(height*(width*4+1));
  for(let y=0;y<height;y++) for(let x=0;x<width;x++) {const i=y*(width*4+1)+1+x*4;pixels[i]=Math.floor(x/width*255);pixels[i+1]=Math.floor(y/height*255);pixels[i+2]=(Math.floor(x/100)%2)*180;pixels[i+3]=255;}
  const header=Buffer.alloc(13);header.writeUInt32BE(width);header.writeUInt32BE(height,4);header[8]=8;header[9]=6;
  return Buffer.concat([Buffer.from('89504e470d0a1a0a','hex'),chunk('IHDR',header),chunk('IDAT',require('node:zlib').deflateSync(pixels)),chunk('IEND',Buffer.alloc(0))]);
}
const landscape=testPng(1920,1080),portrait=testPng(900,1600);
const fileBytes=new Map([['report.pdf',Buffer.from('%PDF-1.7\nFixture document')],['archive.zip',Buffer.from('504b0506000000000000000000000000000000000000','hex')]]);
for(let i=1;i<=11;i++)fileBytes.set(`photo-${i}.png`,i===11?landscape:i===2?portrait:image);
const photo = i => `![Фото ${i}](/api/attachments/knowledge-base/photo-${i}.png)`;
const body = 'Текст заметки\n' + Array.from({ length: 10 }, (_, i) => photo(i + 1)).join('\n');
const attachments='\n[Отчёт](/api/attachments/knowledge-base/report.pdf)\n[Архив](/api/attachments/knowledge-base/archive.zip)';
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
    IguanaNoteContent.render(document.getElementById('first-note'),${JSON.stringify(body+attachments)},20);
    IguanaNoteContent.render(document.getElementById('second-note'),${JSON.stringify(single)});
    IguanaNoteContent.render(document.getElementById('broken-note'),${JSON.stringify(photo(12))});
    IguanaNoteContent.render(document.getElementById('modal-note'),${JSON.stringify(single)});
    IguanaNoteContent.render(document.getElementById('native-note'),${JSON.stringify(photo(2))});
    window.qaModal=new bootstrap.Modal(document.getElementById('dialogDetailsModal'));
  </script></body></html>`;

class Cdp {
  constructor(ws) {
    this.ws = ws; this.next = 0; this.pending = new Map(); this.downloads = []; this.completed = new Set(); this.windows = 0;
    ws.addEventListener('message', event => {
      const m = JSON.parse(String(event.data)), pending = this.pending.get(m.id);
      if (m.method === 'Browser.downloadWillBegin') this.downloads.push(m.params);
      if (m.method === 'Browser.downloadProgress' && m.params.state === 'completed') this.completed.add(m.params.guid);
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
    if (url.pathname === '/api/attachments/knowledge-base/note-files' && req.method === 'POST') {
      assert.equal(req.headers['x-csrf-token'], 'fixture-csrf');
      const chunks=[];for await(const chunk of req)chunks.push(chunk);const raw=Buffer.concat(chunks),name=raw.toString().match(/filename="([^"]+)"/)[1];
      const boundary=req.headers['content-type'].match(/boundary=(.+)$/)[1],start=raw.indexOf('\r\n\r\n')+4,end=raw.indexOf('\r\n--'+boundary,start),payload=raw.subarray(start,end);
      const storedName=`uploaded-${++uploads}-${name}`;fileBytes.set(storedName,payload);
      return send(JSON.stringify({storedName,originalName:name,size:payload.length}),'application/json');
    }
    if (url.pathname.startsWith('/api/attachments/knowledge-base/')) {
      const name=decodeURIComponent(url.pathname.split('/').pop()),data=fileBytes.get(name);
      if(!data)return send('', 'text/plain', 404);
      // Preserve the real download header: viewer must display an image without navigating to it.
      res.setHeader('Content-Disposition', `attachment; filename="${name}"`);return send(data,/\.png$/.test(name)?'image/png':'application/octet-stream');
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
  const profile = path.join(output, 'b-' + crypto.randomBytes(4).toString('hex')); fs.mkdirSync(profile);
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
    let { targetId } = await cdp.send('Target.createTarget', { url: 'about:blank' });
    let { sessionId } = await cdp.send('Target.attachToTarget', { targetId, flatten: true });
    await cdp.send('Page.enable', {}, sessionId);
    const downloadPath=path.join(output,'downloads');fs.mkdirSync(downloadPath,{recursive:true});
    await cdp.send('Browser.setDownloadBehavior', { behavior: 'allowAndName', downloadPath, eventsEnabled: true });
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
    const download = async (selector, expected) => {
      const before=cdp.downloads.length;await evaluate(`document.querySelector(${JSON.stringify(selector)}).click()`);
      for(let i=0;i<100&&(cdp.downloads.length===before||!cdp.completed.has(cdp.downloads[before]?.guid));i++)await pause(50);
      assert.equal(cdp.downloads.length,before+1);const item=cdp.downloads[before];assert(cdp.completed.has(item.guid));
      assert.deepEqual(fs.readFileSync(path.join(downloadPath,item.guid)),expected);
    };
    const geometry = () => evaluate(`(()=>{const stage=document.querySelector('.note-photo-viewer__stage'),img=document.querySelector('[data-note-photo-image]'),a=stage.getBoundingClientRect(),b=img.getBoundingClientRect();return {stage:{x:a.x,y:a.y,w:stage.clientWidth,h:stage.clientHeight},img:{x:b.x,y:b.y,w:b.width,h:b.height},natural:img.naturalWidth,scale:img.width/img.naturalWidth,scrollLeft:stage.scrollLeft,scrollTop:stage.scrollTop};})()`);
    const fitFits = async () => {
      await evaluate('new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)))');
      const g=await geometry();assert(g.img.w>0&&g.img.h>0);
      assert(g.img.w<=g.stage.w+1&&g.img.h<=g.stage.h+1,JSON.stringify(g));
      assert(Math.abs(g.img.x+g.img.w/2-g.stage.x-g.stage.w/2)<3,JSON.stringify(g));
      assert(Math.abs(g.img.y+g.img.h/2-g.stage.y-g.stage.h/2)<3,JSON.stringify(g));
    };
    const cases=[];
    for(const theme of ['light','dark'])for(const width of [320,390,1024,1440]){
      if(cases.length){
        await cdp.send('Target.closeTarget',{targetId});
        ({targetId}=await cdp.send('Target.createTarget',{url:'about:blank'}));
        ({sessionId}=await cdp.send('Target.attachToTarget',{targetId,flatten:true}));
        await cdp.send('Page.enable',{},sessionId);
      }
      await cdp.send('Emulation.setDeviceMetricsOverride',{width,height:1000,deviceScaleFactor:1,mobile:false},sessionId);
      await cdp.send('Emulation.setTouchEmulationEnabled',{enabled:true,maxTouchPoints:5},sessionId);
      await cdp.send('Emulation.setPageScaleFactor',{pageScaleFactor:1},sessionId);
      const search=`?theme=${theme}&width=${width}`;await cdp.send('Page.navigate',{url:origin+'/'+search},sessionId);
      await wait(`location.search===${JSON.stringify(search)}&&typeof qaModal!=='undefined'&&document.documentElement.dataset.theme===${JSON.stringify(theme)}`);
      const passiveDownloads=cdp.downloads.length;
      assert.equal(await evaluate('document.querySelectorAll("#first-note [data-note-photo-open]").length'),9);
      assert.equal(await evaluate('document.querySelectorAll("#first-note .note-file-link").length'),2);
      await evaluate('window.qaTrigger=document.querySelectorAll("#first-note [data-note-photo-open]")[1];qaTrigger.focus()');await key('Enter');await loaded();await counter('2 / 10');await fitFits();
      await key('ArrowRight');await loaded();await counter('3 / 10');await key('ArrowLeft');await key('ArrowLeft');await key('ArrowLeft');await loaded();await counter('10 / 10');
      await evaluate('document.querySelector("[data-note-photo-fit]").click()');await fitFits();
      await key('Escape');await closed();assert(await evaluate('document.activeElement===qaTrigger'));
      await evaluate('document.querySelector("#second-note [data-note-photo-open]").click()');await loaded();await counter('1 / 1');await fitFits();
      let g=await geometry();assert(g.stage.w>width*.7&&g.img.w>g.stage.w*.8,JSON.stringify(g));
      assert(await evaluate('document.querySelector("[data-note-photo-prev]").hidden&&document.querySelector("[data-note-photo-next]").hidden'));
      assert.equal(await evaluate('document.querySelector("[data-note-photo-caption]").textContent'),untrustedCaption);
      assert(await evaluate('!window.qaInjected&&!document.querySelector(".note-photo-viewer [onerror]")'));
      let screen=await cdp.send('Page.captureScreenshot',{format:'png'},sessionId);fs.writeFileSync(path.join(output,`${theme}-${width}-single-fit.png`),Buffer.from(screen.data,'base64'));
      await evaluate('document.querySelector("[data-note-photo-zoom]").click()');g=await geometry();assert(Math.abs(g.scale-1)<.001);
      await evaluate('const input=document.querySelector("[data-note-photo-scale]");input.value="200";input.dispatchEvent(new Event("change",{bubbles:true}))');g=await geometry();assert(Math.abs(g.scale-2)<.001);
      await evaluate('document.querySelector("[data-note-photo-minus]").click()');g=await geometry();assert(Math.abs(g.scale-1.6)<.001);
      await evaluate('document.querySelector("[data-note-photo-plus]").click()');g=await geometry();assert(Math.abs(g.scale-2)<.001);
      const p=await evaluate('(()=>{const s=document.querySelector(".note-photo-viewer__stage");s.scrollLeft=0;s.scrollTop=0;const r=s.getBoundingClientRect();return {x:r.x+r.width*.75,y:r.y+r.height*.5};})()');
      for(const [type,x,button,buttons]of [['mousePressed',p.x,'left',1],['mouseMoved',p.x-80,'none',1],['mouseReleased',p.x-80,'left',0]])await cdp.send('Input.dispatchMouseEvent',{type,x,y:p.y,button,buttons,clickCount:1},sessionId);
      g=await geometry();assert(g.scrollLeft>40,JSON.stringify(g));
      await cdp.send('Input.dispatchMouseEvent',{type:'mouseWheel',x:p.x,y:p.y,deltaX:0,deltaY:-100},sessionId);await pause(70);g=await geometry();assert(g.scale>2);
      await evaluate('document.querySelector("[data-note-photo-fit]").click()');await fitFits();
      const pinchPoint=await evaluate('(()=>{const r=document.querySelector(".note-photo-viewer__stage").getBoundingClientRect();return{x:r.x+r.width/2,y:r.y+r.height/2,d:Math.min(30,r.width/8)};})()'),beforePinch=(await geometry()).scale;
      await evaluate('window.qaPointers=[];for(const type of ["pointerdown","pointermove","pointerup","pointercancel"])document.querySelector(".note-photo-viewer__stage").addEventListener(type,e=>qaPointers.push({type,id:e.pointerId,button:e.button,x:e.clientX,y:e.clientY}),{capture:true})');
      for(const [type,factor]of [['touchStart',1],['touchMove',2],['touchEnd',2]]){
        await cdp.send('Input.dispatchTouchEvent',{type,touchPoints:type==='touchEnd'?[]:[{id:0,x:pinchPoint.x-pinchPoint.d*factor,y:pinchPoint.y},{id:1,x:pinchPoint.x+pinchPoint.d*factor,y:pinchPoint.y}]},sessionId);await pause(50);
      }
      assert((await geometry()).scale>beforePinch*1.5,JSON.stringify({theme,width,beforePinch,after:await geometry(),events:await evaluate('qaPointers')}));
      await evaluate('document.querySelector("[data-note-photo-fit]").click()');await fitFits();
      assert.equal(cdp.downloads.length,passiveDownloads,'Viewing/zooming must not trigger downloads');await download('[data-note-photo-download]',landscape);
      await key('Escape');await closed();await download('#first-note .note-file-link',fileBytes.get('report.pdf'));
      await evaluate('document.querySelectorAll("#first-note .note-file-link")[1].id="qaArchive"');await download('#qaArchive',fileBytes.get('archive.zip'));
      await evaluate('document.querySelector("#first-note .note-photo-more").click()');await loaded();await counter('9 / 10');
      const swipe=await evaluate('(()=>{const r=document.querySelector(".note-photo-viewer__stage").getBoundingClientRect();return{x:r.x+r.width*.8,y:r.y+r.height*.5,to:r.x+r.width*.2};})()');
      for(const [type,x]of [['touchStart',swipe.x],['touchMove',swipe.to],['touchEnd',swipe.to]])await cdp.send('Input.dispatchTouchEvent',{type,touchPoints:type==='touchEnd'?[]:[{x,y:swipe.y,id:0}]},sessionId);
      await wait('document.querySelector("[data-note-photo-counter]").textContent==="10 / 10"');await key('Escape');await closed();
      await evaluate('qaModal.show()');await wait('document.getElementById("dialogDetailsModal").classList.contains("show")');
      await evaluate('document.querySelector("#modal-note [data-note-photo-open]").click()');await loaded();await fitFits();
      for(let i=0;i<12;i++){await key('Tab');assert(await evaluate('document.querySelector(".note-photo-viewer").contains(document.activeElement)'));}
      await key('Escape');await closed();assert(await evaluate('document.getElementById("dialogDetailsModal").classList.contains("show")'));
      await evaluate('qaModal.hide();document.getElementById("native-host").showModal();document.querySelector("#native-note [data-note-photo-open]").click()');await loaded();await fitFits();
      await key('Escape');await closed();assert(await evaluate('document.getElementById("native-host").open'));await evaluate('document.getElementById("native-host").close()');
      await evaluate('document.querySelector("#broken-note [data-note-photo-open]").click()');await wait('document.querySelector("[data-note-photo-status]").textContent.includes("Не удалось")');await key('Escape');await closed();
      await evaluate(`(()=>{const dt=new DataTransfer();dt.items.add(new File([Uint8Array.from(atob(${JSON.stringify(image.toString('base64'))}),c=>c.charCodeAt(0))],'photo.png',{type:'image/png'}));dt.items.add(new File(['%PDF-1.7\\nUploaded'],'document.pdf',{type:'application/pdf'}));dt.items.add(new File([Uint8Array.from([80,75,5,6,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0])],'bundle.zip',{type:'application/zip'}));const input=document.querySelector('[data-note-photo-input]');input.files=dt.files;input.dispatchEvent(new Event('change',{bubbles:true}));})()`);
      await wait('document.querySelector("[data-note-photo-state]").textContent.includes("Добавлено файлов: 3")');
      assert.equal(await evaluate('document.querySelectorAll("[data-note-body-preview] .note-file-link").length'),2);
      await evaluate('document.querySelector("[data-note-body-preview] [data-note-photo-open]").click()');await loaded();await counter('1 / 1');await fitFits();await key('Escape');await closed();
      assert.equal(await evaluate('qaFormSubmits'),0);assert.equal(await evaluate('document.querySelectorAll(".note-photo-viewer").length'),1);assert.deepEqual(await evaluate('qaErrors'),[]);
      cases.push({theme,width,singleGeometry:true,portrait:true,zoomPercent:true,pan:true,wheel:true,pinch:true,swipe:true,downloads:3,mixedUpload:true,acl:'covered by PostgreSQL HTTP tests'});
    }
    assert.equal(cdp.windows,0);assert.equal(cdp.downloads.length,24);
    const report={status:'GREEN',cases,downloads:cdp.downloads.length,newWindows:cdp.windows,uploads};
    fs.writeFileSync(path.join(output,'report.json'),JSON.stringify(report,null,2));console.log(JSON.stringify(report));
  } finally {
    if (cdp) { try { await cdp.send('Browser.close'); } catch (_) {} cdp.ws.close(); }
    if (child.exitCode === null) child.kill(); fs.closeSync(log); server.closeAllConnections(); server.close();
  }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
