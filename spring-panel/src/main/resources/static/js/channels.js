(() => {
  const credentialForm = document.getElementById('credentialForm');
  const channelForm = document.getElementById('channelForm');
  const testMessageForm = document.getElementById('testMessageForm');
  const refreshAllBtn = document.getElementById('refreshAllBtn');
  const reloadNotificationsBtn = document.getElementById('reloadNotifications');

  const credentialsTableBody = document.querySelector('#credentialsTable tbody');
  const channelsTableBody = document.querySelector('#channelsTable tbody');
  const notificationsTableBody = document.querySelector('#notificationsTable tbody');

  const credentialSelect = document.getElementById('channelCredential');
  const testChannelSelect = document.getElementById('testChannel');
  const filterChannelSelect = document.getElementById('filterNotificationChannel');

  const credentialCount = document.getElementById('credentialsCount');
  const channelsCount = document.getElementById('channelsCount');
  const notificationsCount = document.getElementById('notificationsCount');
  const testMessageAlert = document.getElementById('testMessageAlert');

  const statusSelect = document.getElementById('filterNotificationStatus');
  const limitInput = document.getElementById('filterNotificationLimit');

  const credentialMap = new Map();
  const channelMap = new Map();

  function showAlert(target, message, type = 'success') {
    if (!target) return;
    target.textContent = message;
    target.className = `alert alert-${type}`;
    target.classList.remove('d-none');
    setTimeout(() => target.classList.add('d-none'), 5000);
  }

  async function fetchJSON(url, options) {
    const response = await fetch(url, {
      headers: { 'Content-Type': 'application/json' },
      credentials: 'same-origin',
      ...options,
    });
    const data = await response.json().catch(() => ({}));
    if (!response.ok || data.success === false) {
      const error = data.error || response.statusText || 'Не удалось выполнить запрос';
      throw new Error(error);
    }
    return data;
  }

  function renderCredentialRow(credential) {
    const tr = document.createElement('tr');
    tr.dataset.id = credential.id;
    tr.innerHTML = `
      <td>
        <div class="fw-semibold">${credential.name}</div>
        <div class="text-muted small">ID: ${credential.id}</div>
      </td>
      <td class="text-capitalize">${credential.platform}</td>
      <td><code>${credential.masked_token || '—'}</code></td>
      <td class="text-center">
        <span class="badge ${credential.is_active ? 'bg-success-subtle text-success' : 'bg-secondary-subtle text-secondary'}">
          ${credential.is_active ? 'Активно' : 'Выключено'}
        </span>
      </td>
      <td class="text-end">
        <button class="btn btn-sm btn-outline-danger" data-action="delete-credential">Удалить</button>
      </td>
    `;
    return tr;
  }

  function renderChannelRow(channel) {
    const tr = document.createElement('tr');
    tr.dataset.id = channel.id;
    const credentialName = (channel.credential && channel.credential.name) || '—';
    const statusBadge = channel.is_active
      ? '<span class="badge bg-success-subtle text-success">Активен</span>'
      : '<span class="badge bg-secondary-subtle text-secondary">Выключен</span>';
    tr.innerHTML = `
      <td>
        <div class="fw-semibold">${channel.name || '—'}</div>
        <div class="text-muted small">ID: ${channel.id}</div>
      </td>
      <td class="text-capitalize">${channel.platform || '—'}</td>
      <td>${credentialName}</td>
      <td><code>${channel.token || '—'}</code></td>
      <td class="text-center">${statusBadge}</td>
      <td class="text-end">
        <div class="btn-group btn-group-sm" role="group">
          <button class="btn btn-outline-primary" data-action="test-channel">Тест</button>
          <button class="btn btn-outline-danger" data-action="delete-channel">Удалить</button>
        </div>
      </td>
    `;
    return tr;
  }

  function renderNotificationRow(item) {
    const tr = document.createElement('tr');
    const statusBadge = {
      pending: 'bg-warning-subtle text-warning',
      in_progress: 'bg-info-subtle text-info',
      done: 'bg-success-subtle text-success',
      failed: 'bg-danger-subtle text-danger',
    }[item.status] || 'bg-secondary-subtle text-secondary';
    const message = (item.payload && item.payload.message) || '—';
    tr.innerHTML = `
      <td>${item.id}</td>
      <td>${item.channel_id ?? '—'}</td>
      <td>${item.recipient || '—'}</td>
      <td><span class="badge ${statusBadge}">${item.status}</span></td>
      <td class="text-break">${message}</td>
      <td>${item.created_at || '—'}</td>
      <td>${item.error || ''}</td>
    `;
    return tr;
  }

  function populateSelect(select, items, { valueKey = 'id', labelKey = 'name', placeholder = 'Выберите' } = {}) {
    if (!select) return;
    const current = select.value;
    select.innerHTML = '';
    const placeholderOption = document.createElement('option');
    placeholderOption.value = '';
    placeholderOption.textContent = placeholder;
    select.appendChild(placeholderOption);
    items.forEach((item) => {
      const option = document.createElement('option');
      option.value = item[valueKey];
      option.textContent = item[labelKey];
      select.appendChild(option);
    });
    if (current) {
      select.value = current;
    }
  }

  async function loadCredentials() {
    try {
      const data = await fetchJSON('/api/bot-credentials');
      const list = data.credentials || [];
      credentialMap.clear();
      credentialsTableBody.innerHTML = '';
      list.forEach((cred) => {
        credentialMap.set(String(cred.id), cred);
        credentialsTableBody.appendChild(renderCredentialRow(cred));
      });
      credentialCount.textContent = list.length;
      populateSelect(credentialSelect, list.map((cred) => ({ id: cred.id, name: `${cred.name} (${cred.platform})` })), { placeholder: 'Выберите учётные данные' });
      populateSelect(testChannelSelect, Array.from(channelMap.values()), { labelKey: 'name', placeholder: 'Выберите канал' });
      populateSelect(filterChannelSelect, Array.from(channelMap.values()), { labelKey: 'name', placeholder: 'Все каналы' });
    } catch (error) {
      console.error('loadCredentials', error);
      alert(`Не удалось загрузить учётные данные: ${error.message}`);
    }
  }

  async function loadChannels() {
    try {
      const data = await fetchJSON('/api/channels');
      const list = (data.channels || []).map((item) => ({
        id: item.id,
        name: item.channel_name || item.name || '—',
        platform: item.platform,
        credential: item.credential,
        token: item.token,
        is_active: Boolean(item.is_active ?? item.is_active),
      }));
      channelsTableBody.innerHTML = '';
      channelMap.clear();
      list.forEach((channel) => {
        channelMap.set(String(channel.id), channel);
        channelsTableBody.appendChild(renderChannelRow(channel));
      });
      channelsCount.textContent = list.length;
      populateSelect(testChannelSelect, list, { valueKey: 'id', labelKey: 'name', placeholder: 'Выберите канал' });
      populateSelect(filterChannelSelect, list, { valueKey: 'id', labelKey: 'name', placeholder: 'Все каналы' });
    } catch (error) {
      console.error('loadChannels', error);
      alert(`Не удалось загрузить каналы: ${error.message}`);
    }
  }

  async function loadNotifications() {
    const channelId = filterChannelSelect?.value || '';
    const status = statusSelect?.value || '';
    const limit = Number(limitInput?.value || 50);
    const params = new URLSearchParams();
    if (channelId) params.set('channel_id', channelId);
    if (status) params.set('status', status);
    params.set('limit', String(Math.min(Math.max(limit, 1), 500)));
    try {
      const data = await fetchJSON(`/api/channel-notifications?${params.toString()}`);
      const list = data.notifications || [];
      notificationsTableBody.innerHTML = '';
      list.forEach((item) => notificationsTableBody.appendChild(renderNotificationRow(item)));
      notificationsCount.textContent = list.length;
    } catch (error) {
      console.error('loadNotifications', error);
      alert(`Не удалось загрузить журнал уведомлений: ${error.message}`);
    }
  }

  async function handleCredentialSubmit(event) {
    event.preventDefault();
    const name = document.getElementById('credentialName').value.trim();
    const platform = document.getElementById('credentialPlatform').value;
    const token = document.getElementById('credentialToken').value.trim();
    const isActive = document.getElementById('credentialActive').checked;
    if (!name || !token) {
      alert('Заполните название и токен.');
      return;
    }
    try {
      await fetchJSON('/api/bot-credentials', {
        method: 'POST',
        body: JSON.stringify({ name, platform, token, is_active: isActive }),
      });
      credentialForm.reset();
      document.getElementById('credentialActive').checked = true;
      await loadCredentials();
    } catch (error) {
      alert(`Не удалось сохранить учётные данные: ${error.message}`);
    }
  }

  async function handleCredentialTableClick(event) {
    const button = event.target.closest('button[data-action="delete-credential"]');
    if (!button) return;
    const row = button.closest('tr');
    const id = row?.dataset.id;
    if (!id) return;
    if (!confirm('Удалить учётные данные?')) return;
    try {
      await fetchJSON(`/api/bot-credentials/${id}`, { method: 'DELETE' });
      await loadCredentials();
      await loadChannels();
    } catch (error) {
      alert(`Не удалось удалить учётные данные: ${error.message}`);
    }
  }

  async function handleChannelSubmit(event) {
    event.preventDefault();
    const name = document.getElementById('channelName').value.trim();
    const description = document.getElementById('channelDescription').value.trim();
    const platform = document.getElementById('channelPlatform').value;
    const credentialId = credentialSelect.value;
    const isActive = document.getElementById('channelActive').checked;
    const maxQuestions = Number(document.getElementById('channelMaxQuestions').value || 0);
    if (!name || !credentialId) {
      alert('Заполните название и выберите учётные данные.');
      return;
    }
    try {
      await fetchJSON('/api/channels', {
        method: 'POST',
        body: JSON.stringify({
          channel_name: name,
          description,
          platform,
          credential_id: credentialId,
          is_active: isActive,
          max_questions: maxQuestions,
        }),
      });
      channelForm.reset();
      document.getElementById('channelActive').checked = true;
      document.getElementById('channelMaxQuestions').value = '0';
      await loadChannels();
      await loadNotifications();
    } catch (error) {
      alert(`Не удалось создать канал: ${error.message}`);
    }
  }

  async function handleChannelTableClick(event) {
    const button = event.target.closest('button[data-action]');
    if (!button) return;
    const row = button.closest('tr');
    const id = row?.dataset.id;
    if (!id) return;
    const action = button.dataset.action;
    if (action === 'delete-channel') {
      if (!confirm('Удалить канал?')) return;
      try {
        await fetchJSON(`/api/channels/${id}`, { method: 'DELETE' });
        await loadChannels();
        await loadNotifications();
      } catch (error) {
        alert(`Не удалось удалить канал: ${error.message}`);
      }
    }
    if (action === 'test-channel') {
      testChannelSelect.value = id;
      testChannelSelect.focus();
    }
  }

  async function handleTestMessageSubmit(event) {
    event.preventDefault();
    const channelId = testChannelSelect.value;
    const recipient = document.getElementById('testRecipient').value.trim();
    const message = document.getElementById('testMessage').value.trim();
    if (!channelId || !recipient || !message) {
      showAlert(testMessageAlert, 'Заполните все поля для тестового сообщения', 'warning');
      return;
    }
    try {
      await fetchJSON(`/api/channels/${channelId}/test-message`, {
        method: 'POST',
        body: JSON.stringify({ recipient, message }),
      });
      showAlert(testMessageAlert, 'Тестовое сообщение поставлено в очередь на отправку');
      testMessageForm.reset();
      await loadNotifications();
    } catch (error) {
      showAlert(testMessageAlert, `Ошибка отправки: ${error.message}`, 'danger');
    }
  }

  function init() {
    const hasChannelsLayout =
      credentialForm &&
      channelForm &&
      testMessageForm &&
      credentialsTableBody &&
      channelsTableBody &&
      notificationsTableBody &&
      credentialSelect &&
      testChannelSelect &&
      filterChannelSelect;

    if (!hasChannelsLayout) {
      return;
    }

    credentialForm?.addEventListener('submit', handleCredentialSubmit);
    credentialsTableBody?.addEventListener('click', handleCredentialTableClick);
    channelForm?.addEventListener('submit', handleChannelSubmit);
    channelsTableBody?.addEventListener('click', handleChannelTableClick);
    testMessageForm?.addEventListener('submit', handleTestMessageSubmit);
    refreshAllBtn?.addEventListener('click', async () => {
      await loadCredentials();
      await loadChannels();
      await loadNotifications();
    });
    reloadNotificationsBtn?.addEventListener('click', async (event) => {
      event.preventDefault();
      await loadNotifications();
    });
    filterChannelSelect?.addEventListener('change', loadNotifications);
    statusSelect?.addEventListener('change', loadNotifications);
    limitInput?.addEventListener('change', loadNotifications);

    loadCredentials().then(loadChannels).then(loadNotifications);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();