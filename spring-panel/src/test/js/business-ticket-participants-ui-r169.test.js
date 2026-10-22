'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
class Element {
  constructor(){this.children=[];this.textContent='';this.hidden=false;this.disabled=false;}
  replaceChildren(){this.children=[];}
  appendChild(item){this.children.push(item);}
  append(...items){this.children.push(...items);}
  set innerHTML(value){throw Error('Unsafe participant markup '+value);}
}
async function main(){
  const source=fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/js/business-dialogs.js'),'utf8');
  const template=fs.readFileSync(path.resolve(__dirname,'../../main/resources/templates/businesses/dialogs.html'),'utf8');
  for(const selector of ['data-ticket-participants','data-ticket-participant-events','data-ticket-participants-status','data-ticket-participants-older'])assert(template.includes(selector));
  const clearStart=source.indexOf('  function clearParticipants('),clearEnd=source.indexOf('  function announce(',clearStart);
  const start=source.indexOf('  function participantOwnerMatches('),end=source.indexOf('  async function loadSpans(',start);
  assert(clearStart>0&&clearEnd>clearStart&&start>0&&end>start);
  const fields=new Map(),pending=[],ticket={ticketPublicId:'a'.repeat(32),userId:901,ticketId:' raw ',businessId:11};
  const owner={ticketPublicId:ticket.ticketPublicId,userId:'901',ticketId:' raw ',businessId:'11'};
  const roster={owner,revision:'9007199254740993',members:[
    {id:'9223372036854775807',snapshot:'old',username:'<script>current</script>',eligible:false},
    {id:'3',snapshot:'deleted snapshot',username:null,eligible:false}
  ]};
  const event={revision:'9007199254740993',action:'add',memberId:'9223372036854775807',member:'<img src=x>',actor:'<script>actor</script>',createdAt:'date'};
  const history={owner,revision:roster.revision,events:[event],hasMore:true,nextBeforeRevision:'9007199254740991'};
  const context={card:{querySelector(selector){if(!fields.has(selector))fields.set(selector,new Element());return fields.get(selector);}},
    generation:1,cardRequest:7,currentTicketPublicId:ticket.ticketPublicId,participantTicket:null,participantRevision:null,
    participantBefore:null,participantLoading:false,encodeURIComponent,ticketTime:value=>value,
    document:{createElement:()=>new Element()},getJson:url=>new Promise((resolve,reject)=>pending.push({url,resolve,reject}))};
  const api=vm.runInNewContext(`${source.slice(clearStart,clearEnd)}\n${source.slice(start,end)}\n({loadParticipants,loadOlderParticipants,clearParticipants})`,context);
  const list=()=>fields.get('[data-ticket-participants]'),events=()=>fields.get('[data-ticket-participant-events]');
  const status=()=>fields.get('[data-ticket-participants-status]'),button=()=>fields.get('[data-ticket-participants-older]');
  async function load(rosterValue=roster,historyValue=history){
    const call=api.loadParticipants(ticket,context.generation,context.cardRequest);
    assert(pending[0].url.endsWith('/participants'));assert(pending[1].url.endsWith('/participants/history?limit=50'));
    pending.shift().resolve(rosterValue);pending.shift().resolve(historyValue);await call;
  }
  await load();
  assert.equal(list().children.length,2);
  assert.match(list().children[0].textContent,/<script>current<\/script> \(ID 9223372036854775807\)/);
  assert.match(list().children[1].textContent,/deleted snapshot.*нет текущего доступа/);
  assert.match(events().children[0].textContent,/9007199254740993.*<img src=x>/);
  assert.equal(button().hidden,false);
  const older=api.loadOlderParticipants();
  assert.equal(button().disabled,true);assert(pending[0].url.endsWith('before=9007199254740991'));
  await api.loadOlderParticipants();assert.equal(pending.length,1);
  pending.shift().resolve({...history,events:[{...event,revision:'9007199254740990',action:'remove'}],hasMore:false,nextBeforeRevision:null});
  await older;assert.equal(events().children.length,2);assert.equal(button().hidden,true);assert.equal(button().disabled,false);

  await load();
  const oldPage=api.loadOlderParticipants();
  pending.shift().resolve({...history,revision:'9007199254740994'});await oldPage;
  assert.equal(list().children.length,0);assert.equal(events().children.length,0);assert.match(status().textContent,/изменился/);
  await load({...roster,owner:{...owner,businessId:'22'}});
  assert.equal(list().children.length,0);assert.match(status().textContent,/изменился/);
  await load(roster,{...history,revision:'9007199254740994'});
  assert.equal(events().children.length,0);assert.match(status().textContent,/изменился/);

  const late=api.loadParticipants(ticket,1,7),requests=pending.splice(0);
  context.generation=2;context.currentTicketPublicId='b'.repeat(32);api.clearParticipants('Другой бизнес');
  requests[0].resolve(roster);requests[1].resolve(history);await late;
  assert.equal(list().children.length,0);assert.equal(status().textContent,'Другой бизнес');
  context.currentTicketPublicId=ticket.ticketPublicId;
  const denied=api.loadParticipants(ticket,2,7),deniedRequests=pending.splice(0);
  deniedRequests[0].reject(Error('forbidden'));deniedRequests[1].resolve(history);await denied;
  assert.equal(list().children.length,0);assert.match(status().textContent,/недоступны/);
  await load({...roster,members:[]},{...history,events:[],hasMore:false,nextBeforeRevision:null});
  assert.match(status().textContent,/пока не добавлены/);assert.equal(button().hidden,true);
  console.log('BUSINESS_TICKET_PARTICIPANTS_UI_R169_RESULT=GREEN');
}
main().catch(error=>{console.error(error);process.exitCode=1;});
