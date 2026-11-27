'use strict';
// Actual rendered projects template and production assets. Writes are loopback fixtures only.
const fs=require('node:fs'),path=require('node:path'),http=require('node:http'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..'),resources=path.join(root,'spring-panel/src/main/resources'),out=path.join(root,'spring-panel/target/project-settings-qa');
const big='9007199254740993';
async function main(){
 fs.mkdirSync(out,{recursive:true});
 const server=http.createServer((req,res)=>{
  const url=new URL(req.url,'http://localhost'),file=url.pathname==='/projects'?path.join(out,'projects.html'):path.resolve(resources,'static','.'+url.pathname);
  if(url.pathname!=='/projects'&&!file.startsWith(path.join(resources,'static')+path.sep)){res.writeHead(403).end();return;}
  if(!fs.existsSync(file)||!fs.statSync(file).isFile()){res.writeHead(404).end();return;}
  const ext=path.extname(file);res.setHeader('Content-Type',({'.html':'text/html; charset=utf-8','.js':'application/javascript','.css':'text/css','.woff2':'font/woff2'})[ext]||'application/octet-stream');
  if(['.html','.js','.css'].includes(ext))res.end(fs.readFileSync(file,'utf8').replaceAll('\r\n','\n'));else fs.createReadStream(file).pipe(res);
 });
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));const origin='http://127.0.0.1:'+server.address().port;
 const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
 const browser=await chromium.launch({headless:true,...(process.env.CHROMIUM_PATH?{executablePath:process.env.CHROMIUM_PATH}:{})}),report=[];
 try{
  for(const theme of ['light','dark'])for(const [width,height] of [[1440,900],[1280,360],[768,480],[390,640]]){
   console.log(`Checking ${theme} ${width}x${height}`);
   const context=await browser.newContext({viewport:{width,height},colorScheme:theme,reducedMotion:'reduce'});
   await context.addInitScript(theme=>{localStorage.setItem('iguana:theme',theme);localStorage.setItem('iguana:theme-palette','north');},theme);
   let canManage=true,canWrite=true,failNext=false,revoked=false,failListOnce=false;
   const items=[{id:77,project_key:'PRJ_77',name:'Обновление рабочих процессов поддержки',description:'Проверяем участников проекта и настройки.',lead_identity:'anna',status:'active',task_count:3}];
   const roster=new Map([['77',Array.from({length:20},(_,n)=>({userId:String(n+1),username:n===0?'anna':'employee'+(n+1),role:n===0?'OWNER':'VIEWER'}))]]);
   const writes=[],errors=[],missing=[];
   await context.route('**/*',async route=>{
    const req=route.request(),url=new URL(req.url());if(url.origin!==origin)return route.abort();if(!url.pathname.startsWith('/api/'))return route.continue();
    const method=req.method(),p=url.pathname;
    if(method==='GET'){
     if(p==='/api/projects'){
      if(failListOnce){failListOnce=false;return route.fulfill({status:500,json:{message:'Fixture list read failure'}});}
      return route.fulfill({json:{items:revoked?[]:items,total:revoked?0:items.length}});
     }
     if(p==='/api/shared-work/context')return route.fulfill({json:{userId:'1',systemAdmin:true,canCreateTask:true,canCreateProject:true}});
     if(p==='/api/panel-users/profile'){
      const username=url.searchParams.get('username')||'anna',userId=url.searchParams.get('userId')||(username==='andrey'?big:username==='anna'?'1':username.replace('employee',''));
      return route.fulfill({json:{userId,username,fullName:username==='andrey'?'Андрей Участник':username==='anna'?'Анна Смирнова':'Сотрудник '+userId,department:'Поддержка',email:'employee@example.test',phones:[],blocked:userId==='2',enabled:true,serverTime:new Date().toISOString()}});
     }
     const match=p.match(/^\/api\/shared-work\/PROJECT\/(\d+)\/(access|people|history)$/);
     if(match){
      if(revoked)return route.fulfill({status:404,json:{}});
      const id=match[1];
      if(match[2]==='access')return route.fulfill({json:{kind:'PROJECT',id,canManage,canWrite,members:roster.get(id)||[]}});
      if(match[2]==='people')return route.fulfill({json:[{userId:big,username:'andrey',fullName:'Андрей Участник'}]});
      return route.fulfill({json:[]});
     }
     return route.fulfill({json:{success:true,items:[],notifications:[],settings:{},preferences:{}}});
    }
    assert(req.headers()['x-xsrf-token'],'Missing CSRF on '+p);
    const body=req.postData()?req.postDataJSON():null;writes.push({method,path:p,body});
    await new Promise(resolve=>setTimeout(resolve,90));
    if(failNext){failNext=false;return route.fulfill({status:500,json:{}});}
    if(p==='/api/projects'&&method==='POST'){
     const project={...body,id:88,project_key:'PRJ_88',task_count:0};items.push(project);roster.set('88',[{userId:'1',username:'anna',role:'OWNER'}]);failListOnce=true;
     return route.fulfill({json:{ok:true,project}});
    }
    if(p==='/api/projects/77'&&method==='PATCH'){Object.assign(items[0],body);return route.fulfill({json:{ok:true,project:items[0]}});}
    const match=p.match(/^\/api\/shared-work\/PROJECT\/(\d+)\/(members|owner)\/(\d+)$/);assert(match,'Unrelated write: '+p);
    const [,id,action,target]=match;assert.equal(target,big);assert.equal(typeof target,'string');
    if(action==='owner'){
     for(const member of roster.get(id))member.role=member.userId===target?'OWNER':member.role==='OWNER'?'EDITOR':member.role;
     canManage=false;
    }else if(method==='PUT')roster.set(id,[...roster.get(id).filter(member=>member.userId!==target),{userId:target,username:'andrey',role:body.role}]);
    else{assert.equal(method,'DELETE');roster.set(id,roster.get(id).filter(member=>member.userId!==target));}
    return route.fulfill({status:204});
   });
   const page=await context.newPage();page.setDefaultTimeout(12000);
   page.on('pageerror',error=>errors.push(error.message));page.on('response',response=>{if(response.status()===404&&/\.(js|css)(\?|$)/.test(response.url()))missing.push(response.url());});
   const modal=page.locator('#projectModal'),members=modal.locator('[data-work-members]');
   const open=async id=>{await page.locator(`[data-project-members="${id}"]`).click();await modal.waitFor({state:'visible'});await modal.locator('[data-work-add]').waitFor({state:'attached'});};
   const close=async()=>{
    await modal.evaluate(el=>{window.qaModalHidden=false;el.addEventListener('hidden.bs.modal',()=>{window.qaModalHidden=true;},{once:true});});
    await modal.locator('.modal-header .btn-close').click();await page.waitForFunction(()=>window.qaModalHidden);
   };
   const waitRows=async count=>page.waitForFunction(count=>document.querySelectorAll('#projectModal [data-work-members]>li').length===count,count);
   const add=async()=>{
    await modal.locator('.work-access-add > summary').click();await modal.locator('[data-work-search]').fill('andrey');await modal.locator('[data-work-find]').click();
    await page.waitForFunction(big=>document.querySelector('#projectModal [data-work-person]').value===big,big);
    assert.equal(await modal.locator('[data-work-person] option:checked').textContent(),'Андрей Участник');
    await modal.locator('[data-work-role]').selectOption('EDITOR');await modal.locator('[data-work-add]').dblclick();
   };
   await page.goto(origin+'/projects',{waitUntil:'networkidle'});await page.locator('[data-project-members="77"]').waitFor();
   await open(77);await waitRows(20);
   assert.equal(await modal.locator('#project-members-tab').getAttribute('aria-selected'),'true');
   assert.equal(await modal.locator('#saveProjectBtn').isVisible(),false);
   assert.equal(await modal.locator('details[open]').count(),0);
   assert(await modal.locator('.work-access-add > summary').evaluate(el=>{const r=el.getBoundingClientRect(),b=el.closest('.modal-body').getBoundingClientRect();return r.top>=b.top&&r.bottom<=b.bottom&&el.contains(document.elementFromPoint(r.x+r.width/2,r.y+r.height/2));}),'Add participant action is hidden below the roster');
   const bounds=await modal.evaluate(el=>{const d=el.querySelector('.modal-dialog').getBoundingClientRect(),b=el.querySelector('.modal-body'),f=el.querySelector('.modal-footer').getBoundingClientRect();return{left:d.left,right:d.right,top:d.top,bottom:d.bottom,footer:f.bottom,body:b.clientHeight,scroll:b.scrollHeight,overflow:document.documentElement.scrollWidth>innerWidth+1};});
   assert(bounds.top>=0&&bounds.left>=0&&bounds.right<=width+1&&bounds.bottom<=height+1&&bounds.footer<=height+1,JSON.stringify(bounds));
   assert(bounds.body>0&&!bounds.overflow);assert(bounds.scroll>bounds.body,'Roster must scroll inside modal');
   await page.screenshot({path:path.join(out,`members-${theme}-${width}x${height}.png`)});
   await modal.locator('#project-info-tab').click();await modal.locator('#projectName').fill('Несохранённое название');
   assert.equal(await modal.locator('.project-settings-advanced').getAttribute('open'),null);
   await page.screenshot({path:path.join(out,`info-${theme}-${width}x${height}.png`)});
   await modal.locator('#project-members-tab').click();await add();await waitRows(21);
   assert.equal(writes.length,1,'Repeated click duplicated membership mutation');
   assert.deepEqual(writes[0],{method:'PUT',path:'/api/shared-work/PROJECT/77/members/'+big,body:{role:'EDITOR'}});
   await members.locator('li').last().getByRole('button',{name:'Отозвать'}).click();await waitRows(20);
   failNext=true;await modal.locator('[data-work-add]').click();
   await modal.locator('[data-work-status]').filter({hasText:'Не удалось сохранить'}).waitFor();
   assert.equal(writes.length,3,'Uncertain failed write retried automatically');assert.equal(await members.locator('li').count(),20);
   await modal.locator('#project-info-tab').click();assert.equal(await modal.locator('#projectName').inputValue(),'Несохранённое название');
   await modal.locator('#saveProjectBtn').click();await modal.waitFor({state:'hidden'});
   await page.locator('[data-project-edit="77"]').waitFor();
   await page.locator('#createProjectBtn').click();await modal.locator('#projectName').fill('Новый проект');
   await modal.locator('#saveProjectBtn').dblclick();await waitRows(1);
   assert.equal(await modal.locator('#project-members-tab').getAttribute('aria-selected'),'true','New project did not guide to participants');
   await page.locator('.projects-empty-state').filter({hasText:'Не удалось загрузить'}).waitFor({state:'attached'});
   await add();await waitRows(2);
   await modal.locator('.work-access-advanced > summary').click();await modal.locator('[data-work-transfer]').click();
   const confirmation=page.locator('#confirmActionModal');await confirmation.waitFor({state:'visible'});
   await confirmation.locator('[data-confirm-accept]').click();
   await page.waitForFunction(()=>document.querySelector('#projectModal [data-work-manage]').hidden);
   assert.equal(writes.at(-1).path,'/api/shared-work/PROJECT/88/owner/'+big);
   await close();canWrite=false;canManage=false;await open(77);await waitRows(20);
   assert(await modal.locator('[data-work-manage]').isHidden());
   await modal.locator('#project-info-tab').click();assert(await modal.locator('#projectName').isDisabled());assert(await modal.locator('#saveProjectBtn').isDisabled());
   await close();revoked=true;
   await page.addStyleTag({content:'.modal-backdrop.fade { transition: opacity 0.5s linear !important; }'});
   await modal.evaluate(el=>{
    window.qaModalHidden=false;window.qaDeniedBeforeShow=false;
    el.addEventListener('hidden.bs.modal',()=>{window.qaModalHidden=true;},{once:true});
    window.addEventListener('shared-work:denied',()=>{window.qaDeniedBeforeShow=!el.classList.contains('show');},{once:true});
   });
   await page.locator('[data-project-members="77"]').click();await page.waitForFunction(()=>window.qaModalHidden);
   assert(await page.evaluate(()=>window.qaDeniedBeforeShow),'Denial must be tested during backdrop opening');
   assert(await modal.isHidden());
   await page.locator('.projects-empty-state').filter({hasText:'Проектов пока нет'}).waitFor();assert.equal(await members.locator('li').count(),0);
   assert.equal(writes.length,7);assert.deepEqual(errors,[]);assert.deepEqual(missing,[]);
   report.push({theme,width,height,writes:7,bounds,errors,missing,status:'GREEN'});await context.close();
  }
  fs.writeFileSync(path.join(out,'report.json'),JSON.stringify(report,null,2));console.log('PROJECT_SETTINGS_QA_GREEN='+report.length);
 }finally{await browser.close();server.close();}
}
main().catch(error=>{console.error(error);process.exitCode=1;});
