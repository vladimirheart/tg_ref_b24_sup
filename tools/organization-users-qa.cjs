/* Browser regression against the actual Settings MVC render and production scripts/styles. */
const fs=require('node:fs'),path=require('node:path'),http=require('node:http'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..'),resources=path.join(root,'spring-panel/src/main/resources');
const capture=path.join(root,'spring-panel/target/org-rendered/settings/index.html'),out=path.join(root,'spring-panel/target/organization-users-qa');
const baseNodes=[{id:'root',name:'Управляющая компания',parent_id:null,members:[]},{id:'it',name:'IT-Dep',parent_id:'root',members:[]},{id:'support',name:'ITS',parent_id:'it',members:[2,999]},{id:'other',name:'Другой филиал',parent_id:null,members:[]},{id:'other-support',name:'ITS',parent_id:'other',members:[]}];
const users=[{id:1,username:'ivan',full_name:'Иван Сотрудник',department:'Управляющая компания / IT-Dep / ITS',email:'ivan@example.test',phones:[{type:'mobile',value:'+7 (999) 000-00-01'}],role_id:1,role:'Оператор',can_edit_username:true,can_edit_role:true,can_edit_password:true,can_view_password:false,can_block:true},
 {id:2,username:'manual',full_name:'Мария Участник',department:'',role:'Оператор'},
 {id:3,username:'ambiguous',full_name:'Неоднозначный отдел',department:'ITS'},
 {id:4,username:'legacy',full_name:'Сотрудник старого отдела',department:'IT-Dep'},
 ...Array.from({length:85},(_,i)=>({id:i+10,username:'user'+String(i).padStart(2,'0'),full_name:'Сотрудник '+String(i).padStart(2,'0'),department:''}))];
function state(nodes){return {users,roles:[{id:1,name:'Оператор',permissions:{pages:[],fields:{edit:[],view:[]}}}],org_structure:{nodes},catalog:{pages:[],fields:{edit:[],view:[]}},capabilities:{fields:{edit:{'user.create':true,'user.role':true},view:{}}},current_user_id:1};}
async function main(){
 assert(fs.existsSync(capture),'Run targeted MVC capture first');fs.mkdirSync(out,{recursive:true});
 const server=http.createServer((req,res)=>{
  const url=new URL(req.url,'http://localhost'); const file=url.pathname==='/settings'?capture:path.resolve(resources,'static',url.pathname.slice(1));
  if(file!==capture&&!file.startsWith(path.join(resources,'static')+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){res.writeHead(404);res.end();return;}
  const ext=path.extname(file),mime={'.html':'text/html; charset=utf-8','.css':'text/css','.js':'application/javascript','.woff2':'font/woff2','.svg':'image/svg+xml'};
  res.writeHead(200,{'Content-Type':mime[ext]||'application/octet-stream'});
  if(['.html','.css','.js'].includes(ext))res.end(fs.readFileSync(file,'utf8').replaceAll('\r\n','\n'));else fs.createReadStream(file).pipe(res);
 });
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));const origin='http://127.0.0.1:'+server.address().port;
 const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright'),browser=await chromium.launch({headless:true});
 const report=[],errors=[];
 try{
  for(const width of [1440,1024,390,320])for(const theme of ['light','dark']){
   const context=await browser.newContext({viewport:{width,height:900},reducedMotion:'reduce'});
   let nodes=structuredClone(baseNodes),canEdit=true,saves=0,profilePayload=null;
   await context.addInitScript(({theme})=>{localStorage.setItem('sidebarPinned','1');localStorage.setItem('theme',theme);localStorage.setItem('colorTheme','neo');},{theme});
   await context.route('**/*',async route=>{
    const request=route.request(),url=new URL(request.url());if(url.origin!==origin)return route.abort();
    if(url.pathname==='/api/auth/state')return route.fulfill({json:state(nodes)});
    if(url.pathname==='/api/auth/permissions')return route.fulfill({json:{success:true,settings:{organization:canEdit,users:canEdit,workforce:canEdit}}});
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
   await page.locator('#auth-org-tab').click();
   const branch=id=>page.locator(`li[data-node-id="${id}"] > .org-node`);
   assert.equal(await page.locator('[data-org-tree] > li').count(),2);
   assert(await branch('support').getByText('Иван Сотрудник',{exact:true}).count());
   assert(await branch('support').getByText('Мария Участник',{exact:true}).count());
   assert.equal(await branch('support').getByText('Неоднозначный отдел',{exact:true}).count(),0);
   assert(await branch('it').getByText('Сотрудник старого отдела',{exact:true}).count());
   const positions=await Promise.all(['root','it','support'].map(id=>branch(id).boundingBox()));
   assert(positions[1].x>positions[0].x&&positions[2].x>positions[1].x,'Tree indentation absent');
   const toggle=branch('it').locator('[data-org-action="toggle"]');await toggle.click();assert.equal(await branch('support').isVisible(),false);await toggle.click();
   await page.screenshot({path:path.join(out,`tree-${width}-${theme}.png`)});
   await branch('support').locator('[data-org-action="members"]').click();
   const modal=page.locator('#orgMembersModal');await modal.waitFor({state:'visible'});
   assert.equal(await modal.locator('.modal-content').evaluate(el=>getComputedStyle(el).backgroundColor),theme==='dark'?'rgb(28, 29, 33)':'rgb(255, 255, 255)');
   if(width>=768){
    const handle=modal.locator('.modal-resize-handle');await handle.focus();await handle.press('ArrowUp');
    const resized=await modal.evaluate(el=>({dialog:el.querySelector('.modal-dialog').getBoundingClientRect().height,content:el.querySelector('.modal-content').getBoundingClientRect().height}));
    assert(resized.dialog<740&&Math.abs(resized.dialog-resized.content)<2,'Saved resize must resize the content');
    await handle.dblclick();
   }
   assert(await modal.locator('[data-org-members-roster]').getByText('Иван Сотрудник',{exact:true}).count());
   assert(await modal.locator('[data-org-members-roster]').getByText('Отдел в карточке',{exact:true}).count());
   await modal.locator('.employee-picker__trigger').click();
   const geometry=await modal.evaluate(el=>{const dialog=el.querySelector('.modal-dialog').getBoundingClientRect(),list=el.querySelector('.employee-picker__list'),button=el.querySelector('[type=submit]').getBoundingClientRect();return {width:dialog.width,right:dialog.right,left:dialog.left,listHeight:list.clientHeight,scroll:list.scrollHeight,buttonBottom:button.bottom,viewport:innerHeight};});
   assert(geometry.listHeight>100&&geometry.scroll>geometry.listHeight,'List must own scroll');assert(geometry.buttonBottom<=geometry.viewport&&geometry.right<=width&&geometry.left>=0,'Modal clipped');
   if(width===1440)assert(geometry.width>=900,'Membership dialog too narrow');
   assert.equal(await modal.locator('[data-employee-value="2"]').getAttribute('aria-pressed'),'true');
   const search=modal.locator('.employee-picker__menu input[type=search]');
   await search.fill('Сотрудник 00');await modal.locator('[data-employee-value="10"]').click();
   await search.fill('Мария');await modal.locator('[data-employee-value="2"]').click();
   assert.equal(await modal.locator('[data-org-members-roster]').getByText('Мария Участник',{exact:true}).count(),0);
   assert(await modal.locator('[data-org-members-roster]').getByText('Сотрудник 00',{exact:true}).count());
   await search.fill('совсемнет');assert.equal(await modal.locator('[data-employee-value]:visible').count(),0);
   await search.fill('');await page.screenshot({path:path.join(out,`members-${width}-${theme}.png`)});
   await modal.locator('[type=submit]').click();await modal.waitFor({state:'hidden'});await page.locator('[data-org-save]').click();
   await page.waitForFunction(()=>!document.querySelector('[data-auth-management]').__authManager.orgStructureDirty);
   assert.equal(saves,1);assert.deepEqual(nodes.find(n=>n.id==='support').members,[10,999]);assert.equal(nodes.find(n=>n.id==='support').parent_id,'it');
   await page.evaluate(()=>document.querySelector('[data-auth-management]').__authManager.refresh());
   assert(await branch('support').getByText('Иван Сотрудник',{exact:true}).count());assert(await branch('support').getByText('Сотрудник 00',{exact:true}).count());assert.equal(await branch('support').getByText('Мария Участник',{exact:true}).count(),0);
   await page.locator('#auth-users-tab').click();await page.locator('[data-auth-users-body] tr[data-user-id="1"] [data-action="open-user"]').click();
   const card=page.locator('#authUserDetailsModal');await card.waitFor({state:'visible'});
   assert.equal(await card.locator('#authUserModalFullName').inputValue(),'Иван Сотрудник');assert.equal(await card.locator('#authUserModalEmail').inputValue(),'ivan@example.test');
   assert.equal(await card.locator('[data-user-security-disclosure]').getAttribute('open'),null);
   await page.screenshot({path:path.join(out,`employee-${width}-${theme}.png`)});
   const sizes=await card.evaluate(el=>[...el.querySelectorAll('.auth-user-overview,.auth-user-main,.auth-user-photo')].map(n=>({name:n.className,scroll:n.scrollWidth,width:n.clientWidth,display:getComputedStyle(n).display,grid:getComputedStyle(n).gridTemplateColumns})));
   assert(!sizes.some(n=>n.scroll>n.width+2),'Employee card horizontal overflow: '+JSON.stringify(sizes));
   await card.locator('[data-user-security-disclosure] > summary').click();assert(await card.locator('[data-auth-user-password-change]').isVisible());
   await card.locator('#authUserModalFullName').fill('Иван Проверенный');await card.locator('[data-auth-user-submit]').click();
   await page.waitForFunction(()=>document.querySelector('[data-auth-user-save-status]')?.textContent.includes('Сохранено'));
   assert.deepEqual(profilePayload,{full_name:'Иван Проверенный'});
   await card.locator('.modal-header [data-bs-dismiss]').click();await card.waitFor({state:'hidden'});
   // Real permission runtime keeps the roster visible while editing is disabled.
   canEdit=false;await page.evaluate(()=>window.SettingsPermissions.refresh());await page.locator('#auth-org-tab').click();
   assert(await branch('support').locator('[data-org-action="members"]').isEnabled());
   assert(await branch('support').locator('[data-org-action="rename"]').isDisabled());
   await branch('support').locator('[data-org-action="members"]').click();await modal.waitFor({state:'visible'});
   assert(await modal.locator('[data-org-members-roster]').getByText('Иван Сотрудник',{exact:true}).count());
   assert(await modal.locator('.employee-picker__trigger').isDisabled());assert(await modal.locator('[type=submit]').isDisabled());
   await modal.locator('.modal-header [data-bs-dismiss]').click();
   // Corrupt cycles remain visible without recursion or source mutation.
   const cycle=await page.evaluate(()=>{const m=document.querySelector('[data-auth-management]').__authManager,original=m.state.orgStructure;m.state.orgStructure=m.normalizeOrgStructure({nodes:[{id:'a',name:'A',parent_id:'b',members:[]},{id:'b',name:'B',parent_id:'a',members:[]},{id:'orphan',name:'<img onerror=alert(1)>',parent_id:'missing',members:[]}]});m.renderOrgStructure();const result={count:document.querySelectorAll('[data-org-tree] li').length,unsafe:document.querySelectorAll('[data-org-tree] img').length,parent:m.state.orgStructure.nodes[0].parent_id};m.state.orgStructure=original;m.renderOrgStructure();return result;});
   assert.deepEqual(cycle,{count:3,unsafe:0,parent:'b'});
   report.push({width,theme,geometry,checks:['nested-tree','department-and-manual-members','ambiguous-department-excluded','filter-preserves-selection','save-reload','missing-user-retained','employee-layout-and-profile-save','readonly','cycle-xss']});
   await context.close();
  }
  assert.deepEqual(errors,[]);fs.writeFileSync(path.join(out,'report.json'),JSON.stringify({report,errors},null,2));console.log(`GREEN: ${report.length} viewport/theme scenarios; tree, membership save/reload, employee card and read-only permissions.`);
 }finally{await browser.close();await new Promise(resolve=>server.close(resolve));}
}
main().catch(error=>{console.error(error);process.exitCode=1;});
