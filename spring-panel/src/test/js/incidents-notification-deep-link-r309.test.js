'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const source=fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/js/incidents-workbench.js'),'utf8');
function segment(start,end){const a=source.indexOf(start),b=source.indexOf(end,a);assert(a>=0&&b>a,'Production boundary '+start);return source.slice(a,b);}
const initial=segment('  const requestedIncidentId =','  const payloadModalElement =');
const load=segment('  async function loadIncidents() {','  function renderIncidentList() {');
function fixture(search){
 const state={listRequestSerial:0,selectedIncidentId:null},calls=[],details=[];
 const make=new Function('window','URLSearchParams','state','loadIncidentOpsSummary','rememberCurrentIncidentDetailDraft','clearFeedback','listNode','listMetaNode','filterNodes','requestJson','renderIncidentList','loadIncidentDetail','renderIncidentDetail',
  initial+'\n'+load+'\nreturn loadIncidents;');
 const loadIncidents=make({location:{search}},URLSearchParams,state,()=>{},()=>{},()=>{},{setAttribute(){}},{},{},
  async url=>{calls.push(url);return{items:[{id:7}]};},()=>{},async id=>details.push(String(id)),()=>{});
 return{loadIncidents,state,calls,details};
}
for(const id of ['77','9007199254740993','9223372036854775807'])test('Deep link opens exact ID outside the first list: '+id,async()=>{
 const f=fixture('?incidentId='+id);await f.loadIncidents();assert.deepEqual(f.details,[id]);assert.equal(f.state.selectedIncidentId,id);
 f.state.selectedIncidentId='7';await f.loadIncidents();assert.deepEqual(f.details,[id,'7'],'Refresh does not force the old deep link');
});
for(const value of ['','0','-1','1e3','7/../8','INC-7','12345678901234567890'])test('Invalid deep link falls back to normal list: '+value,async()=>{
 const f=fixture('?incidentId='+encodeURIComponent(value));await f.loadIncidents();assert.deepEqual(f.details,['7']);
});
test('Absent deep link keeps normal initial selection',async()=>{const f=fixture('');await f.loadIncidents();assert.deepEqual(f.details,['7']);});

