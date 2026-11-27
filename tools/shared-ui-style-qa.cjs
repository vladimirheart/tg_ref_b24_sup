/* Compare production styles against a pinned Git baseline. Loopback assets only. */
'use strict';
const fs=require('node:fs'),path=require('node:path'),http=require('node:http'),cp=require('node:child_process'),assert=require('node:assert/strict'),crypto=require('node:crypto');
const root=path.resolve(__dirname,'..'),assets=path.join(root,'spring-panel/src/main/resources/static');
const baseline=process.env.UI_BASELINE_REF;if(!baseline)throw Error('Set UI_BASELINE_REF to the commit being reviewed');
assert(/^[0-9a-f]{40}$/.test(baseline));
const out=path.join(root,'spring-panel/target/shared-ui-style-qa');fs.mkdirSync(out,{recursive:true});
const cache=new Map();
function oldAsset(name){if(!cache.has(name))cache.set(name,cp.execFileSync('git',['show',baseline+':spring-panel/src/main/resources/static/'+name],{cwd:root,maxBuffer:4*1024*1024,stdio:['ignore','pipe','pipe']}));return cache.get(name);}
const props=('color background-color background-image border-top-color border-top-width border-top-style border-radius box-shadow filter opacity display position width min-width max-width height min-height max-height padding-top padding-right padding-bottom padding-left margin-top margin-bottom font-family font-size font-weight letter-spacing line-height text-transform outline-color outline-width overflow-x overflow-y transform flex-direction align-items justify-content gap').split(' ');
const sample=`<main class="main-content container-fluid"><div class="page-shell">
<header id="hero" class="page-header-card"><span id="kicker" class="page-kicker">Рабочий раздел</span><h1 id="title" class="page-title">Проверка компонентов</h1><p id="subtitle" class="page-subtitle">Описание</p></header>
<section id="card" class="card"><div id="cardHeader" class="card-header">Карточка</div><div id="cardBody" class="card-body">
<label id="label" class="form-label">Название</label><input id="input" class="form-control" value="Текст"><input id="invalid" class="form-control is-invalid" value="Ошибка"><input id="valid" class="form-control is-valid" value="Верно"><input id="disabledInput" class="form-control" disabled value="Недоступно">
<select id="select" class="form-select"><option>Значение</option></select><select id="disabledSelect" class="form-select" disabled><option>Недоступно</option></select><textarea id="textarea" class="form-control">Описание</textarea>
<div class="form-check"><input id="checkbox" class="form-check-input" type="checkbox" checked><label class="form-check-label" for="checkbox">Разрешено</label></div>
<button id="primary" class="btn btn-primary">Сохранить</button><button id="secondary" class="btn btn-outline-secondary">Отмена</button><button id="disabledButton" class="btn btn-primary" disabled>Недоступно</button>
<table id="table" class="table table-sm table-hover"><thead><tr><th id="th">Название</th></tr></thead><tbody><tr><td id="td">Запись</td></tr><tr class="table-warning"><td id="warningTd">Предупреждение</td></tr></tbody></table>
<div class="dropdown"><div id="dropdown" class="dropdown-menu show" style="position:relative"><button id="dropdownItem" class="dropdown-item">Изменить</button><button id="activeDropdownItem" class="dropdown-item active">Выбрано</button></div></div>
<ul id="tabs" class="nav nav-tabs"><li class="nav-item"><button id="tab" class="nav-link active">Основное</button></li></ul>
<div class="accordion"><section id="accordionItem" class="accordion-item"><h2 class="accordion-header"><button id="accordionButton" class="accordion-button" aria-expanded="true">Раздел</button></h2><div class="accordion-collapse collapse show"><div id="accordionBody" class="accordion-body">Содержимое</div></div></section></div>
</div><footer id="cardFooter" class="card-footer">Подвал</footer></section>
<div id="genericModal" class="modal show" style="display:block;position:relative"><div class="modal-dialog"><div id="modalContent" class="modal-content"><header id="modalHeader" class="modal-header"><h5 id="modalTitle" class="modal-title">Окно</h5><button id="close" class="btn-close" aria-label="Закрыть"></button></header><div id="modalBody" class="modal-body">Содержимое</div><footer id="modalFooter" class="modal-footer"><button class="btn btn-primary">Сохранить</button></footer></div></div></div>
<div id="botTemplateEditorModal"><div id="questionCard" class="card bot-question-card"><div class="card-body">Вопрос</div></div></div>
</div></main>`;
function html(side,page,width){const prefix='/'+side+'/',version=width===390?'5.3.0':'5.3.3';return `<!doctype html><html lang="ru"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<link rel="stylesheet" href="${prefix}vendor/bootstrap/${version}/bootstrap.min.css">
${['style','app','sidebar',...(page==='settings'?['settings']:[]),...(['settings','users'].includes(page)?['permission-editor']:[])].map(name=>`<link rel="stylesheet" href="${prefix}css/${name}.css">`).join('')}
<script src="${prefix}js/ui-preferences.js"></script><script src="${prefix}js/theme.js"></script></head>
<body data-ui-page="${page}" data-ui-visual="neutral-ops" data-ui-density="${width===390?'compact':'comfortable'}" class="density-${width===390?'compact':'comfortable'}">${sample}</body></html>`;}
async function main(){
 const server=http.createServer((req,res)=>{
  const url=new URL(req.url,'http://local');
  if(url.pathname==='/fixture'){res.setHeader('Content-Type','text/html; charset=utf-8');res.end(html(url.searchParams.get('side'),url.searchParams.get('page'),Number(url.searchParams.get('width'))));return;}
  const m=url.pathname.match(/^\/(base|next)\/(.+)$/);if(!m){res.writeHead(404);res.end();return;}
  const file=path.resolve(assets,m[2]);if(!file.startsWith(assets+path.sep)){res.writeHead(400);res.end();return;}
  try{const bytes=m[1]==='base'?oldAsset(m[2]):fs.readFileSync(file),ext=path.extname(file);res.setHeader('Content-Type',({'.css':'text/css','.js':'text/javascript','.woff':'font/woff','.woff2':'font/woff2'})[ext]||'application/octet-stream');res.end(['.css','.js'].includes(ext)?bytes.toString('utf8').replace(/\r\n/g,'\n'):bytes);}catch(error){res.writeHead(404);res.end();}
 });
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));const origin='http://127.0.0.1:'+server.address().port;
 const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');let browser;const report=[];
 try{
  browser=await chromium.launch({headless:true,executablePath:process.env.PLAYWRIGHT_BROWSER_EXECUTABLE});
  for(const uiPage of ['settings','users','dialogs','dashboard','tasks','clients','analytics','knowledge_base'])for(const theme of ['light','dark'])for(const width of [1440,390]){
   const snapshots={},errors=[];
   for(const side of ['base','next']){
    const context=await browser.newContext({viewport:{width,height:900},reducedMotion:'reduce'});
    await context.route('**/*',route=>new URL(route.request().url()).origin===origin?route.continue():route.abort());
    const page=await context.newPage();page.on('pageerror',error=>errors.push(error.message));page.on('response',response=>{if(response.status()===404)errors.push(response.url());});
    await page.goto(`${origin}/fixture?side=${side}&page=${uiPage}&width=${width}`);await page.evaluate(theme=>{ThemeRuntime.setPalette('neo');ThemeRuntime.setTheme(theme);},theme);
    snapshots[side]=await page.evaluate(props=>Object.fromEntries([...document.querySelectorAll('[id]')].map(node=>{
     const style=getComputedStyle(node),values=Object.fromEntries(props.map(prop=>[prop,style.getPropertyValue(prop)]));
     if(node.id==='accordionButton'){const pseudo=getComputedStyle(node,'::after');values['arrow-content']=pseudo.content;values['arrow-transform']=pseudo.transform;}
     return[node.id,values];
    })),props);
    if(side==='next'&&uiPage==='settings')await page.screenshot({path:path.join(out,`${theme}-${width}.png`),fullPage:true});
    await context.close();
   }
   const differences=[],permitted=[];
   for(const [id,values]of Object.entries(snapshots.base))for(const [prop,value]of Object.entries(values))if(value!==snapshots.next[id][prop]){
    const difference={id,prop,base:value,next:snapshots.next[id][prop]};
    if(id==='close'&&theme==='dark'&&['background-image','filter'].includes(prop)||id==='questionCard'&&uiPage==='settings'&&prop==='box-shadow')permitted.push(difference);else differences.push(difference);
   }
   report.push({page:uiPage,theme,width,properties:Object.values(snapshots.base).reduce((n,v)=>n+Object.keys(v).length,0),differences,permitted,errors,sha256:crypto.createHash('sha256').update(JSON.stringify(snapshots.next)).digest('hex')});
   if(differences.length||errors.length)console.log(JSON.stringify(report.at(-1),null,2));
   console.log(`${differences.length||errors.length?'FAIL':'PASS'} ${uiPage} ${theme} ${width}`);
  }
  fs.writeFileSync(path.join(out,'report.json'),JSON.stringify({baseline,report},null,2));
  assert(report.every(r=>!r.differences.length&&!r.errors.length),'Computed style changes outside the reviewed close-icon/question-card fixes');
 }finally{await browser?.close();await new Promise(resolve=>server.close(resolve));}
}
main().catch(error=>{console.error(error);process.exitCode=1;});
