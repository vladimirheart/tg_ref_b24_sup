(function () {
  if (window.SettingsReportingManagerBindings) {
    return;
  }

  const DEFAULT_REPORTING_CONFIG = Object.freeze({
    enabled: false,
    period_days: 7,
    frequency: 'weekly',
    send_at: '09:00',
    channel_id: null,
    recipient_user: '',
    include_appeals: true,
    include_tasks: true,
    include_changes: true,
  });

  function normalizeReportingConfig(raw) {
    if (!raw || typeof raw !== 'object') {
      return { ...DEFAULT_REPORTING_CONFIG };
    }
    return {
      enabled: Boolean(raw.enabled),
      period_days: Math.max(1, Number.parseInt(raw.period_days, 10) || DEFAULT_REPORTING_CONFIG.period_days),
      frequency: ['daily', 'weekly', 'monthly'].includes(raw.frequency)
        ? raw.frequency
        : DEFAULT_REPORTING_CONFIG.frequency,
      send_at: typeof raw.send_at === 'string' && raw.send_at
        ? raw.send_at
        : DEFAULT_REPORTING_CONFIG.send_at,
      channel_id: raw.channel_id ?? DEFAULT_REPORTING_CONFIG.channel_id,
      recipient_user: typeof raw.recipient_user === 'string' ? raw.recipient_user : '',
      include_appeals: raw.include_appeals !== false,
      include_tasks: raw.include_tasks !== false,
      include_changes: raw.include_changes !== false,
    };
  }

  function uniqueSorted(values) {
    return Array.from(
      new Set(
        (values || [])
          .map((value) => (typeof value === 'string' ? value.trim() : ''))
          .filter((value) => value),
      ),
    ).sort((left, right) => left.localeCompare(right));
  }

  function escapeHtml(value) {
    if (typeof window.escapeHtml === 'function') {
      return window.escapeHtml(value);
    }
    return String(value ?? '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function getAuthManagerInstance() {
    const usersModalEl = document.getElementById('usersModal');
    const container = usersModalEl ? usersModalEl.querySelector('[data-auth-management]') : null;
    return container && container.__authManager ? container.__authManager : null;
  }

  function extractOrgPeopleOptions(state) {
    const users = Array.isArray(state?.users) ? state.users : [];
    const nodes = Array.isArray(state?.orgStructure?.nodes) ? state.orgStructure.nodes : [];
    const usersById = new Map();

    users.forEach((user) => {
      const numericId = Number(user?.id);
      if (Number.isInteger(numericId)) {
        usersById.set(numericId, user);
      }
    });

    const memberIds = new Set();
    nodes.forEach((node) => {
      (Array.isArray(node?.members) ? node.members : []).forEach((value) => {
        const numericId = Number(value);
        if (Number.isInteger(numericId)) {
          memberIds.add(numericId);
        }
      });
    });

    const options = [];
    memberIds.forEach((memberId) => {
      const user = usersById.get(memberId);
      const name = String(user?.full_name || user?.username || '').trim();
      if (name) {
        options.push(name);
      }
    });

    if (!options.length) {
      users.forEach((user) => {
        const name = String(user?.full_name || user?.username || '').trim();
        if (name) {
          options.push(name);
        }
      });
    }

    return uniqueSorted(options);
  }

  function getLocationBindingOptionsFromTree(locationsState) {
    const tree = locationsState && typeof locationsState === 'object' ? locationsState.tree || {} : {};
    const departments = [];
    const cities = [];

    Object.values(tree).forEach((types) => {
      Object.values(types || {}).forEach((cityMap) => {
        Object.entries(cityMap || {}).forEach(([city, list]) => {
          const cityName = String(city || '').trim();
          if (cityName) {
            cities.push(cityName);
          }
          if (Array.isArray(list)) {
            list.forEach((department) => {
              const value = String(department || '').trim();
              if (value) {
                departments.push(value);
              }
            });
          }
        });
      });
    });

    return uniqueSorted(departments.length ? departments : cities);
  }

  function normalizeManagerBindingRows(rows) {
    if (!Array.isArray(rows)) {
      return [];
    }
    return rows.map((row) => ({
      location: String(row?.location || '').trim(),
      manager: String(row?.manager || '').trim(),
      supervisor: String(row?.supervisor || '').trim(),
      start_date: String(row?.start_date || '').trim(),
      end_date: String(row?.end_date || '').trim(),
    }));
  }

  function createRuntime(options = {}) {
    const reportingState = {
      config: normalizeReportingConfig(options.reportingConfigInitial || {}),
      channelsLoaded: false,
    };

    const managerBindingsState = {
      rows: normalizeManagerBindingRows(options.managerLocationBindingsInitial),
      options: {
        locations: [],
        managers: [],
        supervisors: [],
      },
      orgOptionsLoaded: false,
    };

    const reportingEls = {
      summary: document.querySelector('[data-reporting-summary]'),
      enabled: document.querySelector('[data-reporting-enabled]'),
      period: document.querySelector('[data-reporting-period]'),
      frequency: document.querySelector('[data-reporting-frequency]'),
      time: document.querySelector('[data-reporting-time]'),
      channel: document.querySelector('[data-reporting-channel]'),
      recipientUser: document.querySelector('[data-reporting-recipient-user]'),
      includeAppeals: document.querySelector('[data-reporting-include-appeals]'),
      includeTasks: document.querySelector('[data-reporting-include-tasks]'),
      includeChanges: document.querySelector('[data-reporting-include-changes]'),
      feedback: document.querySelector('[data-reporting-feedback]'),
      saveBtn: document.querySelector('[data-reporting-save]'),
    };

    const managerBindingsEls = {
      summary: document.querySelector('[data-manager-bindings-summary]'),
      body: document.querySelector('[data-manager-bindings-body]'),
      add: document.querySelector('[data-manager-bindings-add]'),
      save: document.querySelector('[data-manager-bindings-save]'),
      feedback: document.querySelector('[data-manager-bindings-feedback]'),
      locationOptions: document.getElementById('managerBindingsLocationOptions'),
      managerOptions: document.getElementById('managerBindingsManagerOptions'),
      supervisorOptions: document.getElementById('managerBindingsSupervisorOptions'),
    };

    function getLocationsState() {
      if (typeof options.getLocationsState === 'function') {
        return options.getLocationsState();
      }
      return null;
    }

    function renderReportingSummary(config) {
      if (!(reportingEls.summary instanceof HTMLElement)) {
        return;
      }
      reportingEls.summary.textContent = !config.enabled
        ? 'Отправка выключена.'
        : `${config.frequency}, период ${config.period_days} д. в ${config.send_at}`;
    }

    function hydrateReportingForm(config) {
      if (reportingEls.enabled instanceof HTMLInputElement) {
        reportingEls.enabled.checked = !!config.enabled;
      }
      if (reportingEls.period instanceof HTMLInputElement) {
        reportingEls.period.value = String(config.period_days);
      }
      if (reportingEls.frequency instanceof HTMLSelectElement) {
        reportingEls.frequency.value = config.frequency;
      }
      if (reportingEls.time instanceof HTMLInputElement) {
        reportingEls.time.value = config.send_at || '09:00';
      }
      if (reportingEls.channel instanceof HTMLSelectElement) {
        reportingEls.channel.value = config.channel_id == null ? '' : String(config.channel_id);
      }
      if (reportingEls.recipientUser instanceof HTMLInputElement) {
        reportingEls.recipientUser.value = config.recipient_user || '';
      }
      if (reportingEls.includeAppeals instanceof HTMLInputElement) {
        reportingEls.includeAppeals.checked = config.include_appeals !== false;
      }
      if (reportingEls.includeTasks instanceof HTMLInputElement) {
        reportingEls.includeTasks.checked = config.include_tasks !== false;
      }
      if (reportingEls.includeChanges instanceof HTMLInputElement) {
        reportingEls.includeChanges.checked = config.include_changes !== false;
      }
    }

    function showReportingFeedback(message, tone = 'info') {
      if (!(reportingEls.feedback instanceof HTMLElement)) {
        return;
      }
      reportingEls.feedback.classList.remove('d-none', 'alert-info', 'alert-success', 'alert-danger');
      reportingEls.feedback.classList.add(`alert-${tone}`);
      reportingEls.feedback.textContent = message;
    }

    async function loadReportingChannels() {
      if (!(reportingEls.channel instanceof HTMLSelectElement) || reportingState.channelsLoaded) {
        return;
      }
      reportingState.channelsLoaded = true;
      try {
        const response = await fetch('/api/channels');
        const data = await response.json();
        const channels = Array.isArray(data.channels) ? data.channels : [];
        const optionsHtml = ['<option value="">Подобрать автоматически</option>'];

        channels.forEach((channel) => {
          const id = channel.id ?? channel.channel_id;
          if (!id) {
            return;
          }
          const name = channel.channel_name || channel.channelName || channel.name || `Канал #${id}`;
          optionsHtml.push(`<option value="${escapeHtml(id)}">${escapeHtml(name)}</option>`);
        });

        reportingEls.channel.innerHTML = optionsHtml.join('');
      } catch (error) {
        showReportingFeedback(`Не удалось загрузить каналы: ${error.message}`, 'danger');
      }
    }

    async function saveReportingConfig() {
      const payload = {
        enabled: Boolean(reportingEls.enabled?.checked),
        period_days: Number.parseInt(reportingEls.period?.value, 10) || 7,
        frequency: reportingEls.frequency?.value || 'weekly',
        send_at: reportingEls.time?.value || '09:00',
        channel_id: reportingEls.channel?.value || null,
        recipient_user: reportingEls.recipientUser?.value || '',
        include_appeals: Boolean(reportingEls.includeAppeals?.checked),
        include_tasks: Boolean(reportingEls.includeTasks?.checked),
        include_changes: Boolean(reportingEls.includeChanges?.checked),
      };

      const response = await fetch('/settings', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ reporting_config: payload }),
      });
      const data = await response.json();

      if (!response.ok || data.success === false) {
        showReportingFeedback(data.error || 'Ошибка сохранения', 'danger');
        return;
      }

      reportingState.config = normalizeReportingConfig(payload);
      renderReportingSummary(reportingState.config);
      showReportingFeedback('Настройки сохранены', 'success');
    }

    async function loadManagerBindingOrgOptions() {
      if (managerBindingsState.orgOptionsLoaded) {
        return;
      }
      managerBindingsState.orgOptionsLoaded = true;

      try {
        const manager = getAuthManagerInstance();
        if (manager && manager.state) {
          managerBindingsState.options.managers = extractOrgPeopleOptions(manager.state);
          managerBindingsState.options.supervisors = managerBindingsState.options.managers.slice();
          return;
        }

        const response = await fetch('/api/auth/state', { credentials: 'same-origin' });
        const data = await response.json();
        managerBindingsState.options.managers = extractOrgPeopleOptions(data || {});
        managerBindingsState.options.supervisors = managerBindingsState.options.managers.slice();
      } catch (error) {
        console.error('Не удалось загрузить орг.структуру для привязок локаций', error);
      }
    }

    function renderManagerBindingsDatalists() {
      managerBindingsState.options.locations = getLocationBindingOptionsFromTree(getLocationsState());

      const locationOptions = managerBindingsState.options.locations
        .map((value) => `<option value="${escapeHtml(value)}"></option>`)
        .join('');
      const peopleOptions = managerBindingsState.options.managers
        .map((value) => `<option value="${escapeHtml(value)}"></option>`)
        .join('');

      if (managerBindingsEls.locationOptions instanceof HTMLElement) {
        managerBindingsEls.locationOptions.innerHTML = locationOptions;
      }
      if (managerBindingsEls.managerOptions instanceof HTMLElement) {
        managerBindingsEls.managerOptions.innerHTML = peopleOptions;
      }
      if (managerBindingsEls.supervisorOptions instanceof HTMLElement) {
        managerBindingsEls.supervisorOptions.innerHTML = peopleOptions;
      }
    }

    function renderManagerBindingsRows() {
      if (!(managerBindingsEls.body instanceof HTMLElement)) {
        return;
      }
      if (!managerBindingsState.rows.length) {
        managerBindingsState.rows = [{
          location: '',
          manager: '',
          supervisor: '',
          start_date: '',
          end_date: '',
        }];
      }

      renderManagerBindingsDatalists();

      managerBindingsEls.body.innerHTML = managerBindingsState.rows
        .map((row, index) => `
          <tr>
            <td>
              <input class="form-control form-control-sm" list="managerBindingsLocationOptions" data-bind-field="location" data-bind-idx="${index}" value="${escapeHtml(row.location || '')}" placeholder="Начните ввод для поиска">
            </td>
            <td>
              <input class="form-control form-control-sm" list="managerBindingsManagerOptions" data-bind-field="manager" data-bind-idx="${index}" value="${escapeHtml(row.manager || '')}" placeholder="Начните ввод для поиска">
            </td>
            <td>
              <input class="form-control form-control-sm" list="managerBindingsSupervisorOptions" data-bind-field="supervisor" data-bind-idx="${index}" value="${escapeHtml(row.supervisor || '')}" placeholder="Начните ввод для поиска">
            </td>
            <td><input type="date" class="form-control form-control-sm" data-bind-field="start_date" data-bind-idx="${index}" value="${escapeHtml(row.start_date || '')}"></td>
            <td><input type="date" class="form-control form-control-sm" data-bind-field="end_date" data-bind-idx="${index}" value="${escapeHtml(row.end_date || '')}"></td>
            <td><button class="btn btn-outline-danger btn-sm" data-bind-remove="${index}" type="button">×</button></td>
          </tr>
        `)
        .join('');

      if (managerBindingsEls.summary instanceof HTMLElement) {
        managerBindingsEls.summary.textContent = `Настроено привязок: ${managerBindingsState.rows.filter((row) => row.location && row.manager).length}`;
      }
    }

    async function saveManagerBindings() {
      const cleanRows = managerBindingsState.rows.filter((row) => row.location && row.manager);
      const response = await fetch('/settings', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ manager_location_bindings: cleanRows }),
      });
      const data = await response.json();

      if (managerBindingsEls.feedback instanceof HTMLElement) {
        managerBindingsEls.feedback.classList.remove('d-none');
        managerBindingsEls.feedback.textContent = !response.ok || data.success === false
          ? (data.error || 'Ошибка сохранения')
          : 'Привязки сохранены';
      }

      renderManagerBindingsRows();
    }

    function prepareReportingSettingsModal() {
      hydrateReportingForm(reportingState.config);
      loadReportingChannels();
    }

    async function prepareManagerBindingsSettingsModal() {
      await loadManagerBindingOrgOptions();
      renderManagerBindingsRows();
    }

    function initReporting() {
      renderReportingSummary(reportingState.config);

      reportingEls.saveBtn?.addEventListener('click', saveReportingConfig);
      managerBindingsEls.add?.addEventListener('click', () => {
        managerBindingsState.rows.push({
          location: '',
          manager: '',
          supervisor: '',
          start_date: '',
          end_date: '',
        });
        renderManagerBindingsRows();
      });
      managerBindingsEls.save?.addEventListener('click', saveManagerBindings);
      managerBindingsEls.body?.addEventListener('input', (event) => {
        const target = event.target;
        if (!(target instanceof HTMLElement) || !(target instanceof HTMLInputElement)) {
          return;
        }
        const index = Number.parseInt(target.dataset.bindIdx, 10);
        const field = target.dataset.bindField;
        if (Number.isNaN(index) || !field || !managerBindingsState.rows[index]) {
          return;
        }
        managerBindingsState.rows[index][field] = target.value;
      });
      managerBindingsEls.body?.addEventListener('click', (event) => {
        const target = event.target;
        if (!(target instanceof Element)) {
          return;
        }
        const button = target.closest('[data-bind-remove]');
        if (!(button instanceof HTMLElement)) {
          return;
        }
        const index = Number.parseInt(button.dataset.bindRemove, 10);
        if (Number.isNaN(index)) {
          return;
        }
        managerBindingsState.rows.splice(index, 1);
        renderManagerBindingsRows();
      });
    }

    return {
      initReporting,
      prepareReportingSettingsModal,
      prepareManagerBindingsSettingsModal,
    };
  }

  function mount(options = {}) {
    if (window.__settingsReportingManagerBindingsRuntime) {
      return window.__settingsReportingManagerBindingsRuntime;
    }

    const runtime = createRuntime(options);
    window.initReporting = runtime.initReporting;
    window.prepareReportingSettingsModal = runtime.prepareReportingSettingsModal;
    window.prepareManagerBindingsSettingsModal = runtime.prepareManagerBindingsSettingsModal;
    window.__settingsReportingManagerBindingsRuntime = runtime;
    return runtime;
  }

  window.SettingsReportingManagerBindings = Object.freeze({
    mount,
    normalizeReportingConfig,
  });
}());
