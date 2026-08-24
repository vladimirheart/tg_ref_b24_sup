(function () {
  if (window.DialogsMediaComposerEnhancements) {
    return;
  }

  const SEND_MODE_KEY = 'dialogs.replySendMode';
  const SEND_MODE_CTRL = 'ctrl_enter';
  const SEND_MODE_ENTER = 'enter';
  const BLOB_FALLBACK_MAX_BYTES = 32 * 1024 * 1024;
  let activeAudio = null;

  function readSendMode() {
    try {
      const stored = String(window.localStorage.getItem(SEND_MODE_KEY) || '').trim();
      return stored === SEND_MODE_ENTER ? SEND_MODE_ENTER : SEND_MODE_CTRL;
    } catch (_error) {
      return SEND_MODE_CTRL;
    }
  }

  function updateSendModeUi(mode) {
    document.querySelectorAll('[data-dialog-send-mode-option]').forEach((option) => {
      const active = option.dataset.dialogSendModeOption === mode;
      option.classList.toggle('is-active', active);
      option.setAttribute('aria-checked', active ? 'true' : 'false');
      const check = option.querySelector('[data-dialog-send-mode-check]');
      if (check) {
        check.textContent = active ? '✓' : '';
      }
    });
    document.querySelectorAll('[data-dialog-send-mode-toggle]').forEach((toggle) => {
      const label = mode === SEND_MODE_ENTER ? 'Enter' : 'Ctrl+Enter';
      toggle.setAttribute('aria-label', `Способ отправки: ${label}`);
      toggle.setAttribute('title', `Способ отправки: ${label}`);
    });
  }

  function saveSendMode(mode) {
    const normalized = mode === SEND_MODE_ENTER ? SEND_MODE_ENTER : SEND_MODE_CTRL;
    try {
      window.localStorage.setItem(SEND_MODE_KEY, normalized);
    } catch (_error) {
      // Browser storage is optional; keep the in-page preference usable.
    }
    updateSendModeUi(normalized);
    return normalized;
  }

  function closeSendModeMenu(wrapper) {
    if (!(wrapper instanceof HTMLElement)) {
      return;
    }
    wrapper.classList.remove('is-open');
    const toggle = wrapper.querySelector('[data-dialog-send-mode-toggle]');
    toggle?.setAttribute('aria-expanded', 'false');
  }

  function closeOtherSendModeMenus(activeWrapper) {
    document.querySelectorAll('.dialog-send-split.is-open').forEach((wrapper) => {
      if (wrapper !== activeWrapper) {
        closeSendModeMenu(wrapper);
      }
    });
  }

  function installSendModeControl(textareaId, sendButtonId, controlId) {
    const textarea = document.getElementById(textareaId);
    const sendButton = document.getElementById(sendButtonId);
    if (!textarea || !sendButton || document.getElementById(controlId)) {
      return;
    }

    if (/^Отправить\s*\([^)]*Enter[^)]*\)\s*$/i.test(String(sendButton.textContent || '').trim())) {
      sendButton.textContent = 'Отправить';
    }

    const wrapper = document.createElement('div');
    wrapper.id = controlId;
    wrapper.className = 'dialog-send-split';
    wrapper.setAttribute('role', 'group');
    wrapper.setAttribute('aria-label', 'Отправка сообщения');

    sendButton.parentNode.insertBefore(wrapper, sendButton);
    wrapper.appendChild(sendButton);

    const toggle = document.createElement('button');
    toggle.type = 'button';
    toggle.className = 'btn btn-primary dialog-send-mode-toggle';
    if (sendButton.classList.contains('btn-sm')) {
      toggle.classList.add('btn-sm');
    }
    toggle.dataset.dialogSendModeToggle = 'true';
    toggle.setAttribute('aria-haspopup', 'menu');
    toggle.setAttribute('aria-expanded', 'false');
    toggle.innerHTML = '<span aria-hidden="true">▾</span>';

    const menu = document.createElement('div');
    menu.className = 'dialog-send-mode-menu';
    menu.setAttribute('role', 'menu');
    menu.innerHTML = `
      <button class="dialog-send-mode-option" type="button" role="menuitemradio" data-dialog-send-mode-option="ctrl_enter" aria-checked="false">
        <span class="dialog-send-mode-check" data-dialog-send-mode-check aria-hidden="true"></span>
        <span>Ctrl+Enter</span>
      </button>
      <button class="dialog-send-mode-option" type="button" role="menuitemradio" data-dialog-send-mode-option="enter" aria-checked="false">
        <span class="dialog-send-mode-check" data-dialog-send-mode-check aria-hidden="true"></span>
        <span>Enter</span>
      </button>`;

    wrapper.append(toggle, menu);

    const syncToggleDisabled = () => {
      toggle.disabled = sendButton.disabled;
    };
    syncToggleDisabled();
    new MutationObserver(syncToggleDisabled).observe(sendButton, {
      attributes: true,
      attributeFilter: ['disabled'],
    });

    toggle.addEventListener('click', (event) => {
      event.preventDefault();
      event.stopPropagation();
      const opening = !wrapper.classList.contains('is-open');
      closeOtherSendModeMenus(wrapper);
      wrapper.classList.toggle('is-open', opening);
      toggle.setAttribute('aria-expanded', opening ? 'true' : 'false');
      if (opening) {
        const selected = menu.querySelector(`[data-dialog-send-mode-option="${readSendMode()}"]`);
        selected?.focus();
      }
    });

    menu.querySelectorAll('[data-dialog-send-mode-option]').forEach((option) => {
      option.addEventListener('click', (event) => {
        event.preventDefault();
        event.stopPropagation();
        saveSendMode(option.dataset.dialogSendModeOption);
        closeSendModeMenu(wrapper);
        textarea.focus();
      });
    });

    wrapper.addEventListener('keydown', (event) => {
      if (event.key === 'Escape') {
        closeSendModeMenu(wrapper);
        toggle.focus();
      }
    });

    textarea.addEventListener('keydown', (event) => {
      if (event.key !== 'Enter' || event.isComposing) {
        return;
      }
      if (event.shiftKey) {
        return;
      }
      const mode = readSendMode();
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

  function resolveOriginalMediaSource(media) {
    const stored = String(media?.dataset?.originalMediaSrc || '').trim();
    if (stored) {
      return stored;
    }
    const source = String(media?.currentSrc || media?.getAttribute?.('src') || '').trim();
    if (source && !source.startsWith('blob:')) {
      media.dataset.originalMediaSrc = source;
    }
    return source;
  }

  function revokeMediaBlob(media) {
    const current = String(media?.dataset?.mediaBlobUrl || '').trim();
    if (current) {
      URL.revokeObjectURL(current);
      delete media.dataset.mediaBlobUrl;
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
    const source = resolveOriginalMediaSource(media);
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

  function formatBlobFallbackError(label, error) {
    const status = Number(error?.mediaHttpStatus);
    if (Number.isFinite(status) && status > 0) {
      return `${label} HTTP ${status}.`;
    }
    return label;
  }

  async function installBlobFallback(media, failureLabel) {
    if (!(media instanceof HTMLMediaElement || media instanceof HTMLImageElement)) {
      return false;
    }
    if (media.dataset.blobFallbackState === 'ready') {
      return true;
    }
    if (media.dataset.blobFallbackState === 'loading' && media._dialogBlobFallbackPromise) {
      return media._dialogBlobFallbackPromise;
    }
    if (media.dataset.blobFallbackState === 'failed') {
      return false;
    }

    const source = resolveOriginalMediaSource(media);
    if (!source || source.startsWith('blob:') || source.startsWith('data:')) {
      media.dataset.blobFallbackState = 'failed';
      return false;
    }

    media.dataset.blobFallbackState = 'loading';
    const promise = (async () => {
      try {
        const response = await fetch(source, {
          credentials: 'same-origin',
          cache: 'no-store',
        });
        if (!response.ok) {
          const error = new Error(`Media request failed with status ${response.status}`);
          error.mediaHttpStatus = response.status;
          throw error;
        }
        const declaredLength = Number(response.headers.get('Content-Length'));
        if (Number.isFinite(declaredLength) && declaredLength > BLOB_FALLBACK_MAX_BYTES) {
          throw new Error('Media payload is too large for blob fallback');
        }
        const blob = await response.blob();
        if (!blob.size) {
          throw new Error('Media payload is empty');
        }
        if (blob.size > BLOB_FALLBACK_MAX_BYTES) {
          throw new Error('Media payload is too large for blob fallback');
        }

        revokeMediaBlob(media);
        const objectUrl = URL.createObjectURL(blob);
        media.dataset.mediaBlobUrl = objectUrl;
        media.dataset.blobFallbackState = 'ready';
        media.classList.remove('is-unavailable');
        media.setAttribute('src', objectUrl);
        if (media instanceof HTMLImageElement && media.dataset.imageSrc) {
          media.dataset.imageSrc = objectUrl;
        }
        if (media instanceof HTMLVideoElement && media.dataset.videoSrc) {
          media.dataset.videoSrc = objectUrl;
        }
        if (media instanceof HTMLMediaElement) {
          media.load();
        }
        return true;
      } catch (error) {
        media.dataset.blobFallbackState = 'failed';
        markMediaUnavailable(media, formatBlobFallbackError(failureLabel, error));
        return false;
      } finally {
        delete media._dialogBlobFallbackPromise;
      }
    })();
    media._dialogBlobFallbackPromise = promise;
    return promise;
  }

  function enhanceAudio(audio) {
    if (!(audio instanceof HTMLAudioElement) || audio.dataset.customPlayerReady === 'true') {
      return;
    }
    audio.dataset.customPlayerReady = 'true';
    resolveOriginalMediaSource(audio);
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
        const recovered = await installBlobFallback(audio, 'Не удалось загрузить аудио.');
        if (recovered) {
          try {
            await audio.play();
            return;
          } catch (_retryError) {
            // Fall through to compact fallback below.
          }
        }
        player.classList.add('is-error');
        if (toggle) toggle.disabled = true;
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
    audio.addEventListener('loadedmetadata', () => {
      player.classList.remove('is-error');
      if (toggle) toggle.disabled = false;
    });
    audio.addEventListener('error', async () => {
      if (audio.dataset.blobFallbackState === 'ready') {
        player.classList.add('is-error');
        if (toggle) toggle.disabled = true;
        markMediaUnavailable(audio, 'Аудио недоступно для воспроизведения.');
        return;
      }
      const recovered = await installBlobFallback(audio, 'Аудио недоступно.');
      if (!recovered) {
        player.classList.add('is-error');
        if (toggle) toggle.disabled = true;
      }
    });
    updateAudioUi(audio, player);
  }

  function enhanceVideo(video) {
    if (!(video instanceof HTMLVideoElement) || video.dataset.mediaEnhancementReady === 'true') {
      return;
    }
    video.dataset.mediaEnhancementReady = 'true';
    resolveOriginalMediaSource(video);

    const shouldAutoplayInline = video.autoplay
      || video.loop
      || video.classList.contains('chat-media-sticker-video');

    const requestInlineAutoplay = () => {
      if (!shouldAutoplayInline || !video.paused || video.ended) {
        return;
      }
      video.muted = true;
      const playPromise = video.play();
      if (playPromise && typeof playPromise.catch === 'function') {
        playPromise.catch(() => {});
      }
    };

    if (shouldAutoplayInline) {
      video.classList.add('chat-media-animation');
      video.muted = true;
      video.autoplay = true;
      video.loop = true;
      video.playsInline = true;
      video.removeAttribute('controls');
      ['loadedmetadata', 'loadeddata', 'canplay'].forEach((eventName) => {
        video.addEventListener(eventName, requestInlineAutoplay);
      });
      requestInlineAutoplay();
    }

    video.addEventListener('error', async () => {
      if (video.dataset.blobFallbackState === 'ready') {
        markMediaUnavailable(video, 'РќРµ СѓРґР°Р»РѕСЃСЊ Р·Р°РіСЂСѓР·РёС‚СЊ РІРёРґРµРѕ РёР»Рё Р°РЅРёРјР°С†РёСЋ.');
        return;
      }
      const recovered = await installBlobFallback(video, 'РќРµ СѓРґР°Р»РѕСЃСЊ Р·Р°РіСЂСѓР·РёС‚СЊ РІРёРґРµРѕ РёР»Рё Р°РЅРёРјР°С†РёСЋ.');
      if (recovered && shouldAutoplayInline) {
        requestInlineAutoplay();
      }
    });
  }

  function enhanceImage(image) {
    if (!(image instanceof HTMLImageElement) || image.dataset.mediaEnhancementReady === 'true') {
      return;
    }
    image.dataset.mediaEnhancementReady = 'true';
    resolveOriginalMediaSource(image);
    image.addEventListener('error', async () => {
      if (image.dataset.blobFallbackState === 'ready') {
        markMediaUnavailable(image, 'Не удалось загрузить изображение.');
        return;
      }
      await installBlobFallback(image, 'Не удалось загрузить изображение.');
    });
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

    document.addEventListener('click', (event) => {
      document.querySelectorAll('details[data-media-info-menu][open]').forEach((menu) => {
        if (!menu.contains(event.target)) {
          menu.removeAttribute('open');
        }
      });
      document.querySelectorAll('.dialog-send-split.is-open').forEach((wrapper) => {
        if (!wrapper.contains(event.target)) {
          closeSendModeMenu(wrapper);
        }
      });
    });

    window.addEventListener('storage', (event) => {
      if (event.key === SEND_MODE_KEY) {
        updateSendModeUi(readSendMode());
      }
    });

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
