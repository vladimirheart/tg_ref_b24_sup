(function () {
  'use strict';

  if (window.__iguanaWorkforceManagementBound) return;

  const root = document.querySelector('[data-auth-management]');
  const userModal = document.querySelector('[data-auth-user-modal]');
  if (!root || !userModal) return;

  window.__iguanaWorkforceManagementBound = true;

  const state = {
    positions: [],
    channels: [],
    editingPositionId: null,
    currentUserId: null,
    currentUserSettings: null,
    currentSchedule: [],
    currentSessions: [],
    positionRecipients: [],
    positionBusy: false,
  };

  const elements = {
    positionsSection: root.querySelector('[data-workforce-positions-section]'),
    positionsList: root.querySelector('[data-workforce-positions-list]'),
    positionsEmpty: root.querySelector('[data-workforce-positions-empty]'),
    positionForm: root.querySelector('[data-workforce-position-form]'),
    positionFormTitle: root.querySelector('[data-workforce-position-form-title]'),
    positionId: root.querySelector('[data-workforce-position-id]'),
    positionName: root.querySelector('[data-workforce-position-name]'),
    positionDescription: root.querySelector('[data-workforce-position-description]'),
    positionCheckIn: root.querySelector('[data-workforce-position-checkin]'),
    positionNotify: root.querySelector('[data-workforce-position-notify]'),
    positionRecipientBlock: root.querySelector('[data-workforce-recipient-block]'),
    positionRecipientRows: root.querySelector('[data-workforce-recipient-rows]'),
    positionRecipientAdd: root.querySelector('[data-workforce-recipient-add]'),
    positionTest: root.querySelector('[data-workforce-position-test]'),
    positionTestResults: root.querySelector('[data-workforce-position-test-results]'),
    positionSubmit: root.querySelector('[data-workforce-position-form] button[type="submit"]'),
    positionActive: root.querySelector('[data-workforce-position-active]'),
    positionCancel: root.querySelector('[data-workforce-position-cancel]'),
    positionStatus: root.querySelector('[data-workforce-position-status]'),
    userForm: userModal.querySelector('[data-auth-user-form]'),
    userSection: userModal.querySelector('[data-workforce-user-section]'),
    userLoading: userModal.querySelector('[data-workforce-user-loading]'),
    userContent: userModal.querySelector('[data-workforce-user-content]'),
    userUnavailable: userModal.querySelector('[data-workforce-user-unavailable]'),
    userStatus: userModal.querySelector('[data-workforce-user-status]'),
    userEnabled: userModal.querySelector('[data-workforce-user-enabled]'),
    userPosition: userModal.querySelector('[data-workforce-user-position]'),
    userTimeZone: userModal.querySelector('[data-workforce-user-time-zone]'),
    userCheckInOverride: userModal.querySelector('[data-workforce-user-checkin-override]'),
    userNotifyOverride: userModal.querySelector('[data-workforce-user-notify-override]'),
    userChannelOverride: userModal.querySelector('[data-workforce-user-channel-override]'),
    userTargetOverride: userModal.querySelector('[data-workforce-user-target-override]'),
    userChatOverride: userModal.querySelector('[data-workforce-user-chat-override]'),
    userSettingsSave: userModal.querySelector('[data-workforce-user-settings-save]'),
    scheduleRows: userModal.querySelector('[data-workforce-schedule-rows]'),
    scheduleEmpty: userModal.querySelector('[data-workforce-schedule-empty]'),
    scheduleAdd: userModal.querySelector('[data-workforce-schedule-add]'),
    scheduleSave: userModal.querySelector('[data-workforce-schedule-save]'),
    sessionsRows: userModal.querySelector('[data-workforce-sessions-rows]'),
    sessionsEmpty: userModal.querySelector('[data-workforce-sessions-empty]'),
  };

  const dayLabels = new Map([
    [1, 'Понедельник'],
    [2, 'Вторник'],
    [3, 'Среда'],
    [4, 'Четверг'],
    [5, 'Пятница'],
    [6, 'Суббота'],
    [7, 'Воскресенье'],
  ]);

  function escapeHtml(value) {
    return String(value == null ? '' : value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  function csrfHeaders(json) {
    const headers = {};
    if (json) headers['Content-Type'] = 'application/json';
    const token = document.querySelector('meta[name="_csrf"]')?.getAttribute('content') || '';
    const headerName = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content') || 'X-XSRF-TOKEN';
    if (token) headers[headerName] = token;
    return headers;
  }

  async function requestJson(url, options) {
    const init = Object.assign({ credentials: 'same-origin' }, options || {});
    const response = await fetch(url, init);
    let data = null;
    try {
      data = await response.json();
    } catch (_error) {
      data = {};
    }
    if (!response.ok || data?.success === false) {
      throw new Error(data?.error || ('HTTP ' + response.status));
    }
    return data || {};
  }

  function setPositionStatus(message, variant) {
    if (!elements.positionStatus) return;
    elements.positionStatus.textContent = String(message || '');
    elements.positionStatus.classList.remove('d-none', 'text-success', 'text-danger', 'text-muted');
    if (!message) {
      elements.positionStatus.classList.add('d-none');
      return;
    }
    elements.positionStatus.classList.add(variant === 'danger' ? 'text-danger' : variant === 'success' ? 'text-success' : 'text-muted');
  }

  function setUserStatus(message, variant) {
    if (!elements.userStatus) return;
    elements.userStatus.textContent = String(message || '');
    elements.userStatus.classList.remove('d-none', 'alert-success', 'alert-danger', 'alert-info');
    if (!message) {
      elements.userStatus.classList.add('d-none');
      return;
    }
    elements.userStatus.classList.add(variant === 'danger' ? 'alert-danger' : variant === 'success' ? 'alert-success' : 'alert-info');
  }

  function normalizeBooleanSelect(value) {
    if (value === true || String(value) === 'true') return 'true';
    if (value === false || String(value) === 'false') return 'false';
    return '';
  }

  function booleanOrNull(value) {
    if (value === 'true') return true;
    if (value === 'false') return false;
    return null;
  }

  function normalizeTime(value) {
    const text = String(value || '').trim();
    if (!text) return '';
    return text.length >= 5 ? text.slice(0, 5) : text;
  }

  function normalizeDate(value) {
    const text = String(value || '').trim();
    return text ? text.slice(0, 10) : '';
  }

  function channelLabel(channel) {
    const name = String(channel?.channel_name || 'Канал #' + channel?.id).trim();
    const bot = String(channel?.bot_username || channel?.bot_name || '').trim();
    return bot ? name + ' — ' + bot : name;
  }

  function fillChannelSelect(select, currentValue, inheritLabel) {
    if (!select) return;
    const desired = currentValue == null ? '' : String(currentValue);
    const options = [];
    options.push('<option value="">' + escapeHtml(inheritLabel || 'Не выбран') + '</option>');
    state.channels.forEach(function (channel) {
      const value = String(channel.id);
      options.push('<option value="' + escapeHtml(value) + '">' + escapeHtml(channelLabel(channel)) + '</option>');
    });
    select.innerHTML = options.join('');
    select.value = Array.from(select.options).some(function (option) { return option.value === desired; }) ? desired : '';
  }

  function fillPositionSelect(select, currentValue) {
    if (!select) return;
    const desired = currentValue == null ? '' : String(currentValue);
    const options = ['<option value="">Без должности</option>'];
    state.positions.forEach(function (position) {
      const inactive = position.active === false ? ' (неактивна)' : '';
      options.push('<option value="' + escapeHtml(position.id) + '">' + escapeHtml(position.name || ('Должность #' + position.id)) + inactive + '</option>');
    });
    select.innerHTML = options.join('');
    select.value = Array.from(select.options).some(function (option) { return option.value === desired; }) ? desired : '';
  }

  function targetLabel(value) {
    if (value === 'broadcast_channel') return 'Канал рассылки';
    if (value === 'custom_chat') return 'Указанный чат / канал';
    return 'Чат поддержки';
  }

  function notificationRouteLabel(route) {
    const channel = state.channels.find(function (item) { return String(item.id) === String(route.channel_id || ''); });
    return (channel ? channelLabel(channel) : 'Бот #' + String(route.channel_id || '?')) + ' · ' + targetLabel(route.target) +
      (route.target === 'custom_chat' && route.chat_id ? ' · ' + route.chat_id : '');
  }

  function normalizedPositionRecipient(item) {
    const target = String(item?.target || 'support_chat');
    return {
      channel_id: item?.channel_id == null ? '' : String(item.channel_id),
      target: ['support_chat', 'broadcast_channel', 'custom_chat'].includes(target) ? target : 'support_chat',
      chat_id: String(item?.chat_id || ''),
    };
  }

  function routeAvailabilityHint(route) {
    if (!route.channel_id) return 'Выберите бота для этого получателя.';
    const channel = state.channels.find(function (item) { return String(item.id) === String(route.channel_id); });
    if (!channel) return 'Бот недоступен в настройках каналов.';
    if (route.target === 'custom_chat') return 'Укажите числовой Chat ID или @username Telegram-чата/канала.';
    const configured = route.target === 'broadcast_channel'
      ? channel.broadcast_channel_configured : channel.support_chat_configured;
    return configured === true ? 'Получатель настроен у выбранного бота.' :
      'Получатель не настроен у выбранного бота. Проверьте параметры раздела «Каналы (боты)».';
  }

  function channelOptionsForRecipient(value) {
    const selected = String(value || '');
    const known = state.channels.some(function (channel) { return String(channel.id) === selected; });
    const options = ['<option value="">Выберите бота</option>'];
    if (selected && !known) {
      options.push('<option value="' + escapeHtml(selected) + '">' +
        escapeHtml('Бот #' + selected + ' (недоступен)') + '</option>');
    }
    state.channels.forEach(function (channel) {
      options.push('<option value="' + escapeHtml(channel.id) + '">' + escapeHtml(channelLabel(channel)) + '</option>');
    });
    return options.join('');
  }

  function renderRecipientRows() {
    if (!elements.positionRecipientRows) return;
    if (!state.positionRecipients.length) {
      elements.positionRecipientRows.innerHTML = '<div class="small text-muted p-2 border rounded">Получатели пока не добавлены.</div>';
      syncPositionNotificationControls();
      return;
    }
    elements.positionRecipientRows.innerHTML = state.positionRecipients.map(function (recipient, index) {
      const custom = recipient.target === 'custom_chat';
      return '<div class="border rounded p-2" data-workforce-recipient-row="' + index + '">' +
        '<div class="row g-2 align-items-end">' +
        '<div class="col-12 col-md-5"><label class="form-label small mb-1">Отправлять через бота</label>' +
        '<select class="form-select form-select-sm" data-workforce-recipient-channel aria-label="Бот для получателя ' + (index + 1) + '">' +
        channelOptionsForRecipient(recipient.channel_id) + '</select></div>' +
        '<div class="col-12 col-md-4"><label class="form-label small mb-1">Получатель</label>' +
        '<select class="form-select form-select-sm" data-workforce-recipient-target aria-label="Тип получателя ' + (index + 1) + '">' +
        '<option value="support_chat">Чат поддержки из настроек бота</option>' +
        '<option value="broadcast_channel">Канал рассылки из настроек бота</option>' +
        '<option value="custom_chat">Указанный чат / канал</option></select></div>' +
        '<div class="col-10 col-md-2' + (custom ? '' : ' d-none') + '">' +
        '<label class="form-label small mb-1">Chat ID / @username</label>' +
        '<input type="text" class="form-control form-control-sm" data-workforce-recipient-chat ' +
        'aria-label="Chat ID получателя ' + (index + 1) + '" autocomplete="off" ' +
        'value="' + escapeHtml(recipient.chat_id) + '"></div>' +
        '<div class="col-2 col-md-1 ms-auto"><button type="button" class="btn btn-outline-danger btn-sm w-100" ' +
        'data-workforce-recipient-remove="' + index + '" aria-label="Удалить получателя ' + (index + 1) + '">×</button></div>' +
        '</div><div class="small text-muted mt-1">' + escapeHtml(routeAvailabilityHint(recipient)) + '</div></div>';
    }).join('');
    Array.from(elements.positionRecipientRows.querySelectorAll('[data-workforce-recipient-row]')).forEach(function (row) {
      const index = Number(row.dataset.workforceRecipientRow);
      const recipient = state.positionRecipients[index];
      const channelSelect = row.querySelector('[data-workforce-recipient-channel]');
      const targetSelect = row.querySelector('[data-workforce-recipient-target]');
      if (channelSelect) channelSelect.value = recipient.channel_id;
      if (targetSelect) targetSelect.value = recipient.target;
    });
    syncPositionNotificationControls();
  }

  function clearPositionTestResults() {
    if (!elements.positionTestResults) return;
    elements.positionTestResults.textContent = '';
    elements.positionTestResults.classList.add('d-none');
  }

  function displayPositionTestResults(response, requestedCount) {
    const root = elements.positionTestResults;
    if (!root) return;
    const rows = Array.isArray(response.results) ? response.results : [];
    const sent = Number(response.sent || 0);
    const failed = Number(response.failed || 0);
    const skipped = Math.max(0, requestedCount - rows.length);
    const summary = 'Тест: доставлено ' + sent + ', ошибок ' + failed +
      (skipped ? ', пропущено повторов ' + skipped : '') + '. Настройки ещё не сохранены.';
    const errors = {
      channel_not_configured: 'Не выбран бот', channel_not_found: 'Бот не найден',
      channel_inactive: 'Бот отключён', unsupported_platform: 'Платформа пока не поддерживается',
      bot_token_missing: 'У бота отсутствует токен', recipient_not_configured: 'Получатель не настроен',
      message_empty: 'Пустое сообщение', delivery_failed: 'Мессенджер не подтвердил доставку',
      delivery_exception: 'Ошибка транспорта при отправке',
    };
    const lines = rows.map(function (item) {
      const reason = item.success === true ? 'Отправлено' : (errors[item.error] || item.error || 'Ошибка отправки');
      const text = notificationRouteLabel(item) +
        (item.recipient ? ' (' + item.recipient + ')' : '') + ' — ' + reason;
      return '<li>' + escapeHtml(text) + '</li>';
    }).join('');
    root.innerHTML = '<div class="fw-semibold">' + escapeHtml(summary) + '</div>' +
      (lines ? '<ul class="mb-0 mt-1 ps-3">' + lines + '</ul>' : '');
    root.classList.remove('d-none', 'alert-success', 'alert-danger', 'alert-secondary');
    root.classList.add(failed > 0 || sent === 0 ? 'alert-danger' : 'alert-success');
  }

  function renderPositions() {
    if (!elements.positionsList) return;
    if (!state.positions.length) {
      elements.positionsList.innerHTML = '';
      elements.positionsEmpty?.classList.remove('d-none');
      return;
    }
    elements.positionsEmpty?.classList.add('d-none');
    elements.positionsList.innerHTML = state.positions.map(function (position) {
      const routes = Array.isArray(position.notification_recipients) ? position.notification_recipients : [];
      const badges = [
        position.check_in_required !== false ? 'check-in' : 'без check-in',
        position.notify_on_check_in === true ? 'оповещение' : 'без оповещения',
        position.active === false ? 'неактивна' : 'активна',
      ];
      const routesText = routes.length ? routes.map(notificationRouteLabel).join('; ') : 'получатели не выбраны';
      return '<article class="workforce-position-card" data-workforce-position-card="' + escapeHtml(position.id) + '">' +
        '<div class="workforce-position-card__main"><div class="d-flex align-items-start justify-content-between gap-2">' +
        '<div><h6 class="mb-1">' + escapeHtml(position.name || '') + '</h6><div class="small text-muted">' + escapeHtml(position.description || 'Без описания') + '</div></div>' +
        '<button type="button" class="btn btn-outline-secondary btn-sm" data-workforce-position-edit="' + escapeHtml(position.id) + '">Изменить</button></div>' +
        '<div class="workforce-position-card__badges mt-2">' + badges.map(function (item) { return '<span>' + escapeHtml(item) + '</span>'; }).join('') + '</div>' +
        (position.notify_on_check_in === true ? '<div class="small text-muted mt-2">Кому отправлять (' + routes.length + '): ' + escapeHtml(routesText) + '</div>' : '') +
        '</div></article>';
    }).join('');
  }

  function setPositionBusy(isBusy) {
    state.positionBusy = isBusy;
    if (elements.positionTest) elements.positionTest.textContent = isBusy ? 'Отправляем / сохраняем…' : 'Отправить тест';
    if (elements.positionSubmit) elements.positionSubmit.disabled = isBusy;
    syncPositionNotificationControls();
  }

  function resetPositionForm() {
    state.editingPositionId = null;
    state.positionRecipients = [];
    elements.positionForm?.reset();
    if (elements.positionId) elements.positionId.value = '';
    if (elements.positionFormTitle) elements.positionFormTitle.textContent = 'Новая должность';
    if (elements.positionCheckIn) elements.positionCheckIn.checked = true;
    if (elements.positionActive) elements.positionActive.checked = true;
    elements.positionCancel?.classList.add('d-none');
    setPositionStatus('');
    clearPositionTestResults();
    renderRecipientRows();
  }

  function editPosition(positionId) {
    const position = state.positions.find(function (item) { return String(item.id) === String(positionId); });
    if (!position) return;
    state.editingPositionId = position.id;
    if (elements.positionId) elements.positionId.value = String(position.id);
    if (elements.positionFormTitle) elements.positionFormTitle.textContent = 'Редактирование должности';
    if (elements.positionName) elements.positionName.value = position.name || '';
    if (elements.positionDescription) elements.positionDescription.value = position.description || '';
    if (elements.positionCheckIn) elements.positionCheckIn.checked = position.check_in_required !== false;
    if (elements.positionNotify) elements.positionNotify.checked = position.notify_on_check_in === true;
    const recipients = Array.isArray(position.notification_recipients) ? position.notification_recipients : [];
    state.positionRecipients = recipients.map(normalizedPositionRecipient);
    if (elements.positionActive) elements.positionActive.checked = position.active !== false;
    elements.positionCancel?.classList.remove('d-none');
    setPositionStatus('');
    clearPositionTestResults();
    if (elements.positionNotify?.checked && !state.positionRecipients.length) {
      state.positionRecipients.push(normalizedPositionRecipient({}));
    }
    renderRecipientRows();
    elements.positionName?.focus();
  }

  function syncPositionNotificationControls() {
    const enabled = Boolean(elements.positionNotify?.checked);
    elements.positionRecipientBlock?.classList.toggle('d-none', !enabled);
    if (elements.positionRecipientAdd) {
      elements.positionRecipientAdd.disabled = state.positionBusy || !enabled || state.positionRecipients.length >= 10;
    }
    if (elements.positionTest) {
      elements.positionTest.disabled = state.positionBusy || !enabled || state.positionRecipients.length === 0;
    }
    elements.positionRecipientRows?.querySelectorAll('button, select, input').forEach(function (element) {
      element.disabled = state.positionBusy || !enabled;
    });
  }

  function collectPositionRecipients(requireComplete) {
    const items = [];
    const seen = new Set();
    if (state.positionRecipients.length > 10) throw new Error('Можно выбрать не более 10 получателей.');
    state.positionRecipients.forEach(function (route, index) {
      const channelId = String(route.channel_id || '').trim();
      const target = String(route.target || 'support_chat');
      const chatId = String(route.chat_id || '').trim();
      if (!channelId) {
        if (!requireComplete) return;
        throw new Error('Выберите бота для получателя №' + (index + 1) + '.');
      }
      if (!/^\d+$/.test(channelId) || Number(channelId) < 1) {
        throw new Error('Некорректный бот у получателя №' + (index + 1) + '.');
      }
      if (!['support_chat', 'broadcast_channel', 'custom_chat'].includes(target)) {
        throw new Error('Некорректный тип получателя №' + (index + 1) + '.');
      }
      if (target === 'custom_chat' && !/^(?:-?\d{1,20}|@[a-zA-Z0-9_]{5,32})$/.test(chatId)) {
        if (!requireComplete) return;
        throw new Error('Укажите корректный Chat ID или @username для получателя №' + (index + 1) + '.');
      }
      const key = channelId + ':' + target + ':' + (target === 'custom_chat' ? chatId : '');
      if (seen.has(key)) throw new Error('Одинаковый получатель добавлен несколько раз (№' + (index + 1) + ').');
      seen.add(key);
      items.push({
        channel_id: Number(channelId),
        target: target,
        chat_id: target === 'custom_chat' ? chatId : null,
      });
    });
    if (requireComplete && !items.length) throw new Error('Добавьте хотя бы одного получателя уведомления.');
    return items;
  }

  async function testPositionNotification() {
    if (state.positionBusy) return;
    if (!elements.positionNotify?.checked) {
      setPositionStatus('Сначала включите уведомление о приходе.', 'danger');
      return;
    }
    let recipients;
    try {
      recipients = collectPositionRecipients(true);
    } catch (error) {
      setPositionStatus(error.message, 'danger');
      return;
    }
    clearPositionTestResults();
    setPositionStatus('Отправляем тестовые сообщения. Должность не сохраняется…');
    setPositionBusy(true);
    try {
      const result = await requestJson('/api/workforce/positions/test-notification', {
        method: 'POST', headers: csrfHeaders(true),
        body: JSON.stringify({notification_recipients: recipients}),
      });
      displayPositionTestResults(result, recipients.length);
      setPositionStatus('Тест завершён. При необходимости скорректируйте получателей, затем сохраните должность.',
        Number(result.failed || 0) > 0 ? 'danger' : 'success');
    } catch (error) {
      const reason = String(error.message || error);
      setPositionStatus(reason.includes('429') ? 'Повторный тест доступен не ранее чем через 15 секунд.' : reason, 'danger');
      if (elements.positionTestResults) {
        elements.positionTestResults.textContent = 'Тестовая отправка не выполнена. Должность не сохранена.';
        elements.positionTestResults.classList.remove('d-none', 'alert-success', 'alert-secondary');
        elements.positionTestResults.classList.add('alert-danger');
      }
    } finally {
      setPositionBusy(false);
    }
  }

  async function savePosition(event) {
    event.preventDefault();
    if (state.positionBusy) return;
    const name = String(elements.positionName?.value || '').trim();
    if (!name) {
      setPositionStatus('Укажите название должности.', 'danger');
      return;
    }
    const notify = Boolean(elements.positionNotify?.checked);
    let recipients;
    try {
      recipients = collectPositionRecipients(notify);
    } catch (error) {
      setPositionStatus(error.message, 'danger');
      return;
    }
    const payload = {
      name: name,
      description: String(elements.positionDescription?.value || '').trim() || null,
      check_in_required: Boolean(elements.positionCheckIn?.checked),
      notify_on_check_in: notify,
      notification_recipients: recipients,
      active: Boolean(elements.positionActive?.checked),
    };
    const editing = state.editingPositionId != null;
    const url = editing ? '/api/workforce/positions/' + state.editingPositionId : '/api/workforce/positions';
    setPositionStatus('Сохраняем…');
    setPositionBusy(true);
    try {
      await requestJson(url, {
        method: editing ? 'PUT' : 'POST',
        headers: csrfHeaders(true),
        body: JSON.stringify(payload),
      });
      await loadCatalogs();
      resetPositionForm();
      setPositionStatus(editing ? 'Должность обновлена.' : 'Должность создана.', 'success');
    } catch (error) {
      setPositionStatus(error.message || String(error), 'danger');
    } finally {
      setPositionBusy(false);
    }
  }

  async function loadCatalogs() {
    const data = await requestJson('/api/workforce/state');
    state.positions = Array.isArray(data.positions) ? data.positions : [];
    state.channels = Array.isArray(data.notification_channels) ? data.notification_channels : [];
    renderPositions();
    if (elements.userPosition && state.currentUserSettings) fillPositionSelect(elements.userPosition, state.currentUserSettings.position_id);
    if (elements.userChannelOverride && state.currentUserSettings) fillChannelSelect(elements.userChannelOverride, state.currentUserSettings.notification_channel_id_override, 'Наследовать от должности');
  }

  function showUserWorkforceUnavailable(message) {
    elements.userLoading?.classList.add('d-none');
    elements.userContent?.classList.add('d-none');
    if (elements.userUnavailable) {
      elements.userUnavailable.textContent = message || 'Сначала сохраните пользователя, затем настройте его работу и расписание.';
      elements.userUnavailable.classList.remove('d-none');
    }
  }

  function showUserWorkforceLoading() {
    elements.userUnavailable?.classList.add('d-none');
    elements.userContent?.classList.add('d-none');
    elements.userLoading?.classList.remove('d-none');
    setUserStatus('');
  }

  function showUserWorkforceContent() {
    elements.userUnavailable?.classList.add('d-none');
    elements.userLoading?.classList.add('d-none');
    elements.userContent?.classList.remove('d-none');
  }

  function setUserTimeZone(zone) {
    if (!elements.userTimeZone) return;
    const wanted = String(zone || 'UTC').trim() || 'UTC';
    const options = Array.from(elements.userTimeZone.options || []);
    if (!options.some(function (option) { return option.value === wanted; })) {
      // Preserve a previously saved IANA zone even if it is absent from the short list.
      const option = document.createElement('option');
      option.value = wanted;
      option.textContent = wanted + ' (ранее выбран)';
      option.dataset.workforceSavedTimeZone = 'true';
      elements.userTimeZone.appendChild(option);
    }
    elements.userTimeZone.value = wanted;
  }

  function renderUserSettings(settings) {
    state.currentUserSettings = settings || {};
    if (elements.userEnabled) elements.userEnabled.checked = settings?.enabled === true;
    fillPositionSelect(elements.userPosition, settings?.position_id);
    setUserTimeZone(settings?.time_zone || 'UTC');
    if (elements.userCheckInOverride) elements.userCheckInOverride.value = normalizeBooleanSelect(settings?.check_in_required_override);
    if (elements.userNotifyOverride) elements.userNotifyOverride.value = normalizeBooleanSelect(settings?.notify_on_check_in_override);
    fillChannelSelect(elements.userChannelOverride, settings?.notification_channel_id_override, 'Наследовать от должности');
    if (elements.userTargetOverride) elements.userTargetOverride.value = settings?.notification_target_override || '';
    if (elements.userChatOverride) elements.userChatOverride.value = settings?.notification_chat_id_override || '';
    syncUserNotificationControls();
  }

  function syncUserNotificationControls() {
    const target = String(elements.userTargetOverride?.value || '');
    const custom = target === 'custom_chat';
    if (elements.userChatOverride) {
      elements.userChatOverride.disabled = !custom;
      elements.userChatOverride.closest('[data-workforce-user-custom-chat-wrap]')?.classList.toggle('d-none', !custom);
    }
  }

  function scheduleRowMarkup(rule) {
    const day = Number(rule?.day_of_week || 1);
    const options = Array.from(dayLabels.entries()).map(function (entry) {
      return '<option value="' + entry[0] + '"' + (entry[0] === day ? ' selected' : '') + '>' + escapeHtml(entry[1]) + '</option>';
    }).join('');
    return '<tr data-workforce-schedule-row>' +
      '<td><select class="form-select form-select-sm" data-workforce-rule-day>' + options + '</select></td>' +
      '<td><input type="time" class="form-control form-control-sm" value="' + escapeHtml(normalizeTime(rule?.start_time) || '09:00') + '" data-workforce-rule-start></td>' +
      '<td><input type="time" class="form-control form-control-sm" value="' + escapeHtml(normalizeTime(rule?.end_time) || '18:00') + '" data-workforce-rule-end></td>' +
      '<td><input type="date" class="form-control form-control-sm" value="' + escapeHtml(normalizeDate(rule?.effective_from)) + '" data-workforce-rule-from></td>' +
      '<td><input type="date" class="form-control form-control-sm" value="' + escapeHtml(normalizeDate(rule?.effective_to)) + '" data-workforce-rule-to></td>' +
      '<td><input type="number" min="0" max="720" class="form-control form-control-sm workforce-minute-input" value="' + escapeHtml(rule?.check_in_open_minutes == null ? 120 : rule.check_in_open_minutes) + '" data-workforce-rule-open></td>' +
      '<td><input type="number" min="0" max="720" class="form-control form-control-sm workforce-minute-input" value="' + escapeHtml(rule?.late_after_minutes == null ? 15 : rule.late_after_minutes) + '" data-workforce-rule-late></td>' +
      '<td class="text-center"><input type="checkbox" class="form-check-input" data-workforce-rule-active' + (rule?.active === false ? '' : ' checked') + '></td>' +
      '<td class="text-end"><button type="button" class="btn btn-outline-danger btn-sm" data-workforce-rule-remove aria-label="Удалить смену">×</button></td>' +
      '</tr>';
  }

  function renderSchedule(rules) {
    state.currentSchedule = Array.isArray(rules) ? rules : [];
    if (!elements.scheduleRows) return;
    elements.scheduleRows.innerHTML = state.currentSchedule.map(scheduleRowMarkup).join('');
    elements.scheduleEmpty?.classList.toggle('d-none', state.currentSchedule.length > 0);
  }

  function addScheduleRow(rule) {
    if (!elements.scheduleRows) return;
    elements.scheduleRows.insertAdjacentHTML('beforeend', scheduleRowMarkup(rule || {}));
    elements.scheduleEmpty?.classList.add('d-none');
  }

  function collectScheduleRules() {
    if (!elements.scheduleRows) return [];
    return Array.from(elements.scheduleRows.querySelectorAll('[data-workforce-schedule-row]')).map(function (row) {
      const start = row.querySelector('[data-workforce-rule-start]')?.value || '';
      const end = row.querySelector('[data-workforce-rule-end]')?.value || '';
      if (!start || !end) throw new Error('Для каждой смены укажите время начала и окончания.');
      const from = row.querySelector('[data-workforce-rule-from]')?.value || '';
      const to = row.querySelector('[data-workforce-rule-to]')?.value || '';
      if (from && to && from > to) throw new Error('Дата окончания периода не может быть раньше даты начала.');
      return {
        day_of_week: Number(row.querySelector('[data-workforce-rule-day]')?.value || 1),
        start_time: start,
        end_time: end,
        effective_from: from || null,
        effective_to: to || null,
        check_in_open_minutes: Number(row.querySelector('[data-workforce-rule-open]')?.value || 0),
        late_after_minutes: Number(row.querySelector('[data-workforce-rule-late]')?.value || 0),
        active: Boolean(row.querySelector('[data-workforce-rule-active]')?.checked),
      };
    });
  }

  function formatSessionTime(value) {
    if (!value) return '—';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return String(value);
    return date.toLocaleString('ru-RU');
  }

  function sessionStatusLabel(status) {
    if (status === 'on_time') return 'Вовремя';
    if (status === 'late') return 'Опоздание';
    if (status === 'unscheduled') return 'Вне расписания';
    return status || '—';
  }

  function notificationStatusLabel(status) {
    if (status === 'sent') return 'Отправлено';
    if (status === 'failed') return 'Ошибка';
    if (status === 'pending') return 'Ожидает';
    if (status === 'skipped') return 'Не требуется';
    return status || '—';
  }

  function renderSessions(sessions) {
    state.currentSessions = Array.isArray(sessions) ? sessions : [];
    if (!elements.sessionsRows) return;
    elements.sessionsRows.innerHTML = state.currentSessions.map(function (session) {
      return '<tr><td>' + escapeHtml(formatSessionTime(session.checked_in_at)) + '</td><td>' + escapeHtml(sessionStatusLabel(session.check_in_status)) + '</td><td>' + escapeHtml(session.position_name_snapshot || '—') + '</td><td>' + escapeHtml(notificationStatusLabel(session.notification_status)) + (session.notification_error ? '<div class="small text-danger">' + escapeHtml(session.notification_error) + '</div>' : '') + '</td></tr>';
    }).join('');
    elements.sessionsEmpty?.classList.toggle('d-none', state.currentSessions.length > 0);
  }

  async function loadUserWorkforce(userId) {
    state.currentUserId = userId;
    showUserWorkforceLoading();
    try {
      if (!state.positions.length && !state.channels.length) await loadCatalogs();
      const results = await Promise.all([
        requestJson('/api/workforce/users/' + userId + '/settings'),
        requestJson('/api/workforce/users/' + userId + '/schedule'),
        requestJson('/api/workforce/users/' + userId + '/sessions?limit=12'),
      ]);
      if (String(state.currentUserId) !== String(userId)) return;
      renderUserSettings(results[0].settings || {});
      renderSchedule(results[1].schedule || []);
      renderSessions(results[2].sessions || []);
      showUserWorkforceContent();
    } catch (error) {
      showUserWorkforceUnavailable('Не удалось загрузить workforce-настройки: ' + (error.message || String(error)));
    }
  }

  function collectUserWorkforcePayload() {
    const positionId = String(elements.userPosition?.value || '').trim();
    const channelId = String(elements.userChannelOverride?.value || '').trim();
    const target = String(elements.userTargetOverride?.value || '').trim();
    const chatId = String(elements.userChatOverride?.value || '').trim();
    if (target === 'custom_chat' && !chatId) {
      throw new Error('Для персонального custom chat укажите chat ID.');
    }
    return {
      enabled: Boolean(elements.userEnabled?.checked),
      position_id: positionId ? Number(positionId) : null,
      time_zone: String(elements.userTimeZone?.value || 'UTC').trim() || 'UTC',
      check_in_required_override: booleanOrNull(elements.userCheckInOverride?.value || ''),
      notify_on_check_in_override: booleanOrNull(elements.userNotifyOverride?.value || ''),
      notification_channel_id_override: channelId ? Number(channelId) : null,
      notification_target_override: target || null,
      notification_chat_id_override: target === 'custom_chat' ? (chatId || null) : null,
    };
  }

  function snapshotUserWorkforceSettings(settings) {
    const current = settings || {};
    const target = String(current.notification_target_override || '');
    return {
      enabled: current.enabled === true,
      position_id: current.position_id == null ? null : Number(current.position_id),
      time_zone: String(current.time_zone || 'UTC').trim() || 'UTC',
      check_in_required_override: current.check_in_required_override == null ? null : Boolean(current.check_in_required_override),
      notify_on_check_in_override: current.notify_on_check_in_override == null ? null : Boolean(current.notify_on_check_in_override),
      notification_channel_id_override: current.notification_channel_id_override == null ? null : Number(current.notification_channel_id_override),
      notification_target_override: target || null,
      notification_chat_id_override: target === 'custom_chat' ? (String(current.notification_chat_id_override || '').trim() || null) : null,
    };
  }

  function workforceUserSettingsChanged() {
    if (!state.currentUserId || !state.currentUserSettings) return false;
    try {
      return JSON.stringify(collectUserWorkforcePayload()) !== JSON.stringify(snapshotUserWorkforceSettings(state.currentUserSettings));
    } catch (_) {
      // Invalid changed fields still need validation when the user presses Save.
      return true;
    }
  }

  async function saveUserSettings() {
    const userId = state.currentUserId;
    if (!userId) return false;
    let payload;
    try {
      payload = collectUserWorkforcePayload();
    } catch (error) {
      setUserStatus(error.message || String(error), 'danger');
      return false;
    }
    elements.userSettingsSave && (elements.userSettingsSave.disabled = true);
    setUserStatus('Сохраняем настройки…');
    try {
      const data = await requestJson('/api/workforce/users/' + userId + '/settings', {
        method: 'PUT',
        headers: csrfHeaders(true),
        body: JSON.stringify(payload),
      });
      if (String(state.currentUserId) !== String(userId)) return false;
      renderUserSettings(data.settings || payload);
      setUserStatus('Настройки работы сохранены.', 'success');
      return true;
    } catch (error) {
      setUserStatus(error.message || String(error), 'danger');
      return false;
    } finally {
      elements.userSettingsSave && (elements.userSettingsSave.disabled = false);
    }
  }

  async function saveSchedule() {
    const userId = state.currentUserId;
    if (!userId) return;
    let rules;
    try {
      rules = collectScheduleRules();
    } catch (error) {
      setUserStatus(error.message || String(error), 'danger');
      return;
    }
    elements.scheduleSave && (elements.scheduleSave.disabled = true);
    setUserStatus('Сохраняем расписание…');
    try {
      const data = await requestJson('/api/workforce/users/' + userId + '/schedule', {
        method: 'PUT',
        headers: csrfHeaders(true),
        body: JSON.stringify({ rules: rules }),
      });
      renderSchedule(data.schedule || []);
      setUserStatus('Расписание сохранено.', 'success');
    } catch (error) {
      setUserStatus(error.message || String(error), 'danger');
    } finally {
      elements.scheduleSave && (elements.scheduleSave.disabled = false);
    }
  }

  function onUserModalOpen(event) {
    const detail = event?.detail || {};
    const mode = detail.mode || elements.userForm?.dataset.mode || '';
    const userId = detail.userId || elements.userForm?.dataset.userId || '';
    if (mode !== 'edit' || !userId) {
      state.currentUserId = null;
      showUserWorkforceUnavailable('Сначала создайте пользователя и сохраните карточку. После этого появятся должность, check-in и расписание.');
      return;
    }
    loadUserWorkforce(userId);
  }

  function bindEvents() {
    elements.positionForm?.addEventListener('submit', savePosition);
    elements.positionCancel?.addEventListener('click', resetPositionForm);
    elements.positionNotify?.addEventListener('change', function () {
      if (elements.positionNotify.checked && !state.positionRecipients.length) {
        state.positionRecipients.push(normalizedPositionRecipient({}));
      }
      clearPositionTestResults();
      renderRecipientRows();
    });
    elements.positionRecipientAdd?.addEventListener('click', function () {
      if (state.positionBusy || !elements.positionNotify?.checked || state.positionRecipients.length >= 10) return;
      state.positionRecipients.push(normalizedPositionRecipient({}));
      clearPositionTestResults();
      renderRecipientRows();
    });
    elements.positionTest?.addEventListener('click', testPositionNotification);
    elements.positionRecipientRows?.addEventListener('click', function (event) {
      const button = event.target.closest('[data-workforce-recipient-remove]');
      if (!button || state.positionBusy) return;
      const index = Number(button.dataset.workforceRecipientRemove);
      if (!Number.isInteger(index) || index < 0 || index >= state.positionRecipients.length) return;
      state.positionRecipients.splice(index, 1);
      clearPositionTestResults();
      renderRecipientRows();
    });
    elements.positionRecipientRows?.addEventListener('change', function (event) {
      const row = event.target.closest('[data-workforce-recipient-row]');
      if (!row || state.positionBusy) return;
      const index = Number(row.dataset.workforceRecipientRow);
      const recipient = state.positionRecipients[index];
      if (!recipient) return;
      if (event.target.matches('[data-workforce-recipient-channel]')) {
        recipient.channel_id = event.target.value;
      } else if (event.target.matches('[data-workforce-recipient-target]')) {
        recipient.target = event.target.value;
        if (recipient.target !== 'custom_chat') recipient.chat_id = '';
      } else if (event.target.matches('[data-workforce-recipient-chat]')) {
        recipient.chat_id = event.target.value;
      } else return;
      clearPositionTestResults();
      if (!event.target.matches('[data-workforce-recipient-chat]')) renderRecipientRows();
    });
    elements.positionRecipientRows?.addEventListener('input', function (event) {
      if (!event.target.matches('[data-workforce-recipient-chat]')) return;
      const row = event.target.closest('[data-workforce-recipient-row]');
      if (!row) return;
      const recipient = state.positionRecipients[Number(row.dataset.workforceRecipientRow)];
      if (recipient) recipient.chat_id = event.target.value;
      clearPositionTestResults();
    });
    elements.positionsList?.addEventListener('click', function (event) {
      const button = event.target.closest('[data-workforce-position-edit]');
      if (button) editPosition(button.dataset.workforcePositionEdit);
    });
    elements.userTargetOverride?.addEventListener('change', syncUserNotificationControls);
    elements.userSettingsSave?.addEventListener('click', function () { void saveUserSettings(); });
    userModal.addEventListener('authManagement:collectWorkforceChanges', function (event) {
      const detail = event.detail || {};
      if (String(detail.userId) !== String(state.currentUserId) || !state.currentUserSettings) return;
      if (!workforceUserSettingsChanged()) return;
      detail.pending = true;
      detail.save = function () { return saveUserSettings(); };
    });
    elements.scheduleAdd?.addEventListener('click', function () { addScheduleRow({ day_of_week: 1, start_time: '09:00', end_time: '18:00', check_in_open_minutes: 120, late_after_minutes: 15, active: true }); });
    elements.scheduleRows?.addEventListener('click', function (event) {
      const button = event.target.closest('[data-workforce-rule-remove]');
      if (!button) return;
      button.closest('[data-workforce-schedule-row]')?.remove();
      if (!elements.scheduleRows.querySelector('[data-workforce-schedule-row]')) elements.scheduleEmpty?.classList.remove('d-none');
    });
    elements.scheduleSave?.addEventListener('click', saveSchedule);
    userModal.addEventListener('authManagement:userModalOpen', onUserModalOpen);
    userModal.addEventListener('shown.bs.modal', function () {
      if (!state.currentUserId && elements.userForm?.dataset.mode) onUserModalOpen({ detail: { mode: elements.userForm.dataset.mode, userId: elements.userForm.dataset.userId } });
    });
    userModal.addEventListener('hidden.bs.modal', function () {
      state.currentUserId = null;
      state.currentUserSettings = null;
      state.currentSchedule = [];
      state.currentSessions = [];
      setUserStatus('');
    });
  }

  bindEvents();
  resetPositionForm();
  loadCatalogs().catch(function (error) {
    setPositionStatus('Не удалось загрузить workforce-каталог: ' + (error.message || String(error)), 'danger');
  });
})();
