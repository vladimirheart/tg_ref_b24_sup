(function () {
  function getCsrfToken() {
    var meta = document.querySelector('meta[name="_csrf"]');
    return meta ? meta.getAttribute('content') : '';
  }

  function escapeHtml(value) {
    if (value == null) return '';
    return String(value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function renderStatusBadge(status) {
    var normalized = String(status || '').toUpperCase();
    if (normalized === 'APPROVED') return '<span class="badge text-bg-success">Одобрено</span>';
    if (normalized === 'REJECTED') return '<span class="badge text-bg-secondary">Отклонено</span>';
    return '<span class="badge text-bg-warning">Ожидает</span>';
  }

  function formatDate(value) {
    if (!value) return '—';
    var d = new Date(value);
    if (isNaN(d.getTime())) return escapeHtml(value);
    return d.toLocaleString('ru-RU');
  }

  function init(container) {
    var endpoint = container.getAttribute('data-password-reset-endpoint') || '/api/password-reset-requests';
    var refreshBtn = container.querySelector('[data-password-reset-refresh]');
    var tbody = container.querySelector('[data-password-reset-body]');
    var emptyEl = container.querySelector('[data-password-reset-empty]');
    var errorEl = container.querySelector('[data-password-reset-error]');
    var csrfToken = getCsrfToken();

    function showError(message) {
      if (!errorEl) return;
      if (!message) {
        errorEl.classList.add('d-none');
        errorEl.textContent = '';
        return;
      }
      errorEl.textContent = message;
      errorEl.classList.remove('d-none');
    }

    function setEmpty(isEmpty) {
      if (emptyEl) emptyEl.classList.toggle('d-none', !isEmpty);
    }

    function load() {
      showError('');
      fetch(endpoint, { credentials: 'same-origin' })
        .then(function (response) { return response.json(); })
        .then(function (data) {
          if (!data || data.success === false) {
            throw new Error((data && data.error) || 'Не удалось загрузить запросы.');
          }
          var items = Array.isArray(data.items) ? data.items : [];
          if (!tbody) return;
          if (!items.length) {
            tbody.innerHTML = '';
            setEmpty(true);
            return;
          }
          setEmpty(false);
          tbody.innerHTML = items.map(function (item) {
            var status = String(item.status || '').toUpperCase();
            var canProcess = status === 'PENDING';
            var actionButtons = canProcess
              ? '<div class="btn-group btn-group-sm">' +
                  '<button type="button" class="btn btn-outline-success" data-password-reset-approve="' + item.id + '">Одобрить</button>' +
                  '<button type="button" class="btn btn-outline-secondary" data-password-reset-reject="' + item.id + '">Отклонить</button>' +
                '</div>'
              : '<span class="text-muted small">—</span>';

            var note = item.requested_note ? '<div class=\"text-muted small mt-1\">' + escapeHtml(item.requested_note) + '</div>' : '';
            return '' +
              '<tr>' +
                '<td>' + escapeHtml(item.username_snapshot || '') + note + '</td>' +
                '<td>' + renderStatusBadge(item.status) + '</td>' +
                '<td>' + formatDate(item.created_at) + '</td>' +
                '<td>' + escapeHtml(item.resolved_by_username || '—') + '</td>' +
                '<td>' + formatDate(item.resolved_at) + '</td>' +
                '<td class="text-end">' + actionButtons + '</td>' +
              '</tr>';
          }).join('');
        })
        .catch(function (error) {
          showError(error.message || 'Не удалось загрузить запросы.');
        });
    }

    function processRequest(id, action) {
      var note = window.prompt('Комментарий (опционально):', '') || '';
      fetch(endpoint + '/' + id + '/' + action, {
        method: 'POST',
        credentials: 'same-origin',
        headers: {
          'Content-Type': 'application/json',
          'X-CSRF-TOKEN': csrfToken
        },
        body: JSON.stringify({ note: note })
      })
        .then(function (response) { return response.json(); })
        .then(function (data) {
          if (!data || data.success === false) {
            throw new Error((data && data.error) || 'Операция не выполнена.');
          }
          if (action === 'approve' && data.temporary_password) {
            showError('Временный пароль: ' + data.temporary_password + '\nПередайте его пользователю безопасным каналом.');
          }
          load();
        })
        .catch(function (error) {
          showError(error.message || 'Не удалось обработать запрос.');
        });
    }

    if (refreshBtn) {
      refreshBtn.addEventListener('click', load);
    }

    if (tbody) {
      tbody.addEventListener('click', function (event) {
        var approveBtn = event.target.closest('[data-password-reset-approve]');
        if (approveBtn) {
          processRequest(approveBtn.getAttribute('data-password-reset-approve'), 'approve');
          return;
        }
        var rejectBtn = event.target.closest('[data-password-reset-reject]');
        if (rejectBtn) {
          processRequest(rejectBtn.getAttribute('data-password-reset-reject'), 'reject');
        }
      });
    }

    var modal = document.getElementById('passwordResetRequestsModal');
    if (modal) {
      modal.addEventListener('shown.bs.modal', load);
    }
  }

  document.addEventListener('DOMContentLoaded', function () {
    var container = document.querySelector('[data-password-reset-requests]');
    if (!container) return;
    init(container);
  });
})();
