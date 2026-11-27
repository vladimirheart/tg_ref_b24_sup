'use strict';
const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const window={};vm.runInNewContext(fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/js/panel-errors.js'),'utf8'),{window});
const api=window.PanelErrors;
test('JSON and empty success responses keep their contract',async()=>{
  assert.equal((await api.read(new Response('{"success":true,"id":"9007199254740993"}'))).id,'9007199254740993');
  assert.equal(await api.read(new Response(null,{status:204})),null);
});
test('HTML Nginx 413 shows upload reason and never a JSON syntax error',async()=>{
  await assert.rejects(api.read(new Response('<html>413 Request Entity Too Large</html>',{status:413}),{sending:true}),
    error=>/Размер запроса/.test(error.message)&&!error.deliveryUnknown&&!/html|JSON|Unexpected/.test(error.message));
});
test('Nginx JSON preserves limit, source and safe request identifier',async()=>{
  await assert.rejects(api.read(new Response(JSON.stringify({success:false,error:'Размер запроса больше 25 МБ.',errorSource:'nginx',requestId:'abc-123'}),{status:413})),
    error=>error.message.includes('Nginx:')&&error.message.includes('25 МБ')&&error.message.includes('abc-123'));
});
test('HTTP 200 application rejection and nested provider error are failures',async()=>{
  for(const body of [{success:false,error:'Файл не поддерживается'},{ok:false,description:'Bad Request'},{error:{error_msg:'Access denied'}}])
    await assert.rejects(api.read(new Response(JSON.stringify(body))));
});
test('one-shot response body is consumed once',async()=>{
  let reads=0;
  await assert.rejects(api.read({ok:false,status:504,text:async()=>{assert.equal(++reads,1);return '<html>timeout</html>';}}),
    error=>error.httpStatus===504&&/Прокси/.test(error.message));
  assert.equal(reads,1);
});
test('timeout, network and malformed success never claim confirmed rejection or success',async()=>{
  for(const error of [api.network(new TypeError('fetch failed https://secret.test?token=private'),{sending:true}),
    await api.read(new Response('not json'),{sending:true}).catch(error=>error),
    await api.read(new Response('',{status:502}),{sending:true}).catch(error=>error)])
    assert(error.deliveryUnknown&&/не подтвержден/.test(error.message)&&!error.message.includes('private'));
});
test('provider cooldown and RFC problem detail remain readable',async()=>{
  await assert.rejects(api.read(new Response('{"detail":"Уточните параметры"}',{status:400})),/Уточните параметры/);
  await assert.rejects(api.read(new Response('{"parameters":{"retry_after":7}}',{status:429})),/7 сек/);
});
test('legacy API error status does not hide an unknown provider outcome',async()=>{
  await assert.rejects(api.read(new Response('{"success":false,"error":"Telegram: Не удалось подтвердить результат операции."}',{status:400}),{sending:true}),
    error=>error.deliveryUnknown&&!/Не отправлено/.test(error.message));
  const error=api.network(new TypeError('fetch failed'),{mutating:true});
  assert.match(error.message,/Результат действия/);assert(!error.message.includes('отправки'));
});
test('diagnostic text strips credentials, URLs, HTML and control characters',()=>{
  const result=api.safe('secret=private access_token="vk-private" https://bot.test/bot123:private <script>alert(1)</script>\u0000 123456:ABCDEFGHIJKLMNOPQRSTUVWXYZZ');
  assert(!/private|vk-private|https:|<script>|\u0000|ABCDEFGHIJKLMNOP/.test(result));
  assert(!api.safe('{"Authorization":"Bearer private"} Authorization: Bearer private').includes('private'));
});
