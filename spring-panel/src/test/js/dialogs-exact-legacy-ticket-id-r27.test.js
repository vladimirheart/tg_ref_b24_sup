'use strict';
// Source-contract regression for R27, runs without browser / external services.
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const assert=require('node:assert/strict');
const base=path.resolve(__dirname,'../../main/resources/static/js');
const names=['dialogs.js','dialogs-workspace-runtime.js','dialogs-ai-runtime.js','dialogs-macro-runtime.js','dialogs-my-dialogs-runtime.js'];
const source=new Map(names.map(name=>[name,fs.readFileSync(path.join(base,name),'utf8').replace(/\r\n/g,'\n')]));
let passed=0;
function test(name,fn){try{fn();passed++;}catch(e){console.error('UI_CONTRACT_CASE_RED='+name);process.exitCode=1;throw e;}}
function getFunction(text,name){
 const start=text.indexOf('function '+name+'(');
 assert(start>=0,'missing function '+name);
 const begin=text.indexOf('{',start);
 assert(begin>start);
 let depth=0;
 for(let i=begin;i<text.length;i++){
  const c=text[i];if(c==='{')depth++;if(c==='}')depth--;
  if(depth===0)return text.slice(start,i+1);
 }
 throw new Error('unbalanced '+name);
}
function evalFn(text,name,ctx){
 const helper=getFunction(text,'exactLegacyTicketId');
 const js=(name==='exactLegacyTicketId'?'':helper+'\n')+getFunction(text,name)+'\n'+name+';';
 return vm.runInNewContext(js,ctx||{});
}
for(const name of names){
 test('helper_'+name,()=>{
  const body=source.get(name);
  assert.match(body,/function exactLegacyTicketId\(value\)/);
  const f=evalFn(body,'exactLegacyTicketId');
  assert.equal(f(' T-EXACT '),' T-EXACT ');
  assert.equal(f('T-EXACT'),'T-EXACT');
  assert.notEqual(f(' T-EXACT '),f('T-EXACT'));
  for(const invalid of [null,undefined,'','  ','\t\n'])assert.equal(f(invalid),'');
  assert.equal(f(' null '),' null ');
 });
}
test('workspace_contract_exact_gate',()=>{
 const text=source.get('dialogs.js');
 const f=evalFn(text,'resolveWorkspaceContractTicketId',{
  activeDialogTicketId:' T-EXACT ', activeWorkspaceTicketId:' T-EXACT ',
  activeWorkspacePayload:{conversation:{ticketId:' T-EXACT '}}
 });
 assert.equal(f(' T-EXACT '),' T-EXACT ');
 assert.equal(f('T-EXACT'),null);
 assert.equal(f('   '),null);
 assert.equal(f(null),' T-EXACT ');
});
test('row_selection_no_alias',()=>{
 const text=source.get('dialogs.js');
 const plain={dataset:{ticketId:'T-EXACT',channelId:'3'}};
 const padded={dataset:{ticketId:' T-EXACT ',channelId:'3'}};
 const f=evalFn(text,'findDialogRowByTicketId',{
  rowsList:()=>[plain,padded],
  normalizeChannelId:v=>(v===null||v===undefined)?null:(String(v).trim()||null)
 });
 assert.equal(f(' T-EXACT ',3),padded);
 assert.equal(f('T-EXACT',3),plain);
 assert.equal(f('   ',3),null);
 assert.equal(f('DIFFERENT',3),null);
});
test('draft_storage_keys_do_not_alias',()=>{
 const text=source.get('dialogs-workspace-runtime.js');
 const f=evalFn(text,'getWorkspaceDraftStorageKey',{options:{draftStoragePrefix:'iguana:test:'}});
 assert.equal(f(' T-EXACT '),'iguana:test: T-EXACT ');
 assert.equal(f('T-EXACT'),'iguana:test:T-EXACT');
 assert.notEqual(f(' T-EXACT '),f('T-EXACT'));
});
if(passed!==8)throw new Error('TEST_COUNT_RED');
console.log('UI_EXACT_ID_TESTS_GREEN='+passed);
console.log('UI_EXACT_ID_TEST_RESULT=GREEN');
