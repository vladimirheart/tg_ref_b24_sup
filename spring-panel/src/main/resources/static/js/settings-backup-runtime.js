(function () {
  const ENDPOINT = '/api/settings/backup';
  const COMPONENTS = ['postgres', 'minio', 'shared-config', 'templates', 'static-js', 'static-css'];

  function byId(id) {
    return document.getElementById(id);
  }

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
      if (input instanceof HTMLInputElement) {
        input.checked = selected.has(component);
      }
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

  function render(settings) {
    const s = settings && typeof settings === 'object' ? settings : {};
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
      if (input instanceof HTMLInputElement || input instanceof HTMLSelectElement) {
        input.value = String(value);
      }
    });

    const external = byId('backupExternalFailureDomain');
    const criticalEnabled = byId('backupCriticalEnabled');
    const fullEnabled = byId('backupFullEnabled');
    if (external instanceof HTMLInputElement) external.checked = Boolean(s.external_failure_domain);
    if (criticalEnabled instanceof HTMLInputElement) criticalEnabled.checked = Boolean(s.critical_enabled);
    if (fullEnabled instanceof HTMLInputElement) fullEnabled.checked = Boolean(s.full_enabled);

    writeComponents('custom', s.custom_components);
    writeComponents('restore', s.restore_components);

    const status = document.querySelector('[data-backup-settings-status]');
    if (status instanceof HTMLElement) {
      const configured = Boolean(s.configured);
      const offHost = Boolean(s.external_failure_domain);
      status.className = `badge ${configured && offHost ? 'text-bg-success' : configured ? 'text-bg-warning' : 'text-bg-secondary'}`;
      status.textContent = configured && offHost
        ? 'Настроено: внешний failure domain'
        : configured
          ? 'Путь задан, внешний storage не подтверждён'
          : 'Не настроено';
    }

    updateWeekdayVisibility('backupCritical');
    updateWeekdayVisibility('backupFull');
    updateDerivedPaths();
  }

  async function loadSettings() {
    clearFeedback();
    try {
      const response = await fetch(ENDPOINT, { headers: { Accept: 'application/json' } });
      const data = await response.json();
      if (!response.ok || data.success !== true) {
        throw new Error(data.error || `HTTP ${response.status}`);
      }
      render(data.settings);
    } catch (error) {
      setFeedback(`Не удалось загрузить backup policy: ${error.message}`, 'danger');
    }
  }

  async function saveSettings() {
    clearFeedback();
    const destination = byId('backupDestinationPath');
    const external = byId('backupExternalFailureDomain');
    const criticalEnabled = byId('backupCriticalEnabled');
    const fullEnabled = byId('backupFullEnabled');

    const payload = {
      destination_path: destination instanceof HTMLInputElement ? destination.value.trim() : '',
      external_failure_domain: external instanceof HTMLInputElement && external.checked,
      postgres_retention_days: Number(byId('backupPostgresRetentionDays')?.value || 30),
      minio_retention_days: Number(byId('backupMinioRetentionDays')?.value || 14),
      manual_mode: byId('backupManualMode')?.value || 'critical',
      custom_components: readComponents('custom'),
      restore_components: readComponents('restore'),
      critical_enabled: criticalEnabled instanceof HTMLInputElement && criticalEnabled.checked,
      critical_frequency: byId('backupCriticalFrequency')?.value || 'daily',
      critical_time: byId('backupCriticalTime')?.value || '02:00',
      critical_weekday: byId('backupCriticalWeekday')?.value || 'MON',
      full_enabled: fullEnabled instanceof HTMLInputElement && fullEnabled.checked,
      full_frequency: byId('backupFullFrequency')?.value || 'weekly',
      full_time: byId('backupFullTime')?.value || '03:00',
      full_weekday: byId('backupFullWeekday')?.value || 'SUN',
    };

    if (!payload.destination_path) {
      setFeedback('Укажите путь к backup-хранилищу.', 'warning');
      return;
    }
    if (!payload.custom_components.length || !payload.restore_components.length) {
      setFeedback('Для custom backup и restore rehearsal выберите хотя бы по одному компоненту.', 'warning');
      return;
    }

    const button = document.querySelector('[data-backup-settings-save]');
    if (button instanceof HTMLButtonElement) {
      button.disabled = true;
      button.setAttribute('aria-busy', 'true');
    }

    try {
      const response = await fetch(ENDPOINT, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'application/json',
          ...csrfHeaders(),
        },
        body: JSON.stringify(payload),
      });
      const data = await response.json();
      if (!response.ok || data.success !== true) {
        throw new Error(data.error || `HTTP ${response.status}`);
      }
      render(data.settings);
      setFeedback(
        'Backup policy сохранена. Credentials SMB/NFS здесь не хранятся. Формат recovery packages зафиксирован как tar.gz.',
        'success'
      );
    } catch (error) {
      setFeedback(`Не удалось сохранить backup policy: ${error.message}`, 'danger');
    } finally {
      if (button instanceof HTMLButtonElement) {
        button.disabled = false;
        button.removeAttribute('aria-busy');
      }
    }
  }

  document.addEventListener('DOMContentLoaded', () => {
    const modal = byId('backupSettingsModal');
    if (modal instanceof HTMLElement) {
      modal.addEventListener('shown.bs.modal', loadSettings);
    }
    byId('backupDestinationPath')?.addEventListener('input', updateDerivedPaths);
    byId('backupCriticalFrequency')?.addEventListener('change', () => updateWeekdayVisibility('backupCritical'));
    byId('backupFullFrequency')?.addEventListener('change', () => updateWeekdayVisibility('backupFull'));
    document.querySelector('[data-backup-settings-save]')?.addEventListener('click', saveSettings);
  });
}());
