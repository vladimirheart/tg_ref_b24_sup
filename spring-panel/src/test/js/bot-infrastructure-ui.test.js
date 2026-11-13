'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs'), path = require('node:path'), vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/settings-channels-catalog-runtime.js'), 'utf8');
function harness() {
  const button = { disabled: false, innerHTML: 'Добавить канал' }, messages = [], requests = [];
  const form = { querySelector: () => button, setAttribute(name,value) { this[name]=value; } };
  const ids = new Map([['addChannelForm',form], ['new_token',{value:'test-only-token'}],
    ['new_channel_name',{value:'Новый MAX'}], ['new_platform',{value:'max'}]]);
  const context = { window: {}, Map, console, document: {getElementById: id => ids.get(id)},
    fetch: (url,options) => new Promise((resolve,reject) => requests.push({url,options,resolve,reject})) };
  vm.runInNewContext(source,context);
  const runtime = context.window.SettingsChannelsCatalogRuntime.mount({ showNotification: (message,type) => messages.push({message,type}) });
  return { runtime,button,form,messages,requests,window:context.window };
}
test('adding waits for infrastructure with spinner and blocks repeated submit', async () => {
  const h=harness(), pending=h.runtime.addChannel();
  assert.equal(h.button.disabled,true); assert.match(h.button.innerHTML,/spinner-border/);
  assert.equal(h.form['aria-busy'],'true');
  await h.runtime.addChannel();assert.equal(h.requests.length,1);
  h.requests[0].resolve({ok:true,json:async()=>({success:true,infrastructure:{state:'ready'}})});
  await pending;
  assert.equal(h.button.disabled,false);assert.equal(h.form['aria-busy'],'false');
  assert.match(h.messages.at(-1).message,/Доступы подготовлены/);
});
test('partial failure confirms saved channel and explains retry without a second POST', async () => {
  const h=harness(),pending=h.runtime.addChannel();
  h.requests[0].resolve({ok:true,json:async()=>({success:true,infrastructure:{state:'failed',message:'Недоступен RabbitMQ. Повторите «Старт».'}})});
  await pending;
  assert.equal(h.messages.at(-1).type,'warning');assert.match(h.messages.at(-1).message,/добавлен.*RabbitMQ.*Старт/);
  assert.equal(h.requests.length,1);assert.equal(h.button.disabled,false);
});
test('network failure restores controls', async () => {
  const h=harness(),pending=h.runtime.addChannel();h.requests[0].reject(new Error('offline'));await pending;
  assert.equal(h.messages.at(-1).type,'error');assert.equal(h.button.disabled,false);
  assert.equal(h.button.innerHTML,'Добавить канал');
});
test('completion preserves a permission revocation received while waiting', async () => {
  const h=harness(),pending=h.runtime.addChannel();
  h.window.SettingsPermissions={canEdit:()=>false};
  h.requests[0].resolve({ok:true,json:async()=>({success:true,infrastructure:{state:'ready'}})});
  await pending;assert.equal(h.button.disabled,true);
});
