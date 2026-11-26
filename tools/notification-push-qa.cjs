/* Actual MVC HTML + production JS; every API response is a loopback fixture. */
const fs=require('node:fs'),path=require('node:path'),http=require('node:http'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..'),assets=path.join(root,'spring-panel/src/main/resources/static');
const capture=path.join(root,'spring-panel/target/dialog-layout-qa/dialogs.html'),out=path.join(root,'spring-panel/target/notification-push-qa');
const raw='3350bdc8-8a87-3b53-8047-bdc3eb89954a',pub='a'.repeat(32);
const batch=(top)=>Array.from({length:10},(_,i)=>({id:top-i,text:'Notice '+(top-i),url:'/incidents',read:true,createdAt:'2026-11-26T10:00:00Z'}));
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
 const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');const browsers=[],reports=[];
 try{
  for(const engine of ['edge','chromium'])for(const width of [1440,1280,390]){
   console.log('Checking',engine,width);
   const browser=await chromium.launch({headless:true,...(engine==='edge'?{executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'}:{})});browsers.push(browser);
   const context=await browser.newContext({viewport:{width,height:900}}),page=await context.newPage();
   await context.addInitScript(()=>{
    const sources=[];
    window.EventSource=class{
     static CLOSED=2;
     constructor(url){this.url=url;this.readyState=1;this.handlers={};sources.push(this);}
     addEventListener(type,fn){(this.handlers[type]||=[]).push(fn);}
     close(){this.readyState=2;}
    };
    window.emitPanelEvent=(type,data)=>{for(const s of sources.filter(s=>s.url==='/api/events/stream'&&s.readyState!==2))
     for(const fn of s.handlers[type]||[])fn({data:JSON.stringify(data)});};
   });
   const errors=[],requests=[];let active=0,maxActive=0,listDelay=250,fail=false;
   const item={ticketId:raw,ticketPublicId:pub,businessId:11,scopeKind:'BUSINESS',requestNumber:'20261126-001',userId:1234,
    clientName:'Client',username:'client',channelId:3,channelName:'Telegram',business:'Business',status:'open',statusKey:'in_work',
    createdAt:'2026-11-26T08:00:00Z'};
   page.on('pageerror',e=>errors.push(e.message));
   await context.route('**/*',async route=>{
    const req=route.request(),url=new URL(req.url());if(url.origin!==origin)return route.abort();
    if(!url.pathname.startsWith('/api/'))return route.continue();
    requests.push({path:url.pathname,cursor:url.searchParams.get('cursor'),limit:url.searchParams.get('limit')});
    if(url.pathname==='/api/notifications'){
     active++;maxActive=Math.max(active,maxActive);
     try{
      assert.equal(url.searchParams.get('limit'),'10');
      await new Promise(resolve=>setTimeout(resolve,listDelay));
      return await route.fulfill({status:fail?503:200,json:{items:batch(url.searchParams.get('cursor')==='p2'?90:80),nextCursor:'p3',hasMore:true}});
     }finally{active--;}
    }
    if(url.pathname==='/api/notifications/unread_count')return route.fulfill({json:{unread:1}});
    if(url.pathname==='/api/dialogs/query')return route.fulfill({json:{success:true,dialogs:[item],
     pagination:{page:1,page_size:20,total_items:1,total_pages:1},summary:{totalTickets:1},my_dialogs:{counts:{}},sla_orchestration:{tickets:{}}}});
    return route.fulfill({json:{success:true,items:[],channels:[],participants:[],operators:[],preferences:{}}});
   });
   const emit=(type,data)=>page.evaluate(([type,data])=>window.emitPanelEvent(type,data),[type,data]);
   const snapshot=(sequence,unread,top=100,instance='one')=>({instance,sequence,unread,page:{items:batch(top),nextCursor:'p2',hasMore:true}});
   const count=path=>requests.filter(r=>r.path===path).length;
   await page.goto(origin+'/dialogs',{waitUntil:'networkidle'});
   await emit('connected',{connected:true});await emit('notifications_snapshot',snapshot(1,21));
   if(width<768)await page.locator('#sidebarMobileToggle').click();
   await page.locator('#bellBtn').click();
   await page.waitForFunction(()=>document.querySelectorAll('#notify-dropdown .notif-item[data-id]').length===10);
   assert.equal(count('/api/notifications'),0,'Opening fresh pushed snapshot must not fetch');
   const counterCalls=count('/api/notifications/unread_count');
   await page.locator('#notify-dropdown').evaluate(el=>{el.scrollTop=el.scrollHeight;for(let i=0;i<20;i++)el.dispatchEvent(new Event('scroll'));});
   await page.waitForFunction(()=>document.querySelectorAll('#notify-dropdown .notif-item[data-id]').length===20);
   assert.equal(count('/api/notifications'),1);assert.equal(maxActive,1);
   await emit('notifications_snapshot',snapshot(2,22,101));
   await page.waitForFunction(()=>document.querySelector('#notify-count').textContent==='22');
   await emit('notifications_snapshot',snapshot(1,999,999));
   assert.equal(await page.locator('#notify-count').textContent(),'22');
   for(let i=0;i<10;i++)await emit('notifications_changed',{snapshotExpected:true});
   assert.equal(count('/api/notifications/unread_count'),counterCalls,'Push must not trigger counter GET');
   await page.locator('#bellBtn').click();
   listDelay=700;await emit('notifications_refresh_required',{});
   await page.locator('#bellBtn').click();
   await page.waitForFunction(()=>document.querySelector('#notify-dropdown')?.textContent.includes('Загрузка'));
   for(let i=0;i<4;i++)await page.locator('#bellBtn').click();
   await emit('notifications_snapshot',snapshot(3,23,102));
   await page.waitForTimeout(900);
   assert.equal(await page.locator('#notify-count').textContent(),'23');
   assert.equal(await page.locator('#notify-dropdown .notif-item[data-id="102"]').count(),1);
   assert.equal(maxActive,1,'Close/reopen and events must not overlap list requests');
   await page.locator('#bellBtn').click();await emit('connected',{});await emit('notifications_snapshot',snapshot(1,5,103,'two'));
   await page.waitForFunction(()=>document.querySelector('#notify-count').textContent==='5');
   await page.waitForTimeout(350);const beforeRating=count('/api/dialogs/query');
   await emit('dialogs_changed',{reason:'dialog_feedback_created',ticketId:raw,ticketPublicId:pub,businessId:'11',rating:5,feedbackVersion:'99'});
   await page.waitForFunction(([raw])=>document.querySelector('tr[data-ticket-id="'+raw+'"]')?.dataset.rating==='5',[raw]);
   await page.waitForTimeout(300);assert.equal(count('/api/dialogs/query'),beforeRating,'Rating patch must not reload the list');
   assert.equal(await page.locator('#notify-count').textContent(),'5','Rating must not create a bell notification');
   fail=true;listDelay=50;
   await emit('notifications_refresh_required',{});
   await page.locator('#bellBtn').click();
   await page.waitForFunction(()=>document.querySelector('#notify-dropdown')?.textContent.includes('Не удалось'));
   const failedRequests=count('/api/notifications');
   for(let i=0;i<10;i++)await emit('notifications_refresh_required',{});
   await page.waitForTimeout(500);
   assert.equal(count('/api/notifications'),failedRequests,'Failure must back off even when refresh events repeat');
   fail=false;await emit('notifications_snapshot',snapshot(2,5,104,'two'));
   await page.waitForFunction(()=>document.querySelector('#notify-count').textContent==='5');
   await page.screenshot({path:path.join(out,engine+'-'+width+'.png')});
   assert.deepEqual(errors,[]);reports.push({engine,width,maxActive,listRequests:count('/api/notifications'),errors});
   await context.close();await browser.close();
  }
  fs.writeFileSync(path.join(out,'report.json'),JSON.stringify(reports,null,2));console.log('GREEN: '+reports.length+' notification push/paging/race/rating UI scenarios');
 }finally{for(const b of browsers)await b.close().catch(()=>{});await new Promise(resolve=>server.close(resolve));}
}
main().catch(e=>{console.error(e);process.exitCode=1;});
