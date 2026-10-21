'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const api = require('../../main/resources/static/js/business-questionnaire-presets.js');

class Element {
  constructor() { this.children = []; this.listeners = {}; this.dataset = {}; this.value = ''; this.disabled = false; this.textContent = ''; }
  addEventListener(type, handler) { this.listeners[type] = handler; }
  appendChild(item) { item.parent = this; this.children.push(item); return item; }
  replaceChildren() { this.children = []; }
  remove() { this.parent.children = this.parent.children.filter(item => item !== this); }
  setAttribute() {}
  querySelectorAll(selector) {
    return this.children.flatMap(child => [child, ...child.querySelectorAll(selector)]).filter(item => selector === '[data-preset-column]' ? item.dataset.presetColumn : item.input);
  }
  emit(type) { return this.listeners[type]?.({ preventDefault() {} }); }
}
const deferred = () => { let resolve; const promise = new Promise(done => { resolve = done; }); return { promise, resolve }; };
const response = (data, status = 200) => ({ ok: status === 200, status, json: async () => data });
const version = (businessId, groups = {}, char = 'a') => ({ businessId, groups, sha256: char.repeat(64) });
const tick = () => new Promise(done => setImmediate(done));

function harness(fetch) {
  const controls = Object.fromEntries(['business','rows','status','form','reload','add','save'].map(name => [`[data-presets-${name}]`, new Element()]));
  const doc = { querySelector: () => ({ content: 'csrf-proof' }), createElement: tag => Object.assign(new Element(), { input: ['input','textarea','button'].includes(tag) }) };
  const page = { querySelector: selector => controls[selector] };
  const mounted = api.mount(page, { fetch, document: doc, confirm: () => true });
  return { controls, doc, mounted, get: name => controls[`[data-presets-${name}]`] };
}

test('collect preserves independent fields and rejects duplicate, reserved or oversized values', () => {
  const groups = api.collect([{ group: 'department', field: 'name', values: ' Kitchen \nDelivery' }, { group: 'constructor', field: 'name', values: '<img src=x>' }]);
  assert.deepEqual(groups.department.name, ['Kitchen', 'Delivery']);
  assert.deepEqual(groups.constructor.name, ['<img src=x>']);
  for (const rows of [[{ group: 'locations', field: 'city', values: 'Foreign' }], [{ group: 'g', field: 'f', values: 'Same\nSame' }],
    [{ group: 'g', field: 'f', values: '' }], [{ group: 'g', field: 'f', values: 'x'.repeat(201) }],
    [{ group: 'g', field: 'f', values: 'A' }, { group: 'g', field: 'f', values: 'B' }]]) assert.throws(() => api.collect(rows));
});

test('reference display comes only from the requested owner and keeps custom fields independent', () => {
  const snapshot = { locationTree: { businessId: 7, locations: [{ name: 'Only A', city: 'North', locationType: 'Shop' }] },
    presetDefinitions: { businessId: 7, groups: { department: { name: ['Kitchen'] } } } };
  const definitions = api.definitions(snapshot, 7);
  assert.deepEqual(definitions.locations.fields.location_name.options, ['Only A']);
  assert.deepEqual(definitions.department.fields.name.options, ['Kitchen']);
  definitions.department.fields.name.options.push('Local preview');
  assert.deepEqual(snapshot.presetDefinitions.groups.department.name, ['Kitchen']);
  assert.throws(() => api.definitions(snapshot, 8));
});

test('a delayed A response cannot replace B or enable an A write under B', async () => {
  const a = deferred(), writes = [];
  const h = harness(async (url, options) => {
    if (url.endsWith('/overview')) return response({ businesses: [{ id: 7, displayName: 'A', status: 'active' }, { id: 8, displayName: 'B', status: 'active' }] });
    if (options.method === 'PUT') { writes.push([url, options]); return response(version(8, {}, 'c')); }
    return url.includes('/7/') ? a.promise : response(version(8, {}, 'b'));
  });
  await h.mounted.ready;
  h.get('business').value = '7'; h.get('business').emit('change');
  h.get('business').value = '8'; h.get('business').emit('change'); await tick();
  a.resolve(response(version(7, { foreign: { name: ['Only A'] } }))); await tick();
  assert.equal(h.get('rows').children.length, 0);
  await h.get('form').emit('submit');
  assert.equal(writes.length, 1); assert.match(writes[0][0], /businesses\/8\/questionnaire-presets$/);
  assert.equal(JSON.parse(writes[0][1].body).expectedSha256, 'b'.repeat(64));
  assert.deepEqual(JSON.parse(writes[0][1].body).groups, {});
});

test('CAS conflict preserves draft and version; explicit reload replaces both', async () => {
  const writes = []; let reads = 0;
  const h = harness(async (url, options) => {
    if (url.endsWith('/overview')) return response({ businesses: [{ id: 7, displayName: '<script>A</script>', status: 'active' }] });
    if (options.method === 'PUT') { writes.push(options); return response(null, 409); }
    return response(version(7, { department: { name: [++reads === 1 ? 'Saved' : 'Remote'] } }, reads === 1 ? 'a' : 'b'));
  });
  await h.mounted.ready; assert.equal(h.get('business').children[1].textContent, '<script>A</script>');
  h.get('business').value = '7'; h.get('business').emit('change'); await tick();
  const input = h.get('rows').querySelectorAll('[data-preset-column]').find(item => item.dataset.presetColumn === 'values');
  input.value = 'My draft'; input.emit('input'); await h.get('form').emit('submit');
  assert.match(h.get('status').textContent, /другой сессии/); assert.equal(input.value, 'My draft'); assert.equal(reads, 1);
  assert.equal(writes[0].headers['X-XSRF-TOKEN'], 'csrf-proof'); assert.equal(writes[0].credentials, 'same-origin');
  assert.equal(JSON.parse(writes[0].body).expectedSha256, 'a'.repeat(64));
  assert.equal(h.get('business').disabled, false); assert.equal(input.disabled, false);
  h.get('reload').emit('click'); await tick();
  assert.equal(h.get('rows').querySelectorAll('[data-preset-column]').find(item => item.dataset.presetColumn === 'values').value, 'Remote');
});

test('failed read leaves save disabled and cannot submit a write', async () => {
  let writes = 0;
  const h = harness(async (url, options) => {
    if (url.endsWith('/overview')) return response({ businesses: [] });
    if (options.method === 'PUT') writes++;
    return response(null, 403);
  });
  await h.mounted.ready; h.get('business').value = '7'; h.get('business').emit('change'); await tick();
  await h.get('form').emit('submit'); assert.equal(writes, 0); assert.equal(h.get('save').disabled, true);
});

test('reference context clears old fields and ignores stale owner responses including after GENERAL', async () => {
  const select = new Element(), status = new Element(), container = new Element(), slow = deferred(), applied = [], changed = [];
  select.value = 'legacy';
  const context = api.context({ select, status, container, apply: value => applied.push(value), changed: value => changed.push(value), legacy() {},
    document: { createElement: () => new Element() }, fetch: async url => url.endsWith('/overview') ? response({ businesses: [] }) : slow.promise });
  await context.catalog; select.value = '7'; const pending = context.load();
  select.value = 'general'; await context.load();
  slow.resolve(response({ locationTree: { businessId: 7, locations: [] }, presetDefinitions: { businessId: 7, groups: { private: { name: ['Secret'] } } } }));
  await pending;
  assert.deepEqual(changed, ['7','general']); assert.equal(applied.length, 2); assert.equal(Object.keys(applied[1]).length, 0);
  assert.match(status.textContent, /Без бизнеса/);
});

test('saving unrelated templates preserves their preset references, exclusions and forward routes', () => {
  const source = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/bot-settings.js'), 'utf8');
  const start = source.indexOf('  function serializeQuestion('), end = source.indexOf('\n  function collectPayload', start);
  const context = { normalizeBindingKey: value => value, getPresetMeta: () => null, cloneQuestionRoutes: routes => routes || [],
    generateQuestionId: () => { throw new Error('Existing ID must survive'); }, normalizeEditableQuestionOptions: value => value };
  vm.createContext(context); vm.runInContext(source.slice(start, end), context);
  const result = JSON.parse(JSON.stringify(context.serializeQuestion({ id: 'a', type: 'preset', text: 'Private A', preset: { group: 'only_a', field: 'name' },
    excludedOptions: ['Hidden'], routes: [{ valueId: 'A', nextQuestionId: 'last-a' }] }, 0)));
  assert.deepEqual(result.preset, { group: 'only_a', field: 'name' }); assert.deepEqual(result.excluded_options, ['Hidden']);
  assert.deepEqual(result.routes, [{ value_id: 'A', next_question_id: 'last-a' }]);
});

test('legacy bootstrap finishing after a business selection cannot restore global options', async () => {
  const source = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/bot-settings.js'), 'utf8');
  const start = source.indexOf('  function ensurePresetDefinitionsLoaded('), end = source.indexOf('\n  function ensureBotSettingsLoaded', start);
  const pending = deferred(), applied = [];
  const context = { presetContext: 'legacy', presetDefinitionsLoadingPromise: null, hasPresetDefinitions: () => false, rerenderPresetUi() {},
    applyPresetDefinitions: value => applied.push(value), window: { SettingsRuntimeAccess: { fetchPageDataSection: () => pending.promise } } };
  vm.createContext(context); vm.runInContext(source.slice(start, end), context);
  const loading = context.ensurePresetDefinitionsLoaded(); context.presetContext = '7';
  pending.resolve({ botPresetDefinitions: { foreign_global: { fields: { secret: { options: ['GLOBAL'] } } } } });
  await loading; assert.equal(applied.length, 0);
});

test('background route validation uses each template flow without the open business preview', () => {
  const source = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/bot-settings.js'), 'utf8');
  const start = source.indexOf('  function validateQuestionFlow('), end = source.indexOf('\n  function ', start + 10);
  const context = { resolveQuestionBindingKey: q => q.binding_key || '', getPresetMeta: () => null,
    resolveQuestionRouteChoices: () => [], cloneQuestionRoutes: routes => (routes || []).map(route => ({ valueId: route.value_id, nextQuestionId: route.next_question_id })),
    normalizeBindingKey: value => value, isDashboardBindingKeyAllowed: () => true };
  vm.createContext(context); vm.runInContext(source.slice(start, end), context);
  const flow = [{ id: 'a', type: 'preset', preset: { group: 'only_a', field: 'name' }, routes: [{ value_id: 'A', next_question_id: 'last-a' }] },
    { id: 'last-a', type: 'custom', text: 'Describe' }];
  assert.ok(!context.validateQuestionFlow(flow, 'A', false));
  assert.match(context.validateQuestionFlow(flow, 'A', true), /маршруты/);
  flow[0].routes[0].next_question_id = 'foreign-b';
  assert.match(context.validateQuestionFlow(flow, 'A', false), /маршруты/);
});
