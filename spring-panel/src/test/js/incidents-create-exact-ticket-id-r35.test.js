'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const source = fs.readFileSync(path.resolve(__dirname,
  '../../main/resources/static/js/incidents-workbench.js'), 'utf8');
const start = source.indexOf('  async function createIncident() {');
const end = source.indexOf('  async function addIncidentEvent()', start);
assert(start >= 0 && end > start, 'createIncident function boundary');
const createFunction = source.slice(start, end);

async function submit(relationType, relationKey) {
  const requests = [];
  const createNodes = {
    title: { value: 'Incident' },
    relationType: { value: relationType },
    relationKey: { value: relationKey },
  };
  const state = {};
  const requestJson = async (url, options) => {
    requests.push({ url, options });
    return { incident: { id: 7 } };
  };
  const createIncident = new Function('createNodes', 'requestJson', 'showSuccess',
    'rememberCurrentIncidentDetailDraft', 'state', 'loadIncidents', 'splitCommaList',
    'exactLegacyTicketId',
    createFunction + '\nreturn createIncident;')(
      createNodes, requestJson, () => {}, () => {}, state, async () => {}, () => [],
      value => typeof value === 'string' && value.trim() ? value : '');
  let error = null;
  try { await createIncident(); } catch (caught) { error = caught; }
  return { requests, error, state };
}

(async () => {
  for (const key of [' T-EXACT ', '\tT-EXACT\t', ' null ', ' T/№🙂 ']) {
    const result = await submit('ticket', key);
    assert.equal(result.error, null, key);
    assert.equal(result.requests.length, 1);
    assert.equal(result.requests[0].url, '/api/incidents');
    assert.equal(result.requests[0].options.method, 'POST');
    assert.equal(JSON.parse(result.requests[0].options.body).relations[0].relation_key, key);
    assert.equal(result.state.selectedIncidentId, 7);
  }
  const task = await submit('task', ' 15 ');
  assert.equal(JSON.parse(task.requests[0].options.body).relations[0].relation_key, '15');
  const blank = await submit('ticket', ' \t ');
  assert.equal(blank.requests.length, 0);
  assert.match(blank.error.message, /relation key/);
  console.log('INCIDENT_CREATE_EXACT_TICKET_ID_R35_GREEN=6');
})().catch(error => {
  console.error(error.stack || error);
  process.exitCode = 1;
});
