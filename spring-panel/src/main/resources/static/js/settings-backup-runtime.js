(function () {
  const ENDPOINT = '/api/settings/backup';
  const MANUAL_ENDPOINT = `${ENDPOINT}/manual`;
  const COMPONENTS = ['postgres', 'minio', 'shared-config', 'templates', 'static-js', 'static-css'];
  let currentSettings = {};
  let manualPollTimer = null;

  function byId(id) { return document.getElementById(id); }

  function csrfHeaders() {
    const token = document.querySelector('meta[name="_csrf"]')?.getAttribute('content');
    return token ? { 'X-CSRF-TOKEN': token } : {};
  }

  function setFeedback(message, type = 'info') {
    const element = document.querySelector('[data-backup-settings-feedback]');
    if (!(element instanceof HTMLElement)) return;
    element.className = `alert alert-${type}`;
    element.textContent = message;
    element.classList.remove('d-none');
  }

  function clearFeedback() {
    const element = document.querySelector('[data-backup-settings-feedback]');
    if (!(element instanceof HTMLElement)) return;
    element.classList.add('d-none');
    element.textContent = '';
  }

  function readComponents(kind) {
    return COMPONENTS.filter((component) => {
      const input = document.querySelector(`[data-backup-${kind}-component="${component}"]`);
      return input instanceof HTMLInputElement && input.checked;
    });
  }

  function writeComponents(kind, values) {
    const selected = new Set(Array.isArray(values) ? values : []);
    COMPONENTS.forEach((component) => {
      const input = document.querySelector(`[data-backup-${kind}-component="${component}"]`);
      if (input instanceof HTMLInputElement) input.checked = selected.has(component);
    });
  }

  function updateWeekdayVisibility(prefix) {
    const frequency = byId(`${prefix}Frequency`);
    const weekday = byId(`${prefix}WeekdayBlock`);
    if (weekday instanceof HTMLElement && frequency instanceof HTMLSelectElement) {
      weekday.classList.toggle('d-none', frequency.value !== 'weekly');
    }
  }

  function updateDerivedPaths() {
    const input = byId('backupDestinationPath');
    const postgres = document.querySelector('[data-backup-postgres-path]');
    const minio = document.querySelector('[data-backup-minio-path]');
    const files = document.querySelector('[data-backup-files-path]');
    const root = input instanceof HTMLInputElement ? input.value.trim().replace(/[\\/]+$/, '') : '';
    const separator = root.includes('\\') ? '\\' : '/';
    if (postgres instanceof HTMLElement) postgres.textContent = root ? `${root}${separator}postgres` : '—';
    if (minio instanceof HTMLElement) minio.textContent = root ? `${root}${separator}minio` : '—';
    if (files instanceof HTMLElement) files.textContent = root ? `${root}${separator}files` : '—';
  }

  function collectSettingsPayload() {
    return {
      destination_path: byId('backupDestinationPath')?.value?.trim() || '',
      external_failure_domain: Boolean(byId('backupExternalFailureDomain')?.checked),
      postgres_retention_days: Number(byId('backupPostgresRetentionDays')?.value || 30),
      minio_retention_days: Number(byId('backupMinioRetentionDays')?.value || 14),
      manual_mode: byId('backupManualMode')?.value || 'critical',
      custom_components: readComponents('custom'),
      restore_components: readComponents('restore'),
      critical_enabled: Boolean(byId('backupCriticalEnabled')?.checked),
      critical_frequency: byId('backupCriticalFrequency')?.value || 'daily',
      critical_time: byId('backupCriticalTime')?.value || '02:00',
      critical_weekday: byId('backupCriticalWeekday')?.value || 'MON',
      full_enabled: Boolean(byId('backupFullEnabled')?.checked),
      full_frequency: byId('backupFullFrequency')?.value || 'weekly',
      full_time: byId('backupFullTime')?.value || '03:00',
      full_weekday: byId('backupFullWeekday')?.value || 'SUN',
    };
  }

  function validateSettingsPayload(payload) {
    if (!payload.destination_path) throw new Error('Укажите путь к backup-хранилищу.');
    if (!payload.custom_components.length || !payload.restore_components.length) {
      throw new Error('Для custom backup и restore rehearsal выберите хотя бы по одному компоненту.');
    }
  }

  function render(settings) {
    const s = settings && typeof settings === 'object' ? settings : {};
    currentSettings = s;
    const map = {
      backupDestinationPath: s.destination_path || '',
      backupPostgresRetentionDays: s.postgres_retention_days ?? 30,
      backupMinioRetentionDays: s.minio_retention_days ?? 14,
      backupManualMode: s.manual_mode || 'critical',
      backupCriticalFrequency: s.critical_frequency || 'daily',
      backupCriticalTime: s.critical_time || '02:00',
      backupCriticalWeekday: s.critical_weekday || 'MON',
      backupFullFrequency: s.full_frequency || 'weekly',
      backupFullTime: s.full_time || '03:00',
      backupFullWeekday: s.full_weekday || 'SUN',
    };
    Object.entries(map).forEach(([id, value]) => {
      const input = byId(id);
      if (input instanceof HTMLInputElement || input instanceof HTMLSelectElement) input.value = String(value);
    });

    if (byId('backupExternalFailureDomain') instanceof HTMLInputElement) byId('backupExternalFailureDomain').checked = Boolean(s.external_failure_domain);
    if (byId('backupCriticalEnabled') instanceof HTMLInputElement) byId('backupCriticalEnabled').checked = Boolean(s.critical_enabled);
    if (byId('backupFullEnabled') instanceof HTMLInputElement) byId('backupFullEnabled').checked = Boolean(s.full_enabled);
    writeComponents('custom', s.custom_components);
    writeComponents('restore', s.restore_components);

    const status = document.querySelector('[data-backup-settings-status]');
    if (status instanceof HTMLElement) {
      const configured = Boolean(s.configured);
      const offHost = Boolean(s.external_failure_domain);
      status.className = `badge ${configured && offHost ? 'text-bg-success' : configured ? 'text-bg-warning' : 'text-bg-secondary'}`;
      status.textContent = configured && offHost
        ? 'Настроено: внешний failure domain'
        : configured ? 'Путь задан, внешний storage не подтверждён' : 'Не настроено';
    }

    const localBlock = document.querySelector('[data-backup-local-test-block]');
    if (localBlock instanceof HTMLElement) localBlock.classList.toggle('d-none', Boolean(s.external_failure_domain));
    if (Boolean(s.external_failure_domain) && byId('backupManualAllowLocalTest') instanceof HTMLInputElement) {
      byId('backupManualAllowLocalTest').checked = false;
    }

    updateWeekdayVisibility('backupCritical');
    updateWeekdayVisibility('backupFull');
    updateDerivedPaths();
  }

  function formatTimestamp(raw) {
    if (!raw) return '';
    const parsed = new Date(raw);
    return Number.isNaN(parsed.getTime()) ? raw : parsed.toLocaleString();
  }

  function renderManualStatus(manual) {
    const data = manual && typeof manual === 'object' ? manual : {};
    const runner = document.querySelector('[data-backup-runner-status]');
    const operation = document.querySelector('[data-backup-manual-status]');
    const meta = document.querySelector('[data-backup-manual-meta]');
    const runButton = document.querySelector('[data-backup-manual-run]');
    const operationStatus = String(data.operation_status || 'idle');
    const busy = operationStatus === 'queued' || operationStatus === 'running';

    if (runner instanceof HTMLElement) {
      if (operationStatus === 'running') {
        runner.className = 'badge text-bg-primary';
        runner.textContent = 'Host runner выполняет backup';
      } else if (data.runner_active) {
        runner.className = data.schedule_ready ? 'badge text-bg-success' : 'badge text-bg-warning';
        runner.textContent = data.schedule_ready ? 'Host runner online' : 'Host runner online · schedule заблокирован до off-host';
      } else {
        runner.className = 'badge text-bg-secondary';
        runner.textContent = 'Host runner offline';
      }
    }

    if (operation instanceof HTMLElement) {
      const labels = {
        idle: ['Нет активной операции', 'text-bg-secondary'],
        queued: ['В очереди', 'text-bg-warning'],
        running: ['Выполняется', 'text-bg-primary'],
        success: ['Успешно', 'text-bg-success'],
        error: ['Ошибка', 'text-bg-danger'],
      };
      const [label, badgeClass] = labels[operationStatus] || [operationStatus, 'text-bg-secondary'];
      operation.className = `badge ${badgeClass}`;
      operation.textContent = label;
    }

    if (meta instanceof HTMLElement) {
      const parts = [];
      if (data.mode) parts.push(`режим: ${data.mode}`);
      if (data.verify_restore) parts.push('с restore rehearsal');
      if (data.allow_local_test) parts.push('локальный тест, не DR');
      if (data.requested_at) parts.push(`запрошено: ${formatTimestamp(data.requested_at)}`);
      if (data.started_at) parts.push(`старт: ${formatTimestamp(data.started_at)}`);
      if (data.finished_at) parts.push(`финиш: ${formatTimestamp(data.finished_at)}`);
      if (data.message) parts.push(data.message);
      if (operationStatus === 'queued' && !data.runner_active) parts.push('Запрос сохранён. Runner должен стартовать вместе с панелью; перезапустите панель через штатный launcher.'); // panel-lifecycle-queued-v1
      meta.textContent = parts.length ? parts.join(' · ') : 'Ручной backup ещё не запускался.';
    }

    if (runButton instanceof HTMLButtonElement) {
      runButton.disabled = busy || !Boolean(currentSettings.configured);
      runButton.setAttribute('aria-busy', busy ? 'true' : 'false');
    }
  }

  async function loadManualStatus() {
    try {
      const response = await fetch(MANUAL_ENDPOINT, { headers: { Accept: 'application/json' } });
      const data = await response.json();
      if (!response.ok || data.success !== true) throw new Error(data.error || `HTTP ${response.status}`);
      renderManualStatus(data.manual);
      return data.manual;
    } catch (error) {
      const meta = document.querySelector('[data-backup-manual-meta]');
      if (meta instanceof HTMLElement) meta.textContent = `Не удалось получить статус ручного backup: ${error.message}`;
      return null;
    }
  }

  async function loadSettings() {
    clearFeedback();
    try {
      const response = await fetch(ENDPOINT, { headers: { Accept: 'application/json' } });
      const data = await response.json();
      if (!response.ok || data.success !== true) throw new Error(data.error || `HTTP ${response.status}`);
      render(data.settings);
      await loadManualStatus();
    } catch (error) {
      setFeedback(`Не удалось загрузить backup policy: ${error.message}`, 'danger');
    }
  }

  async function persistSettings(showSuccess = true) {
    const payload = collectSettingsPayload();
    validateSettingsPayload(payload);
    const response = await fetch(ENDPOINT, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json', ...csrfHeaders() },
      body: JSON.stringify(payload),
    });
    const data = await response.json();
    if (!response.ok || data.success !== true) throw new Error(data.error || `HTTP ${response.status}`);
    render(data.settings);
    if (showSuccess) setFeedback('Backup policy сохранена. Формат recovery packages: tar.gz.', 'success');
    return data.settings;
  }

  async function saveSettings() {
    clearFeedback();
    const button = document.querySelector('[data-backup-settings-save]');
    if (button instanceof HTMLButtonElement) { button.disabled = true; button.setAttribute('aria-busy', 'true'); }
    try { await persistSettings(true); }
    catch (error) { setFeedback(`Не удалось сохранить backup policy: ${error.message}`, 'danger'); }
    finally { if (button instanceof HTMLButtonElement) { button.disabled = false; button.removeAttribute('aria-busy'); } }
  }

  async function queueManualBackup() {
    clearFeedback();
    const button = document.querySelector('[data-backup-manual-run]');
    if (button instanceof HTMLButtonElement) { button.disabled = true; button.setAttribute('aria-busy', 'true'); }
    try {
      const saved = await persistSettings(false);
      const allowLocalTest = Boolean(byId('backupManualAllowLocalTest')?.checked);
      if (!saved.external_failure_domain && !allowLocalTest) {
        throw new Error('Путь не подтверждён как внешний failure domain. Для локального пути включите «Разрешить локальный тестовый запуск (не DR)».');
      }

      const response = await fetch(MANUAL_ENDPOINT, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'application/json', ...csrfHeaders() },
        body: JSON.stringify({
          mode: byId('backupManualMode')?.value || 'critical',
          verify_restore: Boolean(byId('backupManualVerifyRestore')?.checked),
          allow_local_test: allowLocalTest,
        }),
      });
      const data = await response.json();
      if (!response.ok || data.success !== true) {
        renderManualStatus(data.manual);
        throw new Error(data.error || `HTTP ${response.status}`);
      }
      renderManualStatus(data.manual);
      setFeedback(
        data.manual?.runner_active
          ? 'Ручной backup поставлен в очередь host runner. Статус обновляется автоматически.'
          : 'Backup поставлен в очередь, но host runner offline. Перезапустите панель через штатный launcher.', // panel-lifecycle-feedback-v1
        data.manual?.runner_active ? 'success' : 'warning'
      );
    } catch (error) {
      setFeedback(`Не удалось запустить ручной backup: ${error.message}`, 'danger');
    } finally {
      await loadManualStatus();
      if (button instanceof HTMLButtonElement) button.removeAttribute('aria-busy');
    }
  }

  function startManualPolling() {
    stopManualPolling();
    loadManualStatus();
    manualPollTimer = window.setInterval(loadManualStatus, 2000);
  }

  function stopManualPolling() {
    if (manualPollTimer !== null) { window.clearInterval(manualPollTimer); manualPollTimer = null; }
  }

  document.addEventListener('DOMContentLoaded', () => {
    const modal = byId('backupSettingsModal');
    if (modal instanceof HTMLElement) {
      modal.addEventListener('shown.bs.modal', () => { loadSettings(); startManualPolling(); });
      modal.addEventListener('hidden.bs.modal', stopManualPolling);
    }
    byId('backupDestinationPath')?.addEventListener('input', updateDerivedPaths);
    byId('backupCriticalFrequency')?.addEventListener('change', () => updateWeekdayVisibility('backupCritical'));
    byId('backupFullFrequency')?.addEventListener('change', () => updateWeekdayVisibility('backupFull'));
    document.querySelector('[data-backup-settings-save]')?.addEventListener('click', saveSettings);
    document.querySelector('[data-backup-manual-run]')?.addEventListener('click', queueManualBackup);
    document.querySelector('[data-backup-manual-refresh]')?.addEventListener('click', loadManualStatus);
  });
}());
