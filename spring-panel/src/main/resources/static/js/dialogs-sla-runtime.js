(function () {
  if (window.DialogsSlaRuntime) {
    return;
  }

  function createRuntime(options = {}) {
    function formatDurationMinutes(totalMinutes) {
      const safeValue = Math.max(0, Math.floor(totalMinutes));
      const hours = Math.floor(safeValue / 60);
      const minutes = safeValue % 60;
      if (hours > 0) {
        return `${hours}ч ${minutes}м`;
      }
      return `${minutes}м`;
    }

    function computeSlaState(row) {
      if (!row || options.isResolvedRow?.(row) === true) {
        return { label: 'Закрыт', className: 'dialog-sla-closed', title: 'SLA не применяется к закрытому обращению' };
      }
      const createdAtRaw = String(row.dataset.createdAt || '').trim();
      const createdAt = options.parseUtcDateValue?.(createdAtRaw);
      if (!createdAtRaw || !createdAt) {
        return { label: 'Нет даты', className: 'dialog-sla-closed', title: 'Не удалось определить время создания обращения' };
      }
      const ageMinutes = (Date.now() - createdAt.getTime()) / 60000;
      const minutesLeft = Number(options.slaTargetMinutes) - ageMinutes;
      const deadline = new Date(createdAt.getTime() + Number(options.slaTargetMinutes) * 60000);
      const deadlineLabel = options.formatUtcDate?.(deadline, { includeTime: true }) || '';
      if (minutesLeft <= 0) {
        return {
          label: `Просрочен ${formatDurationMinutes(Math.abs(minutesLeft))}`,
          className: 'dialog-sla-overdue',
          title: `SLA просрочен. Дедлайн: ${deadlineLabel}`,
        };
      }
      if (minutesLeft <= Number(options.slaWarningMinutes)) {
        return {
          label: `Риск ${formatDurationMinutes(minutesLeft)}`,
          className: 'dialog-sla-risk',
          title: `До дедлайна SLA: ${formatDurationMinutes(minutesLeft)} (дедлайн: ${deadlineLabel})`,
        };
      }
      return {
        label: `До SLA ${formatDurationMinutes(minutesLeft)}`,
        className: 'dialog-sla-safe',
        title: `До дедлайна SLA: ${formatDurationMinutes(minutesLeft)} (дедлайн: ${deadlineLabel})`,
      };
    }

    function updateRowSlaBadge(row) {
      if (!row) return;
      const slaCell = row.querySelector('.dialog-sla-cell');
      if (!slaCell) return;
      const badge = slaCell.querySelector('.dialog-sla-badge');
      if (!badge) return;
      const state = computeSlaState(row);
      const criticalPinned = options.isCriticalSlaDialog?.(row) === true;
      const escalationRequired = options.isEscalationRequiredDialog?.(row) === true;
      const pinMarker = criticalPinned ? ' 📌' : '';
      const escalationMarker = escalationRequired ? ' ⚠' : '';
      badge.className = `badge rounded-pill dialog-sla-badge ${state.className}${criticalPinned ? ' is-pinned' : ''}${escalationRequired ? ' is-escalation' : ''}`;
      badge.textContent = `${state.label}${pinMarker}${escalationMarker}`;
      const markers = [
        criticalPinned ? 'Автопин: критичный SLA' : '',
        escalationRequired ? 'Требуется эскалация' : '',
      ].filter(Boolean).join(' · ');
      badge.title = markers ? `${state.title || ''} · ${markers}` : (state.title || '');
    }

    function updateAllSlaBadges() {
      (options.rowsList?.() || []).forEach((row) => {
        updateRowSlaBadge(row);
        options.updateRowQuickActions?.(row);
      });
    }

    return {
      formatDurationMinutes,
      computeSlaState,
      updateRowSlaBadge,
      updateAllSlaBadges,
    };
  }

  window.DialogsSlaRuntime = {
    createRuntime,
  };
})();
