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
  append(...children) { this.children.push(...children); }
}

async function main() {
  const source = fs.readFileSync(path.resolve(__dirname,
    '../../main/resources/static/js/business-dialogs.js'), 'utf8');
  const start = source.indexOf('  async function loadAnswers(');
  const end = source.indexOf('  async function openCard(', start);
  assert.ok(start >= 0 && end > start, 'answers UI implementation not found');

  const pending = [];
  const answers = new Element();
  const answersStatus = new Element();
  const ticket = { ticketPublicId: '0123456789abcdef0123456789abcdef', businessId: 2 };
  const context = {
    answers, answersStatus, document: { createElement: () => new Element() },
    getJson: url => new Promise((resolve, reject) => pending.push({ url, resolve, reject })),
    encodeURIComponent, generation: 1, cardRequest: 7,
    currentTicketPublicId: ticket.ticketPublicId, select: { value: '2' }
  };
  const loadAnswers = vm.runInNewContext(`${source.slice(start, end)}\nloadAnswers`, context);

  const first = loadAnswers(ticket, 1, 7);
  assert.match(pending[0].url, /\/answers$/);
  pending.shift().resolve({ ticket, relationVerified: true, answers: [
    { questionId: 'q1', questionText: 'Адрес', valueLabel: null,
      valueText: '<script>безопасный текст</script>' }
  ] });
  await first;
  assert.match(answersStatus.textContent, /1/);
  assert.equal(answers.children.length, 1);
  assert.equal(answers.children[0].children[0].textContent, 'Адрес');
  assert.equal(answers.children[0].children[1].textContent,
    ': <script>безопасный текст</script>');

  const stale = loadAnswers(ticket, 1, 7);
  context.generation = 2;
  answersStatus.textContent = 'Новый контекст';
  pending.shift().resolve({ ticket, relationVerified: true, answers: [] });
  await stale;
  assert.equal(answersStatus.textContent, 'Новый контекст');

  const ambiguous = loadAnswers(ticket, 2, 7);
  pending.shift().resolve({ ticket, relationVerified: false, answers: [] });
  await ambiguous;
  assert.equal(answers.children.length, 0);
  assert.match(answersStatus.textContent, /не имеет однозначной связи/);

  const failed = loadAnswers(ticket, 2, 7);
  pending.shift().reject(new Error('forbidden'));
  await failed;
  assert.equal(answers.children.length, 0);
  assert.match(answersStatus.textContent, /недоступны/);
  console.log('BUSINESS_TICKET_ANSWERS_UI_TEST_RESULT=GREEN');
}

main().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
