/* Real sidebar password form/runtime; fixture API only, all network blocked. Node 22+. */
const fs=require('node:fs'),path=require('node:path'),cp=require('node:child_process'),crypto=require('node:crypto'),assert=require('node:assert/strict');const root=path.resolve(__dirname,'..');
const reportFile=path.resolve(process.argv.find(arg=>arg.startsWith('--report='))?.slice(9)||path.join(root,'spring-panel/target/profile-password-input-r187/report.json'));
const inputs=['spring-panel/src/main/resources/static/js/sidebar.js','spring-panel/src/main/resources/templates/fragments/navbar.html'];
const sources=inputs.map(file=>({file,sha256:crypto.createHash('sha256').update(fs.readFileSync(path.join(root,file))).digest('hex')}));
const payload={source:fs.readFileSync(path.join(root,inputs[0]),'utf8'),fragment:fs.readFileSync(path.join(root,inputs[1]),'utf8').replace(/<script\b[\s\S]*?<\/script>/g,'')};
class Cdp {
  constructor(ws) {
    this.ws = ws; this.next = 0; this.pending = new Map();
    ws.addEventListener('message', event => {
      const message = JSON.parse(String(event.data));
      if (message.id) {
        const pending = this.pending.get(message.id); if (!pending) return;
        clearTimeout(pending.timer); this.pending.delete(message.id);
        if (message.error) pending.reject(Error(JSON.stringify(message.error))); else pending.resolve(message.result);
      } else if (message.method === 'Runtime.consoleAPICalled' && message.params.args[0]?.value === 'identity-case') {
        console.log(message.params.args[1]?.value);
      }
    });
    ws.addEventListener('close', () => { for (const pending of this.pending.values()) { clearTimeout(pending.timer); pending.reject(Error('Browser connection closed')); } this.pending.clear(); });
  }
  send(method, params = {}, sessionId) {
    return new Promise((resolve, reject) => {
      const id = ++this.next, timer = setTimeout(() => { this.pending.delete(id); reject(Error('CDP timeout: ' + method)); }, 180000);
      this.pending.set(id, { resolve, reject, timer }); this.ws.send(JSON.stringify({ id, method, params, ...(sessionId ? { sessionId } : {}) }));
    });
  }
}

async function browserSuite(input) {
  const cases=[];
  async function scenario(action,extra={}) {
    const frame=document.createElement('iframe');document.body.append(frame);const name=action+' '+(extra.label||'');
    try{const win=frame.contentWindow;win.document.open();win.document.write('<!doctype html><html><body>'+input.fragment+'</body></html>');win.document.close();await win.eval('('+exercise.toString()+')('+JSON.stringify({...input,action,...extra})+')');cases.push({name,ok:true});}
    catch(error){cases.push({name,ok:false,kind:error.name==='AssertionError'?'failure':'error',message:error.message});}finally{frame.remove();}console.log('identity-case',JSON.stringify(cases.at(-1)));
  }
  for(const [i,password]of [' next password ','abcd efgh','Привет123','🔑 пароль','a'.repeat(72),'я'.repeat(36)].entries())await scenario('valid',{password,label:String(i)});
  for(const [i,password]of ['a'.repeat(73),'я'.repeat(37),'   '].entries())await scenario('invalid',{password,label:String(i)});
  for(const action of ['confirmation','paste-current','paste-new','paste-confirm','hints','reset'])await scenario(action);
  return cases;
  async function exercise(data){
    const check=(condition,message)=>{if(!condition){const error=Error(message);error.name='AssertionError';throw error;}},equal=(actual,expected,label)=>check(JSON.stringify(actual)===JSON.stringify(expected),label+': '+JSON.stringify({actual,expected}));
    const qa={writes:[],errors:[]};window.addEventListener('error',event=>qa.errors.push(event.message));window.addEventListener('unhandledrejection',event=>qa.errors.push(String(event.reason)));
    for(const key of ['localStorage','sessionStorage']) { const memory=new Map();Object.defineProperty(window,key,{value:{getItem:name=>memory.get(name)??null,setItem:(name,value)=>memory.set(name,String(value)),removeItem:name=>memory.delete(name)}}); }
    window.EventSource=undefined;window.fetch=async(url,options={})=>{if(!options.method||options.method==='GET')return {ok:true,json:async()=>url==='/api/channels'?[]:{count:0,items:[]}};check(url==='/profile/password'&&options.method==='POST','Only profile fixture write');qa.writes.push(JSON.parse(options.body));return {ok:true,json:async()=>({success:true})};};
    window.bootstrap={Modal:{getOrCreateInstance(node){return {show(){node.dispatchEvent(new Event('shown.bs.modal'));},hide(){node.dispatchEvent(new Event('hidden.bs.modal'));}};}}};
    (0,eval)(data.source);const get=id=>document.getElementById(id),form=document.querySelector('[data-change-password-form]'),tick=()=>new Promise(resolve=>setTimeout(resolve,0));
    const type=(id,value)=>{get(id).value=value;get(id).dispatchEvent(new Event('input',{bubbles:true}));};
    const fill=()=>{type('changePasswordCurrent',' current ');type('changePasswordNew',data.password||'abcdefgh');type('changePasswordConfirm',data.action==='confirmation'?'abcdefgh':data.password||'abcdefgh');};
    const submit=async()=>{form.dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));await tick();await tick();};
    const invalid=id=>get(id).getAttribute('aria-invalid')==='true'&&!get(id).validity.valid;
    fill();
    if(data.action==='valid'){await submit();equal(qa.writes,[{current_password:' current ',new_password:data.password,confirm_password:data.password}],'Sidebar preserves exact current/new/confirmation');}
    else if(data.action==='invalid'){await submit();equal(qa.writes,[],'Invalid new password blocks profile POST');check(invalid('changePasswordNew'),'New password has inline error');}
    else if(data.action==='confirmation'){type('changePasswordNew',' abcdefgh ');await submit();equal(qa.writes,[],'Confirmation compares exact spaces');check(invalid('changePasswordConfirm'),'Confirmation error visible');}
    else if(data.action.startsWith('paste-')){const id=data.action==='paste-current'?'changePasswordCurrent':data.action==='paste-new'?'changePasswordNew':'changePasswordConfirm',event=new Event('paste',{bubbles:true,cancelable:true});Object.defineProperty(event,'clipboardData',{value:{getData:()=> 'ab\ncd'}});check(!get(id).dispatchEvent(event),'Sidebar rejects control paste before value sanitization');await submit();equal(qa.writes,[],'Invalid paste cannot silently submit prior password');check(invalid(id),'Paste error visible');}
    else if(data.action==='hints'){for(const id of ['changePasswordCurrent','changePasswordNew','changePasswordConfirm']){check(get(id).title.length>15,'Sidebar native tooltip');check((get(id).getAttribute('aria-describedby')||'').split(/\s+/).some(ref=>get(ref)?.textContent.trim().length>15),'Sidebar visible accessible hint');}}
    else if(data.action==='reset'){type('changePasswordNew','a'.repeat(73));await submit();check(invalid('changePasswordNew'),'Initial error visible');document.querySelector('[data-sidebar-password-modal]').dispatchEvent(new Event('hidden.bs.modal'));fill();await submit();equal(qa.writes.length,1,'Reset clears stale validation');}
    equal(qa.errors,[],'No sidebar browser runtime errors');
  }
}

async function main() {
  const executable = process.env.BROWSER_BINARY || ['C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
    'C:/Program Files/Google/Chrome/Application/chrome.exe', '/usr/bin/chromium', '/usr/bin/google-chrome'].find(file => fs.existsSync(file));
  assert(executable, 'Set BROWSER_BINARY to a Chromium/Edge executable');
  fs.mkdirSync(path.dirname(reportFile), { recursive: true });
  const profile = path.join(path.dirname(reportFile), 'identity-browser-' + crypto.randomUUID()); fs.mkdirSync(profile);
  const log = fs.openSync(profile + '.log', 'wx');
  const child = cp.spawn(executable, ['--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
    '--disable-extensions', '--disable-background-networking', '--remote-debugging-port=0', '--remote-debugging-address=127.0.0.1', '--user-data-dir=' + profile, 'about:blank'],
  { windowsHide: true, stdio: ['ignore', log, log] });
  let peer;
  try {
    const activePort = path.join(profile, 'DevToolsActivePort'), deadline = Date.now() + 20000;
    while (!fs.existsSync(activePort)) { assert(child.exitCode === null, 'Browser exited during startup'); assert(Date.now() < deadline, 'Browser startup timeout'); await new Promise(resolve => setTimeout(resolve, 100)); }
    const [port, endpoint] = fs.readFileSync(activePort, 'utf8').trim().split(/\r?\n/);
    const ws = new WebSocket('ws://127.0.0.1:' + port + endpoint); await new Promise((resolve, reject) => { ws.addEventListener('open', resolve, { once: true }); ws.addEventListener('error', reject, { once: true }); }); peer = new Cdp(ws);
    const browser = await peer.send('Browser.getVersion');
    const { targetId } = await peer.send('Target.createTarget', { url: 'about:blank' });
    const { sessionId } = await peer.send('Target.attachToTarget', { targetId, flatten: true });
    await peer.send('Runtime.enable', {}, sessionId); await peer.send('Network.enable', {}, sessionId);
    await peer.send('Network.setBlockedURLs', { urls: ['*'] }, sessionId);
    const result = await peer.send('Runtime.evaluate', { expression: '(' + browserSuite.toString() + ')(' + JSON.stringify(payload) + ')', awaitPromise: true, returnByValue: true }, sessionId);
    assert(!result.exceptionDetails, JSON.stringify(result.exceptionDetails)); const cases = result.result.value;
    const report = { browser: browser.product, tests: cases.length, failures: cases.filter(test => !test.ok && test.kind === 'failure').length,
      errors: cases.filter(test => !test.ok && test.kind === 'error').length, sources, cases, finished: new Date().toISOString() };
    fs.writeFileSync(reportFile, JSON.stringify(report, null, 2)); console.log(JSON.stringify({ tests: report.tests, failures: report.failures, errors: report.errors, report: reportFile }));
    process.exitCode = report.failures || report.errors ? 1 : 0;
  } finally {
    if (peer) { try { await peer.send('Browser.close'); } catch (_) {} peer.ws.close(); }
    if (child.exitCode === null) child.kill(); fs.closeSync(log);
  }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
