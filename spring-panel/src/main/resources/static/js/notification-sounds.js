/* Короткие синтезированные сигналы: без сетевых ассетов и лицензируемых семплов. */
(function () {
  'use strict';

  if (window.IguanaNotificationSounds) return;

  const CATEGORY = Object.freeze({
    SYSTEM: 'system',
    INCIDENT: 'incident',
    MESSAGE: 'message',
    RATING: 'rating',
    UNBLOCK: 'unblock',
  });
  const GENERIC_DELAY_MS = 420;
  const SPECIFIC_SUPPRESSION_MS = 1000;
  const MIN_INTERVAL_MS = Object.freeze({
    [CATEGORY.SYSTEM]: 1000,
    [CATEGORY.INCIDENT]: 2500,
    [CATEGORY.MESSAGE]: 700,
    [CATEGORY.RATING]: 900,
    [CATEGORY.UNBLOCK]: 1200,
  });
  const SOUND_PATTERNS = Object.freeze({
    [CATEGORY.SYSTEM]: [[698, 0, 0.09, 'sine', 0.035]],
    [CATEGORY.INCIDENT]: [[440, 0, 0.11, 'triangle', 0.07], [659, 0.15, 0.17, 'triangle', 0.07]],
    [CATEGORY.MESSAGE]: [[740, 0, 0.065, 'sine', 0.045], [988, 0.085, 0.09, 'sine', 0.045]],
    [CATEGORY.RATING]: [[659, 0, 0.07, 'sine', 0.04], [831, 0.08, 0.07, 'sine', 0.04], [988, 0.16, 0.11, 'sine', 0.04]],
    [CATEGORY.UNBLOCK]: [[494, 0, 0.09, 'triangle', 0.05], [622, 0.12, 0.12, 'triangle', 0.05]],
  });

  let audioContext = null;
  let audioUnlocked = false;
  let lastUnreadCount = null;
  let lastUnblockCount = null;
  let pendingGenericTimer = 0;
  let suppressGenericUntil = 0;
  const lastPlayedAt = new Map();
  const knownNotificationIds = new Set();
  let latestNotificationCategory = CATEGORY.SYSTEM;
  let streamConnected = false;
  let polledDialogs = null;

  function handlePolledDialogs(event) {
    const detail = event?.detail;
    if (!detail || typeof detail.queryKey !== 'string' || !Array.isArray(detail.dialogs)) return;
    const rows = new Map();
    let latest = 0;
    let incoming = false;
    const previous = polledDialogs?.queryKey === detail.queryKey ? polledDialogs : null;
    for (const row of detail.dialogs) {
      if (!row || typeof row.ticketId !== 'string' || !row.ticketId) continue;
      const key = JSON.stringify([row.ticketId, row.ticketPublicId, row.userId, row.channelId, row.businessId, row.scopeKind]);
      const stamp = Number(row.lastActivityEpoch) || Date.parse(row.lastMessageTimestamp || row.createdAt || '') || 0;
      const unread = Math.max(0, Number(row.unreadCount) || 0);
      const sender = String(row.lastMessageSender || '').toLowerCase();
      const client = sender && !['operator', 'support', 'admin', 'system', 'ai_agent'].includes(sender);
      const old = previous?.rows.get(key);
      if (previous && (old ? unread > old.unread || (client && stamp > old.stamp)
        : client && unread > 0 && stamp > previous.latest)) incoming = true;
      rows.set(key, { stamp, unread });
      latest = Math.max(latest, stamp);
    }
    polledDialogs = { queryKey: detail.queryKey, rows, latest };
    if (!streamConnected && incoming) {
      cancelGenericSound();
      play(CATEGORY.MESSAGE);
    }
  }

  function updateSoundControls() {
    const ready = audioUnlocked && audioContext?.state === 'running';
    document.querySelectorAll('[data-notification-sound-enable]').forEach(button => {
      button.setAttribute('aria-label', ready ? 'Проверить звук оповещений' : 'Включить звук оповещений');
      button.setAttribute('title', ready ? 'Проверить звук оповещений' : 'Включить звук оповещений');
      button.querySelector('i')?.setAttribute('class', ready ? 'bi bi-volume-up' : 'bi bi-volume-mute');
    });
  }

  async function unlockAudio() {
    const context = getAudioContext();
    if (!context) return false;
    try { if (context.state !== 'running') await context.resume(); }
    catch (_) { /* Keep gesture listeners: the next explicit action may unlock audio. */ }
    audioUnlocked = context.state === 'running';
    updateSoundControls();
    return audioUnlocked;
  }

  function getAudioContext() {
    if (audioContext && audioContext.state !== 'closed') return audioContext;
    const AudioContextClass = window.AudioContext || window.webkitAudioContext;
    if (typeof AudioContextClass !== 'function') return null;
    try {
      audioContext = new AudioContextClass();
      audioContext.addEventListener?.('statechange', updateSoundControls);
      return audioContext;
    } catch (_error) {
      return null;
    }
  }

  function play(category) {
    const pattern = SOUND_PATTERNS[category];
    if (!audioUnlocked || !pattern) return;
    const context = getAudioContext();
    if (!context) return;
    if (context.state !== 'running') {
      void unlockAudio().then(ready => { if (ready) play(category); });
      return;
    }
    const now = Date.now();
    const previous = lastPlayedAt.get(category) || 0;
    if (now - previous < (MIN_INTERVAL_MS[category] || 0)) return;
    lastPlayedAt.set(category, now);

    const startAt = context.currentTime + 0.01;
    pattern.forEach(([frequency, offset, duration, waveform, volume]) => {
      const oscillator = context.createOscillator();
      const gain = context.createGain();
      const toneStart = startAt + offset;
      const toneEnd = toneStart + duration;
      oscillator.type = waveform;
      oscillator.frequency.setValueAtTime(frequency, toneStart);
      gain.gain.setValueAtTime(0.0001, toneStart);
      gain.gain.exponentialRampToValueAtTime(volume, toneStart + 0.012);
      gain.gain.exponentialRampToValueAtTime(0.0001, toneEnd);
      oscillator.connect(gain).connect(context.destination);
      oscillator.start(toneStart);
      oscillator.stop(toneEnd + 0.02);
    });
  }

  function cancelGenericSound() {
    if (pendingGenericTimer) {
      window.clearTimeout(pendingGenericTimer);
      pendingGenericTimer = 0;
    }
    suppressGenericUntil = Date.now() + SPECIFIC_SUPPRESSION_MS;
  }

  function rememberNotificationIds(items) {
    items.forEach((item) => {
      const id = String(item?.id || '').trim();
      if (id) knownNotificationIds.add(id);
    });
  }

  function handleNotificationSnapshot(event) {
    const items = Array.isArray(event?.detail?.items) ? event.detail.items : [];
    const unseenItems = items.filter((item) => {
      const id = String(item?.id || '').trim();
      return id && !knownNotificationIds.has(id);
    });
    rememberNotificationIds(items);
    latestNotificationCategory = unseenItems.some((item) => isIncidentNotification(item)) ? CATEGORY.INCIDENT : CATEGORY.SYSTEM;
  }

  function isIncidentNotification(item) {
    if (typeof item?.url !== 'string' || !item.url.trim()) return false;
    try {
      return new URL(item.url, window.location.origin).pathname === '/incidents';
    } catch (_error) {
      return false;
    }
  }

  function queueGenericSound() {
    if (pendingGenericTimer || Date.now() < suppressGenericUntil) return;
    pendingGenericTimer = window.setTimeout(async () => {
      pendingGenericTimer = 0;
      if (Date.now() < suppressGenericUntil) return;
      play(latestNotificationCategory);
    }, GENERIC_DELAY_MS);
  }

  function handleNotificationCount(event) {
    const count = Number(event?.detail?.count);
    if (!Number.isFinite(count)) return;
    if (lastUnreadCount === null) {
      lastUnreadCount = count;
      return;
    }
    if (count > lastUnreadCount) queueGenericSound();
    lastUnreadCount = count;
  }

  function handleUnblockCount(event) {
    const count = Number(event?.detail?.count);
    if (!Number.isFinite(count)) return;
    if (lastUnblockCount === null) {
      lastUnblockCount = count;
      return;
    }
    if (count > lastUnblockCount) play(CATEGORY.UNBLOCK);
    lastUnblockCount = count;
  }

  function handleDialogEvent(event) {
    const reason = String(event?.detail?.reason || '').trim();
    if (reason === 'incoming_client_message') {
      cancelGenericSound();
      play(CATEGORY.MESSAGE);
    } else if (reason === 'dialog_feedback_created') {
      cancelGenericSound();
      play(CATEGORY.RATING);
    }
  }

  window.addEventListener('pointerdown', unlockAudio, { passive: true });
  window.addEventListener('keydown', unlockAudio);
  window.addEventListener('touchend', unlockAudio, { passive: true });
  document.querySelectorAll('[data-notification-sound-enable]').forEach(button => {
    button.addEventListener('click', () => void unlockAudio().then(ready => { if (ready) play(CATEGORY.MESSAGE); }));
  });
  updateSoundControls();
  window.addEventListener('iguana:notification-count', handleNotificationCount);
  window.addEventListener('iguana:notification-snapshot', handleNotificationSnapshot);
  window.addEventListener('iguana:unblock-count', handleUnblockCount);
  window.addEventListener('panel:sse:dialogs_changed', handleDialogEvent);
  window.addEventListener('panel:dialogs-polled', handlePolledDialogs);
  window.addEventListener('panel:sse-status', event => {
    const next = event?.detail?.connected === true;
    if (next !== streamConnected) polledDialogs = null;
    streamConnected = next;
  });
  window.IguanaNotificationSounds = Object.freeze({ preview: play });
})();
