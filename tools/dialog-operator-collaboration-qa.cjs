/* Actual MVC HTML + production assets; API writes are loopback fixtures only. */
const fs=require('node:fs'),path=require('node:path'),http=require('node:http'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..'),assets=path.join(root,'spring-panel/src/main/resources/static');
const out=path.join(root,'spring-panel/target/operator-dialog-qa'),capture=path.join(root,'spring-panel/target/dialog-layout-qa/dialogs.html');
const raw='3350bdc8-8a87-3b53-8047-bdc3eb89954a',pub='a'.repeat(32);
async function main(){
 fs.mkdirSync(out,{recursive:true});assert(fs.existsSync(capture));
 const server=http.createServer((req,res)=>{
  const pathname=new URL(req.url,'http://localhost').pathname;
  const file=pathname==='/dialogs'?capture:path.resolve(assets,pathname.slice(1));
  if(file!==capture&&!file.startsWith(assets+path.sep)||!fs.existsSync(file)){res.writeHead(404);return res.end('{}');}
  res.setHeader('Content-Type',({'.html':'text/html; charset=utf-8','.js':'application/javascript','.css':'text/css'})[path.extname(file)]||'application/octet-stream');
  fs.createReadStream(file).pipe(res);
 });
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 const origin='http://127.0.0.1:'+server.address().port;
 const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
 const reports=[],browsers=[];
 try{
  for(const engine of ['edge','chromium'])for(const [width,height] of [[1440,900],[1280,540],[390,640]]) {
   const browser=await chromium.launch({headless:true,...(engine==='edge'?{executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'}:{})});
   browsers.push(browser);
   const context=await browser.newContext({viewport:{width,height}}),page=await context.newPage();
   const owner={userId:'1234',ticketId:raw,ticketPublicId:pub,businessId:'11',scopeKind:'BUSINESS'};
   const item={ticketId:raw,ticketPublicId:pub,businessId:11,scopeKind:'BUSINESS',requestNumber:'20261126-001',userId:1234,
    clientName:'Клиент',username:'client',channelId:3,channelName:'Telegram',city:'Москва',locationName:'Кафе',business:'Блинбери',
    status:'open',statusKey:'in_work',rawResponsible:'other',responsible:'Другой оператор',problem:'Тест',
    createdAt:'2026-11-26T08:00:00Z',createdDate:'2026-11-26',createdTime:'11:00'};
   const writes=[],errors=[],messages=Array.from({length:12},(_,n)=>({id:String(n+1),authorId:'2',author:'login',
    authorName:'Иван Иванов',body:'Внутреннее '+(n+1)+'\n'+'Строка\n'.repeat(3),createdAt:new Date().toISOString()}));
   let legacy=0,canWrite=true,conflict=false,lastRead=0;
   page.on('pageerror',e=>errors.push(e.message));page.on('dialog',d=>d.accept());
   await context.route('**/*',async route=>{
    const req=route.request(),url=new URL(req.url());
    if(url.origin!==origin)return route.abort();
    if(!url.pathname.startsWith('/api/'))return route.continue();
    const suffix=url.pathname.split('/api/operator-dialogs/'+pub)[1];
    if(url.pathname==='/api/operator-dialog-unread')return route.fulfill({json:{items:[{ticketPublicId:pub,
     unreadCount:String(messages.filter(m=>Number(m.id)>lastRead&&m.authorId!=='1').length),latestId:String(messages.length)}]}});
    if(suffix==='/internal-messages/read'){
     const body=req.postDataJSON();assert.equal(body.ticketId,raw);assert(Number(body.lastReadId)<=messages.length);
     lastRead=Math.max(lastRead,Number(body.lastReadId));return route.fulfill({json:{success:true}});
    }
    if(suffix==='/assignment')return route.fulfill({json:{owner,revision:'7',canManage:true,responsible:'other'}});
    if(suffix==='/take'){writes.push({kind:'take',body:req.postDataJSON()});return route.fulfill({json:{success:true,responsible:'operator'}});}
    if(suffix==='/business'){
     if(req.method()==='GET')return route.fulfill({json:{owner,revision:'7',canManage:true,businesses:[{id:'11',displayName:'Source'},{id:'22',displayName:'Target'}]}});
     writes.push({kind:'business',body:req.postDataJSON()});
     return route.fulfill({status:409,json:{}});
    }
    if(suffix==='/internal-messages'){
     if(req.method()==='GET')return route.fulfill({json:{owner,canWrite,messages:[...messages].reverse()}});
     const body=req.postDataJSON();writes.push({kind:'internal',body});
     if(conflict)return route.fulfill({status:409,json:{}});
     messages.push({id:String(messages.length+1),authorId:'1',author:'Оператор',body:body.body,createdAt:new Date().toISOString()});
     return route.fulfill({json:{success:true}});
    }
    if(url.pathname==='/api/dialog-ticket/take'){legacy++;throw new Error('BUSINESS called legacy take');}
    if(url.pathname==='/api/dialog-ticket')return route.fulfill({json:{success:true,summary:item,history:[
     {id:1,sender:'client',message:'hey',sentAt:'2026-11-26T08:00:00Z',telegramMessageId:101},
     {id:2,sender:'support',message:'aaaa',sentAt:'2026-11-26T08:00:00Z',telegramMessageId:102},
     {id:3,sender:'client',message:'A'.repeat(400),sentAt:'2026-11-26T08:00:00Z',telegramMessageId:103}],categories:[]}});
    if(url.pathname==='/api/dialogs/query')return route.fulfill({json:{success:true,dialogs:[item],pagination:{page:1,page_size:20,total_items:1,total_pages:1},
     summary:{totalTickets:1},my_dialogs:{new:[],unanswered:[],in_work:[],counts:{}},sla_orchestration:{tickets:{}}}});
    assert(req.method()==='GET'||['/api/dialogs/triage-preferences','/api/dialogs/workspace-telemetry'].includes(url.pathname),'Unexpected write: '+url.pathname);
    return route.fulfill({json:{success:true,items:[],participants:[],operators:[],preferences:{},available:false}});
   });
   await page.goto(origin+'/dialogs',{waitUntil:'networkidle'});
   await page.locator('#dialogsTable tbody tr[data-ticket-id="'+raw+'"] [data-column-key="client"]').dblclick();
   await page.locator('#dialogDetailsModal').waitFor({state:'visible'});
   await page.locator('#dialogDetailsHistory .chat-message-body').first().waitFor();
   const metrics=await page.locator('#dialogDetailsHistory .chat-message-body').evaluateAll(nodes=>nodes.map(el=>{
    const style=getComputedStyle(el),line=parseFloat(style.lineHeight),rect=el.getBoundingClientRect();
    return {text:el.textContent,height:rect.height,line,width:rect.width,overflow:el.scrollWidth>el.clientWidth+1};
   }));
   assert(metrics.filter(m=>['hey','aaaa'].includes(m.text)).every(m=>m.height<=m.line+1),'Short text wrapped: '+JSON.stringify(metrics));
   assert(metrics.every(m=>!m.overflow),'Long message overflows');
   await page.locator('#dialogDetailsTakeBtn').click();await page.waitForFunction(()=>document.documentElement.textContent.includes('Диалог назначен на вас'));
   assert.equal(writes[0].kind,'take');assert.deepEqual(writes[0].body,{...owner,revision:'7'});assert.equal(legacy,0);
   const internal=page.locator('#dialogInternalChat');
   await internal.locator('[data-internal-unread]:not([hidden])').waitFor();
   assert.equal(await internal.locator('[data-internal-unread]').innerText(),'12');
   await internal.locator('summary').click();
   await internal.locator('textarea:enabled').waitFor();
   assert(await internal.locator('[data-internal-status]').isHidden(),'No permanent explanatory row');
   assert((await internal.locator('.dialog-internal-message .small').first().innerText()).startsWith('Иван Иванов'));
   await internal.locator('[data-internal-thread]').scrollIntoViewIfNeeded();
   await page.waitForFunction(()=>document.querySelector('[data-internal-unread]').hidden);
   assert.equal(lastRead,12);
   assert(await internal.locator('[data-internal-thread]').evaluate(el=>el.scrollHeight-el.clientHeight-el.scrollTop<22));
   await page.locator('#dialogReplyText').fill('Черновик клиенту');
   await internal.locator('[data-internal-emoji-toggle]').click();
   await internal.locator('.dialog-internal-emoji').first().click();
   assert.equal(await internal.locator('textarea').inputValue(),'😀');
   await internal.locator('#dialogInternalSendModeControl [data-dialog-send-mode-toggle]').click();
   await internal.locator('#dialogInternalSendModeControl [data-dialog-send-mode-option="enter"]').click();
   await internal.locator('textarea').press('Enter');
   await page.waitForFunction(()=>document.querySelector('#dialogInternalText').value==='');
   assert.equal(writes.filter(w=>w.kind==='internal').at(-1).body.body,'😀');
   assert.equal(await page.locator('#dialogReplyText').inputValue(),'Черновик клиенту');
   await internal.locator('textarea').fill('Перенос');
   const beforeShift=writes.length;await internal.locator('textarea').press('Shift+Enter');
   assert.equal(writes.length,beforeShift);assert((await internal.locator('textarea').inputValue()).includes('\n'));
   await internal.locator('textarea').fill('<script>secret</script>\nВнутреннее сообщение');
   await internal.locator('[data-internal-send]').click();
   await internal.locator('.dialog-internal-message').last().waitFor();
   assert.equal(await internal.locator('.dialog-internal-message script').count(),0);
   assert.equal(writes.filter(w=>w.kind==='internal').length,2);
   assert.match(writes.find(w=>w.kind==='internal').body.requestId,/^[a-f0-9-]{36}$/);
   messages.push({id:String(messages.length+1),authorId:'2',author:'login',authorName:'Иван Иванов',
    body:'Новое входящее внутреннее',createdAt:new Date().toISOString()});
   await page.waitForFunction(()=>document.querySelector('[data-internal-thread]').textContent.includes('Новое входящее внутреннее'));
   assert(await internal.locator('[data-internal-thread]').evaluate(el=>el.scrollHeight-el.clientHeight-el.scrollTop<22));
   await internal.locator('[data-internal-thread]').evaluate(el=>{el.scrollTop=0;});
   messages.push({id:String(messages.length+1),authorId:'2',author:'login',authorName:'Иван Иванов',
    body:'Ещё одно входящее внутреннее',createdAt:new Date().toISOString()});
   await internal.locator('[data-internal-jump]:not([hidden])').waitFor();
   assert(await internal.locator('[data-internal-thread]').evaluate(el=>el.scrollTop<20),'Reading older messages keeps viewport');
   await internal.locator('[data-internal-jump]').click();
   await page.waitForFunction(()=>document.querySelector('[data-internal-thread]').scrollTop>0);
   conflict=true;await internal.locator('textarea').fill('Сохранить черновик');await internal.locator('[data-internal-send]').click();
   await page.waitForFunction(()=>document.querySelector('[data-internal-status]').textContent.includes('Текст сохранён'));
   assert.equal(await internal.locator('textarea').inputValue(),'Сохранить черновик');
   await page.locator('#dialogDetailsLocationBusinessBtn').click();
   await page.locator('#dialogLocationBusinessBusiness:enabled').waitFor();
   assert.equal(await page.locator('#dialogLocationBusinessBusiness option').count(),2);
   await page.selectOption('#dialogLocationBusinessBusiness','22');await page.locator('#dialogLocationBusinessSave').click();
   await page.waitForFunction(()=>document.querySelector('#dialogLocationBusinessError').textContent.includes('Обновите диалог'));
   assert.equal(writes.filter(w=>w.kind==='business').length,1,'No automatic retry after conflict');
   assert.equal(writes.find(w=>w.kind==='business').body.businessTargetId,'22');
   await page.screenshot({path:path.join(out,engine+'-'+width+'.png')});
   await page.locator('#dialogLocationBusinessModal [data-bs-dismiss="modal"]').first().click();
   // Simulate the body class being removed when a nested modal closes.
   await page.evaluate(()=>{document.body.classList.remove('modal-open');document.body.style.overflow='';});
   const originalY=await page.evaluate(()=>window.scrollY);
   const history=page.locator('#dialogDetailsHistory');
   await history.evaluate(el=>{el.scrollTop=el.scrollHeight;});
   await history.hover();await page.mouse.wheel(0,1500);await page.waitForTimeout(100);
   assert.equal(await page.evaluate(()=>window.scrollY),originalY);
   await history.evaluate(el=>{el.innerHTML='Короткая история';});
   await history.hover();await page.mouse.wheel(0,-1500);await page.waitForTimeout(100);
   assert.equal(await page.evaluate(()=>window.scrollY),originalY);
   assert(await page.evaluate(()=>{
    const target=document.querySelector('#dialogDetailsHistory');
    const modal=target.closest('.modal');
    for(let node=target;node&&modal.contains(node);node=node.parentElement){node.scrollTop=node.scrollHeight;if(node===modal)break;}
    const start=new Event('touchstart',{bubbles:true});Object.defineProperty(start,'touches',{value:[{clientY:300}]});target.dispatchEvent(start);
    const move=new Event('touchmove',{bubbles:true,cancelable:true});Object.defineProperty(move,'touches',{value:[{clientY:100}]});
    return !target.dispatchEvent(move);
   }),'Short-history touch cannot chain to the page');
   assert.deepEqual(errors,[]);reports.push({engine,width,metrics,writes,legacy,errors});await browser.close();
  }
  fs.writeFileSync(path.join(out,'report.json'),JSON.stringify(reports,null,2));console.log('GREEN: '+reports.length+' actual UI scenarios');
 }finally{await Promise.allSettled(browsers.map(browser=>browser.close()));await new Promise(resolve=>server.close(resolve));}
}
main().catch(e=>{console.error(e);process.exitCode=1;});

