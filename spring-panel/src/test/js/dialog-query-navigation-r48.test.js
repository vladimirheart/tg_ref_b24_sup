'use strict';
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');

const jsRoot = path.resolve(__dirname, '../../main/resources/static/js');
const read = file => fs.readFileSync(path.join(jsRoot, file), 'utf8').replace(/\r\n/g, '\n');
const dialogs = read('dialogs.js');
const workspace = read('dialogs-workspace-runtime.js');
const flow = read('dialogs-flow-runtime.js');
const locationBusiness = read('dialogs-location-business-runtime.js');
const profile = fs.readFileSync(path.resolve(__dirname, '../../main/resources/templates/clients/profile.html'), 'utf8');
const exactId = ' T/100?&%# Ю ';
const target = '/dialogs?ticketId=%20T%2F100%3F%26%25%23%20%D0%AE%20';

function sourceFunction(source, name) {
  const start = source.indexOf('function ' + name + '(');
  assert.ok(start >= 0, 'missing ' + name);
  const begin = source.indexOf('{', start);
  let depth = 0;
  for (let i = begin; i < source.length; i++) {
    if (source[i] === '{') depth++;
    if (source[i] === '}' && --depth === 0) return source.slice(start, i + 1);
  }
  throw new Error('unbalanced ' + name);
}

function evaluate(source, name, context = {}, dependencies = []) {
  const body = dependencies.map(dependency => sourceFunction(source, dependency)).join('\n');
  return vm.runInNewContext(body + '\n' + sourceFunction(source, name) + '\n' + name, {
    URLSearchParams, encodeURIComponent, decodeURIComponent, ...context,
  });
}

const buildListRoute = evaluate(dialogs, 'buildWorkspaceDialogUrl', {
  withChannelParam: (url, channel) => channel ? url + '&channelId=' + encodeURIComponent(channel) : url,
});
assert.equal(buildListRoute(exactId, null), target);
assert.equal(buildListRoute(exactId, 7), target + '&channelId=7');
assert.match(dialogs, /const openHref = buildWorkspaceDialogUrl\(ticketId, channelId\)/);

const getRouteTicketId = evaluate(dialogs, 'getRouteTicketId', {}, ['exactLegacyTicketId']);
assert.equal(getRouteTicketId('/dialogs', '?ticketId=' + encodeURIComponent(exactId)), exactId);
assert.equal(getRouteTicketId('/dialogs/T-OLD', ''), 'T-OLD');
assert.equal(getRouteTicketId('/dialogs', '?view=all'), '');
const isWorkspaceDialogPath = evaluate(dialogs, 'isWorkspaceDialogPath', {}, ['exactLegacyTicketId', 'getRouteTicketId']);
assert.equal(isWorkspaceDialogPath('/dialogs', '?ticketId=' + encodeURIComponent(exactId)), true);
assert.equal(isWorkspaceDialogPath('/dialogs', '?view=all'), false);
const listUrl = evaluate(dialogs, 'buildDialogsListUrl', {
  window: { location: { pathname: '/dialogs', search: '?ticketId=' + encodeURIComponent(exactId), hash: '' } },
}, ['exactLegacyTicketId', 'getRouteTicketId']);
assert.equal(listUrl(), '/dialogs');

const buildWorkspaceRoute = evaluate(workspace, 'buildWorkspaceDialogUrl', {
  options: { withChannelParam: (url, channel) => channel ? url + '&channelId=' + channel : url },
});
assert.equal(buildWorkspaceRoute(exactId, null), target);
assert.equal(buildWorkspaceRoute(exactId, 7), target + '&channelId=7');

const parseFlowRoute = evaluate(flow, 'parseDialogRouteTicketId', {
  window: { location: { pathname: '/dialogs', search: '?ticketId=' + encodeURIComponent(exactId) } },
  isDialogDetailsPath: () => true,
});
assert.equal(parseFlowRoute('/dialogs'), exactId);
assert.equal(parseFlowRoute('/dialogs/T-OLD'), 'T-OLD');

const resolveLocationTicket = evaluate(locationBusiness, 'resolveTicketId', {
  window: { location: { pathname: '/dialogs', search: '?ticketId=' + encodeURIComponent(exactId), origin: 'https://panel.test' } },
  document: { getElementById: () => null },
});
assert.equal(resolveLocationTicket(), exactId);
// The server supplies the scope-aware URL; execute its transfer into the preview link.
(async()=>{
  const link={hidden:false,href:'',removeAttribute(name){if(name==='href')this.href='';}};
  const elements={clientHistoryMeta:{},clientHistoryOpenDialog:link,clientHistoryMessages:{}};
  const window={},requests=[],pending=new Map(),rendered=[];
  const start=profile.indexOf('    let clientHistoryModalInstance = null;');
  const end=profile.indexOf('    const ticketsSearch',start);
  assert(start>=0&&end>start);
  vm.runInNewContext(profile.slice(start,end),{
    window,document:{getElementById:id=>elements[id]||null},
    bootstrap:{Modal:class{show(){}}},renderClientHistory(history){rendered.push(history);},encodeURIComponent,
    escapeClientHistoryHtml:evaluate(profile,'escapeClientHistoryHtml'),
    fetch:async url=>{
      requests.push(url);const key=decodeURIComponent(new URL(url,'https://panel.test').pathname.slice('/api/dialogs/'.length));
      return pending.get(key)||{ok:true,json:async()=>({summary:{},history:[]})};
    }
  });
  await window.openClientHistory({dataset:{ticketId:exactId,channelId:'7',dialogUrl:target+'&channelId=7'}});
  assert.equal(link.href,target+'&channelId=7');assert.equal(link.hidden,false);
  assert.equal(new URL(requests[0],'https://panel.test').pathname,'/api/dialogs/'+encodeURIComponent(exactId));
  await window.openClientHistory({dataset:{ticketId:'B'}});
  assert.equal(link.hidden,true);assert.equal(link.href,'');
  await window.openClientHistory({dataset:{ticketId:'GENERAL',dialogUrl:'/general-dialog-work/'+'a'.repeat(32)}});
  assert.equal(link.href,'/general-dialog-work/'+'a'.repeat(32));assert.equal(link.hidden,false);
  let releaseA;pending.set('RACE_A',new Promise(resolve=>{releaseA=resolve;}));
  const first=window.openClientHistory({dataset:{ticketId:'RACE_A',dialogUrl:'/dialogs?ticketId=RACE_A'}});
  await window.openClientHistory({dataset:{ticketId:'RACE_B',dialogUrl:'/dialogs?ticketId=RACE_B'}});
  releaseA({ok:true,json:async()=>({summary:{},history:[{message:'OLD_A'}]})});await first;
  assert(!rendered.at(-1).some(row=>row.message==='OLD_A'),'Late A must not replace B history');
  pending.set('ERROR',{ok:false,json:async()=>({error:'<img src=x onerror=alert(1)>'})});
  await window.openClientHistory({dataset:{ticketId:'ERROR'}});
  assert(!elements.clientHistoryMessages.innerHTML.includes('<img'),'Error text must not become HTML');
  console.log('DIALOG_QUERY_NAVIGATION_RESULT=GREEN');
})().catch(error=>{console.error(error);process.exitCode=1;});
