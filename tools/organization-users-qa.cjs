/* Browser regression against the actual Thymeleaf users fragment and production scripts/styles. */
const fs=require('node:fs'),path=require('node:path'),http=require('node:http'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..'),resources=path.join(root,'spring-panel/src/main/resources');
const capture=path.join(root,'spring-panel/target/permission-editors-qa/users-workspace.html'),out=path.join(root,'spring-panel/target/organization-users-qa');
const baseNodes=[{id:'root',name:'Управляющая компания',parent_id:null,members:[]},{id:'it',name:'IT-Dep',parent_id:'root',members:[]},{id:'support',name:'ITS',parent_id:'it',members:[2,999]},{id:'other',name:'Другой филиал',parent_id:null,members:[]},{id:'other-support',name:'ITS',parent_id:'other',members:[]}];
const fixturePhoto='data:image/svg+xml,'+encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="160" height="160"><rect width="160" height="160" fill="#44546a"/><circle cx="80" cy="58" r="27" fill="#dae2ed"/><path d="M24 154v-20a56 56 0 0 1 112 0v20z" fill="#dae2ed"/></svg>');
const users=[{id:1,username:'ivan',full_name:'Иван Сотрудник',department:'Управляющая компания / IT-Dep / ITS',email:'ivan@example.test',phones:[{type:'mobile',value:'79990000001'}],role_id:1,role:'Оператор',can_edit_username:true,can_edit_role:true,can_edit_password:true,can_view_password:false,can_block:true,can_revoke_sessions:true,last_portal_activity_at:new Date(Date.now()-5*60000).toISOString()},
 {id:2,username:'manual',full_name:'Мария Участник',department:'',role:'Оператор',is_blocked:true,last_portal_activity_at:new Date(Date.now()-31*60000).toISOString()},
 {id:3,username:'ambiguous',full_name:'Неоднозначный отдел',department:'ITS'},
 {id:4,username:'legacy',full_name:'Сотрудник старого отдела',department:'IT-Dep'},
 ...Array.from({length:85},(_,i)=>({id:i+10,username:'user'+String(i).padStart(2,'0'),full_name:'Сотрудник '+String(i).padStart(2,'0'),department:''}))];
function state(nodes){return {users:users.map(user=>user.id===1?{...user,photo:fixturePhoto}:user),roles:[{id:1,name:'Оператор',permissions:{pages:[],fields:{edit:[],view:[]}}}],org_structure:{nodes},catalog:{pages:[],fields:{edit:[],view:[]}},capabilities:{fields:{edit:{'user.create':true,'user.role':true},view:{}}},current_user_id:1};}
async function main(){
 assert(fs.existsSync(capture),'Run PermissionEditorsRenderTest first');fs.mkdirSync(out,{recursive:true});
 const settings=fs.readFileSync(path.join(resources,'templates/settings/index.html'),'utf8');
 const css=[...settings.matchAll(/<link th:href="@\{(\/css\/[^()]+)\(v='[^']+'\)\}" rel="stylesheet"/g)].map(match=>match[1]);
 const authScripts=[...settings.matchAll(/<script (?:th:)?src="(?:@\{)?(\/js\/auth-management[^?(" ]*\.js)/g)].map(match=>match[1]);
 assert.equal(authScripts.length,5,'Use all production auth dependencies');
 const html=`<!doctype html><html lang="ru"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
 <link rel="stylesheet" href="/vendor/bootstrap/5.3.0/bootstrap.min.css"><link rel="stylesheet" href="/vendor/bootstrap-icons/1.10.5/bootstrap-icons.css">
 ${css.map(file=>`<link rel="stylesheet" href="${file}">`).join('')}
 <script src="/js/ui-preferences.js"></script><script src="/js/theme.js"></script><script src="/js/common.js"></script></head>
 <body data-ui-page="settings"><button data-settings-overview-target="usersModal">Сотрудники</button>${fs.readFileSync(capture,'utf8')}
 <script src="/vendor/bootstrap/5.3.0/bootstrap.bundle.min.js"></script><script src="/js/modal-helpers.js"></script><script src="/js/modal-resize.js"></script>
 <script src="/js/employee-picker.js"></script><script src="/js/settings-permissions.js"></script><script src="/js/permission-editor.js"></script><script src="/js/user-permissions.js"></script>
 ${authScripts.map(file=>`<script src="${file}"></script>`).join('')}
 <script>AuthManagement.mount(document.querySelector('[data-auth-management]'));document.querySelector('[data-settings-overview-target]').onclick=()=>bootstrap.Modal.getOrCreateInstance(document.getElementById('usersModal')).show();</script></body></html>`;
 const server=http.createServer((req,res)=>{
  const url=new URL(req.url,'http://localhost'); const file=url.pathname==='/settings'?capture:path.resolve(resources,'static',url.pathname.slice(1));
  if(file!==capture&&!file.startsWith(path.join(resources,'static')+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){res.writeHead(404);res.end();return;}
  const ext=path.extname(file),mime={'.html':'text/html; charset=utf-8','.css':'text/css','.js':'application/javascript','.woff2':'font/woff2','.svg':'image/svg+xml'};
  res.writeHead(200,{'Content-Type':mime[ext]||'application/octet-stream'});
  if(file===capture)res.end(html);else if(['.css','.js'].includes(ext))res.end(fs.readFileSync(file,'utf8').replaceAll('\r\n','\n'));else fs.createReadStream(file).pipe(res);
 });
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));const origin='http://127.0.0.1:'+server.address().port;
 const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright'),browser=await chromium.launch({headless:true,...(process.env.PLAYWRIGHT_BROWSER_EXECUTABLE?{executablePath:process.env.PLAYWRIGHT_BROWSER_EXECUTABLE}:{}),...(process.env.PLAYWRIGHT_CHANNEL?{channel:process.env.PLAYWRIGHT_CHANNEL}:{})});
 const report=[],errors=[];
 try{
  const savedCases=[{width:1280,height:663,theme:'dark',savedSmall:true},{width:1280,height:663,theme:'light',savedSmall:true},{width:1280,height:480,theme:'dark',savedSmall:true},{width:390,height:663,theme:'dark',savedSmall:true}];
  const scenarios=process.argv.includes('--short-height-only')?savedCases.filter(item=>item.height<540):process.argv.includes('--saved-size-only')?savedCases:[...[1440,1024,390,320].flatMap(width=>['light','dark'].map(theme=>({width,height:900,theme,savedSmall:false}))),...savedCases];
  for(const {width,height,theme,savedSmall} of scenarios){
   const context=await browser.newContext({viewport:{width,height},reducedMotion:'reduce'});
   let nodes=structuredClone(baseNodes),canEdit=true,saves=0,profilePayload=null,activityReads=0;
   await context.addInitScript(({theme,savedSmall})=>{localStorage.setItem('sidebarPinned','1');localStorage.setItem('theme',theme);localStorage.setItem('colorTheme','neo');if(savedSmall){localStorage.setItem('iguana:modal-size:v1:%2Fsettings:orgMembersModal',JSON.stringify({width:896,height:220}));localStorage.setItem('iguana:modal-size:v1:%2Fsettings:authUserPasswordModal',JSON.stringify({width:480,height:250}));}},{theme,savedSmall});
   await context.route('**/*',async route=>{
    const request=route.request(),url=new URL(request.url());if(url.origin!==origin)return route.abort();
    if(url.pathname==='/api/auth/state')return route.fulfill({json:state(nodes)});
    if(url.pathname==='/api/users/activity'){activityReads++;return route.fulfill({json:{server_time:new Date().toISOString(),users:users.map(({id,is_blocked,last_portal_activity_at})=>({id,is_blocked:!!is_blocked,last_portal_activity_at:last_portal_activity_at||null}))}});}
    if(url.pathname==='/api/auth/permissions')return route.fulfill({json:{success:true,accounts:true,settings:{organization:canEdit,users:canEdit,workforce:canEdit}}});
    if(url.pathname==='/api/users/1'&&request.method()==='PUT'){profilePayload=request.postDataJSON();return route.fulfill({json:{success:true}});}
    if(url.pathname==='/api/auth/org-structure'){
     assert(canEdit,'Read-only user wrote org structure');nodes=request.postDataJSON().org_structure.nodes;saves++;return route.fulfill({json:{success:true,org_structure:{nodes}}});
    }
    if(url.pathname==='/api/workforce/state')return route.fulfill({json:{available:false,positions:[],notification_channels:[]}});
    if(url.pathname.startsWith('/api/')||url.pathname.startsWith('/profile/'))return route.fulfill({json:{success:true,available:false,items:[],positions:[]}});
    return route.continue();
   });
   const page=await context.newPage();page.on('pageerror',error=>errors.push({width,theme,error:error.message}));
   await page.goto(origin+'/settings');await page.evaluate(theme=>{window.ThemeRuntime.setPalette('neo');window.ThemeRuntime.setTheme(theme);},theme);
   assert.equal(await page.evaluate(()=>document.body.dataset.theme),theme);
   await page.locator('[data-settings-overview-target="usersModal"]').click();
   await page.waitForFunction(()=>document.querySelector('[data-auth-management]')?.__authManager?.state.users.length>0);
   await page.waitForFunction(()=>{const manager=document.querySelector('[data-auth-management]').__authManager;return Number.isFinite(manager.activityClockOffset)&&!manager.activityPending;});
   assert(activityReads>0,'Visible user list never fetched activity');
   await page.waitForFunction(()=>document.querySelector('[data-user-status="1"] .auth-user-state--online'));
   assert.equal(await page.locator('[data-user-status="2"] .auth-user-state--blocked').count(),1);
   assert.equal(await page.locator('[data-user-status="2"] .auth-user-state--offline').count(),1);
   assert.equal(await page.locator('[data-user-status="3"] .auth-user-last-activity').textContent(),'Нет данных');
   assert.match(await page.locator('[data-user-status="1"] .auth-user-state--online').getAttribute('aria-label'),/Последняя активность:.*30 минут/);
   const thresholds=await page.evaluate(()=>{
    const manager=document.querySelector('[data-auth-management]').__authManager,cell=document.createElement('div'),originalNow=Date.now,now=originalNow();Date.now=()=>now;
    try{return [29*60000,30*60000,30*60000+1,null,'invalid'].map(age=>{manager.renderUserStatus(cell,{last_portal_activity_at:typeof age==='number'?new Date(now+(manager.activityClockOffset||0)-age).toISOString():age});return !!cell.querySelector('.auth-user-state--online');});}
    finally{Date.now=originalNow;}
   });
   assert.deepEqual(thresholds,[true,true,false,false,false]);
   const firstUser=page.locator('[data-auth-users-body] tr[data-user-id="1"]');
   assert.equal(await firstUser.locator('[data-action="open-user"]').isVisible(),false);
   await firstUser.locator('[data-bs-toggle="dropdown"]').click();
   const menuVisible=await firstUser.locator('[data-action="open-user"]').evaluate(el=>{const rect=el.getBoundingClientRect();return el.contains(document.elementFromPoint(rect.x+rect.width/2,rect.y+rect.height/2));});
   assert(menuVisible,'Row menu is clipped by the table or modal');
   assert(await firstUser.locator('[data-action="revoke-sessions"]').isVisible());
   await firstUser.locator('[data-bs-toggle="dropdown"]').press('Escape');
   assert.equal(await firstUser.locator('[data-action="open-user"]').isVisible(),false);
   const lastUser=page.locator('[data-auth-users-body] tr').last();
   await lastUser.locator('[data-bs-toggle="dropdown"]').click();
   assert(await lastUser.locator('[data-action="open-user"]').evaluate(el=>{const rect=el.getBoundingClientRect();return el.contains(document.elementFromPoint(rect.x+rect.width/2,rect.y+rect.height/2));}),'Last-row menu is clipped');
   assert(await lastUser.locator('[data-action="delete-user"]').isDisabled());
   await lastUser.locator('[data-bs-toggle="dropdown"]').press('Escape');
   await page.locator('#auth-org-tab').click();
   const pausedReads=activityReads;await page.evaluate(()=>document.querySelector('[data-auth-management]').__authManager.refreshUserActivity());
   assert.equal(activityReads,pausedReads,'Activity polling continued outside the users tab');
   const branch=id=>page.locator(`li[data-node-id="${id}"] > .org-node`);
   assert.equal(await page.locator('[data-org-tree] > li').count(),2);
   assert(await branch('support').getByText('Иван Сотрудник',{exact:true}).count());
   assert(await branch('support').getByText('Мария Участник',{exact:true}).count());
   assert.equal(await branch('support').getByText('Неоднозначный отдел',{exact:true}).count(),0);
   assert(await branch('it').getByText('Сотрудник старого отдела',{exact:true}).count());
   const positions=await Promise.all(['root','it','support'].map(id=>branch(id).boundingBox()));
   assert(positions[1].x>positions[0].x&&positions[2].x>positions[1].x,'Tree indentation absent');
   const toggle=branch('it').locator('[data-org-action="toggle"]');await toggle.click();assert.equal(await branch('support').isVisible(),false);await toggle.click();
   await page.screenshot({path:path.join(out,`tree-${width}-${height}-${theme}-${savedSmall?"saved":"fresh"}.png`)});
   await branch('support').locator('[data-org-action="members"]').click();
   const modal=page.locator('#orgMembersModal');await modal.waitFor({state:'visible'});
   assert.equal(await modal.locator('.modal-content').evaluate(el=>getComputedStyle(el).backgroundColor),theme==='dark'?'rgb(28, 29, 33)':'rgb(255, 255, 255)');
   const initialSize=await modal.locator('.modal-dialog').boundingBox();
   const minimumHeight=Math.min(640,height-24);
   if(savedSmall&&width>=768)assert(initialSize.height>=minimumHeight-1,`Saved size collapsed the dialog: ${JSON.stringify({width,height,initialSize})}`);
   if(width>=768){
    const handle=modal.locator('.modal-resize-handle');await handle.focus();await handle.press('ArrowUp');
    const resized=await modal.evaluate(el=>({dialog:el.querySelector('.modal-dialog').getBoundingClientRect().height,content:el.querySelector('.modal-content').getBoundingClientRect().height}));
    assert(resized.dialog<=initialSize.height&&resized.dialog>=minimumHeight-1&&Math.abs(resized.dialog-resized.content)<2,'Resize must respect usable height and resize the content');
    if(savedSmall){for(let attempt=0;attempt<12;attempt++)await handle.press('Shift+ArrowUp');assert((await modal.locator('.modal-dialog').boundingBox()).height>=minimumHeight-1,'Keyboard resize bypassed the minimum');}
    if(!savedSmall)await handle.dblclick();
   }
   assert(await modal.locator('[data-org-members-roster]').getByText('Иван Сотрудник',{exact:true}).count());
   assert(await modal.locator('[data-org-members-roster]').getByText('Отдел в карточке',{exact:true}).count());
   await modal.locator('.employee-picker__trigger').click();
   const geometry=await modal.evaluate(el=>{const dialog=el.querySelector('.modal-dialog').getBoundingClientRect(),list=el.querySelector('.employee-picker__list'),button=el.querySelector('[type=submit]').getBoundingClientRect();return {width:dialog.width,right:dialog.right,left:dialog.left,listHeight:list.clientHeight,scroll:list.scrollHeight,buttonBottom:button.bottom,viewport:innerHeight};});
   assert(geometry.listHeight>100&&geometry.scroll>geometry.listHeight,'List must own scroll');assert(geometry.buttonBottom<=geometry.viewport&&geometry.right<=width&&geometry.left>=0,'Modal clipped');
   if(width===1440)assert(geometry.width>=1100,'Membership dialog too narrow');
   const visibleList=await modal.locator('.employee-picker__list').evaluate(list=>{const body=list.closest('.modal-body').getBoundingClientRect(),rect=list.getBoundingClientRect();return Math.max(0,Math.min(rect.bottom,body.bottom)-Math.max(rect.top,body.top));});
   await page.screenshot({path:path.join(out,`members-${width}-${height}-${theme}-${savedSmall?"saved":"fresh"}.png`)});
   assert(visibleList>=Math.min(150,height*0.25),`Employee list clipped at ${width}x${height}: ${visibleList}px visible`);
   assert.equal(await modal.locator('[data-employee-value="2"]').getAttribute('aria-pressed'),'true');
   const search=modal.locator('.employee-picker__menu input[type=search]');
   await search.fill('Сотрудник 00');await modal.locator('[data-employee-value="10"]').click();
   await search.fill('Мария');await modal.locator('[data-employee-value="2"]').click();
   assert.equal(await modal.locator('[data-org-members-roster]').getByText('Мария Участник',{exact:true}).count(),0);
   assert(await modal.locator('[data-org-members-roster]').getByText('Сотрудник 00',{exact:true}).count());
   await search.fill('совсемнет');assert.equal(await modal.locator('[data-employee-value]:visible').count(),0);
   await search.fill('');await page.screenshot({path:path.join(out,`members-${width}-${height}-${theme}-${savedSmall?"saved":"fresh"}.png`)});
   await modal.locator('[type=submit]').click();await modal.waitFor({state:'hidden'});await page.locator('[data-org-save]').click();
   await page.waitForFunction(()=>!document.querySelector('[data-auth-management]').__authManager.orgStructureDirty);
   assert.equal(saves,1);assert.deepEqual(nodes.find(n=>n.id==='support').members,[10,999]);assert.equal(nodes.find(n=>n.id==='support').parent_id,'it');
   await page.evaluate(()=>document.querySelector('[data-auth-management]').__authManager.refresh());
   assert(await branch('support').getByText('Иван Сотрудник',{exact:true}).count());assert(await branch('support').getByText('Сотрудник 00',{exact:true}).count());assert.equal(await branch('support').getByText('Мария Участник',{exact:true}).count(),0);
   await page.locator('#auth-users-tab').click();await firstUser.locator('[data-bs-toggle="dropdown"]').click();await firstUser.locator('[data-action="open-user"]').click();
   const card=page.locator('#authUserDetailsModal');await card.waitFor({state:'visible'});
   assert.equal(await card.locator('#authUserModalFullName').inputValue(),'Иван Сотрудник');assert.equal(await card.locator('#authUserModalEmail').inputValue(),'ivan@example.test');
   assert.equal(await card.locator('[data-user-security-disclosure]').getAttribute('open'),null);
   assert.equal(await card.locator('[data-user-contacts-disclosure]').getAttribute('open'),null);
   assert.equal(await card.locator('.auth-user-department-disclosure').getAttribute('open'),null);
   assert.equal(await card.locator('[data-auth-user-card-status] .auth-user-state--online').count(),1);
   const contrast=await card.locator('.auth-user-state--online').evaluate(el=>{
    const luminance=color=>{const rgb=color.match(/[\d.]+/g).slice(0,3).map(value=>{const s=Number(value)/255;return s<=0.04045?s/12.92:((s+0.055)/1.055)**2.4;});return rgb[0]*0.2126+rgb[1]*0.7152+rgb[2]*0.0722;};
    const a=luminance(getComputedStyle(el).color),b=luminance(getComputedStyle(el.closest('.modal-content')).backgroundColor);return (Math.max(a,b)+0.05)/(Math.min(a,b)+0.05);
   });
   assert(contrast>=3,`Presence indicator contrast is too low: ${contrast}`);
   assert.equal(await card.locator('[data-auth-user-registration]').evaluate(el=>el.tagName),'SPAN');
   const photoSize=await card.locator('[data-auth-user-photo-img]').boundingBox();
   assert(photoSize.width>=145&&photoSize.height>=145,'User photo must stay about 40% larger than the original 104 px');
   assert.equal(await card.locator('[data-auth-user-photo-change]').isVisible(),false);
   await card.locator('.auth-user-photo-actions [data-bs-toggle="dropdown"]').click();
   assert(await card.locator('[data-auth-user-photo-change]').isVisible());assert(await card.locator('[data-auth-user-photo-remove]').isVisible());
   await card.locator('.auth-user-photo-actions [data-bs-toggle="dropdown"]').press('Escape');
   assert(await card.isVisible(),'Esc in the photo menu must keep the user card open');
   await page.screenshot({path:path.join(out,`employee-${width}-${height}-${theme}-${savedSmall?"saved":"fresh"}.png`)});
   const sizes=await card.evaluate(el=>[...el.querySelectorAll('.auth-user-overview,.auth-user-main,.auth-user-photo')].map(n=>({name:n.className,scroll:n.scrollWidth,width:n.clientWidth,display:getComputedStyle(n).display,grid:getComputedStyle(n).gridTemplateColumns})));
   assert(!sizes.some(n=>n.scroll>n.width+2),'Employee card horizontal overflow: '+JSON.stringify(sizes));
   await card.locator('[data-user-security-disclosure] > summary').click();assert(await card.locator('[data-auth-user-password-change]').isVisible());
   if(savedSmall&&width>=768){
    await card.locator('[data-auth-user-password-change]').click();const passwordModal=page.locator('#authUserPasswordModal');await passwordModal.waitFor({state:'visible'});
    assert(Math.abs((await passwordModal.locator('.modal-dialog').boundingBox()).height-250)<2,'Organization minimum leaked into another modal');
    await passwordModal.locator('.modal-header [data-bs-dismiss]').click();await passwordModal.waitFor({state:'hidden'});
   }
   await card.locator('#authUserModalFullName').fill('Иван Проверенный');await card.locator('[data-auth-user-submit]').click();
   await page.waitForFunction(()=>document.querySelector('[data-auth-user-save-status]')?.textContent.includes('Сохранено'));
   assert.deepEqual(profilePayload,{full_name:'Иван Проверенный'});
   await card.locator('.modal-header [data-bs-dismiss]').click();await card.waitFor({state:'hidden'});
   // Current settings permissions hide sections whose access was revoked.
   canEdit=false;await page.evaluate(()=>window.SettingsPermissions.refresh());
   assert.equal(await page.locator('#auth-org-tab').isVisible(),false);
   assert.equal(await page.evaluate(()=>window.SettingsPermissions.canOpen('settingsAuthOrg')),false);
   assert.equal(await page.locator('[data-org-save]').isVisible(),false);
   // Corrupt cycles remain visible without recursion or source mutation.
   const cycle=await page.evaluate(()=>{const m=document.querySelector('[data-auth-management]').__authManager,original=m.state.orgStructure;m.state.orgStructure=m.normalizeOrgStructure({nodes:[{id:'a',name:'A',parent_id:'b',members:[]},{id:'b',name:'B',parent_id:'a',members:[]},{id:'orphan',name:'<img onerror=alert(1)>',parent_id:'missing',members:[]}]});m.renderOrgStructure();const result={count:document.querySelectorAll('[data-org-tree] li').length,unsafe:document.querySelectorAll('[data-org-tree] img').length,parent:m.state.orgStructure.nodes[0].parent_id};m.state.orgStructure=original;m.renderOrgStructure();return result;});
   assert.deepEqual(cycle,{count:3,unsafe:0,parent:'b'});
   report.push({width,height,theme,savedSmall,activityReads,photoSize,initialSize,visibleList,geometry,checks:['status-threshold-and-tooltips','blocked-independent-of-presence','row-menu-and-escape','photo-size-and-menu','compact-card-disclosures','nested-tree','department-and-manual-members','ambiguous-department-excluded','filter-preserves-selection','save-reload','missing-user-retained','employee-layout-and-profile-save','readonly','cycle-xss']});
   await context.close();
  }
  assert.deepEqual(errors,[]);fs.writeFileSync(path.join(out,'report.json'),JSON.stringify({report,errors},null,2));console.log(`GREEN: ${report.length} viewport/theme scenarios; tree, membership save/reload, employee card and read-only permissions.`);
 }finally{await browser.close();await new Promise(resolve=>server.close(resolve));}
}
main().catch(error=>{console.error(error);process.exitCode=1;});
