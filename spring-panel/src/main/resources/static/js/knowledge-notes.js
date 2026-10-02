(function () {
  'use strict';

  function formatTimes() {
    const uiTime = window.iguanaUiTime;
    document.querySelectorAll('[data-note-time]').forEach(function (element) {
      const value = element.getAttribute('datetime') || element.textContent || '';
      if (!value) return;
      if (uiTime && typeof uiTime.formatDateTime === 'function') {
        element.textContent = uiTime.formatDateTime(value, { fallback: value });
      }
    });
  }

  function bindCustomFields() {
    const container = document.querySelector('[data-note-custom-fields]');
    const addButton = document.querySelector('[data-note-add-field]');
    if (!container || !addButton) return;

    function bindRemove(button) {
      if (!button || button.dataset.noteBound === '1') return;
      button.dataset.noteBound = '1';
      button.addEventListener('click', function () {
        const row = button.closest('[data-note-field-row]');
        if (row) row.remove();
      });
    }

    container.querySelectorAll('[data-note-remove-field]').forEach(bindRemove);
    addButton.addEventListener('click', function () {
      const row = document.createElement('div');
      row.className = 'row g-2 mb-2';
      row.setAttribute('data-note-field-row', '');
      row.innerHTML = '<div class="col-md-5"><input class="form-control" name="customFieldKey" maxlength="120" placeholder="Название поля"></div>' +
        '<div class="col-md-6"><input class="form-control" name="customFieldValue" maxlength="4000" placeholder="Значение"></div>' +
        '<div class="col-md-1 d-grid"><button class="btn btn-outline-secondary" type="button" data-note-remove-field aria-label="Удалить поле"><i class="bi bi-x-lg"></i></button></div>';
      container.appendChild(row);
      bindRemove(row.querySelector('[data-note-remove-field]'));
      row.querySelector('input')?.focus();
    });
  }

  function init() {
    formatTimes();
    bindCustomFields();
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init, { once: true });
  else init();
})();
