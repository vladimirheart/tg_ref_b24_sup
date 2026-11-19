/* Read-only browser proof against actual MVC captures and repository assets. */
const fs=require('node:fs'),path=require('node:path'),http=require('node:http'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..'),resources=path.join(root,'spring-panel/src/main/resources');
const captures=path.join(root,'spring-panel/target/refined-rendered'),output=path.join(root,'spring-panel/target/refined-ui-qa');
const views=process.argv.includes('--dashboard')?['dashboard/index']:['dashboard/index','dialogs/index','settings/index','home/index','clients/profile','knowledge/note-editor','incidents/index','ui-kit/index'];
const palettes=['neo','forest','sand','north','plum'];
const backgrounds={neo:{light:'rgb(255, 255, 255)',dark:'rgb(28, 29, 33)'},forest:{light:'rgb(251, 252, 250)',dark:'rgb(23, 30, 27)'},sand:{light:'rgb(253, 251, 247)',dark:'rgb(35, 30, 26)'},north:{light:'rgb(251, 252, 254)',dark:'rgb(23, 30, 41)'},plum:{light:'rgb(253, 251, 254)',dark:'rgb(33, 27, 38)'}};
function luminance(color){const c=color.match(/[\d.]+/g).slice(0,3).map(Number).map(v=>v/255).map(v=>v<=.04045?v/12.92:((v+.055)/1.055)**2.4);return c[0]*.2126+c[1]*.7152+c[2]*.0722;}
function contrast(a,b){const x=luminance(a),y=luminance(b);return(Math.max(x,y)+.05)/(Math.min(x,y)+.05);}
async function main(){
 for(const view of views)assert(fs.existsSync(path.join(captures,view+'.html')),'Missing actual MVC capture: '+view);
 const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');fs.mkdirSync(output,{recursive:true});
 const mime={'.css':'text/css','.js':'application/javascript','.svg':'image/svg+xml','.png':'image/png','.woff2':'font/woff2','.html':'text/html; charset=utf-8'};
 const server=http.createServer((req,res)=>{
  const pathname=decodeURIComponent(new URL(req.url,'http://localhost').pathname),screen=pathname.startsWith('/screens/');
  const directory=screen?captures:path.join(resources,'static'),file=path.resolve(directory,screen?pathname.slice(9)+'.html':pathname.slice(1));
  if(!file.startsWith(directory+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){res.writeHead(404,{'Content-Type':'application/json'});res.end('{}');return;}
  res.writeHead(200,{'Content-Type':mime[path.extname(file)]||'application/octet-stream'});fs.createReadStream(file).pipe(res);
 });
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));const origin='http://127.0.0.1:'+server.address().port;
 const browser=await chromium.launch({headless:true}),report=[],runtimeErrors=[];
 try{
  const context=await browser.newContext({viewport:{width:1440,height:1050},reducedMotion:'reduce'});
  let editAppearance=false;
  await context.addInitScript(()=>{localStorage.setItem('sidebarPinned','1');});
  await context.route('**/*',route=>{
   const request=route.request(),url=new URL(request.url());if(url.origin!==origin)return route.abort();
   if(url.pathname.startsWith('/api/')||url.pathname==='/profile/ui-preferences'){
    if(url.pathname==='/api/auth/permissions')return route.fulfill({json:{success:true,settings:{appearance:editAppearance}}});
    if(url.pathname==='/api/dashboard/data')return route.fulfill({json:{stats:{current:{total:128},previous:{total:112}},time_stats:{formatted_total:'24 ч 15 мин',formatted_avg:'11 мин',resolved_count:116},staff_time_stats:[],activity_stats:null,charts:{channel:{Telegram:72,MAX:56},business:{'СушиВёсла':70,'Блинбери':58},category:{'Кассовое ПО':80,'Связь':48}},available_restaurants:[]}});
    return route.fulfill({json:{success:true,available:false,items:[],tickets:[],data:[],users:[],roles:[],businesses:[],channels:[]}});
   }
   if(!['GET','HEAD'].includes(request.method()))return route.fulfill({status:409,json:{success:false}});
   return route.continue();
  });
  const page=await context.newPage();page.on('pageerror',error=>runtimeErrors.push(String(error)));
  for(const width of (process.argv.includes('--interactions')?[]:[1440,390])){
   console.log('Checking viewport '+width);
   await page.setViewportSize({width,height:1050});
   for(const palette of palettes)for(const theme of ['light','dark'])for(const view of views){
    await page.goto(origin+'/screens/'+view,{waitUntil:'load'});
    await page.evaluate(({palette,theme})=>{window.ThemeRuntime.setPalette(palette);window.ThemeRuntime.setTheme(theme);},{palette,theme});
    await page.waitForTimeout(60);
    const result=await page.evaluate(()=>{
     const body=getComputedStyle(document.body),sidebar=document.querySelector('.sidebar'),main=document.querySelector('main'),rect=main?.getBoundingClientRect();
     return{palette:document.documentElement.dataset.themePalette,theme:document.documentElement.dataset.theme,background:body.backgroundColor,text:body.color,sidebar:sidebar?getComputedStyle(sidebar).backgroundColor:null,overflow:document.documentElement.scrollWidth>innerWidth+2,mainOutside:!!rect&&(rect.left< -2||rect.right>innerWidth+2),tokens:['--surface-card','--surface-soft','--primary','--state-danger'].map(key=>body.getPropertyValue(key).trim())};
    });
    assert.equal(result.palette,palette);assert.equal(result.theme,theme);assert.equal(result.background,backgrounds[palette][theme],JSON.stringify({view,width,...result}));
    assert(!result.overflow&&!result.mainOutside,JSON.stringify({view,width,...result}));assert(result.tokens.every(Boolean));assert(contrast(result.text,result.background)>=4.5);
    report.push({view,width,...result});
    if(width===1440&&['dashboard/index','dialogs/index'].includes(view)&&['neo','forest','sand'].includes(palette))await page.screenshot({path:path.join(output,view.replace('/','-')+'-'+palette+'-'+theme+'.png')});
   }
  }
  if(report.length)fs.writeFileSync(path.join(output,process.argv.includes('--dashboard')?'dashboard-matrix.json':'matrix.json'),JSON.stringify(report,null,2));
  await page.setViewportSize({width:1440,height:1050});await page.goto(origin+'/screens/dashboard/index',{waitUntil:'load'});
  await page.locator('#customizeDashboardLayout').click();assert.equal(await page.locator('#dashboardLayoutTools').isVisible(),true);
  const cards=page.locator('.dashboard-grid .data-panel'),firstId=await cards.first().getAttribute('data-panel-id'),first=page.locator(`[data-panel-id="${firstId}"]`);
  assert(await first.locator('.panel-actions').isVisible());await first.locator('.panel-pin-btn').click();assert.equal(await first.locator('.panel-pin-btn').getAttribute('aria-pressed'),'true');
  await first.locator('.panel-hide-btn').click();assert(!(await first.isVisible()));await page.locator('.dashboard-hidden-panel-chip').first().click();assert(await first.isVisible());
  const twoIds=await cards.evaluateAll(nodes=>nodes.slice(0,2).map(node=>node.dataset.panelId));
  for(const id of twoIds)await page.locator(`[data-panel-id="${id}"] .panel-hide-btn`).click();await page.locator('#restoreAllDashboardPanels').click();assert(await first.isVisible());
  const before=await page.evaluate(()=>window.iguanaUiPreferences.get('dashboardPanelLayout'));
  await page.evaluate(()=>{window.ThemeRuntime.setPalette('forest');window.ThemeRuntime.setTheme('dark');});assert.deepEqual(await page.evaluate(()=>window.iguanaUiPreferences.get('dashboardPanelLayout')),before);
  await page.reload({waitUntil:'load'});assert.equal(await page.evaluate(()=>window.ThemeRuntime.getPalette()),'forest');assert.equal(await page.evaluate(()=>window.ThemeRuntime.getTheme()),'dark');
  assert.equal(await page.locator('#customizeDashboardLayout').getAttribute('aria-expanded'),'false');assert(!(await page.locator('.data-panel .panel-actions').first().isVisible()));
  await page.locator('#customizeDashboardLayout').click();await page.locator('#resetDashboardLayout').click();assert(!(await cards.first().getAttribute('class')).includes('is-pinned'));
  const originalOrder=await cards.evaluateAll(nodes=>nodes.map(node=>node.dataset.panelId));
  await page.setViewportSize({width:1440,height:1700});
  const dragTarget=cards.nth(1);await dragTarget.scrollIntoViewIfNeeded();
  const targetBox=await dragTarget.boundingBox();
  await cards.first().locator('.panel-drag-handle').dragTo(dragTarget,{targetPosition:{x:targetBox.width/2,y:targetBox.height-8}});
  assert.notDeepEqual(await cards.evaluateAll(nodes=>nodes.map(node=>node.dataset.panelId)),originalOrder,'Native drag changes the existing saved layout');
  await page.locator('#resetDashboardLayout').click();assert.deepEqual(await cards.evaluateAll(nodes=>nodes.map(node=>node.dataset.panelId)),originalOrder);
  await page.evaluate(()=>window.ThemeRuntime.setTheme('auto'));await page.emulateMedia({colorScheme:'dark'});await page.waitForFunction(()=>document.documentElement.dataset.theme==='dark');await page.emulateMedia({colorScheme:'light'});await page.waitForFunction(()=>document.documentElement.dataset.theme==='light');
  await page.goto(origin+'/screens/settings/index',{waitUntil:'load'});
  await page.evaluate(()=>window.bootstrap.Modal.getOrCreateInstance(document.getElementById('panelDesignSettingsModal')).show());
  await page.waitForFunction(()=>Boolean(window.SettingsPermissions));assert(await page.locator('#themePaletteSand').isDisabled(),'Read-only access cannot change appearance');
  editAppearance=true;await page.evaluate(()=>window.SettingsPermissions.refresh());assert(await page.locator('#themePaletteSand').isEnabled(),'Separate appearance permission enables the form');
  await page.locator('#themePaletteSand').check();assert.equal(await page.evaluate(()=>window.ThemeRuntime.getPalette()),'sand');
  await page.locator('#themeOptionDark').check();assert.equal(await page.evaluate(()=>window.ThemeRuntime.getTheme()),'dark');
  const invalidDistinct=await page.evaluate(()=>{const probe=document.createElement('div');probe.innerHTML='<input class="form-control"><input class="form-control is-invalid">';document.body.append(probe);const fields=probe.querySelectorAll('input'),different=getComputedStyle(fields[0]).borderColor!==getComputedStyle(fields[1]).borderColor;probe.remove();return different;});
  assert(invalidDistinct,'Validation errors retain a distinct border in the selected theme');
  assert.notEqual(await page.locator('#panelDesignSettingsModal .btn-close').evaluate(node=>getComputedStyle(node).filter),'none','Close action stays visible in dark appearance');
  await page.screenshot({path:path.join(output,'appearance-settings.png')});
  await page.reload({waitUntil:'load'});assert.equal(await page.evaluate(()=>window.ThemeRuntime.getPalette()),'sand');
  await page.setViewportSize({width:390,height:844});await page.goto(origin+'/screens/dialogs/index',{waitUntil:'load'});
  await page.locator('#sidebarMobileToggle').click();assert.equal(await page.locator('#sidebarMobileToggle').getAttribute('aria-expanded'),'true');await page.locator('#sidebarMobileToggle').click();assert.equal(await page.locator('#sidebarMobileToggle').getAttribute('aria-expanded'),'false');
  assert.deepEqual(runtimeErrors,[],'No uncaught browser errors');
  fs.writeFileSync(path.join(output,process.argv.includes('--interactions')?'interactions.json':process.argv.includes('--dashboard')?'dashboard-report.json':'report.json'),JSON.stringify({screens:report,runtimeErrors,interactions:'palette/theme/reload/system-mode/form/permissions/pin/hide/restore/reset/native-drag/mobile/invalid-state passed'},null,2));
  console.log('GREEN: '+report.length+' actual MVC viewport/theme checks; settings, preferences and dashboard controls passed');
 }finally{await browser.close();await new Promise(resolve=>server.close(resolve));}
}
main().catch(error=>{console.error(error);process.exitCode=1;});
