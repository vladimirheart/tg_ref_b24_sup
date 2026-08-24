(function () {
  if (window.DialogsMediaComposerEnhancements) {
    return;
  }

  const SEND_MODE_KEY = 'dialogs.replySendMode';
  const SEND_MODE_CTRL = 'ctrl_enter';
  const SEND_MODE_ENTER = 'enter';
  let activeAudio = null;

  function readSendMode() {
    try {
      const stored = String(window.localStorage.getItem(SEND_MODE_KEY) || '').trim();
      return stored === SEND_MODE_ENTER ? SEND_MODE_ENTER : SEND_MODE_CTRL;
    } catch (_error) {
      return SEND_MODE_CTRL;
    }
  }

  function saveSendMode(mode) {
    const normalized = mode === SEND_MODE_ENTER ? SEND_MODE_ENTER : SEND_MODE_CTRL;
    try {
      window.localStorage.setItem(SEND_MODE_KEY, normalized);
    } catch (_error) {
      // Browser storage is optional; keep the in-page preference usable.
    }
    document.querySelectorAll('[data-dialog-send-mode-select]').forEach((select) => {
      select.value = normalized;
    });
    document.querySelectorAll('[data-dialog-send-mode-hint]').forEach((hint) => {
      hint.textContent = normalized === SEND_MODE_ENTER
        ? 'Enter — отправить · Shift+Enter — новая строка'
        : 'Ctrl+Enter — отправить · Enter — новая строка';
    });
    return normalized;
  }

  function installSendModeControl(textareaId, sendButtonId, controlId) {
    const textarea = document.getElementById(textareaId);
    const sendButton = document.getElementById(sendButtonId);
    if (!textarea || !sendButton || document.getElementById(controlId)) {
      return;
    }

    const wrapper = document.createElement('div');
    wrapper.id = controlId;
    wrapper.className = 'dialog-send-mode-control';

    const label = document.createElement('span');
    label.className = 'dialog-send-mode-label';
    label.textContent = 'Отправка';

    const select = document.createElement('select');
    select.className = 'form-select form-select-sm dialog-send-mode-select';
    select.dataset.dialogSendModeSelect = 'true';
    select.setAttribute('aria-label', 'Способ отправки сообщения');
    select.innerHTML = '<option value="ctrl_enter">Ctrl+Enter</option><option value="enter">Enter</option>';

    const hint = document.createElement('span');
    hint.className = 'dialog-send-mode-hint';
    hint.dataset.dialogSendModeHint = 'true';

    wrapper.append(label, select, hint);
    sendButton.insertAdjacentElement('afterend', wrapper);

    select.addEventListener('change', () => saveSendMode(select.value));

    textarea.addEventListener('keydown', (event) => {
      if (event.key !== 'Enter' || event.isComposing) {
        return;
      }
      const mode = readSendMode();
      if (event.shiftKey) {
        if (event.ctrlKey || event.metaKey || event.altKey) {
          event.stopImmediatePropagation();
        }
        return;
      }
      const shortcutPressed = event.ctrlKey || event.metaKey || event.altKey;
      const shouldSend = mode === SEND_MODE_ENTER || shortcutPressed;
      if (!shouldSend) {
        return;
      }
      event.preventDefault();
      event.stopImmediatePropagation();
      if (!sendButton.disabled) {
        sendButton.click();
      }
    }, true);
  }

  function formatAudioTime(seconds) {
    const safe = Number(seconds);
    if (!Number.isFinite(safe) || safe < 0) {
      return '0:00';
    }
    const total = Math.floor(safe);
    const minutes = Math.floor(total / 60);
    const remainder = String(total % 60).padStart(2, '0');
    return `${minutes}:${remainder}`;
  }

  function updateAudioUi(audio, player) {
    const toggle = player.querySelector('[data-audio-toggle]');
    const progress = player.querySelector('[data-audio-progress]');
    const current = player.querySelector('[data-audio-current]');
    const duration = player.querySelector('[data-audio-duration]');
    if (toggle) {
      toggle.textContent = audio.paused ? '▶' : '❚❚';
      toggle.setAttribute('aria-label', audio.paused ? 'Воспроизвести аудио' : 'Поставить аудио на паузу');
    }
    if (current) {
      current.textContent = formatAudioTime(audio.currentTime);
    }
    if (duration) {
      duration.textContent = Number.isFinite(audio.duration) ? formatAudioTime(audio.duration) : '—:—';
    }
    if (progress) {
      const ratio = Number.isFinite(audio.duration) && audio.duration > 0
        ? Math.min(1, Math.max(0, audio.currentTime / audio.duration))
        : 0;
      progress.value = String(Math.round(ratio * 1000));
    }
  }

  function markMediaUnavailable(media, label) {
    const container = media.closest('.chat-media');
    if (!container || container.querySelector('.chat-media-load-error')) {
      return;
    }
    media.classList.add('is-unavailable');
    const error = document.createElement('div');
    error.className = 'chat-media-load-error';
    const text = document.createElement('span');
    text.textContent = label || 'Не удалось загрузить превью.';
    const source = String(media.currentSrc || media.getAttribute('src') || '').trim();
    if (source) {
      const link = document.createElement('a');
      link.className = 'chat-media-load-error-link';
      link.href = source;
      link.target = '_blank';
      link.rel = 'noopener';
      link.textContent = 'Открыть файл';
      error.append(text, link);
    } else {
      error.append(text);
    }
    container.prepend(error);
  }

  function enhanceAudio(audio) {
    if (!(audio instanceof HTMLAudioElement) || audio.dataset.customPlayerReady === 'true') {
      return;
    }
    audio.dataset.customPlayerReady = 'true';
    audio.removeAttribute('controls');
    audio.classList.add('chat-media-audio-source');

    const player = document.createElement('div');
    player.className = 'dialog-custom-audio-player';
    player.innerHTML = `
      <button class="dialog-custom-audio-toggle" type="button" data-audio-toggle aria-label="Воспроизвести аудио">▶</button>
      <div class="dialog-custom-audio-body">
        <div class="dialog-custom-audio-title" data-audio-title></div>
        <input class="dialog-custom-audio-progress" data-audio-progress type="range" min="0" max="1000" value="0" aria-label="Позиция воспроизведения">
        <div class="dialog-custom-audio-time"><span data-audio-current>0:00</span><span data-audio-duration>—:—</span></div>
      </div>`;
    const title = player.querySelector('[data-audio-title]');
    if (title) {
      title.textContent = String(audio.closest('.chat-media')?.querySelector('.chat-media-meta-title')?.textContent || 'Аудио');
    }
    audio.insertAdjacentElement('afterend', player);

    const toggle = player.querySelector('[data-audio-toggle]');
    const progress = player.querySelector('[data-audio-progress]');

    toggle?.addEventListener('click', async () => {
      if (activeAudio && activeAudio !== audio) {
        activeAudio.pause();
      }
      activeAudio = audio;
      if (!audio.paused) {
        audio.pause();
        return;
      }
      try {
        await audio.play();
      } catch (_error) {
        markMediaUnavailable(audio, 'Не удалось воспроизвести аудио.');
      }
    });

    progress?.addEventListener('input', () => {
      if (!Number.isFinite(audio.duration) || audio.duration <= 0) {
        return;
      }
      const position = Number(progress.value) / 1000;
      audio.currentTime = Math.max(0, Math.min(audio.duration, audio.duration * position));
      updateAudioUi(audio, player);
    });

    ['loadedmetadata', 'durationchange', 'timeupdate', 'play', 'pause', 'ended'].forEach((eventName) => {
      audio.addEventListener(eventName, () => updateAudioUi(audio, player));
    });
    audio.addEventListener('error', () => {
      player.classList.add('is-error');
      if (toggle) toggle.disabled = true;
      markMediaUnavailable(audio, 'Аудио недоступно для воспроизведения.');
    });
    updateAudioUi(audio, player);
  }

  function enhanceVideo(video) {
    if (!(video instanceof HTMLVideoElement) || video.dataset.mediaEnhancementReady === 'true') {
      return;
    }
    video.dataset.mediaEnhancementReady = 'true';
    if (video.loop) {
      video.classList.add('chat-media-animation');
      video.muted = true;
      video.autoplay = true;
      video.playsInline = true;
      video.removeAttribute('controls');
      video.play().catch(() => {});
    }
    video.addEventListener('error', () => markMediaUnavailable(video, 'Не удалось загрузить видео или анимацию.'));
  }

  function enhanceImage(image) {
    if (!(image instanceof HTMLImageElement) || image.dataset.mediaEnhancementReady === 'true') {
      return;
    }
    image.dataset.mediaEnhancementReady = 'true';
    image.addEventListener('error', () => markMediaUnavailable(image, 'Не удалось загрузить изображение.'));
  }

  function enhanceMedia(root) {
    const scope = root instanceof Element || root instanceof Document ? root : document;
    if (scope.matches?.('audio.chat-media-audio')) enhanceAudio(scope);
    if (scope.matches?.('video.chat-media-preview')) enhanceVideo(scope);
    if (scope.matches?.('img.chat-media-preview')) enhanceImage(scope);
    scope.querySelectorAll?.('audio.chat-media-audio').forEach(enhanceAudio);
    scope.querySelectorAll?.('video.chat-media-preview').forEach(enhanceVideo);
    scope.querySelectorAll?.('img.chat-media-preview').forEach(enhanceImage);
  }

  function initialize() {
    installSendModeControl('dialogReplyText', 'dialogReplySend', 'dialogReplySendModeControl');
    installSendModeControl('workspaceComposerText', 'workspaceComposerSend', 'workspaceReplySendModeControl');
    saveSendMode(readSendMode());
    enhanceMedia(document);

    const observer = new MutationObserver((mutations) => {
      mutations.forEach((mutation) => {
        mutation.addedNodes.forEach((node) => {
          if (node instanceof Element) {
            enhanceMedia(node);
          }
        });
      });
    });
    observer.observe(document.body, { childList: true, subtree: true });
  }

  window.DialogsMediaComposerEnhancements = {
    initialize,
    readSendMode,
    saveSendMode,
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initialize, { once: true });
  } else {
    initialize();
  }
})();
