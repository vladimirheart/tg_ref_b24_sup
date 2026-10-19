'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
class Element {
  constructor(tag = 'div') { this.tagName=tag; this.children=[]; this.events={}; this.value=''; this.hidden=false; this.disabled=false; this._text=''; }
  set textContent(value) { this._text=String(value); this.children=[]; }
  get textContent() { return this._text; }
  set innerHTML(_) { throw new Error('Untrusted HTML'); }
  append(...nodes) { this.children.push(...nodes); }
  appendChild(node) { this.children.push(node); return node; }
  prepend(...nodes) { this.children.unshift(...nodes); }
  replaceChildren(...nodes) { this._text=''; this.children=[...nodes]; }
  addEventListener(name, handler) { this.events[name]=handler; }
  fire(name) { return this.events[name]?.({preventDefault(){}}); }
}
function harness() {
  const fields=new Map(), pending=[], streams=[], timers=new Map(), lifecycle={}; let timerId=0;
  const page=new Element(); page.querySelector=selector=>{
    if(!fields.has(selector)) fields.set(selector,new Element()); return fields.get(selector);
  };
  class Source {
    constructor(url) { this.url=url;this.events={};this.closed=false;streams.push(this); }
    addEventListener(name, handler) { this.events[name]=handler; }
    close() { this.closed=true; }
    fire(name,data) { this.events[name]?.({data}); }
  }
  vm.runInNewContext(fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/js/general-dialogs.js'),'utf8'),{
    document:{querySelector:()=>page,createElement:tag=>new Element(tag)},
    EventSource:Source,URLSearchParams,encodeURIComponent,
    fetch:(url,options)=>new Promise(resolve=>pending.push({url,options,resolve})),
    setTimeout:handler=>{timers.set(++timerId,handler);return timerId;},
    clearTimeout:id=>timers.delete(id),
    addEventListener:(name,handler)=>{lifecycle[name]=handler;}
  });
  return {field:selector=>page.querySelector(selector),pending,source:streams[0],timers,lifecycle,
    flush(){const work=[...timers.values()];timers.clear();for(const handler of work)handler();}};
}
const tick=()=>new Promise(resolve=>setImmediate(resolve));
async function respond(request,body,status=200) {
  assert.ok(request);request.resolve({ok:status>=200&&status<300,status,json:async()=>body});await tick();
}
const id='a'.repeat(32), ticket={ticketPublicId:id,ticketId:'RAW',clientName:'Client',status:'open'};
async function page(h) { await respond(h.pending.shift(),{tickets:[ticket],hasMore:true}); }
async function card(h,message='initial') {
  await respond(h.pending.shift(),ticket);
  await respond(h.pending.shift(),{ticket,entries:[{id:7,sender:'client',message}],hasMore:false});
  await respond(h.pending.shift(),{ticket,answers:[],relationVerified:true});
}
(async()=>{
  const h=harness();
  assert.equal(h.source.url,'/api/general-tickets/events/stream');
  await page(h);
  const opening=h.field('#generalTicketRows').children[0].children[4].children[0].fire('click');
  await card(h);await opening;
  assert.equal(h.field('#generalTicketCard').hidden,false);
  for(const data of ['{bad','{}',JSON.stringify({ticketPublicId:'not-public'})]) h.source.fire('general_tickets_changed',data);
  assert.equal(h.timers.size,0);
  h.field('#generalTicketSearch').value='kept filter';
  h.source.fire('general_tickets_changed',JSON.stringify({ticketPublicId:id}));
  h.source.fire('general_tickets_changed',JSON.stringify({ticketPublicId:'b'.repeat(32)}));
  assert.equal(h.timers.size,1);
  h.flush();assert.equal(h.pending.length,1);assert.ok(h.pending[0].url.includes('q=kept+filter'));
  await page(h);assert.equal(h.pending.length,3);await card(h,'fresh <script>text</script>');
  assert.equal(h.field('#generalTicketCard').hidden,false);
  assert.equal(h.field('[data-general-history]').children[0].children[1].textContent,'fresh <script>text</script>');
  h.source.fire('connected');h.flush();const stale=h.pending.shift();
  h.source.fire('error');
  assert.equal(h.field('#generalTicketRows').children.length,0);
  assert.equal(h.field('#generalTicketCard').hidden,true);
  await respond(stale,{tickets:[ticket],hasMore:false});
  assert.equal(h.field('#generalTicketRows').children.length,0);
  h.source.fire('connected');h.flush();await page(h);
  assert.equal(h.field('#generalTicketRows').children.length,1);
  h.source.fire('general_tickets_changed',JSON.stringify({ticketPublicId:id}));h.flush();
  const revoked=h.pending.shift();
  h.source.fire('general_access_revoked');
  assert.equal(h.source.closed,true);assert.equal(h.timers.size,0);
  await respond(revoked,{tickets:[ticket],hasMore:false});
  assert.equal(h.field('#generalTicketRows').children.length,0);
  assert.equal(h.field('[data-general-client]').textContent,'');
  h.source.fire('connected');h.source.fire('general_tickets_changed',JSON.stringify({ticketPublicId:id}));
  assert.equal(h.timers.size,0);
  const leaving=harness();await page(leaving);
  leaving.source.fire('connected');assert.equal(leaving.timers.size,1);
  leaving.lifecycle.pagehide();assert.equal(leaving.source.closed,true);assert.equal(leaving.timers.size,0);
  console.log('GENERAL_TICKET_REALTIME_UI_R134_RESULT=GREEN');
})().catch(error=>{console.error(error);process.exitCode=1;});
