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
    'Москва',
    'Длинное описание проблемы, которое оператор видит как итог опросника.',
    'Первое сообщение после опросника',
    'Второе сообщение после опросника',
  ]
);
assert.deepEqual(
  Array.from(filter(messages.slice(2)), message => message.message),
  ['Первое сообщение после опросника', 'Второе сообщение после опросника']
);

const initialMessages = [
  { sender: 'client', message: 'эй\nчувак', questionnaireAnswer: true },
  { sender: 'client', message: 'выше', questionnaireAnswer: true },
  { sender: 'client', message: 'ага, выше-выше', questionnaireAnswer: false },
];
assert.deepEqual(Array.from(filter(initialMessages), message => message.message),
  ['эй\nчувак', 'выше', 'ага, выше-выше']);

const mixedMessages = [
  { sender: 'client', message: 'Адлер', questionnaireAnswer: true },
  { sender: 'client', message: 'Ещё', questionnaireAnswer: true },
  { sender: 'client', message: '', attachment: 'photo.png', questionnaireAnswer: true },
  { sender: 'client', message: 'читай выше', questionnaireAnswer: true },
  { sender: 'operator', message: 'Смотрю', senderIdentity: 'other_operator' },
];
assert.deepEqual(Array.from(filter(mixedMessages, { location: 'Адлер' })), mixedMessages);
assert.deepEqual(Array.from(filter(mixedMessages.slice(0, 2))), mixedMessages.slice(0, 2));
assert.deepEqual(Array.from(filter(initialMessages.slice(0, 1))), initialMessages.slice(0, 1));
assert.deepEqual(Array.from(filter([
  { sender: 'system', message: 'Оцените работу оператора' },
  initialMessages[0],
  { sender: 'client', message: '5', messageType: 'feedback_rating' },
  { sender: 'client', message: 'Оценка не работает', questionnaireAnswer: true },
])), [initialMessages[0], { sender: 'client', message: 'Оценка не работает', questionnaireAnswer: true }]);
assert.deepEqual(Array.from(filter(null)), []);
assert.deepEqual(Array.from(filter([])), []);

console.log('DIALOG_QUESTIONNAIRE_FOLLOW_UP_HISTORY_R308_RESULT=GREEN');
