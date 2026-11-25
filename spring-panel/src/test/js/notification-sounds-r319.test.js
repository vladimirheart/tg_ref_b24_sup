const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const source = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/notification-sounds.js'), 'utf8');

function fixture() {
  const handlers = new Map();
  const controls = {};
  const icon = { setAttribute(name, value) { controls.icon = value; } };
  const button = {
    setAttribute(name, value) { controls[name] = value; },
    querySelector() { return icon; },
    addEventListener(name, fn) { controls[name] = fn; },
  };
  const state = { resumes: 0, tones: 0, deny: true, now: 5000 };
  class AudioContext {
    constructor() { this.state = 'suspended'; this.currentTime = 1; this.destination = {}; }
    addEventListener() {}
    async resume() { state.resumes++; if (state.deny) throw new Error('Browser denied autoplay'); this.state = 'running'; }
    createOscillator() { return { frequency: { setValueAtTime() {} }, connect: () => ({ connect() {} }), start() { state.tones++; }, stop() {} }; }
    createGain() { return { gain: { setValueAtTime() {}, exponentialRampToValueAtTime() {} }, connect() {} }; }
  }
  const window = {
    AudioContext, location: { origin: 'http://10.20.30.40:8080' },
    addEventListener(name, fn, options) { handlers.set(name, { fn, options }); },
    setTimeout() { return 1; }, clearTimeout() {},
  };
  const document = { visibilityState: 'hidden', querySelectorAll() { return [button]; } };
  class ClockDate extends Date { static now() { return state.now; } }
  vm.runInNewContext(source, { window, document, Date: ClockDate, Map, Set, URL });
  return { window, document, controls, handlers, state };
}

test('a denied first gesture can be retried and messages sound in a background remote tab', async () => {
  const f = fixture();
  const event = { detail: { reason: 'incoming_client_message' } };
  f.handlers.get('panel:sse:dialogs_changed').fn(event);
  assert.equal(f.state.tones, 0);
  await f.handlers.get('pointerdown').fn();
  assert.equal(f.state.resumes, 1);
  assert.equal(f.state.tones, 0);
  assert.notEqual(f.handlers.get('pointerdown').options?.once, true);
  f.state.deny = false;
  await f.handlers.get('pointerdown').fn();
  f.handlers.get('panel:sse:dialogs_changed').fn(event);
  assert.equal(f.state.tones, 2);
  f.handlers.get('panel:sse:dialogs_changed').fn(event);
  assert.equal(f.state.tones, 2, 'duplicate event is throttled');
  assert.match(f.controls['aria-label'], /Проверить/);
});

test('the sound control explicitly unlocks and previews audio without a provider request', async () => {
  const f = fixture(); f.state.deny = false;
  f.controls.click();
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(f.state.tones, 2);
  assert.equal(f.state.resumes, 1);
  assert.equal(f.controls.icon, 'bi bi-volume-up');
});

test('fallback polling sounds for a new client message but not initial history, filters or live SSE duplicates', async () => {
  const f = fixture(); f.state.deny = false; await f.handlers.get('pointerdown').fn();
  const poll = (count, stamp, queryKey = 'all', sender = 'client') =>
    f.handlers.get('panel:dialogs-polled').fn({ detail: { queryKey, dialogs: [{
      ticketId: ' T-1 ', ticketPublicId: 'a'.repeat(32), unreadCount: count,
      lastMessageTimestamp: stamp, lastMessageSender: sender,
    }] } });
  poll(10, '2026-11-25T15:20:00Z');
  assert.equal(f.state.tones, 0);
  poll(11, '2026-11-25T15:20:01Z');
  assert.equal(f.state.tones, 2);
  f.state.now += 1000;
  poll(11, '2026-11-25T15:20:01Z');
  assert.equal(f.state.tones, 2);
  poll(40, '2026-11-24T15:20:00Z', 'other-filter');
  assert.equal(f.state.tones, 2);
  f.handlers.get('panel:sse-status').fn({ detail: { connected: true } });
  poll(40, '2026-11-25T15:20:02Z', 'other-filter');
  poll(41, '2026-11-25T15:20:03Z', 'other-filter');
  assert.equal(f.state.tones, 2);
  f.handlers.get('panel:sse-status').fn({ detail: { connected: false } });
  poll(41, '2026-11-25T15:20:03Z', 'other-filter');
  assert.equal(f.state.tones, 2);
  poll(0, '2026-11-25T15:20:04Z', 'other-filter', 'operator');
  assert.equal(f.state.tones, 2);
  poll(0, '2026-11-25T15:20:05Z', 'other-filter');
  assert.equal(f.state.tones, 4, 'active-dialog client message also sounds after read count resets');
});
