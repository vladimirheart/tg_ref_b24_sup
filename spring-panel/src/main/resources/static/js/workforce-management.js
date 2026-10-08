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
    positionChannel: root.querySelector('[data-workforce-position-channel]'),
    positionTarget: root.querySelector('[data-workforce-position-target]'),
    positionChat: root.querySelector('[data-workforce-position-chat]'),
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
    if (value === 'custom_chat') return 'Другой chat ID';
    return 'Чат поддержки';
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
      const channel = state.channels.find(function (item) { return String(item.id) === String(position.notification_channel_id || ''); });
      const badges = [
        position.check_in_required !== false ? 'check-in' : 'без check-in',
        position.notify_on_check_in === true ? 'оповещение' : 'без оповещения',
        position.active === false ? 'неактивна' : 'активна',
      ];
      return '<article class="workforce-position-card" data-workforce-position-card="' + escapeHtml(position.id) + '">' +
        '<div class="workforce-position-card__main"><div class="d-flex align-items-start justify-content-between gap-2">' +
        '<div><h6 class="mb-1">' + escapeHtml(position.name || '') + '</h6><div class="small text-muted">' + escapeHtml(position.description || 'Без описания') + '</div></div>' +
        '<button type="button" class="btn btn-outline-secondary btn-sm" data-workforce-position-edit="' + escapeHtml(position.id) + '">Изменить</button></div>' +
        '<div class="workforce-position-card__badges mt-2">' + badges.map(function (item) { return '<span>' + escapeHtml(item) + '</span>'; }).join('') + '</div>' +
        (position.notify_on_check_in === true ? '<div class="small text-muted mt-2">Бот: ' + escapeHtml(channel ? channelLabel(channel) : 'не выбран') + ' · ' + escapeHtml(targetLabel(position.notification_target)) + (position.notification_target === 'custom_chat' && position.notification_chat_id ? ' · ' + escapeHtml(position.notification_chat_id) : '') + '</div>' : '') +
        '</div></article>';
    }).join('');
  }

  function resetPositionForm() {
    state.editingPositionId = null;
    elements.positionForm?.reset();
    if (elements.positionId) elements.positionId.value = '';
    if (elements.positionFormTitle) elements.positionFormTitle.textContent = 'Новая должность';
    if (elements.positionCheckIn) elements.positionCheckIn.checked = true;
    if (elements.positionActive) elements.positionActive.checked = true;
    if (elements.positionTarget) elements.positionTarget.value = 'support_chat';
    fillChannelSelect(elements.positionChannel, '', 'Выберите канал/бот');
    elements.positionCancel?.classList.add('d-none');
    setPositionStatus('');
    syncPositionNotificationControls();
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
    fillChannelSelect(elements.positionChannel, position.notification_channel_id, 'Выберите канал/бот');
    if (elements.positionTarget) elements.positionTarget.value = position.notification_target || 'support_chat';
    if (elements.positionChat) elements.positionChat.value = position.notification_chat_id || '';
    if (elements.positionActive) elements.positionActive.checked = position.active !== false;
    elements.positionCancel?.classList.remove('d-none');
    setPositionStatus('');
    syncPositionNotificationControls();
    elements.positionName?.focus();
  }

  function syncPositionNotificationControls() {
    const enabled = Boolean(elements.positionNotify?.checked);
    [elements.positionChannel, elements.positionTarget].forEach(function (control) {
      if (control) control.disabled = !enabled;
    });
    const custom = enabled && elements.positionTarget?.value === 'custom_chat';
    if (elements.positionChat) {
      elements.positionChat.disabled = !custom;
      elements.positionChat.closest('[data-workforce-custom-chat-wrap]')?.classList.toggle('d-none', !custom);
    }
  }

  async function savePosition(event) {
    event.preventDefault();
    const name = String(elements.positionName?.value || '').trim();
    if (!name) {
      setPositionStatus('Укажите название должности.', 'danger');
      return;
    }
    const notify = Boolean(elements.positionNotify?.checked);
    const channelId = String(elements.positionChannel?.value || '').trim();
    const target = String(elements.positionTarget?.value || 'support_chat').trim();
    const chatId = String(elements.positionChat?.value || '').trim();
    if (notify && !channelId) {
      setPositionStatus('Для оповещения выберите канал/бот.', 'danger');
      return;
    }
    if (notify && target === 'custom_chat' && !chatId) {
      setPositionStatus('Для другого чата укажите chat ID.', 'danger');
      return;
    }
    const payload = {
      name: name,
      description: String(elements.positionDescription?.value || '').trim() || null,
      check_in_required: Boolean(elements.positionCheckIn?.checked),
      notify_on_check_in: notify,
      notification_channel_id: channelId ? Number(channelId) : null,
      notification_target: target || 'support_chat',
      notification_chat_id: target === 'custom_chat' ? (chatId || null) : null,
      active: Boolean(elements.positionActive?.checked),
    };
    const editing = state.editingPositionId != null;
    const url = editing ? '/api/workforce/positions/' + state.editingPositionId : '/api/workforce/positions';
    setPositionStatus('Сохраняем…');
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
    }
  }

  async function loadCatalogs() {
    const data = await requestJson('/api/workforce/state');
    state.positions = Array.isArray(data.positions) ? data.positions : [];
    state.channels = Array.isArray(data.notification_channels) ? data.notification_channels : [];
    renderPositions();
    fillChannelSelect(elements.positionChannel, elements.positionChannel?.value || '', 'Выберите канал/бот');
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

  function renderUserSettings(settings) {
    state.currentUserSettings = settings || {};
    if (elements.userEnabled) elements.userEnabled.checked = settings?.enabled === true;
    fillPositionSelect(elements.userPosition, settings?.position_id);
    if (elements.userTimeZone) elements.userTimeZone.value = settings?.time_zone || 'UTC';
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

  async function saveUserSettings() {
    const userId = state.currentUserId;
    if (!userId) return;
    const positionId = String(elements.userPosition?.value || '').trim();
    const channelId = String(elements.userChannelOverride?.value || '').trim();
    const target = String(elements.userTargetOverride?.value || '').trim();
    const chatId = String(elements.userChatOverride?.value || '').trim();
    if (target === 'custom_chat' && !chatId) {
      setUserStatus('Для персонального custom chat укажите chat ID.', 'danger');
      return;
    }
    const payload = {
      enabled: Boolean(elements.userEnabled?.checked),
      position_id: positionId ? Number(positionId) : null,
      time_zone: String(elements.userTimeZone?.value || 'UTC').trim() || 'UTC',
      check_in_required_override: booleanOrNull(elements.userCheckInOverride?.value || ''),
      notify_on_check_in_override: booleanOrNull(elements.userNotifyOverride?.value || ''),
      notification_channel_id_override: channelId ? Number(channelId) : null,
      notification_target_override: target || null,
      notification_chat_id_override: target === 'custom_chat' ? (chatId || null) : null,
    };
    elements.userSettingsSave && (elements.userSettingsSave.disabled = true);
    setUserStatus('Сохраняем настройки…');
    try {
      const data = await requestJson('/api/workforce/users/' + userId + '/settings', {
        method: 'PUT',
        headers: csrfHeaders(true),
        body: JSON.stringify(payload),
      });
      renderUserSettings(data.settings || payload);
      setUserStatus('Настройки работы сохранены.', 'success');
    } catch (error) {
      setUserStatus(error.message || String(error), 'danger');
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
    elements.positionNotify?.addEventListener('change', syncPositionNotificationControls);
    elements.positionTarget?.addEventListener('change', syncPositionNotificationControls);
    elements.positionsList?.addEventListener('click', function (event) {
      const button = event.target.closest('[data-workforce-position-edit]');
      if (button) editPosition(button.dataset.workforcePositionEdit);
    });
    elements.userTargetOverride?.addEventListener('change', syncUserNotificationControls);
    elements.userSettingsSave?.addEventListener('click', saveUserSettings);
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
