'use strict';
const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const root=path.resolve(__dirname,'../../main/resources/static/js');
const source=fs.readFileSync(path.join(root,'dialogs-workspace-runtime.js'),'utf8');
const start=source.indexOf('    async function sendWorkspaceReply() {'),end=source.indexOf('    function mergeWorkspacePayload(',start);
assert(start>=0&&end>start);
async function send(response,viewFailure=false){
  const window={};vm.runInNewContext(fs.readFileSync(path.join(root,'panel-errors.js'),'utf8'),{window});
  const requests=[],messages=[],states=[],text={value:'Сообщение',disabled:false};
  const active={composerTicketId:' T/100?&%# Ю ',readonlyMode:false,row:{dataset:{responsible:'fixture'}},payload:{}};
  const execute=vm.runInNewContext(source.slice(start,end)+'\nsendWorkspaceReply',{
    window,elements:{workspaceComposerText:text,workspaceComposerSend:{}},state:{pendingReplySequence:0},encodeURIComponent,
    getActiveWorkspaceState:()=>active,saveWorkspaceDraft(){},resetWorkspaceReplyTarget(){},
    appendWorkspaceMessage:item=>messages.push(item),updateWorkspaceMessageDeliveryState:(_id,status)=>states.push(status),
    options:{updateRowStatus(){if(viewFailure)throw Error('DOM refresh failed');}},
    fetch:async(url,options)=>{requests.push({url,options});if(response instanceof Error)throw response;return response;}
  });
  await execute();for(let i=0;i<4;i++)await new Promise(setImmediate);
  assert.equal(requests.length,1);assert.equal(requests[0].options.method,'POST');
  assert.equal(new URL(requests[0].url,'https://panel.test').searchParams.get('ticketId'),active.composerTicketId);
  return {requests,messages,states};
}
test('proxy upload rejection is readable and never retries a send',async()=>{
  const result=await send(new Response('<html>413</html>',{status:413}));
  assert.deepEqual(result.states,['failed']);assert.match(result.messages.at(-1).messageText,/Размер запроса/);
});
test('timeout proxy, network and malformed reply show unknown delivery rather than not sent',async()=>{
  for(const response of [new Response('<html>504</html>',{status:504}),new TypeError('Failed to fetch'),new Response('not JSON')]){
    const result=await send(response);assert.deepEqual(result.states,['unknown']);
    assert.match(result.messages.at(-1).messageText,/не подтвержден/);
  }
});
test('explicit provider rejection stays a rejection with the original useful reason',async()=>{
  const result=await send(new Response('{"success":false,"error":"Telegram: слишком длинный текст сообщения"}',{status:400}));
  assert.deepEqual(result.states,['failed']);assert.match(result.messages.at(-1).messageText,/длинный текст/);
});
test('acknowledged message stays sent when refreshing the interface fails',async()=>{
  const result=await send(new Response('{"success":true}'),true);
  assert.deepEqual(result.states,['sent']);assert.match(result.messages.at(-1).messageText,/Сообщение отправлено/);
});
