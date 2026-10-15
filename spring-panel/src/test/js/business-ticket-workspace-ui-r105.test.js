'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

class Element {
  constructor() {
    this.children = [];
    this.textContent = '';
  }
  replaceChildren() { this.children = []; }
  appendChild(child) { this.children.push(child); }
}

async function main() {
  const source = fs.readFileSync(path.resolve(__dirname,
    '../../main/resources/static/js/business-dialogs.js'), 'utf8');
  const start = source.indexOf('  async function loadWorkspace(');
  const end = source.indexOf('  async function openCard(', start);
  assert.ok(start >= 0 && end > start);

  const ticket = { ticketPublicId: '0123456789abcdef0123456789abcdef',
    businessId: 2, userId: 11, ticketId: 'raw' };
  const pending = [];
  const workspace = new Element();
  const workspaceStatus = new Element();
  const context = {
    workspace, workspaceStatus, document: { createElement: () => new Element() },
    getJson: url => new Promise((resolve, reject) => pending.push({ url, resolve, reject })),
    encodeURIComponent, generation: 1, cardRequest: 7,
    currentTicketPublicId: ticket.ticketPublicId, select: { value: '2' }
  };
  const loadWorkspace = vm.runInNewContext(`${source.slice(start, end)}\nloadWorkspace`, context);

  const first = loadWorkspace(ticket, 1, 7);
  assert.match(pending[0].url, /\/workspace$/);
  pending.shift().resolve({ ticket, relationVerified: true,
    responsible: '<script>operator</script>', categories: ['Доставка'], participants: ['alice'] });
  await first;
  assert.equal(workspace.children.length, 3);
  assert.equal(workspace.children[0].textContent, 'Ответственный: <script>operator</script>');
  assert.equal(workspace.children[1].textContent, 'Категории: Доставка');
  assert.equal(workspace.children[2].textContent, 'Участники: alice');

  const stale = loadWorkspace(ticket, 1, 7);
  context.generation = 2;
  workspaceStatus.textContent = 'Новый контекст';
  pending.shift().resolve({ ticket, relationVerified: true,
    responsible: 'late', categories: [], participants: [] });
  await stale;
  assert.equal(workspaceStatus.textContent, 'Новый контекст');

  const ambiguous = loadWorkspace(ticket, 2, 7);
  pending.shift().resolve({ ticket, relationVerified: false,
    responsible: null, categories: [], participants: [] });
  await ambiguous;
  assert.equal(workspace.children.length, 0);
  assert.match(workspaceStatus.textContent, /не имеет однозначной связи/);

  const mismatched = loadWorkspace(ticket, 2, 7);
  pending.shift().resolve({ ticket: { ...ticket, userId: 22 }, relationVerified: true,
    responsible: 'foreign', categories: ['foreign'], participants: ['foreign'] });
  await mismatched;
  assert.equal(workspace.children.length, 0);
  assert.match(workspaceStatus.textContent, /обращение изменилось/);

  const failed = loadWorkspace(ticket, 2, 7);
  pending.shift().reject(new Error('forbidden'));
  await failed;
  assert.equal(workspace.children.length, 0);
  assert.match(workspaceStatus.textContent, /недоступны/);
  console.log('BUSINESS_TICKET_WORKSPACE_UI_R105_RESULT=GREEN');
}

main().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
