'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const runtime = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/static/js/dialogs-details-history-runtime.js'),
  'utf8'
);
const context = { window: {} };
vm.runInNewContext(runtime, context);

const filter = context.window.DialogsDetailsHistoryRuntime.createRuntime({
  normalizeMessageSenderByType: (_messageType, sender) => sender === 'client' ? 'user' : sender,
}).filterDialogHistoryMessages;

const messages = [
  { sender: 'client', message: 'Москва', questionnaireAnswer: true },
  { sender: 'client', message: 'Длинное описание проблемы, которое оператор видит как итог опросника.', questionnaireAnswer: true },
  { sender: 'client', message: 'Первое сообщение после опросника', questionnaireAnswer: false },
  { sender: 'client', message: 'Второе сообщение после опросника', questionnaireAnswer: false },
];

assert.deepEqual(
  Array.from(filter(messages), message => message.message),
  [
    'Длинное описание проблемы, которое оператор видит как итог опросника.',
    'Первое сообщение после опросника',
    'Второе сообщение после опросника',
  ]
);
assert.deepEqual(
  Array.from(filter(messages.slice(2)), message => message.message),
  ['Первое сообщение после опросника', 'Второе сообщение после опросника']
);

console.log('DIALOG_QUESTIONNAIRE_FOLLOW_UP_HISTORY_R308_RESULT=GREEN');
