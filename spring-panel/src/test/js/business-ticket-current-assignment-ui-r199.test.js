'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
class Element {
  constructor(){this.children=[];this.textContent='';this.hidden=false;this.disabled=false;}
  replaceChildren(){this.children=[];}
  append(...items){this.children.push(...items);}
  set innerHTML(value){throw Error('Unsafe assignment markup '+value);}
}
async function main(){
  const source=fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/js/business-dialogs.js'),'utf8');
  const template=fs.readFileSync(path.resolve(__dirname,'../../main/resources/templates/businesses/dialogs.html'),'utf8');
  assert(template.includes('data-ticket-current-assignment'),'Current assignment field must exist');
  assert(source.includes('clearCurrentAssignment();'));
  assert(source.includes('loadCurrentAssignment(detail, expectedGeneration, request);'));
  const clear=source.indexOf('  function clearCurrentAssignment('),clearEnd=source.indexOf('  function clearParticipants(',clear);
  const start=source.indexOf('  function participantOwnerMatches('),end=source.indexOf('  function participantEventItem(',start);
  const historyStart=source.indexOf('  function assignmentOwnerMatches('),historyEnd=source.indexOf('  async function loadSpans(',historyStart);
  const historyClear=source.indexOf('  function clearAssignmentHistory('),historyClearEnd=source.indexOf('  function announce(',historyClear);
  assert(clear>=0&&clearEnd>clear&&start>0&&end>start&&historyStart>0&&historyEnd>historyStart);
  const fields=new Map(),pending=[],ticket={ticketPublicId:'a'.repeat(32),userId:'9007199254740993',ticketId:' raw ',businessId:'11'};
  const owner={...ticket,userId:ticket.userId,businessId:ticket.businessId};
  const assignee={id:'9223372036854775807',username:' <img src=x> Ю\r\n ',origin:'MANUAL',assignedAt:'2026-10-27T10:00:00Z'};
  const state={owner,revision:'9007199254740993',status:'ASSIGNED',assignee};
  const context={card:{querySelector(selector){if(!fields.has(selector))fields.set(selector,new Element());return fields.get(selector);}},
    generation:1,cardRequest:7,currentTicketPublicId:ticket.ticketPublicId,currentAssignmentRevision:null,
    assignmentTicket:null,assignmentRevision:null,assignmentLatest:null,assignmentBefore:null,assignmentLoading:false,
    encodeURIComponent,ticketTime:value=>value,document:{createElement:()=>new Element()},
    getJson:url=>new Promise((resolve,reject)=>pending.push({url,resolve,reject}))};
  const api=vm.runInNewContext(`${source.slice(clear,clearEnd)}\n${source.slice(start,end)}\n${source.slice(historyClear,historyClearEnd)}\n${source.slice(historyStart,historyEnd)}\n({loadCurrentAssignment,clearCurrentAssignment,loadAssignmentHistory,clearAssignmentHistory})`,context);
  const field=()=>fields.get('[data-ticket-current-assignment]');
  async function load(value=state){const call=api.loadCurrentAssignment(ticket,context.generation,context.cardRequest);assert(pending[0].url.endsWith('/assignments/current'));pending.shift().resolve(value);await call;}
  function reset(){api.clearCurrentAssignment();api.clearAssignmentHistory();}
  await load();assert.match(field().textContent,/<img src=x>/);assert.match(field().textContent,/9223372036854775807/);
  assert.equal(context.currentAssignmentRevision,state.revision);
  await load({...state,assignee:{...assignee,id:null,origin:'SLA'}});assert.match(field().textContent,/SLA/);assert.doesNotMatch(field().textContent,/ID null/);
  reset();await load({owner,revision:'0',status:'UNASSIGNED',assignee:null});assert.match(field().textContent,/не назначен/i);
  reset();await load({owner,revision:'2',status:'UNVERIFIED',assignee:null,responsible:'private raw fallback'});assert.match(field().textContent,/не подтверждено/i);assert.doesNotMatch(field().textContent,/private/);
  for(const patch of [
    {owner:{...owner,businessId:'22'}},{owner:{...owner,userId:'9007199254740994'}},
    {owner:{...owner,ticketId:'raw'}},{owner:{...owner,ticketPublicId:'b'.repeat(32)}},
    {revision:9007199254740992},{revision:'01'},{revision:'-1'},{revision:'9223372036854775808'},
    {status:'UNKNOWN'},{status:'UNASSIGNED'},{assignee:null},
    {assignee:{...assignee,id:9007199254740992}},{assignee:{...assignee,id:'0'}},
    {assignee:{...assignee,id:null}},{assignee:{...assignee,origin:'OTHER'}},
    {assignee:{...assignee,username:' '}},{assignee:{...assignee,origin:'SLA'}}
  ]){
    reset();await load({...state,...patch});assert.doesNotMatch(field().textContent,/<img|922337/);assert.equal(context.currentAssignmentRevision,null);
  }
  reset();const late=api.loadCurrentAssignment(ticket,1,7),request=pending.shift();context.generation=2;context.currentTicketPublicId='b'.repeat(32);api.clearCurrentAssignment('Другой бизнес');
  request.resolve(state);await late;assert.equal(field().textContent,'Другой бизнес');
  context.currentTicketPublicId=ticket.ticketPublicId;
  const lateCard=api.loadCurrentAssignment(ticket,2,7),cardReply=pending.shift();context.cardRequest=8;api.clearCurrentAssignment('Другая карточка');
  cardReply.resolve(state);await lateCard;assert.equal(field().textContent,'Другая карточка');
  const denied=api.loadCurrentAssignment(ticket,2,8);pending.shift().reject(Error('forbidden'));await denied;assert.match(field().textContent,/недоступен/);
  reset();context.assignmentRevision='9007199254740994';await load();assert.match(field().textContent,/изменилось/);assert.equal(context.currentAssignmentRevision,null);
  reset();await load();const history=api.loadAssignmentHistory(ticket,2,8);
  pending.shift().resolve({owner,revision:'9007199254740994',latestEventRevision:'9007199254740994',events:[],hasMore:false,nextBeforeRevision:null});
  await history;assert.match(field().textContent,/изменилось/);assert.equal(context.currentAssignmentRevision,null);
  console.log('BUSINESS_TICKET_CURRENT_ASSIGNMENT_UI_R199_RESULT=GREEN');
}
main().catch(error=>{console.error(error);process.exitCode=1;});
