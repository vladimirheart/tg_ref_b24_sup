(function () {
  if (window.DialogsPresentationRuntime) {
    return;
  }

  function createRuntime(options = {}) {
    function escapeHtml(value) {
      return typeof options.escapeHtml === 'function'
        ? options.escapeHtml(value)
        : String(value ?? '');
    }

    function resolveChannelToneKey(label) {
      const normalized = String(label || '').trim().toLowerCase();
      if (!normalized) return 'custom';
      if (normalized.includes('telegram') || normalized.startsWith('tg') || normalized.includes(' тг')) return 'telegram';
      if (normalized.includes('vk') || normalized.includes('вк')) return 'vk';
      if (normalized.includes('max') || normalized.includes('мах')) return 'max';
      if (normalized.includes('form')
        || normalized.includes('форма')
        || normalized.includes('web')
        || normalized.includes('site')
        || normalized.includes('external')
        || normalized.includes('внеш')) return 'form';
      return 'custom';
    }

    function renderChannelBadge(label) {
      const safeLabel = String(label || '').trim() || 'Без канала';
      const tone = resolveChannelToneKey(safeLabel);
      return `<span class="dialog-channel-pill channel-tone-${escapeHtml(tone)}">${escapeHtml(safeLabel)}</span>`;
    }

    function formatRatingStars(value) {
      const numeric = Number(value);
      if (!Number.isFinite(numeric) || numeric <= 0) return '';
      const capped = Math.min(5, Math.max(1, Math.round(numeric)));
      return '★'.repeat(capped);
    }

    function formatDialogMeta(ticketId, requestNumber) {
      const normalizedTicketId = ticketId ? String(ticketId) : '';
      const normalizedRequest = requestNumber ? String(requestNumber) : '';
      if (normalizedRequest) {
        if (normalizedTicketId && normalizedRequest !== normalizedTicketId) {
          return `№ обращения: ${normalizedRequest} · ID: ${normalizedTicketId}`;
        }
        return `№ обращения: ${normalizedRequest}`;
      }
      return normalizedTicketId ? `ID диалога: ${normalizedTicketId}` : '';
    }

    function renderWorkspaceMessageItem(message) {
      const author = message?.senderName || message?.senderRole || 'Участник';
      const timestamp = options.formatWorkspaceDateTime?.(message?.sentAt || message?.createdAt) || '';
      const text = String(message?.messageText || message?.message || '').trim();
      const replyPreviewText = String(message?.replyPreview || message?.reply_preview || '').trim();
      const telegramMessageId = Number.parseInt(message?.telegramMessageId ?? message?.telegram_message_id, 10);
      const senderType = String(message?.senderRole || message?.sender || '').trim().toLowerCase();
      const normalizedMessage = {
        messageType: message?.messageType || message?.type || '',
        message: text,
        attachment: message?.attachment || message?.attachmentUrl || null,
        attachmentName: message?.attachmentName || message?.fileName || null,
      };
      const mediaMarkup = normalizedMessage.attachment ? (options.buildMediaMarkup?.(normalizedMessage) || '') : '';
      const textMarkup = text ? `<div class="workspace-message-body">${escapeHtml(text)}</div>` : '';
      const replyPreviewMarkup = replyPreviewText
        ? `<div class="small text-muted border-start ps-2 mb-1 workspace-message-reply-source">↪ ${escapeHtml(replyPreviewText)}</div>`
        : '';
      const activeWorkspacePayload = options.getActiveWorkspacePayload?.() || null;
      const replyTargetSupported = activeWorkspacePayload?.composer?.reply_target_supported !== false;
      const canReply = replyTargetSupported && Number.isFinite(telegramMessageId) && senderType !== 'system';
      const actionMarkup = canReply
        ? `<div class="mt-2"><button class="btn btn-sm btn-outline-secondary" type="button" data-workspace-action="reply" data-message-id="${telegramMessageId}">Ответить</button></div>`
        : '';
      const fallbackMarkup = textMarkup || mediaMarkup ? '' : '<div>—</div>';
      const avatarMarkup = options.renderMessageAvatar?.(
        options.resolveWorkspaceMessageAvatarSpec?.(message),
        'workspace-message-avatar'
      ) || '';
      return `<article class="workspace-message-item" data-telegram-message-id="${Number.isFinite(telegramMessageId) ? telegramMessageId : ''}">${avatarMarkup}<div class="workspace-message-content"><div class="workspace-message-meta">${escapeHtml(author)} · ${escapeHtml(timestamp)}</div>${replyPreviewMarkup}${textMarkup}${mediaMarkup}${fallbackMarkup}${actionMarkup}</div></article>`;
    }

    return {
      resolveChannelToneKey,
      renderChannelBadge,
      formatRatingStars,
      formatDialogMeta,
      renderWorkspaceMessageItem,
    };
  }

  window.DialogsPresentationRuntime = {
    createRuntime,
  };
})();
