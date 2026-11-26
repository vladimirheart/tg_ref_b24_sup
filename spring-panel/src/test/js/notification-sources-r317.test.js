'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/sidebar.js'), 'utf8');
function definition(name) {
  const start = source.lastIndexOf('  function ' + name + '(');
  assert(start >= 0, name);
  const rest = source.slice(start + 3);
  const next = /\n  (?:async )?function /.exec(rest);
  assert(next, name + ' boundary');
  return source.slice(start, start + 3 + next.index);
}
const start = source.indexOf('  const NOTIFICATION_SOURCES =');
const end = source.indexOf('\n  });', start) + '\n  });'.length;
const escape = value => String(value ?? '').replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;').replaceAll('"','&quot;');
const context = { escapeHtml: escape, formatNotificationTime: () => '25.11.2026',
  NOTIFICATION_EMPTY_LABEL: 'Пусто', NOTIFICATION_ITEM_FALLBACK: 'Уведомление',
  renderNotificationToolbar: count => `<div data-count="${count}"></div>` };
vm.createContext(context);
vm.runInContext(source.slice(start,end) + '\n' + ['notificationSource','renderNotificationSource','normalizeNotificationItem','renderNotificationItems'].map(definition).join('\n'), context);
for (const [section,label] of Object.entries({dialogs:'Диалоги',incidents:'Инциденты',tasks:'Задачи',projects:'Проекты',analytics:'Мониторинг',settings:'Настройки',knowledge:'База знаний'})) {
  const normalized = context.normalizeNotificationItem({id:1,text:'<img src=x onerror=evil>',url:`/${section}?id=1`,read:false});
  const html = context.renderNotificationItems([normalized], 'Пусто', 3);
  assert(html.includes(label));
  assert(html.includes('data-notification-source='));
  assert(html.includes('badge rounded-pill text-bg-'));
  assert(html.includes('data-count="3"'));
  assert(html.includes('&lt;img'));
  assert(!html.includes('<img'));
}
for (const sourceKey of ['__proto__','<img src=x>','constructor','unknown']) {
  const item = context.normalizeNotificationItem({id:2,text:'hello',source:sourceKey,sourceLabel:'<script>evil</script>',url:'//evil.test/dialogs'});
  assert.equal(item.source,'system');
  const html = context.renderNotificationItems([item]);
  assert(html.includes('Система'));
  assert(!html.includes('evil</script>'));
}
assert.equal(context.normalizeNotificationItem({source:'dialogs',url:'/dialogs',read:true}).is_read,true);
assert.equal(context.notificationSource({url:'/dialogsevil'}),'system');
assert(context.renderNotificationItems([], 'Пусто', 0).includes('Пусто'));
const template = fs.readFileSync(path.resolve(__dirname,'../../main/resources/templates/fragments/navbar.html'),'utf8');
assert(template.includes("sidebar.js(v='20261126-notification-push-r326')"));
console.log('NOTIFICATION_SOURCES_R317=GREEN;SOURCES=7;UNTRUSTED_SOURCES=4');
