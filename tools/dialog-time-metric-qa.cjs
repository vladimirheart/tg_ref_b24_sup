/* Real MVC dialog markup, production details runtime/CSS, loopback fixture data. */
const fs=require('node:fs'),path=require('node:path'),http=require('node:http'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..'),resources=path.join(root,'spring-panel/src/main/resources');
const capture=path.join(root,'spring-panel/target/dialog-time-rendered/dialogs-base.html'),out=path.join(root,'spring-panel/target/dialog-time-metric-qa');
const scenarios=[{name:'good',minutes:17,color:'#d1fae5'},{name:'warning',minutes:45,color:'#fef3c7'},{name:'danger',minutes:90,color:'#fee2e2'},
 {name:'custom-dark',minutes:17,color:'#172336'},{name:'custom-white',minutes:17,color:'#ffffff'},{name:'custom-mid',minutes:17,color:'#777777'},
 {name:'short-hex',minutes:17,color:'#abc'},{name:'invalid',minutes:17,color:'red; color: transparent'}];
function luminance(rgb){const c=rgb.match(/[\d.]+/g).slice(0,3).map(Number).map(v=>v/255).map(v=>v<=.04045?v/12.92:((v+.055)/1.055)**2.4);return c[0]*.2126+c[1]*.7152+c[2]*.0722;}
function contrast(fg,bg){const a=luminance(fg),b=luminance(bg);return(Math.max(a,b)+.05)/(Math.min(a,b)+.05);}
async function main(){
 assert(fs.existsSync(capture),'Run DialogsControllerWebMvcTest capture first');fs.mkdirSync(out,{recursive:true});
 let minutes=17,missing=false;
 const server=http.createServer((req,res)=>{
  const url=new URL(req.url,'http://localhost'),file=url.pathname==='/dialogs'?capture:path.resolve(resources,'static',url.pathname.slice(1));
  if(file!==capture&&!file.startsWith(path.join(resources,'static')+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){res.writeHead(404);res.end('{}');return;}
  const ext=path.extname(file);res.setHeader('Content-Type',({'.html':'text/html; charset=utf-8','.css':'text/css','.js':'application/javascript','.woff2':'font/woff2','.svg':'image/svg+xml'})[ext]||'application/octet-stream');
  if(['.html','.css','.js'].includes(ext))res.end(fs.readFileSync(file,'utf8').replaceAll('\r\n','\n'));else fs.createReadStream(file).pipe(res);
 });
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));const origin='http://127.0.0.1:'+server.address().port;
 const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright'),browser=await chromium.launch({headless:true});
 const report=[],errors=[];
 try{
  const context=await browser.newContext({viewport:{width:1280,height:838},reducedMotion:'reduce'});
  await context.route('**/*',route=>{
   const url=new URL(route.request().url());if(url.origin!==origin)return route.abort();
   if(url.pathname==='/api/dialog-ticket')return route.fulfill({json:{success:true,summary:{ticketId:'contrast-ticket',channelId:1,clientName:'Клиент',status:'closed',business:'СушиВёсла',channelName:'Telegram',responsible:'admin',createdAt:missing?null:'2026-11-19T11:26:00Z',resolvedAt:new Date(Date.parse('2026-11-19T11:26:00Z')+minutes*60000).toISOString()},history:[],categories:[]}});
   if(url.pathname.startsWith('/api/')||url.pathname.startsWith('/profile/'))return route.fulfill({json:{success:true,items:[],dialogs:[],data:[],settings:{},available:false}});
   return route.continue();
  });
  const page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));
  await page.goto(origin+'/dialogs');await page.waitForFunction(()=>window.DialogsDetailsRuntime&&window.ThemeRuntime);
  await page.evaluate(()=>{
   const state={context:{}};const el=document.getElementById('dialogDetailsModal');
   window.metricQaRuntime=window.DialogsDetailsRuntime.createRuntime({elements:{detailsModalEl:el,detailsMetrics:document.getElementById('dialogDetailsMetrics')},
    getActiveDialogState:()=>state,setActiveDialogTicketId:id=>{state.ticketId=id;},setActiveDialogChannelId:id=>{state.channelId=id;},
    showModalSafe:()=>bootstrap.Modal.getOrCreateInstance(el).show(),formatTimestamp:value=>value||'—'});
   document.getElementById('dialogDetailsMetricsSection').open=true;
  });
  const baseline=process.argv.includes('--baseline');
  for(const palette of baseline?['north']:['north','neo','forest','sand','plum'])for(const theme of baseline?['dark']:['dark','light'])for(const scenario of baseline?[scenarios[0]]:scenarios){
   minutes=scenario.minutes;
   await page.evaluate(async({palette,theme,scenario})=>{
    window.ThemeRuntime.setPalette(palette);window.ThemeRuntime.setTheme(theme);
    const colors={good:'#d1fae5',warning:'#fef3c7',danger:'#fee2e2'};
    colors[['warning','danger'].includes(scenario.name)?scenario.name:'good']=scenario.color;
    window.DIALOG_CONFIG={...(window.DIALOG_CONFIG||{}),time_metrics:{good_limit:30,warning_limit:60,colors}};
    await window.metricQaRuntime.openDialogDetails('contrast-ticket',null,{channelId:1});
   },{palette,theme,scenario});
   const badge=page.locator('#dialogDetailsMetrics .dialog-time-metric-badge');await badge.waitFor({state:'visible'});
   const metric=await badge.evaluate(el=>({text:el.textContent,foreground:getComputedStyle(el).color,background:getComputedStyle(el).backgroundColor}));
   const ratio=contrast(metric.foreground,metric.background);report.push({palette,theme,...scenario,...metric,contrast:ratio});
   if(palette==='north'&&theme==='dark'&&['good','custom-dark'].includes(scenario.name))await page.screenshot({path:path.join(out,`${palette}-${theme}-${scenario.name}.png`)});
   assert.equal(metric.text,scenario.minutes<60?`${scenario.minutes} мин`:'1 ч 30 мин');
   assert(ratio>=4.5,`Unreadable duration: ${JSON.stringify(report.at(-1))}`);
   if(scenario.name==='custom-dark')assert.equal(metric.foreground,'rgb(255, 255, 255)');
   const defaults={good:'rgb(209, 250, 229)',warning:'rgb(254, 243, 199)',danger:'rgb(254, 226, 226)',invalid:'rgb(209, 250, 229)'};
   if(defaults[scenario.name])assert.equal(metric.background,defaults[scenario.name]);
  }
  missing=true;await page.evaluate(()=>window.metricQaRuntime.openDialogDetails('contrast-ticket',null,{channelId:1}));
  assert.equal(await page.locator('#dialogDetailsMetrics .dialog-time-metric-badge').count(),0);
  assert.deepEqual(errors,[]);fs.writeFileSync(path.join(out,'report.json'),JSON.stringify({report,errors,missingTimestamp:true},null,2));
  console.log(`GREEN: ${report.length} computed contrast checks, five palettes, light/dark; missing timestamp unchanged.`);
 }finally{await browser.close();await new Promise(resolve=>server.close(resolve));}
}
main().catch(e=>{console.error(e);process.exitCode=1;});
