'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
class Element {
  constructor(){this.children=[];this.textContent='';this.hidden=false;this.disabled=false;}
  replaceChildren(){this.children=[];}
  append(...items){this.children.push(...items);}
  set innerHTML(value){throw Error('Unsafe audit markup '+value);}
}
async function main(){
  const source=fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/js/business-dialogs.js'),'utf8');
  const template=fs.readFileSync(path.resolve(__dirname,'../../main/resources/templates/businesses/dialogs.html'),'utf8');
  for(const selector of ['data-ticket-work-events','data-ticket-work-status','data-ticket-work-older'])assert(template.includes(selector));
  assert(source.includes('clearWorkHistory();'));assert(source.includes('loadWorkHistory(detail, expectedGeneration, request);'));
  const clearStart=source.indexOf('  function clearWorkHistory('),clearEnd=source.indexOf('  function announce(',clearStart);
  const start=source.indexOf('  function workOwnerMatches('),end=source.indexOf('  async function loadSpans(',start);
  assert(clearStart>0&&clearEnd>clearStart&&start>0&&end>start);
  const fields=new Map(),pending=[],ticket={ticketPublicId:'a'.repeat(32),userId:901,ticketId:' raw ',businessId:11};
  const owner={ticketPublicId:ticket.ticketPublicId,userId:'901',ticketId:' raw ',businessId:'11'};
  const event={revision:'9007199254740993',action:'close',fromStatus:'open',toStatus:'resolved',previousCategories:['Old'],categories:['<img src=x>'],actor:'<script>actor</script>',createdAt:'date'};
  const history={owner,revision:'9007199254740994',latestEventRevision:'9007199254740993',events:[event],hasMore:true,nextBeforeRevision:'9007199254740993'};
  const context={card:{querySelector(selector){if(!fields.has(selector))fields.set(selector,new Element());return fields.get(selector);}},
    generation:1,cardRequest:7,currentTicketPublicId:ticket.ticketPublicId,workTicket:null,workRevision:null,workLatest:null,
    workBefore:null,workLoading:false,encodeURIComponent,ticketTime:value=>value,statusLabel:value=>value,
    document:{createElement:()=>new Element()},getJson:url=>new Promise((resolve,reject)=>pending.push({url,resolve,reject}))};
  const api=vm.runInNewContext(`${source.slice(clearStart,clearEnd)}\n${source.slice(start,end)}\n({loadWorkHistory,loadOlderWorkEvents,clearWorkHistory})`,context);
  const events=()=>fields.get('[data-ticket-work-events]'),status=()=>fields.get('[data-ticket-work-status]'),button=()=>fields.get('[data-ticket-work-older]');
  async function load(value=history){const call=api.loadWorkHistory(ticket,context.generation,context.cardRequest);assert(pending[0].url.endsWith('/work/history?limit=50'));pending.shift().resolve(value);await call;}
  await load();assert.equal(events().children.length,1);assert.match(events().children[0].textContent,/9007199254740993.*<script>actor<\/script>.*Закрыл.*open → resolved.*Old → <img src=x>/);
  assert.equal(button().hidden,false);
  const older=api.loadOlderWorkEvents();assert.equal(button().disabled,true);assert(pending[0].url.endsWith('before=9007199254740993'));
  await api.loadOlderWorkEvents();assert.equal(pending.length,1);
  pending.shift().resolve({...history,events:[{...event,action:'reopen',fromStatus:'resolved',toStatus:'pending',revision:'9007199254740992'}],hasMore:false,nextBeforeRevision:null});
  await older;assert.equal(events().children.length,2);assert.match(events().children[1].textContent,/Открыл повторно/);assert.equal(button().hidden,true);assert.equal(button().disabled,false);
  for(const mismatch of [{revision:'9007199254740995'},{latestEventRevision:'9007199254740994'},{owner:{...owner,businessId:'22'}},{owner:{...owner,userId:'902'}},{owner:{...owner,ticketId:'raw'}},{owner:{...owner,ticketPublicId:'b'.repeat(32)}}]){
    await load();const call=api.loadOlderWorkEvents();pending.shift().resolve({...history,...mismatch});await call;
    assert.equal(events().children.length,0);assert.match(status().textContent,/изменилась/);assert.equal(button().hidden,true);
  }
  await load({...history,owner:{...owner,businessId:'22'}});assert.equal(events().children.length,0);
  await load({...history,revision:10});assert.equal(events().children.length,0);
  const late=api.loadWorkHistory(ticket,1,7),request=pending.shift();context.generation=2;context.currentTicketPublicId='b'.repeat(32);api.clearWorkHistory('Другой бизнес');
  request.resolve(history);await late;assert.equal(events().children.length,0);assert.equal(status().textContent,'Другой бизнес');
  context.currentTicketPublicId=ticket.ticketPublicId;
  await load();const lateOlder=api.loadOlderWorkEvents(),latePage=pending.shift();context.cardRequest++;api.clearWorkHistory('Другая карточка');latePage.resolve(history);await lateOlder;
  assert.equal(events().children.length,0);assert.equal(status().textContent,'Другая карточка');
  const denied=api.loadWorkHistory(ticket,2,context.cardRequest);pending.shift().reject(Error('forbidden'));await denied;
  assert.equal(events().children.length,0);assert.match(status().textContent,/недоступна/);
  await load({...history,events:[],hasMore:false,nextBeforeRevision:null});assert.match(status().textContent,/пока нет/);assert.equal(button().hidden,true);
  console.log('BUSINESS_TICKET_WORK_HISTORY_UI_R171_RESULT=GREEN');
}
main().catch(error=>{console.error(error);process.exitCode=1;});
