(function () {
  if (window.DialogsDetailsHistoryRuntime) {
    return;
  }

  function createRuntime(options = {}) {
    const elements = options.elements || {};
    const state = {
      lastHistoryMarker: null,
      historyLoading: false,
      currentMessages: [],
      previousBatches: [],
      previousNextOffset: 0,
      previousHasMore: true,
      previousLoading: false,
      activeAudioPlayer: null,
      activeAudioSource: null,
      mediaImageScale: 1,
      historyPollTimer: null,
      activeReplyToTelegramId: null,
      stickerPreviewCache: new Map(),
    };

    function getActiveDialogState() {
      const activeState = typeof options.getActiveDialogState === 'function'
        ? options.getActiveDialogState()
        : null;
      return activeState && typeof activeState === 'object'
        ? activeState
        : { ticketId: '', channelId: null, row: null, context: {} };
    }

    function getActiveWorkspaceState() {
      const workspaceState = typeof options.getActiveWorkspaceState === 'function'
        ? options.getActiveWorkspaceState()
        : null;
      return workspaceState && typeof workspaceState === 'object'
        ? workspaceState
        : {
          ticketId: '',
          payload: null,
          composerText: null,
          composerSend: null,
          composerMedia: null,
          composerTicketId: '',
          messagesState: null,
          messagesError: null,
        };
    }

    function escapeHtml(value) {
      return typeof options.escapeHtml === 'function'
        ? options.escapeHtml(value)
        : String(value ?? '');
    }

    function formatTimestamp(value, formatOptions = {}) {
      return typeof options.formatTimestamp === 'function'
        ? options.formatTimestamp(value, formatOptions)
        : String(value || '').trim();
    }

    function withChannelParam(path, channelId) {
      return typeof options.withChannelParam === 'function'
        ? options.withChannelParam(path, channelId)
        : path;
    }

    function notify(message, type = 'info') {
      if (typeof options.showNotification === 'function') {
        options.showNotification(message, type);
      }
    }

    function resetReplyTarget() {
      state.activeReplyToTelegramId = null;
      if (elements.replyTarget) {
        elements.replyTarget.classList.add('d-none');
      }
      if (elements.replyTargetText) {
        elements.replyTargetText.textContent = '';
      }
      if (elements.detailsReplyText) {
        elements.detailsReplyText.placeholder = 'Введите ответ...';
      }
    }

    function setReplyTarget(messageId, preview) {
      const normalizedMessageId = Number.parseInt(messageId, 10);
      if (!Number.isFinite(normalizedMessageId)) {
        resetReplyTarget();
        return;
      }
      state.activeReplyToTelegramId = normalizedMessageId;
      if (elements.detailsReplyText) {
        elements.detailsReplyText.placeholder = `Ответ на сообщение #${normalizedMessageId}`;
      }
      if (elements.replyTarget) {
        elements.replyTarget.classList.remove('d-none');
      }
      if (elements.replyTargetText) {
        const safePreview = String(preview || '').trim();
        elements.replyTargetText.textContent = safePreview || `Сообщение #${normalizedMessageId}`;
      }
    }

    function getActiveReplyToTelegramId() {
      return Number.isFinite(Number(state.activeReplyToTelegramId))
        ? Number(state.activeReplyToTelegramId)
        : null;
    }

    function resetMediaPreview() {
      state.mediaImageScale = 1;
      if (elements.mediaPreviewImage) {
        elements.mediaPreviewImage.style.transform = 'scale(1)';
        elements.mediaPreviewImage.removeAttribute('src');
        elements.mediaPreviewImage.classList.add('d-none');
      }
      if (elements.mediaPreviewVideo) {
        elements.mediaPreviewVideo.pause();
        elements.mediaPreviewVideo.removeAttribute('src');
        elements.mediaPreviewVideo.load();
        elements.mediaPreviewVideo.classList.add('d-none');
      }
      if (elements.mediaPreviewImageControls) {
        elements.mediaPreviewImageControls.classList.add('d-none');
      }
      if (elements.mediaPreviewDownloadLink) {
        elements.mediaPreviewDownloadLink.classList.add('d-none');
        elements.mediaPreviewDownloadLink.setAttribute('href', '#');
      }
    }

    function showImagePreview(src, name) {
      if (!src || !elements.mediaPreviewModalEl || !elements.mediaPreviewImage) {
        return;
      }
      resetMediaPreview();
      elements.mediaPreviewImage.src = src;
      elements.mediaPreviewImage.alt = name || 'Изображение';
      elements.mediaPreviewImage.classList.remove('d-none');
      if (elements.mediaPreviewImageControls) {
        elements.mediaPreviewImageControls.classList.remove('d-none');
      }
      if (elements.mediaPreviewDownloadLink) {
        elements.mediaPreviewDownloadLink.classList.remove('d-none');
        elements.mediaPreviewDownloadLink.setAttribute('href', src);
        elements.mediaPreviewDownloadLink.setAttribute('download', name || 'image');
      }
      options.showModalSafe?.(elements.mediaPreviewModalEl, elements.mediaPreviewModal);
    }

    function showVideoPreview(src) {
      if (!src || !elements.mediaPreviewModalEl || !elements.mediaPreviewVideo) {
        return;
      }
      resetMediaPreview();
      elements.mediaPreviewVideo.src = src;
      elements.mediaPreviewVideo.classList.remove('d-none');
      elements.mediaPreviewVideo.play().catch(() => {});
      if (elements.mediaPreviewDownloadLink) {
        elements.mediaPreviewDownloadLink.classList.remove('d-none');
        elements.mediaPreviewDownloadLink.setAttribute('href', src);
        elements.mediaPreviewDownloadLink.setAttribute('download', 'video');
      }
      options.showModalSafe?.(elements.mediaPreviewModalEl, elements.mediaPreviewModal);
    }

    function adjustMediaPreviewZoom(direction) {
      if (!elements.mediaPreviewImage) {
        return;
      }
      if (direction === 'in') {
        state.mediaImageScale = Math.min(3, state.mediaImageScale + 0.2);
      } else {
        state.mediaImageScale = Math.max(0.4, state.mediaImageScale - 0.2);
      }
      elements.mediaPreviewImage.style.transform = `scale(${state.mediaImageScale})`;
    }

    function handleMediaPreviewEscape(event) {
      if (event.key !== 'Escape') return;
      if (!elements.mediaPreviewModalEl?.classList.contains('show')) return;
      event.preventDefault();
      event.stopPropagation();
      if (typeof event.stopImmediatePropagation === 'function') {
        event.stopImmediatePropagation();
      }
      options.hideModalSafe?.(elements.mediaPreviewModalEl, elements.mediaPreviewModal);
    }

    function resolveAttachmentKind(messageType, attachment) {
      const type = String(messageType || '').toLowerCase();
      if (type.includes('sticker')) return 'sticker';
      if (type.includes('animation')) return 'animation';
      if (type.includes('video') || type.includes('videonote') || type.includes('video_note')) return 'video';
      if (type.includes('audio') || type.includes('voice')) return 'audio';
      if (type.includes('photo') || type.includes('image')) return 'image';
      if (attachment) {
        const lower = attachment.toLowerCase();
        if (lower.endsWith('.tgs')) return 'sticker';
        if (lower.endsWith('.gif')) return 'animation';
        if (/\.(mp4|webm|mov|m4v)$/i.test(lower)) return 'video';
        if (/\.(mp3|wav|ogg|m4a)$/i.test(lower)) return 'audio';
        if (/\.(png|jpe?g|webp|bmp)$/i.test(lower)) return 'image';
      }
      return 'file';
    }

    function isTgsStickerAttachment(attachment) {
      return /\.tgs($|\?)/i.test(String(attachment || ''));
    }

    function isVideoStickerAttachment(attachment) {
      return /\.webm($|\?)/i.test(String(attachment || ''));
    }

    function isImageStickerAttachment(attachment) {
      return /\.(webp|png|jpe?g|bmp)($|\?)/i.test(String(attachment || ''));
    }

    function escapeAttribute(value) {
      return escapeHtml(value).replace(/"/g, '&quot;');
    }

    function resolveAttachmentName(message, attachment) {
      const extractExtension = (value) => {
        if (!value) return '';
        const clean = String(value).split('?')[0].split('#')[0];
        const lastSegment = clean.split('/').pop()?.split('\\').pop() || clean;
        const dotIndex = lastSegment.lastIndexOf('.');
        return dotIndex > 0 ? lastSegment.slice(dotIndex) : '';
      };

      if (message) {
        const hasPath = /[\\/]/.test(message) || /^https?:\/\//i.test(message);
        if (!hasPath) return message;
        const extension = extractExtension(message);
        return extension ? `Файл ${extension}` : 'Файл';
      }

      if (!attachment) return 'Файл';
      const extension = extractExtension(attachment);
      return extension ? `Файл ${extension}` : 'Файл';
    }

    function resolveAttachmentTypeLabel(message, kind = '') {
      const normalizedKind = String(kind || '').trim()
        || resolveAttachmentKind(message?.messageType, message?.attachment);
      const normalizedType = String(message?.messageType || '').toLowerCase();
      if (normalizedType.includes('video_note') || normalizedType.includes('videonote')) return 'Видеосообщение';
      if (normalizedType.includes('voice')) return 'Голосовое сообщение';
      if (normalizedType.includes('audio')) return 'Аудио';
      if (normalizedType.includes('video')) return 'Видео';
      if (normalizedType.includes('animation')) return 'Анимация';
      if (normalizedType.includes('sticker')) return 'Стикер';
      if (normalizedType.includes('photo') || normalizedType.includes('image')) return 'Изображение';
      if (normalizedKind === 'audio') return 'Аудио';
      if (normalizedKind === 'video') return 'Видео';
      if (normalizedKind === 'animation') return 'Анимация';
      if (normalizedKind === 'sticker') return 'Стикер';
      if (normalizedKind === 'image') return 'Изображение';
      return 'Файл';
    }

    function buildMediaInfoMarkup(message, name, kind) {
      if (!message?.attachment) return '';
      const attachmentUrl = escapeAttribute(message.attachment);
      const typeLabel = resolveAttachmentTypeLabel(message, kind);
      const normalizedName = String(name || '').trim();
      const shouldShowFileName = normalizedName && normalizedName !== typeLabel;
      return `
        <div class="chat-media-info">
          <button class="chat-media-info-toggle" type="button" aria-label="Информация о вложении">i</button>
          <div class="chat-media-info-panel">
            <div class="chat-media-info-label">${escapeHtml(typeLabel)}</div>
            ${shouldShowFileName ? `<div class="chat-media-info-value">${escapeHtml(normalizedName)}</div>` : ''}
            <a class="btn btn-sm btn-outline-secondary" href="${attachmentUrl}" download target="_blank" rel="noopener">
              Скачать
            </a>
          </div>
        </div>
      `;
    }

    function buildMediaMarkup(message) {
      if (!message?.attachment) return '';
      const kind = resolveAttachmentKind(message.messageType, message.attachment);
      const name = resolveAttachmentName(message.message, message.attachment);
      const attachmentUrl = escapeAttribute(message.attachment);
      const mediaInfo = buildMediaInfoMarkup(message, name, kind);
      if (kind === 'audio') {
        return `
          <div class="chat-media chat-media--audio">
            <audio class="chat-media-audio" src="${attachmentUrl}" controls preload="metadata"></audio>
            ${mediaInfo}
          </div>
        `;
      }
      if (kind === 'video') {
        return `
          <div class="chat-media">
            <video class="chat-media-preview video" src="${attachmentUrl}" data-video-src="${attachmentUrl}" data-media-name="${escapeAttribute(name)}" muted playsinline preload="metadata"></video>
            ${mediaInfo}
          </div>
        `;
      }
      if (kind === 'animation') {
        const isGif = /\.gif($|\?)/i.test(message.attachment);
        const preview = isGif
          ? `<img class=\"chat-media-preview\" src=\"${attachmentUrl}\" alt=\"${escapeAttribute(name)}\" data-image-src=\"${attachmentUrl}\" data-media-name=\"${escapeAttribute(name)}\">`
          : `<video class=\"chat-media-preview video\" src=\"${attachmentUrl}\" data-video-src=\"${attachmentUrl}\" data-media-name=\"${escapeAttribute(name)}\" controls loop muted playsinline preload=\"metadata\"></video>`;
        return `
          <div class="chat-media">
            ${preview}
            ${mediaInfo}
          </div>
        `;
      }
      if (kind === 'sticker') {
        let preview = `
          <div class="chat-media-preview chat-media-sticker">
            <div class="chat-media-sticker-status text-muted">Предпросмотр стикера недоступен.</div>
          </div>
        `;
        if (isTgsStickerAttachment(message.attachment)) {
          preview = `
            <div class="chat-media-preview chat-media-sticker" data-sticker-src="${attachmentUrl}" data-media-name="${escapeAttribute(name)}">
              <div class="chat-media-sticker-status text-muted">Загрузка стикера…</div>
            </div>
          `;
        } else if (isVideoStickerAttachment(message.attachment)) {
          preview = `<video class="chat-media-preview" src="${attachmentUrl}" autoplay loop muted playsinline preload="metadata"></video>`;
        } else if (isImageStickerAttachment(message.attachment)) {
          preview = `<img class="chat-media-preview" src="${attachmentUrl}" alt="${escapeAttribute(name)}" data-image-src="${attachmentUrl}" data-media-name="${escapeAttribute(name)}">`;
        }
        return `
          <div class="chat-media">
            ${preview}
            ${mediaInfo}
          </div>
        `;
      }
      if (kind === 'image') {
        return `
          <div class="chat-media">
            <img class="chat-media-preview" src="${attachmentUrl}" alt="${escapeAttribute(name)}" data-image-src="${attachmentUrl}" data-media-name="${escapeAttribute(name)}">
            ${mediaInfo}
          </div>
        `;
      }
      return `
        <div class="chat-media chat-media--file">
          <div class="chat-media-file-tile">Вложение</div>
          ${mediaInfo}
        </div>
      `;
    }

    async function loadTgsAnimationData(src) {
      const cacheKey = String(src || '').trim();
      if (!cacheKey) {
        throw new Error('Sticker source is empty');
      }
      if (state.stickerPreviewCache.has(cacheKey)) {
        return state.stickerPreviewCache.get(cacheKey);
      }
      const loader = (async () => {
        const response = await fetch(cacheKey, {
          credentials: 'same-origin',
          cache: 'force-cache',
        });
        if (!response.ok) {
          throw new Error(`Sticker request failed with status ${response.status}`);
        }
        if (typeof DecompressionStream !== 'function') {
          throw new Error('Browser does not support gzip decompression for TGS');
        }
        const decompressed = response.body.pipeThrough(new DecompressionStream('gzip'));
        const payload = await new Response(decompressed).text();
        return JSON.parse(payload);
      })();
      state.stickerPreviewCache.set(cacheKey, loader);
      try {
        return await loader;
      } catch (error) {
        state.stickerPreviewCache.delete(cacheKey);
        throw error;
      }
    }

    async function hydrateStickerPreview(container) {
      if (!(container instanceof HTMLElement)) return;
      const src = String(container.dataset.stickerSrc || '').trim();
      if (!src) return;
      if (container.dataset.stickerState === 'loading' || container.dataset.stickerState === 'ready') return;
      const statusNode = container.querySelector('.chat-media-sticker-status');
      container.dataset.stickerState = 'loading';
      if (statusNode) {
        statusNode.textContent = 'Загрузка стикера…';
      }
      if (!window.lottie || typeof window.lottie.loadAnimation !== 'function') {
        container.dataset.stickerState = 'error';
        if (statusNode) {
          statusNode.textContent = 'Не удалось загрузить проигрыватель стикеров.';
        }
        return;
      }
      try {
        const animationData = await loadTgsAnimationData(src);
        container.innerHTML = '';
        window.lottie.loadAnimation({
          container,
          renderer: 'svg',
          loop: true,
          autoplay: true,
          animationData,
          rendererSettings: {
            preserveAspectRatio: 'xMidYMid meet',
          },
        });
        container.dataset.stickerState = 'ready';
      } catch (_error) {
        container.dataset.stickerState = 'error';
        if (statusNode) {
          statusNode.textContent = 'Не удалось показать стикер.';
        } else {
          container.innerHTML = '<div class="chat-media-sticker-status text-muted">Не удалось показать стикер.</div>';
        }
      }
    }

    function hydrateMediaRoot(root) {
      if (!(root instanceof Element || root instanceof Document)) return;
      const stickerContainers = root.querySelectorAll('[data-sticker-src]');
      stickerContainers.forEach((container) => {
        hydrateStickerPreview(container);
      });
    }

    function messageToHtml(message, renderOptions = {}) {
      const activeDialogState = getActiveDialogState();
      const senderType = options.normalizeMessageSenderByType?.(message?.messageType, message?.sender) || '';
      const senderLabel = options.resolveSenderLabel?.(message, activeDialogState.context || {}) || '';
      const timestamp = formatTimestamp(message?.timestamp, { includeTime: true });
      const isDeleted = Boolean(message?.deletedAt);
      const isEdited = Boolean(message?.editedAt);
      const isSupport = senderType === 'support';
      const archivedHistory = renderOptions.archivedHistory === true;
      const mediaKind = message?.attachment
        ? resolveAttachmentKind(message.messageType, message.attachment)
        : '';
      const replyPreview = message?.replyPreview
        ? `<div class="small text-muted border-start ps-2 mb-1 chat-message-reply-source">↪ ${escapeHtml(message.replyPreview)}</div>`
        : '';
      const forwardedBadge = message?.forwardedFrom
        ? `<div class="small text-muted mb-1">Переслано от ${escapeHtml(message.forwardedFrom)}</div>`
        : '';
      const bodyText = message?.message ? escapeHtml(message.message).replace(/\n/g, '<br>') : '';
      const fallbackType = !message?.attachment && message?.messageType && !bodyText
        ? `[${escapeHtml(message.messageType)}]`
        : '';
      let body = '';
      if (isDeleted) {
        body = '<span class="text-muted">Сообщение удалено</span>';
      } else if (bodyText) {
        body = bodyText;
      } else if (fallbackType) {
        body = fallbackType;
      } else if (!message?.attachment) {
        body = '—';
      }
      const originalBlock = isEdited && message?.originalMessage && message.originalMessage !== message.message
        ? `<div class="small text-muted mt-1"><div>Было: ${escapeHtml(message.originalMessage)}</div><div>Стало: ${escapeHtml(message.message || '')}</div></div>`
        : '';
      const statusBadges = [
        isEdited ? '<span class="chat-message-meta-badge">✏️ Изменено</span>' : '',
        isDeleted ? '<span class="chat-message-meta-badge">🗑 Удалено</span>' : '',
        archivedHistory ? '<span class="chat-message-meta-badge">Архив</span>' : '',
      ].join(' ');
      const media = isDeleted ? '' : buildMediaMarkup(message);
      const messagePreviewText = String(message?.replyPreview || message?.message || '').trim()
        || (message?.attachment ? resolveAttachmentTypeLabel(message, mediaKind) : 'Сообщение');
      const canReply = !archivedHistory && senderType !== 'system' && message?.telegramMessageId;
      const actionButtons = canReply
        ? `<div class="chat-message-menu">
            <button class="chat-message-menu-toggle" type="button" data-action-menu aria-label="Действия с сообщением">⋯</button>
            <div class="chat-message-menu-list">
              <button class="btn btn-sm btn-outline-secondary" type="button" data-action="reply" data-message-id="${message.telegramMessageId}">Ответить</button>
              ${isSupport ? `<button class="btn btn-sm btn-outline-secondary" type="button" data-action="edit" data-message-id="${message.telegramMessageId}" ${isDeleted ? 'disabled' : ''}>Редактировать</button>` : ''}
              ${isSupport ? `<button class="btn btn-sm btn-outline-danger" type="button" data-action="delete" data-message-id="${message.telegramMessageId}" ${isDeleted ? 'disabled' : ''}>Удалить</button>` : ''}
            </div>
          </div>`
        : '';
      const avatarMarkup = options.renderMessageAvatar?.(
        options.resolveDialogMessageAvatarSpec?.(message, activeDialogState.context || {}),
        archivedHistory ? 'is-archived-history' : ''
      ) || '';

      return `
        <div class="chat-message-row ${senderType} ${archivedHistory ? 'is-archived-history' : ''}" data-telegram-message-id="${message?.telegramMessageId || ''}">
          ${avatarMarkup}
          <div class="chat-message ${senderType} ${isDeleted ? 'is-deleted' : ''} ${archivedHistory ? 'is-archived-history' : ''}" data-message-preview="${escapeAttribute(messagePreviewText)}">
            <div class="chat-message-header">
              <span>${escapeHtml(senderLabel)}</span>
              <span>${escapeHtml(timestamp)}</span>
            </div>
            ${statusBadges ? `<div class="small text-muted mb-1">${statusBadges}</div>` : ''}
            ${forwardedBadge}
            ${replyPreview}
            ${body ? `<div class="chat-message-body">${body}</div>` : ''}
            ${originalBlock}
            ${media}
            ${actionButtons}
          </div>
        </div>
      `;
    }

    function isTechnicalHistoryMessage(message) {
      const type = String(message?.messageType || '').toLowerCase();
      const senderType = options.normalizeMessageSenderByType?.(message?.messageType, message?.sender) || '';
      const text = String(message?.message || '').toLowerCase();
      if (type.includes('feedback') || type.includes('rating')) return true;
      if (senderType === 'system' && (text.includes('поставьте оценку') || text.includes('оцените') || text.includes('оценк'))) {
        return true;
      }
      return false;
    }

    function normalizeHistoryComparisonValue(value) {
      return String(value || '')
        .toLowerCase()
        .replace(/\s+/g, ' ')
        .trim();
    }

    function buildHistoryTechnicalValueSet(context = {}) {
      const values = new Set();
      const addValue = (value) => {
        const normalized = normalizeHistoryComparisonValue(value);
        if (!normalized) return;
        values.add(normalized);
        normalized
          .split(/[,/|]+/)
          .map((item) => normalizeHistoryComparisonValue(item))
          .filter(Boolean)
          .forEach((item) => values.add(item));
      };
      addValue(context?.business);
      addValue(context?.location);
      return values;
    }

    function isLikelyQuestionnaireProblemMessage(text, technicalValues) {
      const normalized = normalizeHistoryComparisonValue(text);
      if (!normalized) return false;
      if (technicalValues.has(normalized)) return false;
      const words = normalized.split(' ').filter(Boolean);
      if (normalized.length >= 48) return true;
      if (words.length >= 4) return true;
      if (/[,.!?;:()-]/.test(text) && words.length >= 2) return true;
      return false;
    }

    function filterDialogHistoryMessages(messages, context = getActiveDialogState().context || {}) {
      const baseMessages = Array.isArray(messages)
        ? messages.filter((message) => !isTechnicalHistoryMessage(message))
        : [];
      if (baseMessages.length < 2) {
        return baseMessages;
      }

      let prefixLength = 0;
      while (prefixLength < baseMessages.length) {
        const senderType = options.normalizeMessageSenderByType?.(baseMessages[prefixLength]?.messageType, baseMessages[prefixLength]?.sender) || '';
        if (senderType !== 'user') {
          break;
        }
        prefixLength += 1;
      }

      if (prefixLength < 2) {
        return baseMessages;
      }

      const technicalValues = buildHistoryTechnicalValueSet(context);
      const prefix = baseMessages.slice(0, prefixLength);
      const preservedIndexes = new Set();

      prefix.forEach((message, index) => {
        if (message?.attachment) {
          preservedIndexes.add(index);
        }
      });

      let informativeIndex = -1;
      for (let index = prefix.length - 1; index >= 0; index -= 1) {
        const text = String(prefix[index]?.message || '').trim();
        if (isLikelyQuestionnaireProblemMessage(text, technicalValues)) {
          informativeIndex = index;
          break;
        }
      }

      preservedIndexes.add(informativeIndex >= 0 ? informativeIndex : prefix.length - 1);

      return baseMessages.filter((message, index) => index >= prefixLength || preservedIndexes.has(index));
    }

    function resetPreviousDialogHistoryState() {
      state.previousBatches = [];
      state.previousNextOffset = 0;
      state.previousHasMore = true;
      state.previousLoading = false;
    }

    function renderPreviousDialogHistoryControls() {
      const activeDialogState = getActiveDialogState();
      const disabled = state.previousLoading || !activeDialogState.ticketId || !state.previousHasMore;
      const buttonLabel = state.previousLoading
        ? 'Загружаем предыдущие обращения…'
        : (state.previousHasMore ? 'Загрузить предыдущие сообщения' : 'Предыдущих обращений больше нет');
      const helperText = state.previousBatches.length > 0
        ? `Подгружено обращений: ${state.previousBatches.length}. Архив показан отдельным цветом.`
        : 'Можно подгрузить переписку из предыдущих обращений этого клиента.';
      return `
        <div class="dialog-history-controls">
          <button class="btn btn-sm btn-outline-secondary" type="button" data-action="load-previous-history" ${disabled ? 'disabled' : ''}>${escapeHtml(buttonLabel)}</button>
          <div class="small text-muted mt-2">${escapeHtml(helperText)}</div>
        </div>
      `;
    }

    function renderArchivedHistoryBatch(batch) {
      const messages = filterDialogHistoryMessages(batch?.messages || []);
      const ticketId = String(batch?.ticketId || '—').trim() || '—';
      const createdAt = formatTimestamp(batch?.createdAt || batch?.created_at, { includeTime: true, fallback: '—' });
      const status = String(batch?.status || '—').trim() || '—';
      const problem = String(batch?.problem || 'Без описания').trim() || 'Без описания';
      const sourceLabel = String(batch?.sourceLabel || batch?.source_label || '').trim();
      const channelName = String(batch?.channelName || batch?.channel_name || '').trim();
      const meta = [sourceLabel, channelName, createdAt].filter(Boolean).join(' · ');
      const body = messages.length
        ? messages.map((message) => messageToHtml(message, { archivedHistory: true })).join('')
        : '<div class="small text-muted">В обращении не найдено сообщений.</div>';
      return `
        <section class="chat-history-archive-section">
          <div class="chat-history-archive-head">
            <div class="fw-semibold">Предыдущее обращение #${escapeHtml(ticketId)}</div>
            <div class="small text-muted">${escapeHtml(meta || 'Архивная переписка')}</div>
            <div class="small text-muted">Статус: ${escapeHtml(status)}</div>
            <div class="small mt-1">${escapeHtml(problem)}</div>
          </div>
          <div class="d-flex flex-column gap-2 mt-3">
            ${body}
          </div>
        </section>
      `;
    }

    function captureHistoryViewport() {
      if (!elements.detailsHistory) return null;
      const container = elements.detailsHistory;
      const containerTop = container.getBoundingClientRect().top;
      const anchor = Array.from(container.querySelectorAll('.chat-message-row'))
        .find((row) => {
          const rect = row.getBoundingClientRect();
          return rect.bottom >= containerTop + 4;
        });
      return {
        scrollTop: container.scrollTop,
        pinnedToBottom: Math.abs((container.scrollHeight - container.clientHeight) - container.scrollTop) <= 24,
        anchorMessageId: String(anchor?.dataset?.telegramMessageId || '').trim(),
        anchorOffset: anchor ? anchor.getBoundingClientRect().top - containerTop : 0,
      };
    }

    function restoreHistoryViewport(snapshot, renderOptions = {}) {
      if (!elements.detailsHistory || !snapshot) return;
      const container = elements.detailsHistory;
      if (renderOptions.scrollToBottom !== false) {
        container.scrollTop = container.scrollHeight;
        return;
      }
      if (snapshot.pinnedToBottom && renderOptions.stickToBottom !== false) {
        container.scrollTop = container.scrollHeight;
        return;
      }
      if (snapshot.anchorMessageId) {
        const anchor = container.querySelector(`.chat-message-row[data-telegram-message-id="${CSS.escape(snapshot.anchorMessageId)}"]`);
        if (anchor instanceof HTMLElement) {
          container.scrollTop = Math.max(0, anchor.offsetTop - snapshot.anchorOffset);
          return;
        }
      }
      container.scrollTop = Math.max(0, snapshot.scrollTop);
    }

    function renderDialogHistory(renderOptions = {}) {
      if (!elements.detailsHistory) return;
      const viewportSnapshot = renderOptions.preserveViewport ? captureHistoryViewport() : null;
      const currentMessages = filterDialogHistoryMessages(state.currentMessages);
      const controlsMarkup = renderPreviousDialogHistoryControls();
      const archivedMarkup = state.previousBatches.map(renderArchivedHistoryBatch).join('');
      const currentMarkup = currentMessages.length
        ? currentMessages.map((message) => messageToHtml(message)).join('')
        : '<div class="text-muted">Сообщения не найдены.</div>';
      elements.detailsHistory.innerHTML = `${controlsMarkup}${archivedMarkup}${currentMarkup}`;
      hydrateMediaRoot(elements.detailsHistory);
      if (viewportSnapshot) {
        restoreHistoryViewport(viewportSnapshot, renderOptions);
      } else if (renderOptions.scrollToBottom !== false) {
        elements.detailsHistory.scrollTop = elements.detailsHistory.scrollHeight;
      }
    }

    function renderHistory(messages, renderOptions = {}) {
      state.currentMessages = Array.isArray(messages) ? messages : [];
      const filteredMessages = filterDialogHistoryMessages(state.currentMessages);
      state.lastHistoryMarker = historyMarker(filteredMessages);
      renderDialogHistory(renderOptions);
    }

    function appendHistoryMessage(message) {
      if (!elements.detailsHistory) return;
      if (isTechnicalHistoryMessage(message)) return;
      state.currentMessages = Array.isArray(state.currentMessages)
        ? [...state.currentMessages, message]
        : [message];
      renderDialogHistory({ scrollToBottom: true });
      state.lastHistoryMarker = `local:${Date.now()}`;
    }

    function historyMarker(messages) {
      if (!Array.isArray(messages) || messages.length === 0) return 'empty';
      const last = messages[messages.length - 1] || {};
      return [
        messages.length,
        last.telegramMessageId || '',
        last.timestamp || '',
        last.sender || '',
        last.message || '',
      ].join('|');
    }

    async function loadPreviousDialogHistory() {
      const activeDialogState = getActiveDialogState();
      if (!activeDialogState.ticketId || state.previousLoading || !state.previousHasMore) return;
      state.previousLoading = true;
      renderDialogHistory({ scrollToBottom: false });
      try {
        const previousHeight = elements.detailsHistory ? elements.detailsHistory.scrollHeight : 0;
        const previousScrollTop = elements.detailsHistory ? elements.detailsHistory.scrollTop : 0;
        const resp = await fetch(`/api/dialogs/${encodeURIComponent(activeDialogState.ticketId)}/history/previous?offset=${encodeURIComponent(state.previousNextOffset)}`, {
          credentials: 'same-origin',
          cache: 'no-store',
        });
        const data = await resp.json();
        if (!resp.ok || data?.success === false) {
          throw new Error(data?.error || `Ошибка ${resp.status}`);
        }
        if (data?.batch) {
          state.previousBatches = [data.batch, ...state.previousBatches];
          state.previousNextOffset = Number.isInteger(data?.next_offset)
            ? Number(data.next_offset)
            : (state.previousNextOffset + 1);
          state.previousHasMore = data?.has_more === true;
          renderDialogHistory({ scrollToBottom: false });
          if (elements.detailsHistory) {
            const nextHeight = elements.detailsHistory.scrollHeight;
            elements.detailsHistory.scrollTop = previousScrollTop + Math.max(0, nextHeight - previousHeight);
          }
        } else {
          state.previousHasMore = false;
          renderDialogHistory({ scrollToBottom: false });
        }
      } catch (error) {
        state.previousHasMore = state.previousHasMore || state.previousBatches.length === 0;
        renderDialogHistory({ scrollToBottom: false });
        notify(error.message || 'Не удалось загрузить предыдущие сообщения', 'warning');
      } finally {
        state.previousLoading = false;
        renderDialogHistory({ scrollToBottom: false });
      }
    }

    async function refreshHistory() {
      const activeDialogState = getActiveDialogState();
      if (!activeDialogState.ticketId || state.historyLoading) return;
      state.historyLoading = true;
      try {
        const url = withChannelParam(`/api/dialogs/${encodeURIComponent(activeDialogState.ticketId)}/history`, activeDialogState.channelId);
        const resp = await fetch(url, {
          credentials: 'same-origin',
          cache: 'no-store',
        });
        const data = await resp.json();
        if (!resp.ok || !data?.success) {
          throw new Error(data?.error || `Ошибка ${resp.status}`);
        }
        const messages = data.messages || [];
        const marker = historyMarker(filterDialogHistoryMessages(messages));
        const hasHistoryChanges = marker !== state.lastHistoryMarker;
        if (hasHistoryChanges) {
          const shouldPreserveViewport = state.lastHistoryMarker !== null;
          renderHistory(
            messages,
            shouldPreserveViewport
              ? { scrollToBottom: false, preserveViewport: true, stickToBottom: true }
              : {}
          );
        }
        options.updateDialogUnreadCount?.(0);
        if (hasHistoryChanges) {
          options.requestSidebarNotificationRefresh?.('dialogs-history-change');
        }
      } catch (_error) {
        // ignore polling errors
      } finally {
        state.historyLoading = false;
      }
    }

    function startHistoryPolling() {
      if (state.historyPollTimer) return;
      state.historyPollTimer = setInterval(refreshHistory, options.historyPollInterval);
    }

    function stopHistoryPolling() {
      if (state.historyPollTimer) {
        clearInterval(state.historyPollTimer);
        state.historyPollTimer = null;
      }
    }

    function getSelectedMediaFiles(mediaInput) {
      return mediaInput instanceof HTMLInputElement ? Array.from(mediaInput.files || []) : [];
    }

    function isUploadableMediaFile(file) {
      if (!file || typeof file !== 'object') return false;
      if (typeof Blob === 'function' && file instanceof Blob) return true;
      return typeof file.size === 'number' && typeof file.type === 'string';
    }

    function resolvePendingMediaFileName(file, fallbackName = '') {
      const directName = typeof file?.name === 'string' ? file.name.trim() : '';
      const stagedName = typeof file?.__dialogsPendingName === 'string' ? file.__dialogsPendingName.trim() : '';
      return directName || stagedName || fallbackName || `attachment-${Date.now()}.bin`;
    }

    function createClipboardUploadFile(file, fallbackName) {
      if (!isUploadableMediaFile(file)) {
        return null;
      }
      const normalizedName = String(fallbackName || '').trim() || `clipboard-${Date.now()}.png`;
      if (typeof File === 'function') {
        try {
          const normalizedType = String(file.type || '').trim() || 'application/octet-stream';
          return new File([file], resolvePendingMediaFileName(file, normalizedName), { type: normalizedType });
        } catch (error) {
          // Fall back to using Blob directly below.
        }
      }
      try {
        Object.defineProperty(file, '__dialogsPendingName', {
          value: resolvePendingMediaFileName(file, normalizedName),
          configurable: true,
        });
      } catch (error) {
        file.__dialogsPendingName = resolvePendingMediaFileName(file, normalizedName);
      }
      return file;
    }

    function getStagedMediaFiles(mediaInput) {
      if (!(mediaInput instanceof HTMLInputElement) || !Array.isArray(mediaInput.__stagedMediaFiles)) {
        return [];
      }
      return mediaInput.__stagedMediaFiles.filter((file) => isUploadableMediaFile(file));
    }

    function getPendingMediaFiles(mediaInput) {
      return getSelectedMediaFiles(mediaInput).concat(getStagedMediaFiles(mediaInput));
    }

    function stageMediaFilesInInput(mediaInput, files) {
      if (!(mediaInput instanceof HTMLInputElement) || !files?.length) {
        return 0;
      }
      const stagedFiles = getStagedMediaFiles(mediaInput);
      Array.from(files).forEach((file) => {
        if (isUploadableMediaFile(file)) {
          stagedFiles.push(file);
        }
      });
      mediaInput.__stagedMediaFiles = stagedFiles;
      return getPendingMediaFiles(mediaInput).length;
    }

    function clearPendingMediaFiles(mediaInput) {
      if (!(mediaInput instanceof HTMLInputElement)) {
        return;
      }
      mediaInput.value = '';
      mediaInput.__stagedMediaFiles = [];
    }

    async function sendMediaFiles(files, sendOptions = {}) {
      const activeDialogState = getActiveDialogState();
      const activeWorkspaceState = getActiveWorkspaceState();
      const ticketId = String(sendOptions.ticketId || activeDialogState.ticketId || activeWorkspaceState.ticketId || '').trim();
      if (!ticketId || !files || files.length === 0) return;
      const captionSource = typeof sendOptions.caption === 'string' ? sendOptions.caption : elements.detailsReplyText?.value || '';
      const caption = String(captionSource).trim();
      const replyToTelegramId = Number.isFinite(Number(sendOptions.replyToTelegramId))
        ? Number(sendOptions.replyToTelegramId)
        : null;
      const sendButton = sendOptions.sendButton || elements.detailsReplySend;
      const mediaInput = sendOptions.mediaInput || elements.detailsReplyMedia;
      const appendHistoryFlag = sendOptions.appendHistory !== false;
      if (sendButton) sendButton.disabled = true;
      try {
        for (const file of Array.from(files)) {
          const formData = new FormData();
          formData.append('file', file, resolvePendingMediaFileName(file));
          if (caption) {
            formData.append('message', caption);
          }
          if (replyToTelegramId !== null) {
            formData.append('replyToTelegramId', String(replyToTelegramId));
          }
          const resp = await fetch(`/api/dialogs/${encodeURIComponent(ticketId)}/media`, {
            method: 'POST',
            body: formData,
          });
          const data = await resp.json();
          if (!resp.ok || !data?.success) {
            throw new Error(data?.error || `Ошибка ${resp.status}`);
          }
          options.updateDetailsResponsible?.(data.responsible || activeDialogState.context?.operatorName || '');
          if (appendHistoryFlag) {
            appendHistoryMessage({
              sender: options.operatorDisplayName || data.responsible || 'Оператор',
              message: data.message || '',
              timestamp: data.timestamp || new Date().toISOString(),
              messageType: data.messageType || 'operator_media',
              attachment: data.attachment || null,
            });
          }
          if (activeDialogState.row) {
            options.updateRowStatus?.(activeDialogState.row, 'pending', 'ожидает ответа клиента', 'waiting_client', 0);
          }
          options.updateDetailsStatusSummary?.('ожидает ответа клиента', 'waiting_client');
          if (ticketId === activeWorkspaceState.ticketId) {
            options.emitWorkspaceTelemetry?.('workspace_media_sent', {
              ticketId,
              reason: data.messageType || file.type || 'attachment',
              contractVersion: activeWorkspaceState.payload?.contract_version || 'workspace.v1',
            });
          }
        }
        if (typeof sendOptions.afterSuccess === 'function') {
          sendOptions.afterSuccess();
        } else if (elements.detailsReplyText) {
          elements.detailsReplyText.value = '';
        }
        notify(sendOptions.successMessage || 'Медиа отправлено', 'success');
      } catch (error) {
        notify(error.message || sendOptions.errorMessage || 'Не удалось отправить медиа', 'error');
      } finally {
        if (sendButton) sendButton.disabled = false;
        if (mediaInput) {
          clearPendingMediaFiles(mediaInput);
        }
      }
    }

    function extractClipboardImageFiles(event) {
      const files = [];
      let sequence = 1;
      const mimeToExtension = (mimeType) => {
        const normalized = String(mimeType || '').toLowerCase();
        if (!normalized) return 'png';
        if (normalized.includes('jpeg') || normalized.includes('jpg')) return 'jpg';
        if (normalized.includes('webp')) return 'webp';
        if (normalized.includes('gif')) return 'gif';
        if (normalized.includes('bmp')) return 'bmp';
        if (normalized.includes('svg')) return 'svg';
        if (normalized.includes('png')) return 'png';
        return 'png';
      };
      const items = Array.from(event?.clipboardData?.items || []);
      for (const item of items) {
        if (item?.kind !== 'file' || !String(item.type || '').startsWith('image/')) continue;
        const file = item.getAsFile ? item.getAsFile() : null;
        if (!file) continue;
        const extension = mimeToExtension(file.type);
        const generatedName = `clipboard-${Date.now()}-${sequence++}.${extension}`;
        const normalizedFile = createClipboardUploadFile(file, generatedName);
        if (normalizedFile) {
          files.push(normalizedFile);
        }
      }
      if (!files.length) {
        const clipboardFiles = Array.from(event?.clipboardData?.files || []);
        for (const file of clipboardFiles) {
          if (!String(file?.type || '').startsWith('image/')) continue;
          const extension = mimeToExtension(file.type);
          const generatedName = `clipboard-${Date.now()}-${sequence++}.${extension}`;
          const normalizedFile = createClipboardUploadFile(file, generatedName);
          if (normalizedFile) {
            files.push(normalizedFile);
          }
        }
      }
      return files;
    }

    async function sendWorkspaceMediaFiles(files, sendOptions = {}) {
      const activeWorkspaceState = getActiveWorkspaceState();
      const afterSuccess = typeof sendOptions.afterSuccess === 'function' ? sendOptions.afterSuccess : null;
      await sendMediaFiles(files, {
        ticketId: activeWorkspaceState.ticketId,
        caption: typeof sendOptions.caption === 'string'
          ? sendOptions.caption
          : (activeWorkspaceState.composerText?.value || ''),
        replyToTelegramId: sendOptions.replyToTelegramId,
        sendButton: sendOptions.sendButton || activeWorkspaceState.composerSend,
        mediaInput: sendOptions.mediaInput || activeWorkspaceState.composerMedia,
        appendHistory: false,
        successMessage: sendOptions.successMessage,
        errorMessage: sendOptions.errorMessage,
        afterSuccess: () => {
          if (activeWorkspaceState.composerText) {
            activeWorkspaceState.composerText.value = '';
          }
          options.saveWorkspaceDraft?.(activeWorkspaceState.composerTicketId, '');
          if (afterSuccess) {
            afterSuccess();
          }
          options.reloadWorkspaceSection?.('messages', {
            stateElement: activeWorkspaceState.messagesState,
            errorElement: activeWorkspaceState.messagesError,
            statusText: 'Обновление ленты после отправки медиа…',
            failMessage: 'Медиа отправлено, но лента workspace не обновилась автоматически.',
          });
        },
      });
    }

    function handleMediaSurfaceClick(event) {
      const playButton = event.target.closest('.chat-audio-play');
      if (playButton) {
        const src = playButton.dataset.audioSrc;
        if (!src) return true;
        if (state.activeAudioPlayer && state.activeAudioSource === src && !state.activeAudioPlayer.paused) {
          state.activeAudioPlayer.pause();
          playButton.textContent = 'Воспроизвести';
          return true;
        }
        if (state.activeAudioPlayer) {
          state.activeAudioPlayer.pause();
        }
        state.activeAudioPlayer = new Audio(src);
        state.activeAudioSource = src;
        playButton.textContent = 'Пауза';
        state.activeAudioPlayer.addEventListener('ended', () => {
          playButton.textContent = 'Воспроизвести';
        });
        state.activeAudioPlayer.play().catch(() => {
          playButton.textContent = 'Воспроизвести';
        });
        return true;
      }
      const imagePreview = event.target.closest('[data-image-src]');
      if (imagePreview) {
        const src = imagePreview.dataset.imageSrc;
        if (!src) return true;
        showImagePreview(src, imagePreview.dataset.mediaName || 'image');
        return true;
      }
      const videoPreview = event.target.closest('[data-video-src]');
      if (videoPreview) {
        const src = videoPreview.dataset.videoSrc;
        if (!src) return true;
        showVideoPreview(src);
        return true;
      }
      return false;
    }

    function closeHistoryActionMenus(exceptMenu = null) {
      if (!elements.detailsHistory) return;
      elements.detailsHistory.querySelectorAll('.chat-message-menu.is-open').forEach((menu) => {
        if (menu !== exceptMenu) {
          menu.classList.remove('is-open');
        }
      });
    }

    function bindHistoryInteractionEvents() {
      if (elements.detailsHistory) {
        elements.detailsHistory.addEventListener('click', async (event) => {
          const loadPreviousButton = event.target.closest('button[data-action="load-previous-history"]');
          if (loadPreviousButton) {
            await loadPreviousDialogHistory();
            return;
          }
          if (handleMediaSurfaceClick(event)) {
            return;
          }
          const menuToggle = event.target.closest('[data-action-menu]');
          if (menuToggle) {
            const menu = menuToggle.closest('.chat-message-menu');
            if (!menu) return;
            closeHistoryActionMenus(menu);
            menu.classList.toggle('is-open');
            return;
          }
          const button = event.target.closest('button[data-action]');
          const ticketId = String(getActiveDialogState().ticketId || '').trim();
          if (!button || !ticketId) return;
          const menu = button.closest('.chat-message-menu');
          if (menu) menu.classList.remove('is-open');
          const messageId = Number.parseInt(button.dataset.messageId, 10);
          if (!Number.isFinite(messageId)) return;
          const action = button.dataset.action;
          if (action === 'reply') {
            const messageNode = button.closest('.chat-message');
            const previewText = messageNode?.dataset.messagePreview
              || messageNode?.querySelector('.chat-message-reply-source')?.textContent
              || messageNode?.querySelector('.chat-message-body')?.textContent
              || '';
            setReplyTarget(messageId, previewText);
            if (elements.detailsReplyText) {
              elements.detailsReplyText.focus();
            }
            return;
          }
          if (action === 'edit') {
            const current = button.closest('.chat-message')?.querySelector('.chat-message-body')?.textContent || '';
            const nextText = window.prompt('Введите новый текст сообщения:', current.trim());
            if (!nextText || !nextText.trim()) return;
            const response = await fetch(`/api/dialogs/${encodeURIComponent(ticketId)}/edit`, {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ telegramMessageId: messageId, message: nextText.trim() }),
            });
            const payload = await response.json();
            if (!response.ok || !payload?.success) {
              throw new Error(payload?.error || `Ошибка ${response.status}`);
            }
            await refreshHistory();
            return;
          }
          if (action === 'delete') {
            if (!window.confirm('Удалить сообщение у клиента?')) return;
            const response = await fetch(`/api/dialogs/${encodeURIComponent(ticketId)}/delete`, {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ telegramMessageId: messageId }),
            });
            const payload = await response.json();
            if (!response.ok || !payload?.success) {
              throw new Error(payload?.error || `Ошибка ${response.status}`);
            }
            await refreshHistory();
          }
        });
      }

      document.addEventListener('click', (event) => {
        if (!elements.detailsHistory) return;
        if (event.target.closest('.chat-message-menu')) return;
        closeHistoryActionMenus();
      });

      if (elements.detailsReplyMediaTrigger && elements.detailsReplyMedia) {
        elements.detailsReplyMediaTrigger.addEventListener('click', () => {
          elements.detailsReplyMedia.click();
        });
        elements.detailsReplyMedia.addEventListener('change', () => {
          const totalFiles = getPendingMediaFiles(elements.detailsReplyMedia).length;
          if (totalFiles > 0) {
            notify(`Медиа прикреплено: ${totalFiles}. Нажмите "Отправить", чтобы отправить клиенту.`, 'info');
          }
        });
      }

      if (elements.replyTargetClear) {
        elements.replyTargetClear.addEventListener('click', () => {
          resetReplyTarget();
          if (elements.detailsReplyText) {
            elements.detailsReplyText.focus();
          }
        });
      }

      if (elements.mediaPreviewModalEl) {
        elements.mediaPreviewModalEl.addEventListener('hidden.bs.modal', () => {
          resetMediaPreview();
        });
      }
    }

    return {
      resetReplyTarget,
      setReplyTarget,
      getActiveReplyToTelegramId,
      resetMediaPreview,
      showImagePreview,
      showVideoPreview,
      adjustMediaPreviewZoom,
      handleMediaPreviewEscape,
      buildMediaMarkup,
      messageToHtml,
      filterDialogHistoryMessages,
      resetPreviousDialogHistoryState,
      renderPreviousDialogHistoryControls,
      renderArchivedHistoryBatch,
      renderDialogHistory,
      renderHistory,
      appendHistoryMessage,
      historyMarker,
      loadPreviousDialogHistory,
      refreshHistory,
      startHistoryPolling,
      stopHistoryPolling,
      sendMediaFiles,
      stageMediaFilesInInput,
      getPendingMediaFiles,
      clearPendingMediaFiles,
      extractClipboardImageFiles,
      sendWorkspaceMediaFiles,
      hydrateMediaRoot,
      handleMediaSurfaceClick,
      bindHistoryInteractionEvents,
    };
  }

  window.DialogsDetailsHistoryRuntime = {
      createRuntime,
    };
})();
