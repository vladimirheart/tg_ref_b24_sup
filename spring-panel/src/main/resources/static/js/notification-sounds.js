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

  function unlockAudio() {
    audioUnlocked = true;
    const context = getAudioContext();
    if (context?.state === 'suspended') {
      void context.resume().catch(() => {
        // Браузер может запретить звук до следующего явного действия оператора.
      });
    }
  }

  function getAudioContext() {
    if (audioContext) return audioContext;
    const AudioContextClass = window.AudioContext || window.webkitAudioContext;
    if (typeof AudioContextClass !== 'function') return null;
    try {
      audioContext = new AudioContextClass();
      return audioContext;
    } catch (_error) {
      return null;
    }
  }

  function play(category) {
    const pattern = SOUND_PATTERNS[category];
    if (!audioUnlocked || document.visibilityState !== 'visible' || !pattern) return;
    const context = getAudioContext();
    if (!context || context.state !== 'running') return;
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

  async function loadLatestNotifications() {
    try {
      const response = await fetch('/api/notifications?limit=20', {
        credentials: 'same-origin',
        headers: { Accept: 'application/json' },
      });
      if (!response.ok) return [];
      const payload = await response.json();
      return Array.isArray(payload?.items) ? payload.items : [];
    } catch (_error) {
      return [];
    }
  }

  function rememberNotificationIds(items) {
    items.forEach((item) => {
      const id = String(item?.id || '').trim();
      if (id) knownNotificationIds.add(id);
    });
  }

  async function resolveGenericCategory() {
    const items = await loadLatestNotifications();
    const unseenItems = items.filter((item) => {
      const id = String(item?.id || '').trim();
      return id && !knownNotificationIds.has(id);
    });
    rememberNotificationIds(items);
    return unseenItems.some((item) => isIncidentNotification(item)) ? CATEGORY.INCIDENT : CATEGORY.SYSTEM;
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
      play(await resolveGenericCategory());
    }, GENERIC_DELAY_MS);
  }

  function handleNotificationCount(event) {
    const count = Number(event?.detail?.count);
    if (!Number.isFinite(count)) return;
    if (lastUnreadCount === null) {
      lastUnreadCount = count;
      void loadLatestNotifications().then(rememberNotificationIds);
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
      window.setTimeout(() => void loadLatestNotifications().then(rememberNotificationIds), GENERIC_DELAY_MS);
    } else if (reason === 'dialog_feedback_created') {
      cancelGenericSound();
      play(CATEGORY.RATING);
      window.setTimeout(() => void loadLatestNotifications().then(rememberNotificationIds), GENERIC_DELAY_MS);
    }
  }

  window.addEventListener('pointerdown', unlockAudio, { once: true, passive: true });
  window.addEventListener('keydown', unlockAudio, { once: true });
  window.addEventListener('iguana:notification-count', handleNotificationCount);
  window.addEventListener('iguana:unblock-count', handleUnblockCount);
  window.addEventListener('panel:sse:dialogs_changed', handleDialogEvent);
  window.IguanaNotificationSounds = Object.freeze({ preview: play });
})();
