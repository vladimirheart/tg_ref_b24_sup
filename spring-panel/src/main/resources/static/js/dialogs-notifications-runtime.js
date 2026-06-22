(function () {
  if (window.DialogsNotificationsRuntime) {
    return;
  }

  function createRuntime(options = {}) {
    const elements = options.elements || {};

    function getActiveDialogState() {
      const activeState = typeof options.getActiveDialogState === 'function'
        ? options.getActiveDialogState()
        : null;
      return activeState && typeof activeState === 'object'
        ? activeState
        : { row: null };
    }

    function setRowUnreadCount(row, unreadCount) {
      if (!row) return;
      const count = Number(unreadCount) || 0;
      row.dataset.unread = String(count);
      const unreadBadge = row.querySelector('.dialog-unread-count');
      if (unreadBadge) {
        unreadBadge.textContent = count;
        unreadBadge.classList.toggle('d-none', count <= 0);
      }
    }

    function updateDialogUnreadCount(unreadCount) {
      const count = Number(unreadCount) || 0;
      if (elements.detailsUnreadCount) {
        elements.detailsUnreadCount.textContent = count;
        elements.detailsUnreadCount.classList.toggle('d-none', count <= 0);
      }
      setRowUnreadCount(getActiveDialogState().row, count);
    }

    function requestSidebarNotificationRefresh(source = 'dialogs') {
      if (typeof window.refreshSidebarNotifications === 'function') {
        window.refreshSidebarNotifications();
        return;
      }
      try {
        window.dispatchEvent(new CustomEvent('iguana:notifications-refresh', { detail: { source } }));
      } catch (_error) {
        // ignore custom event errors
      }
    }

    return {
      setRowUnreadCount,
      updateDialogUnreadCount,
      requestSidebarNotificationRefresh,
    };
  }

  window.DialogsNotificationsRuntime = {
    createRuntime,
  };
})();
