'use strict';
// Real Edge DOM + actual Thymeleaf-rendered client profile and production JS.
// All API responses/mutations are loopback fixtures; no live panel/providers.
const fs=require('node:fs'),path=require('node:path'),http=require('node:http'),assert=require('node:assert/strict');
const root=process.env.AUDIT_ROOT||path.resolve(__dirname,'..'), out=path.join(root,'spring-panel/target/error-audit-qa'), resources=path.join(root,'spring-panel/src/main/resources');
fs.mkdirSync(out,{recursive:true});
const read=p=>fs.readFileSync(path.join(resources,p),'utf8'), strip=s=>s.replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi,'');
const exact=' T/100?&%#+ Ю ',publicId='a'.repeat(32),big='9007199254740993';
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const delay=ms=>new Promise(r=>setTimeout(r,ms));
async function main(){
 const server=http.createServer((req,res)=>res.writeHead(200,{'Content-Type':'text/html; charset=utf-8'}).end('<!doctype html><html><body></body></html>'));
 await new Promise(r=>server.listen(0,'127.0.0.1',r));const origin='http://127.0.0.1:'+server.address().port;
 const browser=await chromium.launch({headless:true,executablePath:process.env.CHROMIUM_PATH}),report=[],allErrors=[];
 async function boot(kind,html,scripts,api,query=''){
  const context=await browser.newContext({viewport:{width:1440,height:900}}),requests=[];
  await context.route('**/*',async route=>{
   const req=route.request(),u=new URL(req.url());if(u.origin!==origin)return route.abort();
   if(!u.pathname.startsWith('/api/'))return route.continue();
   requests.push({url:u,method:req.method(),body:req.postData(),headers:req.headers()});
   const result=await api(u,req);
   if(result?.abort)return route.abort('connectionfailed');
   return route.fulfill(result||{json:{success:true}});
  });
  const page=await context.newPage();page.on('pageerror',e=>allErrors.push(kind+': '+e.message));
  await page.goto(origin+'/qa/'+kind+query);
  await page.setContent(strip(html));await page.addStyleTag({content:'.d-none{display:none!important}'});
  await page.evaluate(()=>{document.querySelectorAll('meta[name="_csrf"],meta[name="_csrf_header"]').forEach(e=>e.remove());for(const[name,content]of [['_csrf','fixture-csrf'],['_csrf_header','X-CSRF-TOKEN']]){const m=document.createElement('meta');m.name=name;m.content=content;document.head.append(m);}window.confirm=()=>true;});
  for(const file of scripts)await page.addScriptTag({content:read('static/'+file)});
  return{context,page,requests};
 }
 try{
  const rendered=fs.readFileSync(path.join(root,'spring-panel/target/error-audit-qa/client-profile.html'),'utf8');
  let hold, release,profileMode='normal';
  const p=await boot('profile',rendered,['vendor/bootstrap/5.3.3/bootstrap.bundle.min.js','js/panel-errors.js','js/passport-dialog-preview-runtime.js'],async(u)=>{
   if(profileMode==='hold'){profileMode='normal';await new Promise(r=>{release=r;hold=true;});return{json:{summary:{},history:[{message:'OLD A',sender:'A'}]}};}
   if(profileMode==='error')return{status:500,json:{error:'<img src=x onerror="window.qaXss=true">'}};
   return{json:{summary:{},history:[{message:'LATEST B',sender:'B'}]}};
  });
  const inline=[...rendered.matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script>/gi)].find(m=>!m[1].includes('src=')&&m[2].includes('window.openClientHistory'));
  assert(inline);await p.page.addScriptTag({content:inline[2]});
  const links=await p.page.locator('[data-client-ticket-open]').evaluateAll(es=>es.map(e=>e.getAttribute('href')));
  assert(links.some(h=>h.startsWith('/general-dialog-work/')));
  const businessLink=links.find(h=>h.startsWith('/dialogs?'));assert.equal(new URL(businessLink,origin).searchParams.get('ticketId'),exact);
  for(const prefix of ['/dialogs?','/general-dialog-work/']){
   await p.page.locator('[data-client-ticket-open]').evaluateAll((es,prefix)=>es.find(e=>e.getAttribute('href').startsWith(prefix)).click(),prefix);
   await p.page.waitForFunction(()=>document.getElementById('passportDialogPreview').open&&document.querySelector('#passportDialogPreview iframe').getAttribute('src'));
   const target=new URL(await p.page.locator('#passportDialogPreview iframe').getAttribute('src'));assert.equal(target.origin,origin);
   if(prefix==='/dialogs?')assert.equal(target.searchParams.get('ticketId'),exact);
   else assert(target.pathname.startsWith(prefix));
   await p.page.locator('[data-passport-dialog-close]').evaluate(e=>e.click());
   await p.page.waitForFunction(()=>!document.getElementById('passportDialogPreview').open&&!document.querySelector('#passportDialogPreview iframe').hasAttribute('src'));
  }
  assert.equal(await p.page.locator('[data-dialog-url]').count(),1);
  await p.page.evaluate(()=>{window.qaButton=document.querySelector('[data-dialog-url]');});
  profileMode='hold';await p.page.evaluate(()=>{window.qaA=window.openClientHistory(window.qaButton);});
  while(!hold)await delay(10);
  await p.page.evaluate(()=>{const b=document.createElement('button');b.dataset.ticketId='B';b.dataset.dialogUrl='/dialogs?ticketId=B';window.qaB=window.openClientHistory(b);});
  await p.page.evaluate(()=>window.qaB);
  release();await p.page.evaluate(()=>window.qaA);
  assert.match(await p.page.locator('#clientHistoryMessages').innerText(),/LATEST B/);assert.doesNotMatch(await p.page.locator('#clientHistoryMessages').innerText(),/OLD A/);
  assert.equal(await p.page.locator('#clientHistoryOpenDialog').getAttribute('href'),'/dialogs?ticketId=B');
  await p.page.evaluate(()=>{const b=document.createElement('button');b.dataset.ticketId='C';return window.openClientHistory(b);});
  assert.equal(await p.page.locator('#clientHistoryOpenDialog').getAttribute('href'),null);
  assert.equal(await p.page.locator('#clientHistoryOpenDialog').evaluate(e=>e.hidden),true);
  profileMode='error';await p.page.evaluate(()=>window.openClientHistory(window.qaButton));
  assert.equal(await p.page.evaluate(()=>Boolean(window.qaXss)),false);assert.equal(await p.page.locator('#clientHistoryMessages img').count(),0);
  assert.match(await p.page.locator('#clientHistoryMessages').innerText(),/Ошибка|панел|сервис|img/i);
  await p.context.close();report.push('profile: actual Thymeleaf BUSINESS/GENERAL links, literal +, stale A/B, escaped error');

  const dialogs=read('templates/dialogs/index.html');
  let mode='empty',noteRelease,noteHeld=false;
  const n=await boot('notes',dialogs,['js/dialogs-details-runtime.js'],async(u)=>{
   if(u.pathname==='/api/dialog-ticket')return{json:{summary:{},history:[],categories:[]}};
   if(u.pathname==='/api/dialog-ticket/notes'){
    if(mode==='hold'){mode='empty';await new Promise(r=>{noteRelease=r;noteHeld=true;});return{json:{notes:[{id:1,title:'OLD A',body:'stale'}]}};}
    if(mode==='network')return{abort:true};
    if(['401','403','500'].includes(mode))return{status:Number(mode),json:{error:'fixture'}};
    return{json:{notes:mode==='filled'?[{id:7,title:'<img src=x onerror=window.qaXss=true>',body:'alpha'},{id:8,title:'Beta',body:'second'}]:[]}};
   }
   throw Error('Unexpected notes request '+u.pathname);
  });
  await n.page.evaluate(()=>{
   window.active={ticketId:'',channelId:null,context:{}};
   window.qaNotes=DialogsDetailsRuntime.createRuntime({
    elements:{detailsModalEl:document.getElementById('dialogDetailsModal')},
    getActiveDialogState:()=>window.active,
    setActiveDialogTicketId:id=>active.ticketId=id,setActiveDialogChannelId:id=>active.channelId=id,setActiveDialogContext:c=>active.context=c
   });
  });
  const open=async id=>{await n.page.evaluate(id=>window.qaNotes.openDialogDetails(id),id);};
  await open(exact);await n.page.waitForFunction(()=>document.getElementById('dialogDetailsNotesState').textContent==='Заметок нет.');
  const noteReq=n.requests.find(r=>r.url.pathname.endsWith('/notes'));assert.equal(noteReq.url.searchParams.get('ticketId'),exact);
  mode='filled';await open('filled');await n.page.waitForFunction(()=>document.querySelectorAll('#dialogDetailsNotesList article').length===2);
  assert.equal(await n.page.locator('#dialogDetailsNotesList img').count(),0);
  await n.page.locator('#dialogDetailsNotesSearch').evaluate(e=>{e.value='alpha';e.dispatchEvent(new Event('input'));});
  assert.equal(await n.page.locator('#dialogDetailsNotesList article').count(),1);
  for(mode of ['403','401','500','network']){
   await open(mode);await delay(50);
   assert.equal(await n.page.locator('#dialogDetailsNotesList article').count(),0);
   if(mode==='401'||mode==='403')assert(await n.page.locator('#dialogDetailsNotesSection').evaluate(e=>e.classList.contains('d-none')));
   else assert.match(await n.page.locator('#dialogDetailsNotesState').textContent(),/недоступны|Не удалось/);
  }
  mode='hold';await open('A');while(!noteHeld)await delay(10);
  await open('B');await delay(50);noteRelease();await delay(50);
  assert.doesNotMatch(await n.page.locator('#dialogDetailsNotesList').textContent(),/OLD A/);
  await n.page.addScriptTag({content:read('static/js/dialogs-auth-response-runtime.js')});mode='401';await open('expired-session');
  await n.page.waitForURL(origin+'/login');assert.equal(await n.page.locator('#dialogDetailsNotesList article').count(),0);
  await n.context.close();report.push('notes: native replaceChildren, empty/populated/search/HTML, 401/403/500/network, stale A/B');

  let bmode='business',canManage=true,revision='revision-fixture',waitGet,releaseGet;
  const owner={ticketId:exact,ticketPublicId:publicId,scopeKind:'BUSINESS',businessId:'7'};
  const b=await boot('business',dialogs,['vendor/bootstrap/5.3.3/bootstrap.bundle.min.js','js/panel-errors.js','js/dialogs-operator-collaboration.js','js/dialogs-business-selection-runtime.js'],async(u,req)=>{
   if(req.method()==='POST'){await delay(80);return{status:409,json:{error:'Данные или права изменились.'}};}
   if(waitGet){await new Promise(r=>releaseGet=r);waitGet=false;}
   if(u.pathname==='/api/unresolved-scope/businesses')return{json:[{id:big,displayName:'Big business'},{id:8,displayName:'Business eight'}]};
   return{json:{owner,revision,canManage,businesses:[{id:big,displayName:'<img src=x onerror=window.qaXss=true>'},{id:8,displayName:'Business eight'}]}};
  });
  await b.page.evaluate(owner=>{
   document.querySelectorAll('tr.dialog-row-active').forEach(e=>e.classList.remove('dialog-row-active'));
   const table=document.createElement('table'),tr=document.createElement('tr');tr.className='dialog-row-active';Object.assign(tr.dataset,owner);table.append(tr);document.body.append(table);
  },owner);
  const click=()=>b.page.locator('#dialogDetailsLocationBusinessBtn').evaluate(e=>e.click());
  const choose=()=>b.page.locator('#dialogLocationBusinessBusiness').evaluate((e,big)=>e.value=big,big);
  const submit=()=>b.page.locator('#dialogLocationBusinessForm').evaluate(f=>{f.dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));f.dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));});
  await click();await b.page.waitForFunction(()=>!document.getElementById('dialogLocationBusinessSave').disabled);
  await choose();await submit();await b.page.waitForFunction(()=>document.getElementById('dialogLocationBusinessError').textContent.includes('Данные или права'));
  let posts=b.requests.filter(r=>r.method==='POST');assert.equal(posts.length,1);let payload=JSON.parse(posts[0].body);
  assert.equal(payload.businessTargetId,big);assert.equal(payload.revision,revision);assert.equal(payload.ticketId,exact);assert.equal(posts[0].headers['x-csrf-token'],'fixture-csrf');
  assert.match(await b.page.locator('#dialogLocationBusinessError').textContent(),/Данные или права/);
  assert.equal(await b.page.locator('#dialogLocationBusinessBusiness img').count(),0);
  canManage=false;await click();await b.page.waitForFunction(()=>document.getElementById('dialogLocationBusinessError').textContent.includes('право'));assert(await b.page.locator('#dialogLocationBusinessSave').isDisabled());
  canManage=true;waitGet=true;await click();while(!releaseGet)await delay(10);
  await b.page.locator('#dialogLocationBusinessModal').evaluate(e=>e.dispatchEvent(new Event('hidden.bs.modal')));releaseGet();await delay(80);
  assert(await b.page.locator('#dialogLocationBusinessSave').isDisabled());
  await b.page.evaluate(()=>{const r=document.querySelector('tr.dialog-row-active');r.dataset.scopeKind='UNRESOLVED';r.dataset.businessId='';});
  await click();await b.page.waitForFunction(()=>!document.getElementById('dialogLocationBusinessSave').disabled);await choose();await submit();await delay(50);
  assert.equal(b.requests.filter(r=>r.method==='POST').length,1);assert.match(await b.page.locator('#dialogLocationBusinessError').textContent(),/Идентификатор/);
  await b.page.locator('#dialogLocationBusinessBusiness').evaluate(e=>e.value='8');await submit();await delay(160);
  posts=b.requests.filter(r=>r.method==='POST');assert.equal(posts.length,2);assert.deepEqual(JSON.parse(posts[1].body),{businessId:8});assert(posts[1].url.pathname.includes('/unresolved-scope/tickets/'+publicId+'/business'));
  await b.context.close();report.push('business: actual handlers, owner/revision, exact BIGINT, CSRF, conflict, denial, closed-modal stale response, double submit, unresolved guard');

  for(const query of ['?incidentId=7','?incidentId=invalid']){
   const i=await boot('incidents',read('templates/incidents/index.html'),['js/panel-errors.js','js/incident-workbench-ui.js','js/incident-descriptions.js','js/incidents-workbench.js'],async(u,req)=>{
    if(u.pathname==='/api/incidents')return{json:{items:[]}};
    if(u.pathname==='/api/incidents/7')return{json:{incident:{id:'7',incident_key:'INC_7',title:'Fixture incident',status:'open',severity:'warning'},events:[]}};
    if(u.pathname.includes('/tickets/'))return{json:{success:true,ticket_id:exact,inbound_events:[],outbound_events:[],related_incidents:[],recent_operations:[]}};
    return{json:{success:true,items:[]}};
   },query);
   await delay(120);
   assert.equal(i.requests.filter(r=>r.url.pathname==='/api/incidents/7').length,query.includes('=7')?1:0);
   await i.page.locator('#transportWorkbenchTicketId').evaluate((e,id)=>e.value=id,exact);
   for(const [mode,suffix]of [['Inspect','debug'],['Replay','replay-inbound'],['Requeue','requeue-outbound']]){
    const before=i.requests.length;await i.page.locator('#transportWorkbench'+mode+'Ticket').evaluate(e=>e.click());await delay(80);
    const rs=i.requests.slice(before).filter(r=>r.url.pathname.includes('/tickets/'));assert.equal(rs.length,1);
    assert.equal(decodeURIComponent(rs[0].url.pathname.split('/')[5]),exact);assert(rs[0].url.pathname.endsWith('/'+suffix));
    assert.equal(rs[0].method,mode==='Inspect'?'GET':'POST');assert.equal(rs[0].headers['x-csrf-token'],'fixture-csrf');
   }
   await i.context.close();
  }
  report.push('incidents: native window.location valid/invalid deep link and isolated exact-ID inspect/replay/requeue');
  assert.deepEqual(allErrors,[]);
  fs.writeFileSync(path.join(out,'browser-report.json'),JSON.stringify({engine:'Edge Chromium',report,uncaughtErrors:allErrors},null,2));
  console.log('BROWSER_R331=GREEN;GROUPS='+report.length+';UNCAUGHT_ERRORS=0;REAL_PROVIDER_WRITES=0');
 }finally{await browser.close();await new Promise(r=>server.close(r));}
}
main().catch(e=>{console.error(e);process.exitCode=1;});
