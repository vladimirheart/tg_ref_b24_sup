/* Real account forms + auth-management runtime. Fixture API only; all browser network blocked. Node 22+. */
const fs=require('node:fs'),path=require('node:path'),cp=require('node:child_process'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..');const reportFile=path.resolve(process.argv.find(arg=>arg.startsWith('--report='))?.slice(9)||path.join(root,'spring-panel/target/auth-credential-input-r187/report.json'));
const inputs=['spring-panel/src/main/resources/static/js/auth-management.js','spring-panel/src/main/resources/templates/settings/fragments/users-access.html'];
const sources=inputs.map(file=>({file,sha256:crypto.createHash('sha256').update(fs.readFileSync(path.join(root,file))).digest('hex')}));
const payload={source:fs.readFileSync(path.join(root,inputs[0]),'utf8'),fragment:fs.readFileSync(path.join(root,inputs[1]),'utf8')};
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
  async function scenario(action, extra={}) {
    const frame=document.createElement('iframe'); document.body.append(frame);
    const name=action+' '+(extra.label || '');
    try {
      const win=frame.contentWindow; win.document.open(); win.document.write('<!doctype html><html><head><meta charset="UTF-8"></head><body>'+input.fragment+'</body></html>'); win.document.close();
      await win.eval('('+exercise.toString()+')('+JSON.stringify({...input,action,...extra})+')'); cases.push({name,ok:true});
    } catch(error) { cases.push({name,ok:false,kind:error.name==='AssertionError'?'failure':'error',message:error.message}); }
    finally { frame.remove(); }
    console.log('identity-case',JSON.stringify(cases.at(-1)));
  }
  for(const mode of ['create','edit']) {
    for(const login of [' a ',' A_Z-9.@ ','reader','  reader  ']) await scenario('login-valid',{mode,login,label:mode+' '+JSON.stringify(login)});
    for(const login of ['re ader','иван','a/b','a,b','a;b','a!','a🙂','a\u00a0b']) await scenario('login-invalid',{mode,login,label:mode+' '+JSON.stringify(login)});
    for(const [index,password] of [' secret ','p a s s','Привет','🔑 пароль','a'.repeat(72),'я'.repeat(36)].entries()) await scenario('password-valid',{mode,password,label:mode+' '+index});
    for(const [index,password] of ['a'.repeat(73),'я'.repeat(37),'   '].entries()) await scenario('password-invalid',{mode,password,label:mode+' '+index});
    for(const [password,confirmation] of [[' secret ','secret'],['secret',' secret '],['secret','other']]) await scenario('confirmation',{mode,password,confirmation,label:mode+' '+cases.length});
  }
  for(const legacy of [' legacy ','иван','\nreader\r','legacy,name']) await scenario('legacy-unchanged',{legacy,label:JSON.stringify(legacy)});
  for(const action of ['legacy-rename','readonly-legacy','hints','collision-retry','blur','paste-login','paste-create-password','paste-change-password','reset-errors']) await scenario(action);
  return cases;

  async function exercise(data) {
    const check=(condition,message)=>{if(!condition){const error=Error(message);error.name='AssertionError';throw error;}};
    const equal=(actual,expected,label)=>check(JSON.stringify(actual)===JSON.stringify(expected),label+': '+JSON.stringify({actual,expected}));
    const qa={writes:[],errors:[],denied:false}; window.__credentialQa=qa;
    window.addEventListener('error',event=>qa.errors.push(event.message));window.addEventListener('unhandledrejection',event=>qa.errors.push(String(event.reason)));
    const tick=()=>new Promise(resolve=>setTimeout(resolve,0));
    const wait=async test=>{for(let i=0;i<100;i++){if(test())return;await tick();}throw Error('Credential fixture did not settle');};
    const get=id=>document.getElementById(id), root=document.querySelector('[data-auth-management]');
    const user={id:7,username:data.legacy || 'existing',full_name:'Original',email:'',department:'',phones:[],photo:'',can_edit_username:data.action!=='readonly-legacy',can_edit_role:true,can_edit_password:true,can_view_password:false,can_block:false,is_self:true};
    window.fetch=async(url,options={})=>{
      if(url==='/api/auth/state')return {ok:true,json:async()=>({users:[user],roles:[],catalog:{pages:[],fields:{edit:[],view:[]}},capabilities:{fields:{edit:{'user.create':true,'user.role':true},view:{}}},org_structure:{nodes:[]},current_user_id:7})};
      check(['/api/users','/api/users/7'].includes(url)&&['POST','PUT'].includes(options.method),'Unexpected fixture request '+url);
      const body=JSON.parse(options.body);qa.writes.push({url,method:options.method,body});
      if(data.action==='collision-retry'&&!qa.denied){qa.denied=true;return {ok:true,json:async()=>({success:false,error:'Пользователь уже существует',field:'username'})};}
      Object.assign(user,body);return {ok:true,json:async()=>({success:true})};
    };
    window.bootstrap={Modal:class Modal{constructor(node){this.node=node;}static getOrCreateInstance(node){return new this(node);}show(){this.node.dispatchEvent(new Event('shown.bs.modal'));}hide(){this.node.dispatchEvent(new Event('hidden.bs.modal'));}}};
    (0,eval)(data.source);const manager=window.AuthManagement.mount(root);await wait(()=>manager.state.users.length===1&&!root.classList.contains('auth-management--loading'));
    const open=mode=>manager.showUserModal(mode,mode==='edit'?user:undefined);
    const type=(id,value)=>{get(id).value=value;get(id).dispatchEvent(new Event('input',{bubbles:true}));};
    const form=()=>document.querySelector('[data-auth-user-form]');
    const createFields=()=>{type('authUserModalLogin','reader');type('authUserModalPassword',data.password || 'secret');type('authUserModalPasswordConfirm',data.confirmation ?? data.password ?? 'secret');};
    const submit=async(which='create')=>{
      const target=which==='password'?document.querySelector('[data-auth-user-password-form]'):form();target.dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));
      const button=target.querySelector('button[type=submit]');await wait(()=>!button.disabled);await tick();
    };
    const changePassword=()=>{manager.openUserModal(7);manager.handlePasswordChangeTrigger({preventDefault(){}});type('authUserPasswordNew',data.password || 'secret');type('authUserPasswordConfirmNew',data.confirmation ?? data.password ?? 'secret');};
    const editLogin=login=>{open('edit');type('authUserModalLogin',login);};
    const fieldError=id=>get(id).getAttribute('aria-invalid')==='true'&&!get(id).validity.valid;
    if(data.action.startsWith('login-')){
      if(data.mode==='create'){open('create');createFields();type('authUserModalLogin',data.login);}else editLogin(data.login);
      await submit();if(data.action==='login-valid'){equal(qa.writes.length,1,'One credential write');equal(qa.writes[0].body.username,data.login.trim(),'Login normalization');}
      else{equal(qa.writes,[],'Invalid login blocks mutation');check(fieldError('authUserModalLogin'),'Invalid login has inline accessible error');}
    }else if(['password-valid','password-invalid','confirmation'].includes(data.action)){
      if(data.mode==='create'){open('create');createFields();}else changePassword();
      await submit(data.mode==='edit'?'password':'create');
      if(data.action==='password-valid'){equal(qa.writes.length,1,'One password write');equal(qa.writes[0].body.password,data.password,'Password text remains exact');}
      else{equal(qa.writes,[],'Invalid password/confirmation blocks mutation');check(fieldError(data.mode==='edit'?(data.action==='confirmation'?'authUserPasswordConfirmNew':'authUserPasswordNew'):(data.action==='confirmation'?'authUserModalPasswordConfirm':'authUserModalPassword')),'Password field error is visible');}
    }else if(['legacy-unchanged','readonly-legacy'].includes(data.action)){
      if(data.action==='readonly-legacy')user.username=' legacy ';open('edit');type('authUserModalFullName','Changed');await submit();
      equal(qa.writes.length,1,'Profile write succeeds for existing account');check(!Object.hasOwn(qa.writes[0].body,'username'),'Profile save never renames untouched legacy login');equal(qa.writes[0].body.full_name,'Changed','Profile field retained');
    }else if(data.action==='legacy-rename'){
      user.username=' legacy ';editLogin(' ascii ');await submit();equal(qa.writes[0].body.username,'ascii','Explicit legacy rename canonicalizes new login');
    }else if(data.action==='hints'){
      for(const id of ['authUserModalLogin','authUserModalPassword','authUserModalPasswordConfirm','authUserPasswordNew','authUserPasswordConfirmNew']){
        const control=get(id);check(control.title.length>15,'Native tooltip '+id);const refs=control.getAttribute('aria-describedby')?.split(/\s+/)||[];check(refs.some(ref=>get(ref)?.textContent.trim().length>15),'Visible accessible hint '+id);
      }
    }else if(data.action==='blur'){
      open('create');type('authUserModalLogin',' reader ');get('authUserModalLogin').dispatchEvent(new Event('blur'));equal(get('authUserModalLogin').value,'reader','Blur visibly removes edge spaces');
      user.username=' legacy ';open('edit');get('authUserModalLogin').dispatchEvent(new Event('blur'));equal(get('authUserModalLogin').value,' legacy ','Focus/blur does not rename legacy account');
    }else if(data.action==='collision-retry'){
      open('create');createFields();type('authUserModalLogin',' reader ');await submit();equal(qa.writes.length,1,'Collision reached backend');equal(qa.writes[0].body.username,'reader','Collision uses canonical key');
      check(document.querySelector('[data-auth-user-modal-error]').textContent.includes('существует'),'Collision feedback visible');equal(get('authUserModalPassword').value,'secret','Rejected save retains draft');type('authUserModalLogin','other');await submit();equal(qa.writes.length,2,'User chooses explicit retry');equal(qa.writes[1].body.username,'other','Retry uses new key');
    }else if(data.action.startsWith('paste-')){
      let id;if(data.action==='paste-change-password'){changePassword();id='authUserPasswordNew';}else{open('create');createFields();id=data.action==='paste-login'?'authUserModalLogin':'authUserModalPassword';}
      const before=get(id).value,event=new Event('paste',{bubbles:true,cancelable:true});Object.defineProperty(event,'clipboardData',{value:{getData:()=> 'ab\ncd'}});
      check(!get(id).dispatchEvent(event),'Control-character paste is rejected before native sanitization');equal(get(id).value,before,'Rejected paste keeps field value');check(fieldError(id),'Rejected paste has inline error');
      await submit(data.action==='paste-change-password'?'password':'create');equal(qa.writes,[],'Rejected paste cannot silently save old text');
    }else if(data.action==='reset-errors'){
      open('create');createFields();type('authUserModalLogin','неверно');await submit();check(fieldError('authUserModalLogin'),'Initial field error');manager.handleUserModalHidden();open('create');createFields();await submit();equal(qa.writes.length,1,'Reopened form clears stale validation');
    }else check(false,'Unknown fixture action');
    equal(qa.errors,[],'No browser runtime errors');
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
