(function () {
  if (window.DialogsAvatarRuntime) {
    return;
  }

  function createRuntime(options = {}) {
    function escapeHtml(value) {
      return typeof options.escapeHtml === 'function'
        ? options.escapeHtml(value)
        : String(value ?? '');
    }

    function buildResponsibleAvatarSpec(responsible, avatarUrl = '') {
      const safeLabel = String(responsible || '').trim();
      const safeAvatarUrl = String(avatarUrl || '').trim();
      if (!safeLabel || safeLabel === '—') {
        return null;
      }
      const currentOperatorOwned = options.normalizeIdentity?.(safeLabel) === options.normalizeIdentity?.(options.operatorIdentity)
        || options.normalizeIdentity?.(safeLabel) === options.normalizeIdentity?.(options.operatorDisplayName);
      return {
        label: safeLabel,
        avatarUrl: safeAvatarUrl || (currentOperatorOwned ? String(options.operatorAvatarUrl || '').trim() : ''),
        initial: options.avatarInitial?.(safeLabel) || '—',
      };
    }

    function renderResponsibleCell(responsible, avatarUrl = '') {
      const spec = buildResponsibleAvatarSpec(responsible, avatarUrl);
      if (!spec) {
        return '<span class="text-muted">—</span>';
      }
      const avatarMarkup = spec.avatarUrl
        ? `<span class="dialog-responsible-avatar has-image"><img src="${escapeHtml(spec.avatarUrl)}" alt="Аватар ответственного"></span>`
        : `<span class="dialog-responsible-avatar"><span>${escapeHtml(spec.initial)}</span></span>`;
      return `
      <div class="dialog-responsible-cell">
        ${avatarMarkup}
        <span class="dialog-responsible-name">${escapeHtml(spec.label)}</span>
      </div>
    `;
    }

    function renderMessageAvatar(spec, extraClassName = '') {
      const safeInitial = escapeHtml(String(spec?.initial || '—').trim() || '—');
      const safeLabel = escapeHtml(String(spec?.label || '').trim() || 'Аватар');
      const safeSrc = String(spec?.src || '').trim();
      const classes = ['chat-message-avatar'];
      if (extraClassName) classes.push(extraClassName);
      if (safeSrc) classes.push('has-image');
      const classAttr = classes.join(' ');
      if (safeSrc) {
        return `<span class="${classAttr}" aria-hidden="true"><img src="${escapeHtml(safeSrc)}" alt="${safeLabel}"></span>`;
      }
      return `<span class="${classAttr}" aria-hidden="true"><span>${safeInitial}</span></span>`;
    }

    function resolveDialogMessageAvatarSpec(message, context) {
      const senderType = options.normalizeMessageSenderByType?.(message?.messageType, message?.sender);
      if (senderType === 'support') {
        return {
          src: String(options.operatorAvatarUrl || '').trim(),
          initial: options.avatarInitial?.(options.operatorDisplayName) || '—',
          label: options.operatorDisplayName || 'Оператор',
        };
      }
      if (senderType === 'system') {
        return {
          src: '',
          initial: 'S',
          label: 'Система',
        };
      }
      return {
        src: options.buildAvatarUrl?.(context?.clientUserId) || '',
        initial: options.avatarInitial?.(context?.clientName || message?.sender) || '—',
        label: context?.clientName || message?.sender || 'Клиент',
      };
    }

    function resolveWorkspaceMessageAvatarSpec(message) {
      const senderRole = String(message?.senderRole || message?.sender || '').trim().toLowerCase();
      if (senderRole === 'operator' || senderRole === 'support' || senderRole === 'admin' || senderRole === 'ai_agent') {
        return {
          src: String(options.operatorAvatarUrl || '').trim(),
          initial: options.avatarInitial?.(options.operatorDisplayName) || '—',
          label: options.operatorDisplayName || 'Оператор',
        };
      }
      if (senderRole === 'system') {
        return {
          src: '',
          initial: 'S',
          label: 'Система',
        };
      }
      const activeDialogContext = options.getActiveDialogContext?.() || {};
      return {
        src: options.buildAvatarUrl?.(activeDialogContext.clientUserId) || '',
        initial: options.avatarInitial?.(activeDialogContext.clientName || message?.senderName || message?.senderRole) || '—',
        label: activeDialogContext.clientName || message?.senderName || 'Клиент',
      };
    }

    return {
      buildResponsibleAvatarSpec,
      renderResponsibleCell,
      renderMessageAvatar,
      resolveDialogMessageAvatarSpec,
      resolveWorkspaceMessageAvatarSpec,
    };
  }

  window.DialogsAvatarRuntime = {
    createRuntime,
  };
})();
