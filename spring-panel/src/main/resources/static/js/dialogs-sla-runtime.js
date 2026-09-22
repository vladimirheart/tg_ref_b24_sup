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
      if (!row) {
        return { label: 'Нет данных', timingLabel: '—', className: 'dialog-sla-closed', title: 'SLA для обращения не определён' };
      }
      const createdAtRaw = String(row.dataset.createdAt || '').trim();
      const createdAt = options.parseUtcDateValue?.(createdAtRaw);
      const resolved = options.isResolvedRow?.(row) === true;
      const dialogStatus = String(row.dataset.status || '').trim() || (resolved ? 'Закрыт' : 'В работе');
      if (!createdAtRaw || !createdAt) {
        return { label: dialogStatus, timingLabel: 'Нет даты', className: 'dialog-sla-closed', title: 'Не удалось определить время создания обращения' };
      }
      const targetMinutes = Number(options.slaTargetMinutes);
      const deadline = new Date(createdAt.getTime() + targetMinutes * 60000);
      const deadlineLabel = options.formatUtcDate?.(deadline, { includeTime: true }) || '';

      if (resolved) {
        const resolvedAtRaw = String(row.dataset.resolvedAt || '').trim();
        const resolvedAt = options.parseUtcDateValue?.(resolvedAtRaw);
        if (!resolvedAtRaw || !resolvedAt) {
          return {
            label: dialogStatus,
            timingLabel: 'Срок закрытия —',
            className: 'dialog-sla-closed',
            title: `Обращение закрыто. Дедлайн SLA: ${deadlineLabel}`,
          };
        }
        const totalMinutes = Math.max(0, (resolvedAt.getTime() - createdAt.getTime()) / 60000);
        const overdueMinutes = (resolvedAt.getTime() - deadline.getTime()) / 60000;
        if (overdueMinutes > 0) {
          return {
            label: dialogStatus,
            timingLabel: `Просрочен на ${formatDurationMinutes(overdueMinutes)}`,
            className: 'dialog-sla-overdue',
            title: `SLA просрочен на ${formatDurationMinutes(overdueMinutes)}. Закрыто за ${formatDurationMinutes(totalMinutes)}. Дедлайн: ${deadlineLabel}`,
          };
        }
        return {
          label: dialogStatus,
          timingLabel: `В SLA · ${formatDurationMinutes(totalMinutes)}`,
          className: 'dialog-sla-closed',
          title: `Закрыто в SLA за ${formatDurationMinutes(totalMinutes)}. Дедлайн: ${deadlineLabel}`,
        };
      }

      const ageMinutes = (Date.now() - createdAt.getTime()) / 60000;
      const minutesLeft = targetMinutes - ageMinutes;
      if (minutesLeft <= 0) {
        return {
          label: 'Просрочен',
          timingLabel: `+${formatDurationMinutes(Math.abs(minutesLeft))}`,
          className: 'dialog-sla-overdue',
          title: `SLA просрочен. Дедлайн: ${deadlineLabel}`,
        };
      }
      if (minutesLeft <= Number(options.slaWarningMinutes)) {
        return {
          label: 'Риск',
          timingLabel: `до SLA ${formatDurationMinutes(minutesLeft)}`,
          className: 'dialog-sla-risk',
          title: `До дедлайна SLA: ${formatDurationMinutes(minutesLeft)} (дедлайн: ${deadlineLabel})`,
        };
      }
      return {
        label: 'В срок',
        timingLabel: `до SLA ${formatDurationMinutes(minutesLeft)}`,
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
      badge.replaceChildren();
      const statusLine = document.createElement('span');
      statusLine.className = 'dialog-sla-status';
      statusLine.textContent = `${state.label}${pinMarker}${escalationMarker}`;
      const timingLine = document.createElement('span');
      timingLine.className = 'dialog-sla-timing';
      timingLine.textContent = String(state.timingLabel || '—');
      badge.append(statusLine, timingLine);
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
