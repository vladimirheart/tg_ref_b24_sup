/* Production browser scripts in isolated Chromium documents; API fixtures, no external network. */
const fs = require('node:fs'), path = require('node:path'), cp = require('node:child_process'), assert = require('node:assert/strict');
const root = path.resolve(__dirname, '..');
const folder = path.join(root, 'spring-panel/target/access-recovery-qa');
const input = Object.fromEntries(['attention', 'password-reset-requests', 'auth-login'].map(name => [name, fs.readFileSync(path.join(root, `spring-panel/src/main/resources/static/js/${name}.js`), 'utf8')]));
class Cdp {
  constructor(ws) {
    this.ws = ws; this.id = 0; this.pending = new Map();
    ws.addEventListener('message', event => {
      const message = JSON.parse(String(event.data)), pending = this.pending.get(message.id);
      if (!pending) return; this.pending.delete(message.id); clearTimeout(pending.timer);
      message.error ? pending.reject(Error(JSON.stringify(message.error))) : pending.resolve(message.result);
    });
  }
  send(method, params = {}, sessionId) {
    return new Promise((resolve, reject) => {
      const id = ++this.id, timer = setTimeout(() => reject(Error(`CDP timeout: ${method}`)), 45000);
      this.pending.set(id, { resolve, reject, timer }); this.ws.send(JSON.stringify({ id, method, params, sessionId }));
    });
  }
}
async function suite(source) {
  const cases = [];
  for (const name of ['counts', 'zero', 'unauthorized', 'seen', 'secret', 'escape', 'verify', 'unknown', 'reject', 'busy', 'conflict', 'login']) {
    const frame = document.createElement('iframe'); document.body.append(frame);
    try {
      const win = frame.contentWindow;
      win.document.open(); win.document.write('<html><head><meta name="_csrf" content="fixture-csrf"></head><body></body></html>'); win.document.close();
      await new Promise(resolve => setTimeout(resolve, 0));
      await win.eval('(' + exercise.toString() + ')(' + JSON.stringify({ source, name }) + ')');
      cases.push({ name, ok: true });
    } catch (error) { cases.push({ name, ok: false, error: error.message }); }
    finally { frame.remove(); }
  }
  return cases;

  async function exercise({ source, name }) {
    const check = (value, message) => { if (!value) throw Error(message); };
    const tick = () => new Promise(resolve => setTimeout(resolve, 0));
    async function wait(predicate) { for (let i=0;i<100;i++) { if (predicate()) return; await tick(); } throw Error('UI did not settle'); }
    const writes = [];
    window.iguanaAttention = { seen: async (...args) => writes.push({ seen: args }), refresh: () => {} };
    Object.defineProperty(document, 'hidden', { configurable: true, value: false });
    if (['counts','zero','unauthorized','seen'].includes(name)) {
      document.body.innerHTML = `<div data-attention-panel hidden><a data-attention-kind="unblock" title="Разблокировки" hidden><strong data-attention-count></strong><small data-attention-new></small></a><a data-attention-kind="recovery" title="Восстановление" hidden><strong data-attention-count></strong><small data-attention-new></small></a></div>`;
      if (name === 'seen') document.body.insertAdjacentHTML('beforeend','<main data-attention-page="unblock"><div data-request-id="19"></div></main>');
      window.fetch = async (url, options = {}) => {
        if (options.method === 'POST') { writes.push({ url, options }); return { ok: true }; }
        return { ok: true, json: async () => ({ unblock: { pending: name==='zero'?0:3, unseen: name==='seen'?0:2 }, ...(name==='unauthorized'?{}:{ recovery: { pending: name==='zero'?0:1, unseen: 0 } }) }) };
      };
      (0,eval)(source.attention); await tick();
      if (name === 'zero') check(document.querySelector('[data-attention-panel]').hidden,'Empty panel must hide');
      else {
        check(!document.querySelector('[data-attention-panel]').hidden,'Pending requests must stay visible');
        check(document.querySelector('[data-attention-count]').textContent==='3','Count must show unresolved requests');
        if (name==='counts') check(!document.querySelector('[data-attention-new]').hidden,'Personal new marker required');
        if (name==='unauthorized') check(document.querySelector('[data-attention-kind="recovery"]').hidden,'Unauthorized recovery category must hide');
        if (name==='seen') {
          await wait(()=>writes.length===1);
          check(writes[0].options.headers['X-XSRF-TOKEN']==='fixture-csrf','Read markers need CSRF');
          check(JSON.parse(writes[0].options.body).ids[0]===19,'Mark only displayed request IDs');
          check(document.querySelector('[data-attention-count]').textContent==='3','Reading must preserve pending count');
        }
      }
      return;
    }
    if (name==='login') {
      document.body.innerHTML='<canvas id="authParticles"></canvas><input name="_csrf" value="fixture-csrf"><form data-forgot-password-form><input name="username" value=" legacy "><input name="kind" value="blocked"><input name="full_name" value="Иван"><input name="contact" value="org@example.test"><textarea name="comment">Помогите</textarea><button data-forgot-password-submit></button></form><div data-forgot-password-error></div><div data-forgot-password-success></div>';
      window.requestAnimationFrame=()=>{};
      window.fetch=async(url,options)=>{writes.push({url,options});return{json:async()=>({success:true,message:'Обращение принято'})};};
      (0,eval)(source['auth-login']);
      document.querySelector('form').dispatchEvent(new Event('submit',{cancelable:true,bubbles:true})); await tick(); await tick();
      const payload=JSON.parse(writes[0].options.body);
      check(payload.username===' legacy ','Preserve exact legacy login');
      check(payload.kind==='blocked'&&payload.full_name==='Иван'&&payload.contact==='org@example.test','Send recovery context');
      check(writes[0].options.headers['X-XSRF-TOKEN']==='fixture-csrf','Public requests need CSRF');
      return;
    }
    let item = { id: 11, user_id: 3, username_snapshot:'operator', linked_username:'operator', status:'PENDING', request_kind:'password', repeat_count:2, requested_note:'<img src=x onerror="window.injected=true">', requested_user_agent:'<script>alert(1)</script>', created_at:'2026-11-25T12:00:00Z' };
    if(name==='unknown') Object.assign(item,{user_id:null,linked_username:null,status:'NEEDS_REVIEW'});
    document.body.innerHTML='<main data-password-reset-requests><div data-password-reset-error class="d-none"></div><div data-password-reset-success class="d-none"></div><button data-password-reset-refresh>Обновить</button><div data-password-reset-empty></div><table><tbody data-password-reset-body></tbody></table></main>';
    let release;
    window.fetch=async(url,options={})=>{
      if(options.method==='POST') {
        writes.push({url,options});
        if(name==='busy') await new Promise(resolve=>{release=resolve;});
        if(name==='conflict') return {ok:false,json:async()=>({success:false,error:'Заявка уже обработана'})};
        item={...item,status:'APPROVED'};
        return {ok:true,json:async()=>({success:true,temporary_password:name==='reject'?'':'Fixture!Password',message:'Готово'})};
      }
      return {ok:true,json:async()=>({success:true,items:[item]})};
    };
    (0,eval)(source['password-reset-requests']); document.dispatchEvent(new Event('DOMContentLoaded'));
    await wait(()=>document.querySelector('[data-recovery-open]'));
    document.querySelector('[data-recovery-open]').click(); await tick();
    check(writes.some(entry=>entry.seen),'Opening a request must mark it seen personally');
    const form=document.querySelector('form');
    if(name==='escape'){check(!document.querySelector('img')&&!window.injected,'Untrusted content must render as text');return;}
    form.querySelector('[name="note"]').value='Проверено у руководителя';
    if(name!=='verify'&&name!=='reject')form.querySelector('[name="verified"]').checked=true;
    const action=name==='unknown'?'link':name==='reject'?'reject':'approve';
    if(name==='unknown'){
      check(!form.querySelector('[data-recovery-action="approve"]'),'Unknown UZ must not allow password reset');
      form.querySelector('[name="username"]').value='real-login';
    }
    form.querySelector(`[data-recovery-action="${action}"]`).click(); await tick();
    if(name==='verify'){check(writes.filter(entry=>entry.url).length===0,'Unverified identity must not write');return;}
    if(name==='busy'){
      await wait(()=>release);check(form.querySelector('button').disabled,'Busy action must disable buttons');
      form.querySelector('button').click();check(writes.filter(entry=>entry.url).length===1,'Duplicate click must not submit');release();
    }
    if(name==='conflict'){await wait(()=>document.querySelector('[data-password-reset-error]').textContent.includes('уже обработана'));return;}
    await wait(()=>!document.querySelector('[data-password-reset-success]').classList.contains('d-none'));
    const sent=writes.find(entry=>entry.url), payload=JSON.parse(sent.options.body);
    check(sent.options.headers['X-XSRF-TOKEN']==='fixture-csrf','Decisions need CSRF');
    if(name==='unknown')check(payload.username==='real-login'&&payload.identity_verified,'Binding must carry verified decision');
    if(name==='reject')check(!payload.identity_verified&&payload.note,'Rejection requires reason, not verified identity');
    if(name==='secret'){
      check(payload.reset_password&&!payload.unblock_account,'Password reset must not silently unblock');
      document.querySelector('[data-password-reset-refresh]').click();await tick();await tick();
      check(document.querySelector('[data-password-reset-success]').textContent.includes('Fixture!Password'),'Refresh must preserve issued password');
      const close=Array.from(document.querySelectorAll('[data-password-reset-success] button')).find(button=>button.textContent==='Скрыть результат');close.click();
      check(!document.body.textContent.includes('Fixture!Password'),'Explicit dismissal must remove password');
    }
  }
}
(async()=>{
  fs.mkdirSync(folder,{recursive:true});
  const executable=process.env.BROWSER_BINARY||['C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe','C:/Program Files/Google/Chrome/Application/chrome.exe'].find(fs.existsSync);
  assert(executable,'Set BROWSER_BINARY');
  const profile=path.join(folder,'browser-'+Date.now());fs.mkdirSync(profile);
  const log=fs.openSync(profile+'.log','wx');
  const child=cp.spawn(executable,['--headless=new','--disable-gpu','--no-first-run','--disable-background-networking','--remote-debugging-port=0','--remote-debugging-address=127.0.0.1','--user-data-dir='+profile,'about:blank'],{windowsHide:true,stdio:['ignore',log,log]});
  let peer;
  try{
    const active=path.join(profile,'DevToolsActivePort'),deadline=Date.now()+30000;
    while(!fs.existsSync(active)){assert(child.exitCode===null&&Date.now()<deadline,'Browser startup failed');await new Promise(resolve=>setTimeout(resolve,100));}
    const [port,endpoint]=fs.readFileSync(active,'utf8').trim().split(/\r?\n/),ws=new WebSocket('ws://127.0.0.1:'+port+endpoint);
    await new Promise((resolve,reject)=>{ws.addEventListener('open',resolve,{once:true});ws.addEventListener('error',reject,{once:true});});
    peer=new Cdp(ws);const browser=await peer.send('Browser.getVersion');
    const {targetId}=await peer.send('Target.createTarget',{url:'about:blank'});
    const {sessionId}=await peer.send('Target.attachToTarget',{targetId,flatten:true});
    await peer.send('Network.enable',{},sessionId);await peer.send('Network.setBlockedURLs',{urls:['*']},sessionId);
    const result=await peer.send('Runtime.evaluate',{expression:'('+suite.toString()+')('+JSON.stringify(input)+')',awaitPromise:true,returnByValue:true},sessionId);
    assert(!result.exceptionDetails,JSON.stringify(result.exceptionDetails));
    const cases=result.result.value,report={browser:browser.product,tests:cases.length,failures:cases.filter(item=>!item.ok).length,cases};
    fs.writeFileSync(path.join(folder,'report.json'),JSON.stringify(report,null,2));console.log(JSON.stringify(report));process.exitCode=report.failures?1:0;
  }finally{if(peer){try{await peer.send('Browser.close');}catch(_){}peer.ws.close();}if(child.exitCode===null)child.kill();fs.closeSync(log);}
})().catch(error=>{console.error(error);process.exitCode=1;});
