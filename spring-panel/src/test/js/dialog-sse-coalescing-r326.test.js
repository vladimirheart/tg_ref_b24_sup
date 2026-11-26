const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),assert=require('node:assert/strict'),test=require('node:test');
const source=fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/js/dialogs.js'),'utf8');
for(const [name,timer,refresh] of [
 ['scheduleDialogsListRefresh','dialogsSseRefreshTimer','refreshDialogsList'],
 ['scheduleHistoryRefresh','historySseRefreshTimer','refreshHistory']
])test(name+' delivers during a continuous burst without overlapping timers',()=>{
 const start=source.indexOf('  function '+name+'('),end=source.indexOf('\n  }',start)+4;
 assert(start>=0&&end>start);const callbacks=new Map();let sequence=0,calls=0;
 const context={window:{setTimeout(fn){callbacks.set(++sequence,fn);return sequence;},clearTimeout(){throw Error('Burst postponed an already scheduled refresh');}},
  [timer]:null,[refresh](){calls++;},debugLog(){},activeDialogTicketId:'fixture'};
 vm.createContext(context);vm.runInContext(source.slice(start,end),context);
 for(let i=0;i<100;i++)context[name]('burst');
 assert.equal(callbacks.size,1);callbacks.get(1)();assert.equal(calls,1);
 context[name]('next');assert.equal(callbacks.size,2);callbacks.get(2)();assert.equal(calls,2);
});
