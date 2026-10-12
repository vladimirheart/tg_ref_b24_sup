'use strict';
// R29 Node source-contract regressions. No browser, network or DB required.
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const assert=require('node:assert/strict');
const base=path.resolve(__dirname,'../../main/resources/static/js');
const names=['dialogs-shell-runtime.js','dialogs-flow-runtime.js','dialogs-actions-runtime.js','dialogs-details-history-runtime.js','dialogs-participants-runtime.js'];
const source=new Map(names.map(name=>[name,fs.readFileSync(path.join(base,name),'utf8').replace(/\r\n/g,'\n')]));
let passed=0;
async function test(name,fn){
 try{await fn();passed++;}
 catch(e){console.error('UI_CONTRACT_CASE_RED='+name);throw e;}
}
function getFunction(text,name){
 const marker='function '+name+'(';
 const at=text.indexOf(marker);
 assert(at>=0,'missing function '+name);
 const start=text.slice(Math.max(0,at-6),at)==='async '?at-6:at;
 const begin=text.indexOf('{',at);
 assert(begin>at);
 let depth=0;
 for(let i=begin;i<text.length;i++){
  const c=text[i];if(c==='{')depth++;if(c==='}')depth--;
  if(depth===0)return text.slice(start,i+1);
 }
 throw Error('unbalanced function '+name);
}
function evalFn(text,name,ctx={}){
 const helper=getFunction(text,'exactLegacyTicketId');
 const js=(name==='exactLegacyTicketId'?'':helper+'\n')+getFunction(text,name)+'\n'+name+';';
 return vm.runInNewContext(js,ctx);
}
(async()=>{
 for(const name of names){
  await test('exact_helper_'+name,()=>{
   const body=source.get(name);
   const f=evalFn(body,'exactLegacyTicketId');
   assert.equal(f(' T-EXACT '),' T-EXACT ');
   assert.equal(f('T-EXACT'),'T-EXACT');
   assert.notEqual(f(' T-EXACT '),f('T-EXACT'));
   assert.equal(f(' null '),' null ');
   for(const invalid of [null,undefined,'',' ','\t\n'])assert.equal(f(invalid),'');
  });
 }
 await test('task_create_surface_preserves_id',()=>{
  const state={href:''};let draft;
  const ctx={window:{location:state},setTaskDraft:v=>{draft=v;},
   buildTaskCreateUrl:(id)=>'/tasks?ticketId='+encodeURIComponent(id)};
  const f=evalFn(source.get('dialogs-shell-runtime.js'),'openTaskCreateSurface',ctx);
  f(' T-EXACT ',' Example Client ',' NUM-3 ');
  assert.equal(draft.ticketId,' T-EXACT ');
  assert.equal(draft.client,'Example Client');
  assert.equal(draft.requestNumber,'NUM-3');
  assert.equal(state.href,'/tasks?ticketId=%20T-EXACT%20');
  f('T-EXACT','client','NR');
  assert.equal(draft.ticketId,'T-EXACT');
  assert.equal(state.href,'/tasks?ticketId=T-EXACT');
 });
 await test('modal_popstate_does_not_alias_active',()=>{
  const opened=[];
  let active='T-EXACT';
  const ctx={elements:{detailsModalEl:{classList:{contains:()=>true}}},
   options:{workspaceEnabled:false,getRouteChannelId:()=> '5',getActiveDialogChannelId:()=> '5',
    getActiveDialogTicketId:()=>active,findDialogRowByTicketId:(id)=>({dataset:{ticketId:id}}),
    setActiveDialogRow:()=>{},openDialogDetails:(id)=>{opened.push(id);return Promise.resolve();},
    debugLog:()=>{}},parseDialogRouteTicketId:()=> ' T-EXACT ',
   window:{location:{search:'?channelId=5'}},console:{error:()=>{}}};
  const f=evalFn(source.get('dialogs-flow-runtime.js'),'handleDialogModalPopstate',ctx);
  f();assert.deepEqual(opened,[' T-EXACT ']);
  active=' T-EXACT ';
  f();assert.deepEqual(opened,[' T-EXACT ']);
 });
 await test('actions_resolve_details_id_exact',()=>{
  let current=' T-EXACT ';
  const ctx={getActiveDialogState:()=>({ticketId:current}),elements:{detailsMeta:{textContent:'#T-EXACT'}},options:{setActiveDialogTicketId:()=>{}}};
  const f=evalFn(source.get('dialogs-actions-runtime.js'),'resolveDetailsTicketId',ctx);
  assert.equal(f(),' T-EXACT ');
  current='T-EXACT';assert.equal(f(),'T-EXACT');
  const body=source.get('dialogs-actions-runtime.js');
  assert.match(body,/exactLegacyTicketId\(row\.dataset\.ticketId\) === exactLegacyTicketId\(activeDialogState\.ticketId\)/);
  assert.match(body,/exactLegacyTicketId\(activeDialogState\.ticketId\) === exactLegacyTicketId\(ticketId\)/);
 });
 await test('history_delete_endpoint_encodes_padded_id',async()=>{
  const urls=[];
  class Button{constructor(){this.dataset={messageId:'17',action:'delete'};}}
  const ctx={getActiveDialogState:()=>({ticketId:' T-EXACT '}),
   HTMLElement:Button,closeHistoryActionMenus:()=>{},window:{confirm:()=>true},
   fetch:async(url)=>{urls.push(url);return{ok:true,json:async()=>({success:true})};},
   refreshHistory:async()=>{}};
  const f=evalFn(source.get('dialogs-details-history-runtime.js'),'handleHistoryAction',ctx);
  await f(new Button());
  assert.deepEqual(urls,['/api/dialog-ticket/delete?ticketId=%20T-EXACT%20']);
  const body=source.get('dialogs-details-history-runtime.js');
  assert.match(body,/exactLegacyTicketId\(sendOptions\.ticketId \|\| activeDialogState\.ticketId \|\| activeWorkspaceState\.ticketId\)/);
  assert.match(body,/fetch\(`\/api\/dialogs\/\$\{encodeURIComponent\(ticketId\)\}\/media`/);
 });
 await test('participants_resolve_active_id_exact',()=>{
  let current=' T-EXACT ';
  const f=evalFn(source.get('dialogs-participants-runtime.js'),'getActiveDialogTicketId',{
   options:{getActiveDialogTicketId:()=>current}});
  assert.equal(f(),' T-EXACT ');
  current='T-EXACT';assert.equal(f(),'T-EXACT');
  current='   ';assert.equal(f(),'');
 });
 if(passed!==10)throw Error('TEST_COUNT_RED:'+passed);
 console.log('UI_EXACT_ID_TESTS_GREEN='+passed);
 console.log('UI_EXACT_ID_TEST_RESULT=GREEN');
})().catch(err=>{console.error('UI_EXACT_ID_TEST_FATAL='+String(err&&err.message||err));process.exitCode=1;});
