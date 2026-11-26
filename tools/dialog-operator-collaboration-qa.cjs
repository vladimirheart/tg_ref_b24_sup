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
 const reports=[];
 try{
  for(const engine of ['edge','chromium'])for(const [width,height] of [[1440,900],[1280,540],[390,640]]) {
   const browser=await chromium.launch({headless:true,...(engine==='edge'?{executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'}:{})});
   const context=await browser.newContext({viewport:{width,height}}),page=await context.newPage();
   const owner={userId:'1234',ticketId:raw,ticketPublicId:pub,businessId:'11',scopeKind:'BUSINESS'};
   const item={ticketId:raw,ticketPublicId:pub,businessId:11,scopeKind:'BUSINESS',requestNumber:'20261126-001',userId:1234,
    clientName:'Клиент',username:'client',channelId:3,channelName:'Telegram',city:'Москва',locationName:'Кафе',business:'Блинбери',
    status:'open',statusKey:'in_work',rawResponsible:'other',responsible:'Другой оператор',problem:'Тест',
    createdAt:'2026-11-26T08:00:00Z',createdDate:'2026-11-26',createdTime:'11:00'};
   const writes=[],errors=[],messages=[];let legacy=0,canWrite=true,conflict=false;
   page.on('pageerror',e=>errors.push(e.message));page.on('dialog',d=>d.accept());
   await context.route('**/*',async route=>{
    const req=route.request(),url=new URL(req.url());
    if(url.origin!==origin)return route.abort();
    if(!url.pathname.startsWith('/api/'))return route.continue();
    const suffix=url.pathname.split('/api/operator-dialogs/'+pub)[1];
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
   const internal=page.locator('#dialogInternalChat');await internal.locator('summary').click();
   await internal.locator('textarea:enabled').waitFor();
   await internal.locator('textarea').fill('<script>secret</script>\nВнутреннее сообщение');
   await internal.locator('[data-internal-send]').click();
   await internal.locator('.dialog-internal-message').waitFor();
   assert.equal(await internal.locator('.dialog-internal-message script').count(),0);
   assert.equal(writes.filter(w=>w.kind==='internal').length,1);
   assert.match(writes.find(w=>w.kind==='internal').body.requestId,/^[a-f0-9-]{36}$/);
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
   assert.deepEqual(errors,[]);reports.push({engine,width,metrics,writes,legacy,errors});await browser.close();
  }
  fs.writeFileSync(path.join(out,'report.json'),JSON.stringify(reports,null,2));console.log('GREEN: '+reports.length+' actual UI scenarios');
 }finally{await new Promise(resolve=>server.close(resolve));}
}
main().catch(e=>{console.error(e);process.exitCode=1;});

