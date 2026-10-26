'use strict';
const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), path = require('node:path');
const runbook = require('./fixtures/incident-multiline-r20.cjs');
const source = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/incidents-workbench.js'), 'utf8');
function extract(start, end) {
  const from = source.indexOf(start), to = source.indexOf(end, from);
  assert(from >= 0 && to > from, 'Production function boundary: ' + start);
  return source.slice(from, to);
}
const escapeHtml = new Function(extract('  function escapeHtml(', '  function badgeClass(') + '\nreturn escapeHtml;')();
const render = new Function('window', 'transportNodes', 'escapeHtml',
  extract('  function renderIncidentExplanation(', '  function renderCurrentIncidentProblems(') + '\nreturn renderIncidentExplanation;')(
    { IncidentDescriptions: require('../../main/resources/static/js/incident-descriptions.js') }, {}, escapeHtml);

for (const newline of ['\n', '\r\n']) test('Технические подробности сохраняют текст и XML: ' + JSON.stringify(newline), () => {
  const text = runbook.replaceAll('\n', newline);
  const incident = { title: 'INC-238', summary: 'Первая строка\nВторая строка', description: text };
  const before = JSON.stringify(incident), html = render(incident);
  assert(html.includes(escapeHtml(text)), 'No collapse, HTML conversion or Markdown interpretation');
  assert(html.includes('class="text-break incident-multiline"'));
  assert(html.includes('&lt;add key=&quot;ForceHttpSync&quot; value=&quot;true&quot; /&gt;'));
  assert(!html.includes('<add key=')); assert(!html.includes('<!--Force HTTP'));
  assert.equal(JSON.stringify(incident), before, 'No historical data mutation');
});

test('Комментарий использует textarea с прежним ID и безопасный multiline rendering', () => {
  assert.match(source, /<textarea\b[^>]*id="incidentEventText"[^>]*><\/textarea>/);
  assert(!/<input\b[^>]*id="incidentEventText"/.test(source));
  assert.match(source, /class="incident-event-text incident-multiline mb-1">\$\{escapeHtml\(event\.event_text \|\| ''\)\}/);
  const scss = fs.readFileSync(path.resolve(__dirname, '../../main/resources/scss/app/_incident-workbench.scss'), 'utf8');
  assert.match(scss, /\.incident-multiline\s*\{[^}]*white-space:\s*pre-wrap;[^}]*overflow-wrap:\s*anywhere;/);
});

for (const newline of ['\n', '\r\n']) test('POST комментария не схлопывает переносы: ' + JSON.stringify(newline), async () => {
  const calls = [], text = runbook.replaceAll('\n', newline);
  const addEvent = new Function('state', 'document', 'requestJson', 'showSuccess', 'loadIncidents',
    extract('  async function addIncidentEvent()', '  async function addWatcher()') + '\nreturn addIncidentEvent;')(
      { selectedIncident: { id: 238 } }, { getElementById: id => ({ value: id === 'incidentEventText' ? text : 'comment' }) },
      async (url, options) => { calls.push({ url, payload: JSON.parse(options.body) }); }, () => {}, async () => {});
  await addEvent();
  assert.equal(calls.length, 1); assert.equal(calls[0].url, '/api/incidents/238/events');
  assert.equal(calls[0].payload.event_text, text); assert.equal(calls[0].payload.event_type, 'comment');
});

test('HTML и скрипты остаются текстом, включая технические подробности', () => {
  const html = render({ title: 'Ручной', description: '<script>window.injection=1</script>\n<img src=x onerror=alert(1)>' });
  assert(!html.includes('<script>')); assert(!html.includes('<img '));
  assert(html.includes('&lt;script&gt;')); assert(html.includes('&lt;img '));
});
