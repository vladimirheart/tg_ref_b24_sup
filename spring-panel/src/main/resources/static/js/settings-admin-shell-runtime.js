(function () {
  if (window.SettingsAdminShellRuntime) {
    return;
  }

  function resolveAuthManagementContainer(modalEl) {
    return modalEl instanceof HTMLElement
      ? modalEl.querySelector('[data-auth-management]')
      : null;
  }

  function resolveConfig(options) {
    const config = options && typeof options.config === 'object' ? options.config : null;
    return config && !Array.isArray(config) ? config : {};
  }

  function readConfigObject(config, key) {
    const value = config && typeof config[key] === 'object' ? config[key] : null;
    return value && !Array.isArray(value) ? value : null;
  }

  function readConfigArray(config, key) {
    return Array.isArray(config && config[key]) ? config[key] : null;
  }

  function createEscapeHtml() {
    return function escapeHtml(value) {
      if (value === null || value === undefined) {
        return '';
      }
      return String(value)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
    };
  }

  function resolveStorageInventoryContainer(modalEl) {
    return modalEl instanceof HTMLElement
      ? modalEl.querySelector('[data-storage-inventory]')
      : null;
  }

  function createRuntime(options = {}) {
    const config = resolveConfig(options);
    const escapeHtml = typeof options.escapeHtml === 'function'
      ? options.escapeHtml
      : createEscapeHtml();
    const getCookieValue = typeof options.getCookieValue === 'function'
      ? options.getCookieValue
      : function fallbackGetCookieValue() { return ''; };
    const showPopup = typeof options.showPopup === 'function'
      ? options.showPopup
      : function noopShowPopup() {};
    const showNotification = typeof options.showNotification === 'function'
      ? options.showNotification
      : null;
    const reportingRuntime = window.SettingsRuntimeAccess?.mountRuntime?.('SettingsReportingManagerBindings', {
      reportingConfigInitial: readConfigObject(config, 'reportingConfig') || {},
      managerLocationBindingsInitial: readConfigArray(config, 'managerLocationBindings') || [],
      getLocationsState: typeof options.getLocationsState === 'function'
        ? options.getLocationsState
        : null,
    }) || null;

    function formatBytes(bytes) {
      const normalized = Number(bytes);
      if (!Number.isFinite(normalized) || normalized < 0) {
        return '0 B';
      }
      const units = ['B', 'KB', 'MB', 'GB', 'TB'];
      let value = normalized;
      let unitIndex = 0;
      while (value >= 1024 && unitIndex < units.length - 1) {
        value /= 1024;
        unitIndex += 1;
      }
      return `${value.toFixed(unitIndex === 0 ? 0 : 1)} ${units[unitIndex]}`;
    }

    function formatRelativePath(path, repositoryRoot) {
      if (typeof path !== 'string' || !path.trim()) {
        return '';
      }
      if (typeof repositoryRoot !== 'string' || !repositoryRoot.trim()) {
        return path;
      }
      const normalizedPath = path.replace(/\\/g, '/');
      const normalizedRoot = repositoryRoot.replace(/\\/g, '/').replace(/\/+$/, '');
      if (!normalizedPath.toLowerCase().startsWith(normalizedRoot.toLowerCase() + '/')) {
        return path;
      }
      return normalizedPath.slice(normalizedRoot.length + 1);
    }

    function createStorageInventoryRuntime(container) {
      const endpoint = String(container.dataset.storageInventoryEndpoint || '').trim();
      const feedbackEl = container.querySelector('[data-storage-inventory-feedback]');
      const metaEl = container.querySelector('[data-storage-inventory-meta]');
      const summaryEl = container.querySelector('[data-storage-inventory-summary]');
      const rootsEl = container.querySelector('[data-storage-inventory-roots]');
      const risksEl = container.querySelector('[data-storage-inventory-risks]');
      const outputEl = container.querySelector('[data-storage-inventory-output]');
      const topInputEl = container.querySelector('[data-storage-inventory-top]');
      const runButtonEl = container.querySelector('[data-storage-inventory-run]');
      const lastInfoEl = container.querySelector('[data-storage-inventory-last-info]');
      let running = false;

      function setFeedback(message, type = 'info') {
        if (!(feedbackEl instanceof HTMLElement)) {
          return;
        }
        const normalizedMessage = typeof message === 'string' ? message.trim() : '';
        if (!normalizedMessage) {
          feedbackEl.textContent = '';
          feedbackEl.className = 'alert d-none';
          return;
        }
        const alertClass = type === 'danger'
          ? 'alert-danger'
          : type === 'warning'
            ? 'alert-warning'
            : type === 'success'
              ? 'alert-success'
              : 'alert-info';
        feedbackEl.className = `alert ${alertClass}`;
        feedbackEl.textContent = normalizedMessage;
      }

      function setRunning(nextRunning) {
        running = Boolean(nextRunning);
        if (runButtonEl instanceof HTMLButtonElement) {
          runButtonEl.disabled = running || !endpoint;
          runButtonEl.innerHTML = running
            ? '<span class="spinner-border spinner-border-sm me-2" aria-hidden="true"></span>Собираем inventory…'
            : '<i class="bi bi-play-circle me-1" aria-hidden="true"></i>Запустить inventory';
        }
      }

      function readTop() {
        const value = Number(topInputEl instanceof HTMLInputElement ? topInputEl.value : 5);
        if (!Number.isFinite(value)) {
          return 5;
        }
        return Math.max(1, Math.min(20, Math.round(value)));
      }

      function updateMeta(payload) {
        if (!(metaEl instanceof HTMLElement)) {
          return;
        }
        const durationMs = Number(payload && payload.duration_ms);
        const repositoryRoot = typeof payload?.repository_root === 'string' ? payload.repository_root : '';
        const markdownPath = formatRelativePath(payload?.markdown_report_path || '', repositoryRoot);
        const jsonPath = formatRelativePath(payload?.json_report_path || '', repositoryRoot);
        metaEl.innerHTML = [
          durationMs > 0 ? `Выполнение: <strong>${durationMs} мс</strong>` : '',
          markdownPath ? `Markdown: <code>${escapeHtml(markdownPath)}</code>` : '',
          jsonPath ? `JSON: <code>${escapeHtml(jsonPath)}</code>` : '',
        ].filter(Boolean).join(' · ');
      }

      function renderSummary(payload) {
        const report = payload && typeof payload.report === 'object' ? payload.report : {};
        const storageRoots = Array.isArray(report.storage_roots) ? report.storage_roots : [];
        const databases = Array.isArray(report.databases) ? report.databases : [];
        const risks = Array.isArray(report.risks) ? report.risks : [];
        const references = Array.isArray(report.attachment_references) ? report.attachment_references : [];
        const totalStorageBytes = storageRoots.reduce((sum, item) => sum + (Number(item?.total_bytes) || 0), 0);
        const missingReferenceRows = references.reduce((sum, item) => sum + (Number(item?.resolved_missing) || 0), 0);
        const largestDb = databases.reduce((largest, item) => {
          const currentBytes = Number(item?.bytes) || 0;
          const largestBytes = Number(largest?.bytes) || 0;
          return currentBytes > largestBytes ? item : largest;
        }, null);

        if (summaryEl instanceof HTMLElement) {
          summaryEl.innerHTML = `
            <div class="settings-storage-inventory__stats">
              <div class="settings-storage-inventory__stat">
                <div class="settings-storage-inventory__stat-value">${escapeHtml(formatBytes(totalStorageBytes))}</div>
                <div class="settings-storage-inventory__stat-label">Суммарный размер storage roots</div>
              </div>
              <div class="settings-storage-inventory__stat">
                <div class="settings-storage-inventory__stat-value">${escapeHtml(String(databases.length))}</div>
                <div class="settings-storage-inventory__stat-label">SQLite-файлов в inventory</div>
              </div>
              <div class="settings-storage-inventory__stat">
                <div class="settings-storage-inventory__stat-value">${escapeHtml(String(missingReferenceRows))}</div>
                <div class="settings-storage-inventory__stat-label">Missing attachment references</div>
              </div>
              <div class="settings-storage-inventory__stat">
                <div class="settings-storage-inventory__stat-value">${escapeHtml(String(risks.length))}</div>
                <div class="settings-storage-inventory__stat-label">Сигналов риска</div>
              </div>
            </div>
            <div class="small text-muted mt-2">
              ${largestDb ? `Самая тяжёлая БД: <code>${escapeHtml(String(largestDb.path || ''))}</code> (${escapeHtml(formatBytes(Number(largestDb.bytes) || 0))})` : 'Крупнейшая БД не определена.'}
            </div>
          `;
        }

        if (rootsEl instanceof HTMLElement) {
          if (!storageRoots.length) {
            rootsEl.innerHTML = '<div class="text-muted small">Storage roots пока не обнаружены.</div>';
          } else {
            rootsEl.innerHTML = storageRoots.map((root) => {
              const rootPath = typeof root?.root === 'string' ? root.root : '';
              const exists = Boolean(root?.exists);
              return `
                <div class="settings-storage-inventory__root-item">
                  <div class="fw-semibold"><code>${escapeHtml(formatRelativePath(rootPath, payload?.repository_root || '') || rootPath)}</code></div>
                  <div class="small text-muted">
                    ${exists ? 'Каталог доступен' : 'Каталог не найден'} · ${escapeHtml(String(root?.total_files ?? 0))} files · ${escapeHtml(formatBytes(Number(root?.total_bytes) || 0))}
                  </div>
                </div>
              `;
            }).join('');
          }
        }

        if (risksEl instanceof HTMLElement) {
          if (!risks.length) {
            risksEl.innerHTML = '<li class="text-success">Явных storage-рисков отчёт не выявил.</li>';
          } else {
            risksEl.innerHTML = risks.map((risk) => `<li>${escapeHtml(String(risk))}</li>`).join('');
          }
        }
      }

      function renderReport(payload) {
        if (outputEl instanceof HTMLElement) {
          outputEl.textContent = typeof payload?.markdown_report === 'string' && payload.markdown_report.trim()
            ? payload.markdown_report
            : 'Отчёт пока не запускался.';
        }
        if (lastInfoEl instanceof HTMLElement) {
          const report = payload && typeof payload.report === 'object' ? payload.report : {};
          const generatedAt = typeof report.generated_at_utc === 'string' ? report.generated_at_utc : '';
          lastInfoEl.textContent = generatedAt
            ? `Последний snapshot: ${generatedAt}`
            : 'После запуска здесь появится timestamp последнего inventory.';
        }
      }

      async function runInventory() {
        if (!endpoint || running) {
          return;
        }
        setRunning(true);
        setFeedback('Собираю inventory: storage roots, SQLite и attachment references…', 'info');
        try {
          const response = await fetch(endpoint, {
            method: 'POST',
            credentials: 'same-origin',
            headers: {
              'Content-Type': 'application/json',
              'X-CSRF-TOKEN': getCookieValue('XSRF-TOKEN'),
            },
            body: JSON.stringify({ top: readTop() }),
          });
          const payload = await response.json().catch(() => ({}));
          if (!response.ok || payload.success === false) {
            throw new Error(payload.error || `HTTP ${response.status}`);
          }
          updateMeta(payload);
          renderSummary(payload);
          renderReport(payload);
          setFeedback('Inventory обновлён. Отчёт сохранён в run/storage-inventory и показан ниже.', 'success');
          if (showNotification) {
            showNotification('Storage inventory обновлён.', 'success');
          }
        } catch (error) {
          const message = error instanceof Error ? error.message : 'Не удалось собрать storage inventory.';
          setFeedback(message, 'danger');
          showPopup(message, 'error');
        } finally {
          setRunning(false);
        }
      }

      if (runButtonEl instanceof HTMLButtonElement && !runButtonEl.dataset.storageInventoryBound) {
        runButtonEl.dataset.storageInventoryBound = 'true';
        runButtonEl.addEventListener('click', () => {
          runInventory().catch((error) => {
            console.error('Storage inventory run failed.', error);
          });
        });
      }

      setRunning(false);
      setFeedback('', 'info');
      return {
        runInventory,
        reset() {
          setRunning(false);
          setFeedback('', 'info');
        },
      };
    }

    function mountAuthManagementSettingsModal(modalEl) {
      const container = resolveAuthManagementContainer(modalEl);
      const authManagementApi = window.SettingsRuntimeAccess?.resolveRuntimeApi?.('AuthManagement');
      if (!(container instanceof HTMLElement) || !authManagementApi) {
        console.error('Модуль управления доступом недоступен.');
        return;
      }

      if (!container.__authManager) {
        container.__authManager = window.SettingsRuntimeAccess?.mountRuntime?.('AuthManagement', container) || null;
        return;
      }

      container.__authManager.refresh();
    }

    function resetAuthManagementSettingsModal(modalEl) {
      const container = resolveAuthManagementContainer(modalEl);
      const manager = container && container.__authManager;
      if (!manager) {
        return;
      }

      manager.resetPasswords();
      manager.clearMessage();
    }

    function mountStorageInventorySettingsModal(modalEl) {
      const container = resolveStorageInventoryContainer(modalEl);
      if (!(container instanceof HTMLElement)) {
        return;
      }
      if (!container.__storageInventoryRuntime) {
        container.__storageInventoryRuntime = createStorageInventoryRuntime(container) || null;
      }
    }

    function resetStorageInventorySettingsModal(modalEl) {
      const container = resolveStorageInventoryContainer(modalEl);
      const runtime = container && container.__storageInventoryRuntime;
      if (!runtime || typeof runtime.reset !== 'function') {
        return;
      }
      runtime.reset();
    }

    return {
      reportingRuntime,
      mountAuthManagementSettingsModal,
      resetAuthManagementSettingsModal,
      mountStorageInventorySettingsModal,
      resetStorageInventorySettingsModal,
    };
  }

  function mount(options = {}) {
    if (window.__settingsAdminShellRuntime) {
      return window.__settingsAdminShellRuntime;
    }

    const runtime = createRuntime(options);
    window.SettingsPageCallbackRegistry?.registerMany({
      mountAuthManagementSettingsModal: runtime.mountAuthManagementSettingsModal,
      resetAuthManagementSettingsModal: runtime.resetAuthManagementSettingsModal,
      mountStorageInventorySettingsModal: runtime.mountStorageInventorySettingsModal,
      resetStorageInventorySettingsModal: runtime.resetStorageInventorySettingsModal,
    });
    window.__settingsAdminShellRuntime = runtime;
    return runtime;
  }

  window.SettingsAdminShellRuntime = Object.freeze({
    mount,
  });
}());
