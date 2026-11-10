'use strict';
const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const source=fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/js/shared-work-access.js'),'utf8');
const tick=async()=>{for(let i=0;i<6;i++)await new Promise(setImmediate);};
class Element {
  constructor(tag='div'){this.tagName=tag;this.children=[];this.listeners={};this.selectors={};this.dataset={};this.textContent='';this.hidden=false;this.disabled=false;this.checked=false;this.value='';}
  appendChild(child){this.children.push(child);return child;}
  append(...children){children.forEach(child=>this.appendChild(child));}
  replaceChildren(...children){this.children=children;}
  addEventListener(name,fn){(this.listeners[name]??=[]).push(fn);}
  async fire(name='click'){if(name==='click'&&this.onclick)await this.onclick();for(const fn of this.listeners[name]||[])await fn({target:this,preventDefault(){}});await tick();}
  getAttribute(name){return this[name]||'';}
  querySelector(selector){return this.selectors[selector]||null;}
  querySelectorAll(selector){
    const tags=selector.split(',');
    return this.children.flatMap(child=>[...(tags.includes(child.tagName)?[child]:[]),...child.querySelectorAll(selector)]);
  }
  closest(){return this.modal||null;}
  get selectedOptions(){return this.children.filter(child=>child.value===this.value);}
  set innerHTML(value){throw new Error('Unsafe HTML: '+value);}
}
function fixture(options={}){
  const module=new Element(),root=new Element(),modal=new Element(),create=new Element('button'),requests=[],events=[];
  create.dataset.workCreate='TASK';create.disabled=true;root.dataset.workObject='TASK';root.modal=modal;
  const selectors=['status','members','manage','search','person','role','find','add','transfer','history','history-more'];
  for(const name of selectors){const el=new Element(['find','add','transfer','history-more'].includes(name)?'button':['person','role'].includes(name)?'select':name==='search'?'input':'div');root.selectors['[data-work-'+name+']']=el;root.appendChild(el);}
  root.selectors['[data-work-role]'].value='VIEWER';
  for(const name of ['toggle','grants','reload','accounts','module-status','history','history-more'])module.selectors['[data-work-'+name+']']=new Element(['toggle','reload','history-more'].includes(name)?'button':'div');
  module.selectors['[data-work-grants]'].hidden=true;
  const write=new Element('button');modal.querySelectorAll=()=>[write];
  let manage=options.manage!==false, revoked=false,failMutation=!!options.failMutation;
  const accounts=[{userId:'11',username:'<img src=x>',canRead:true,canCreateTask:true,canCreateProject:true}];
  const members=[{userId:'11',username:'owner',role:'OWNER'},{userId:'9007199254740993',username:' SysAdmin/Ю ',role:'VIEWER'}];
  const responses=new Map();
  const response=(body,status=200)=>({status,ok:status>=200&&status<300,json:async()=>structuredClone(body)});
  const fetch=async(url,opts)=>{
    requests.push({url,...opts});
    if(responses.has(url))return responses.get(url);
    if(url==='/api/shared-work/context')return response({userId:'11',systemAdmin:options.admin!==false,canCreateTask:options.create!==false,canCreateProject:true});
    if(url==='/api/shared-work-admin/access')return response(accounts);
    if(url==='/api/shared-work-admin/access/11'){Object.assign(accounts[0],JSON.parse(opts.body));return response(null,204);}
    if(url.includes('/history'))return response([{id:'9007199254740993',action:'MEMBER_SET',actorName:'<img actor>',occurredAt:'date',beforeState:'{}',afterState:'{"role":"EDITOR"}'}]);
    if(url.endsWith('/access'))return revoked?response({},404):response({kind:'TASK',id:url.split('/')[4],canWrite:options.write!==false,canManage:manage,members});
    if(url.includes('/people?'))return options.peopleDenied?response({},403):response([{userId:'9007199254740993',username:' SysAdmin/Ю '}]);
    if(opts.method==='PUT'||opts.method==='DELETE'){
      if(failMutation){failMutation=false;return response({},500);}
      if(url.includes('/owner/'))manage=false;
      return response(null,204);
    }
    throw new Error('Unexpected request '+url);
  };
  const token=new Element();token.content=options.noCsrf?'':'csrf-value';const header=new Element();header.content='X-TEST-CSRF';
  const document={
    querySelector:selector=>selector==='[data-work-module]'?module:selector==='meta[name="_csrf"]'?token:selector==='meta[name="_csrf_header"]'?header:null,
    querySelectorAll:selector=>selector==='[data-work-object]'?[root]:selector==='[data-work-create]'?[create]:[],
    createElement:tag=>new Element(tag)
  };
  const window={dispatchEvent:event=>events.push(event),confirm:()=>options.confirm!==false};
  class CustomEvent{constructor(type,{detail}={}){this.type=type;this.detail=detail;}}
  vm.runInNewContext(source,{document,window,fetch,Headers,CustomEvent,encodeURIComponent,console,BigInt});
  return {root,module,write,create,requests,events,responses,accounts,open:id=>window.SharedWorkAccess.openObject('TASK',id),revoke:()=>{revoked=true;},el:name=>root.selectors['[data-work-'+name+']'],moduleEl:name=>module.selectors['[data-work-'+name+']'],response};
}
test('capabilities enable creation and expose module controls only to admin',async()=>{
  const f=fixture();await tick();assert.equal(f.create.disabled,false);assert.equal(f.moduleEl('toggle').hidden,false);
  const normal=fixture({admin:false,create:false});await tick();assert.equal(normal.create.disabled,true);assert.equal(normal.moduleEl('toggle').hidden,true);
});
test('owner sees current roster and exact BIGINT candidate without interpreting HTML',async()=>{
  const f=fixture();await f.open('7');assert.equal(f.el('manage').hidden,false);
  assert.equal(f.el('person').value,'9007199254740993');assert.match(f.el('members').children[1].children[0].textContent,/ SysAdmin\/Ю /);
  assert.equal(f.write.disabled,false);
});
test('module grants save once and are re-read from server',async()=>{
  const f=fixture();await tick();await f.moduleEl('toggle').fire();
  const row=f.moduleEl('accounts').children[0],inputs=row.querySelectorAll('input'),save=row.querySelectorAll('button')[0];
  assert.equal(row.children[0].textContent,'<img src=x>');
  inputs[0].checked=false;await inputs[0].fire('change');assert.equal(inputs[1].checked,false);assert.equal(inputs[2].disabled,true);
  await save.fire();
  const mutation=f.requests.filter(r=>r.method==='PUT');assert.equal(mutation.length,1);
  assert.equal(mutation[0].headers.get('X-TEST-CSRF'),'csrf-value');
  assert.deepEqual(JSON.parse(mutation[0].body),{canRead:false,canCreateTask:false,canCreateProject:false});
  assert.equal(f.moduleEl('accounts').children[0].querySelectorAll('input')[0].checked,false);
  assert.match(f.moduleEl('module-status').textContent,/повторно прочитаны/);
});
test('member save uses numeric candidate and reloads object permissions',async()=>{
  const f=fixture();await f.open('7');f.el('role').value='EDITOR';await f.el('add').fire();
  const put=f.requests.find(r=>r.method==='PUT');assert.equal(put.url,'/api/shared-work/TASK/7/members/9007199254740993');
  assert.deepEqual(JSON.parse(put.body),{role:'EDITOR'});assert.equal(f.requests.filter(r=>r.url.endsWith('/access')).length,2);
  assert(f.events.some(e=>e.type==='shared-work:changed'));
});
test('member revoke is followed by a server reload',async()=>{
  const f=fixture();await f.open('7');const remove=f.el('members').children[1].querySelectorAll('button')[0];await remove.fire();
  assert(f.requests.some(r=>r.method==='DELETE'&&r.url.endsWith('/members/9007199254740993')));
  assert.equal(f.requests.filter(r=>r.url.endsWith('/access')).length,2);
});
test('owner transfer asks confirmation and removes old owner controls after reload',async()=>{
  const f=fixture();await f.open('7');await f.el('transfer').fire();await tick();
  assert(f.requests.some(r=>r.method==='PUT'&&r.url.endsWith('/owner/9007199254740993')));
  assert.equal(f.el('manage').hidden,true);assert.equal(f.write.disabled,false);
});
test('cancelled transfer sends no mutation',async()=>{
  const f=fixture({confirm:false});await f.open('7');await f.el('transfer').fire();
  assert.equal(f.requests.filter(r=>r.method==='PUT').length,0);
});
test('late response from previous object cannot overwrite current object',async()=>{
  const f=fixture();let resolve;f.responses.set('/api/shared-work/TASK/7/access',new Promise(r=>{resolve=r;}));
  const old=f.open('7');await f.open('8');
  resolve(f.response({kind:'TASK',id:'7',canWrite:false,canManage:false,members:[]}));await old;
  assert.equal(f.write.disabled,false);assert.equal(f.el('members').children.length,2);
});
test('viewer has no membership controls or eligible-user lookup',async()=>{
  const f=fixture({manage:false,write:false});await f.open('7');
  assert.equal(f.el('manage').hidden,true);assert.equal(f.write.disabled,true);
  assert.equal(f.requests.some(r=>r.url.includes('/people')),false);
});
test('current access rejection clears roster and disables writes',async()=>{
  const f=fixture();await f.open('7');f.revoke();await f.open('7');
  assert.equal(f.el('members').children.length,0);assert.equal(f.write.disabled,true);
  assert(f.events.some(e=>e.type==='shared-work:denied'));
});
test('missing CSRF sends no mutation and uncertain failure never retries automatically',async()=>{
  const f=fixture({noCsrf:true});await f.open('7');await f.el('add').fire();assert.equal(f.requests.some(r=>r.method==='PUT'),false);
  const uncertain=fixture({failMutation:true});await uncertain.open('7');await uncertain.el('add').fire();
  assert.equal(uncertain.requests.filter(r=>r.method==='PUT').length,1);assert.equal(uncertain.requests.filter(r=>r.url.endsWith('/access')).length,2);
});
test('candidate lookup revocation fails closed',async()=>{
  const f=fixture({peopleDenied:true});await f.open('7');assert.equal(f.el('members').children.length,0);assert.equal(f.write.disabled,true);
  assert.equal(f.el('add').disabled,true);
});
test('tasks and projects include access panels and updated scripts',()=>{
  for(const page of ['tasks','projects']){
    const html=fs.readFileSync(path.resolve(__dirname,'../../main/resources/templates',page,'index.html'),'utf8');
    assert(html.includes('fragments/shared-work-access :: moduleAccess'));assert(html.includes('fragments/shared-work-access :: objectAccess'));
    assert(html.includes('shared-work-access.js?v=20261109-r304'));assert(html.includes(page+'.js?v=20261109-r304'));
    assert(html.includes('data-work-write disabled'));assert(html.includes('data-work-create='));
  }
});

test('audit renders raw actor and states as text and pages string BIGINT cursor',async()=>{
  const f=fixture();
  const records=Array.from({length:50},(_,i)=>({id:String(9007199254740993n-BigInt(i)),action:'MEMBER_SET',actorName:'<img actor>',occurredAt:'date',beforeState:'{}',afterState:'{"role":"EDITOR"}'}));
  f.responses.set('/api/shared-work/TASK/7/history',f.response(records));
  const cursor=records.at(-1).id;
  f.responses.set('/api/shared-work/TASK/7/history?before='+cursor,f.response([records[0]]));
  await f.open('7');
  assert.equal(f.el('history').children.length,50);assert.equal(f.el('history-more').hidden,false);
  assert.match(f.el('history').children[0].children[0].textContent,/<img actor>/);
  await f.el('history-more').fire();
  assert.equal(f.el('history').children.length,51);assert.equal(f.el('history-more').hidden,true);
  assert(f.requests.some(r=>r.url.endsWith('?before='+cursor)));
});
test('late audit cannot populate a different object',async()=>{
  const f=fixture();let resolve;
  f.responses.set('/api/shared-work/TASK/7/history',new Promise(r=>{resolve=r;}));
  const old=f.open('7');await tick();await f.open('8');
  resolve(f.response([{id:'1',actorName:'stale actor',action:'GRANT_SET',occurredAt:'old',beforeState:'{}',afterState:'{}'}]));await old;
  assert.equal(f.el('history').children.length,1);assert.doesNotMatch(f.el('history').children[0].children[0].textContent,/stale actor/);
});
