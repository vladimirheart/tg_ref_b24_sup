'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const context = { window: {} };
for (const file of ['dialogs-avatar-runtime.js', 'dialogs-presentation-runtime.js']) {
  vm.runInNewContext(
    fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js', file), 'utf8'),
    context,
  );
}

const avatarRuntime = context.window.DialogsAvatarRuntime.createRuntime({
  normalizeIdentity: value => String(value || '').trim().toLowerCase(),
  normalizeMessageSenderByType: (_type, sender) => sender === 'operator' ? 'support' : 'user',
  avatarInitial: value => String(value || '').trim().slice(0, 1),
  operatorIdentity: 'viewer',
  operatorDisplayName: 'Текущий оператор',
  operatorAvatarUrl: '/avatars/viewer.png',
});
const presentationRuntime = context.window.DialogsPresentationRuntime.createRuntime({
  operatorDisplayName: 'Текущий оператор',
});
const authorMessage = {
  sender: 'operator',
  messageType: 'operator_message',
  senderUsername: 'other_operator',
  senderName: 'Другой оператор',
  senderAvatarUrl: '/avatars/other.png',
};

const dialogAvatar = avatarRuntime.resolveDialogMessageAvatarSpec(authorMessage, { operatorName: 'Текущий оператор' });
assert.equal(dialogAvatar.src, '/avatars/other.png');
assert.equal(dialogAvatar.initial, 'Д');
assert.equal(dialogAvatar.label, 'Другой оператор');
assert.equal(
  presentationRuntime.resolveSenderLabel(authorMessage, { operatorName: 'Текущий оператор' }),
  'Другой оператор',
);
const workspaceAvatar = avatarRuntime.resolveWorkspaceMessageAvatarSpec({ ...authorMessage, senderRole: 'operator' });
assert.equal(workspaceAvatar.src, '/avatars/other.png');
assert.equal(workspaceAvatar.initial, 'Д');
assert.equal(workspaceAvatar.label, 'Другой оператор');

console.log('DIALOG_MESSAGE_AUTHOR_R309_RESULT=GREEN');
