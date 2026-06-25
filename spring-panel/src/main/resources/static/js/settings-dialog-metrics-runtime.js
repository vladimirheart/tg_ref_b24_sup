(function () {
  if (window.SettingsDialogMetricsRuntime) {
    return;
  }

  function createRuntime(options = {}) {
    const elements = {
      dialogStatusBadgesList: document.getElementById('dialogStatusBadgesList'),
      dialogStatusBadgeAddBtn: document.querySelector('[data-dialog-status-badge-add]'),
    };

    let statusBadgeAddBound = false;

    function getDialogConfig() {
      const value = typeof options.getDialogConfig === 'function' ? options.getDialogConfig() : null;
      return value && typeof value === 'object' ? value : {};
    }

    function getDefaultDialogTimeMetrics() {
      const value = typeof options.getDefaultDialogTimeMetrics === 'function'
        ? options.getDefaultDialogTimeMetrics()
        : null;
      return value && typeof value === 'object'
        ? value
        : {
            good_limit: 30,
            warning_limit: 60,
            colors: {
              good: '#d1f7d1',
              warning: '#fff4d6',
              danger: '#f8d7da',
            },
          };
    }

    function getDefaultSummaryBadges() {
      const value = typeof options.getDefaultSummaryBadges === 'function'
        ? options.getDefaultSummaryBadges()
        : null;
      return value && typeof value === 'object'
        ? value
        : {
            default: {
              background: '#f1f3f5',
              text: '#1f2937',
            },
          };
    }

    function sanitizeHexColor(value, fallback) {
      const raw = typeof value === 'string' ? value.trim() : '';
      if (/^#([0-9a-f]{3}|[0-9a-f]{6})$/i.test(raw)) {
        return raw;
      }
      return fallback;
    }

    function isValidHexColor(value) {
      if (typeof value !== 'string') {
        return false;
      }
      return /^#([0-9a-f]{3}|[0-9a-f]{6})$/i.test(value.trim());
    }

    function normalizeDialogTimeMetrics(raw) {
      const defaults = getDefaultDialogTimeMetrics();
      const base = {
        good_limit: defaults.good_limit,
        warning_limit: defaults.warning_limit,
        colors: {
          ...(defaults.colors || {}),
        },
      };

      if (raw && typeof raw === 'object') {
        const good = Number.parseInt(raw.good_limit, 10);
        const warning = Number.parseInt(raw.warning_limit, 10);
        if (Number.isFinite(good) && good > 0) {
          base.good_limit = good;
        }
        if (Number.isFinite(warning) && warning > 0) {
          base.warning_limit = warning;
        }
        if (raw.colors && typeof raw.colors === 'object') {
          base.colors.good = sanitizeHexColor(raw.colors.good, base.colors.good);
          base.colors.warning = sanitizeHexColor(raw.colors.warning, base.colors.warning);
          base.colors.danger = sanitizeHexColor(raw.colors.danger, base.colors.danger);
        }
      }

      if (!Number.isFinite(base.good_limit) || base.good_limit <= 0) {
        base.good_limit = defaults.good_limit;
      }
      if (!Number.isFinite(base.warning_limit) || base.warning_limit <= base.good_limit) {
        base.warning_limit = Math.max(base.good_limit + 1, defaults.warning_limit);
      }

      return base;
    }

    function normalizeSummaryBadgeStyle(value) {
      if (!value) {
        return {};
      }
      if (typeof value === 'string') {
        return { background: sanitizeHexColor(value, ''), text: '' };
      }
      if (typeof value === 'object') {
        return {
          background: sanitizeHexColor(value.background || value.bg || value.fill || '', ''),
          text: sanitizeHexColor(value.text || value.color || value.foreground || '', ''),
        };
      }
      return {};
    }

    function normalizeSummaryBadgeSettings(raw) {
      const defaults = getDefaultSummaryBadges();
      const fallback = normalizeSummaryBadgeStyle(raw?.default) || {};
      const base = {
        default: {
          background: fallback.background || defaults.default.background,
          text: fallback.text || defaults.default.text,
        },
        status: {},
      };
      const status = raw?.status && typeof raw.status === 'object' ? raw.status : {};
      Object.entries(status).forEach(([key, value]) => {
        if (!key) {
          return;
        }
        base.status[key.toLowerCase()] = normalizeSummaryBadgeStyle(value);
      });
      return base;
    }

    function getTimeMetricsInputs() {
      return {
        good: document.getElementById('timeMetricGoodLimit'),
        warning: document.getElementById('timeMetricWarningLimit'),
        goodColor: document.getElementById('timeMetricColorGood'),
        warningColor: document.getElementById('timeMetricColorWarning'),
        dangerColor: document.getElementById('timeMetricColorDanger'),
        preview: document.getElementById('timeMetricPreview'),
      };
    }

    function updateTimeMetricsPreview(metrics) {
      const inputs = getTimeMetricsInputs();
      if (!inputs.preview) {
        return;
      }
      const normalized = normalizeDialogTimeMetrics(metrics);
      const segments = [
        { label: `Менее ${normalized.good_limit} мин`, color: normalized.colors.good },
        { label: `От ${normalized.good_limit} до ${normalized.warning_limit} мин`, color: normalized.colors.warning },
        { label: `От ${normalized.warning_limit} мин и более`, color: normalized.colors.danger },
      ];
      inputs.preview.innerHTML = segments
        .map(
          (segment) => `
            <div class="dialog-time-metrics-preview__chip" style="background-color: ${segment.color};">
              ${segment.label}
            </div>
          `,
        )
        .join('');
    }

    function initTimeMetricsControls() {
      const inputs = getTimeMetricsInputs();
      if (!inputs.good || !inputs.warning || !inputs.goodColor || !inputs.warningColor || !inputs.dangerColor) {
        return;
      }
      const metrics = normalizeDialogTimeMetrics(getDialogConfig().time_metrics);
      inputs.good.value = metrics.good_limit;
      inputs.warning.value = metrics.warning_limit;
      inputs.goodColor.value = metrics.colors.good;
      inputs.warningColor.value = metrics.colors.warning;
      inputs.dangerColor.value = metrics.colors.danger;
      updateTimeMetricsPreview(metrics);

      [inputs.good, inputs.warning, inputs.goodColor, inputs.warningColor, inputs.dangerColor].forEach((input) => {
        if (!input) {
          return;
        }
        input.addEventListener('input', () => {
          if (input.classList.contains('is-invalid')) {
            input.classList.remove('is-invalid');
          }
          updateTimeMetricsPreview({
            good_limit: Number(inputs.good.value),
            warning_limit: Number(inputs.warning.value),
            colors: {
              good: inputs.goodColor.value,
              warning: inputs.warningColor.value,
              danger: inputs.dangerColor.value,
            },
          });
        });
      });
    }

    function collectTimeMetricsConfig() {
      const inputs = getTimeMetricsInputs();
      const defaults = getDefaultDialogTimeMetrics();
      const errors = [];

      let goodLimit = Math.round(Number(inputs.good?.value));
      if (!Number.isFinite(goodLimit) || goodLimit <= 0) {
        errors.push('Укажите положительное значение зелёной зоны (минуты).');
        if (inputs.good) {
          inputs.good.classList.add('is-invalid');
        }
        goodLimit = defaults.good_limit;
      } else if (inputs.good) {
        inputs.good.classList.remove('is-invalid');
        inputs.good.value = goodLimit;
      }

      let warningLimit = Math.round(Number(inputs.warning?.value));
      let warningValid = true;
      if (!Number.isFinite(warningLimit) || warningLimit <= 0) {
        errors.push('Укажите корректное значение жёлтой зоны (минуты).');
        warningValid = false;
      } else if (warningLimit <= goodLimit) {
        errors.push('Жёлтая зона должна быть больше зелёной.');
        warningValid = false;
      }

      if (!warningValid) {
        warningLimit = Math.max(goodLimit + 1, defaults.warning_limit);
        if (inputs.warning) {
          inputs.warning.classList.add('is-invalid');
        }
      } else if (inputs.warning) {
        inputs.warning.classList.remove('is-invalid');
        inputs.warning.value = warningLimit;
      }

      const config = normalizeDialogTimeMetrics({
        good_limit: goodLimit,
        warning_limit: warningLimit,
        colors: {
          good: inputs.goodColor?.value,
          warning: inputs.warningColor?.value,
          danger: inputs.dangerColor?.value,
        },
      });

      updateTimeMetricsPreview(config);
      return { config, errors };
    }

    function updateDialogStatusBadgePreview(row) {
      if (!row) {
        return;
      }
      const nameInput = row.querySelector('[data-dialog-status-label]');
      const bgInput = row.querySelector('[data-dialog-status-bg]');
      const textInput = row.querySelector('[data-dialog-status-text]');
      const preview = row.querySelector('[data-dialog-status-preview]');
      if (!preview) {
        return;
      }
      const normalized = normalizeSummaryBadgeSettings(getDialogConfig().summary_badges);
      const base = normalized.default || getDefaultSummaryBadges().default;
      const label = nameInput?.value?.trim() || 'Статус';
      const background = sanitizeHexColor(bgInput?.value, base.background || '');
      const text = sanitizeHexColor(textInput?.value, base.text || '');
      preview.textContent = label;
      preview.style.backgroundColor = background;
      preview.style.color = text;
    }

    function buildDialogStatusBadgeRow(entry) {
      const row = document.createElement('div');
      row.className = 'dialog-status-badge-row';
      row.dataset.dialogStatusBadge = 'true';
      row.innerHTML = `
        <div class="row g-2 align-items-end">
          <div class="col-12 col-lg-4">
            <label class="form-label mb-1">Статус</label>
            <input type="text" class="form-control form-control-sm" data-dialog-status-label placeholder="Например, Новый">
          </div>
          <div class="col-6 col-lg-3">
            <label class="form-label mb-1">Фон</label>
            <input type="text" class="form-control form-control-sm" data-dialog-status-bg placeholder="#e7f1ff">
            <div class="invalid-feedback">Укажите корректный HEX-цвет.</div>
          </div>
          <div class="col-6 col-lg-3">
            <label class="form-label mb-1">Текст</label>
            <input type="text" class="form-control form-control-sm" data-dialog-status-text placeholder="#0d6efd">
            <div class="invalid-feedback">Укажите корректный HEX-цвет.</div>
          </div>
          <div class="col-8 col-lg-2">
            <label class="form-label mb-1">Превью</label>
            <div class="dialog-status-badge-preview" data-dialog-status-preview>Статус</div>
          </div>
          <div class="col-4 col-lg-12 text-end text-lg-start">
            <button class="btn btn-sm btn-outline-danger" type="button" data-dialog-status-remove>Удалить</button>
          </div>
        </div>
      `;

      if (entry) {
        const nameInput = row.querySelector('[data-dialog-status-label]');
        const bgInput = row.querySelector('[data-dialog-status-bg]');
        const textInput = row.querySelector('[data-dialog-status-text]');
        if (nameInput) nameInput.value = entry.label || '';
        if (bgInput) bgInput.value = entry.background || '';
        if (textInput) textInput.value = entry.text || '';
      }

      row.querySelectorAll('input').forEach((input) => {
        input.addEventListener('input', () => {
          if (input.classList.contains('is-invalid')) {
            input.classList.remove('is-invalid');
          }
          updateDialogStatusBadgePreview(row);
        });
      });

      const removeBtn = row.querySelector('[data-dialog-status-remove]');
      if (removeBtn) {
        removeBtn.addEventListener('click', () => {
          row.remove();
        });
      }

      updateDialogStatusBadgePreview(row);
      return row;
    }

    function addDialogStatusBadgeRow(entry) {
      if (!elements.dialogStatusBadgesList) {
        return;
      }
      elements.dialogStatusBadgesList.appendChild(buildDialogStatusBadgeRow(entry));
    }

    function initDialogStatusBadges() {
      if (!elements.dialogStatusBadgesList) {
        return;
      }
      elements.dialogStatusBadgesList.innerHTML = '';
      const normalized = normalizeSummaryBadgeSettings(getDialogConfig().summary_badges);
      const entries = Object.entries(normalized.status || {});
      if (entries.length) {
        entries.forEach(([label, style]) => {
          addDialogStatusBadgeRow({
            label,
            background: style.background || '',
            text: style.text || '',
          });
        });
      } else {
        addDialogStatusBadgeRow();
      }

      if (elements.dialogStatusBadgeAddBtn && !statusBadgeAddBound) {
        elements.dialogStatusBadgeAddBtn.addEventListener('click', () => addDialogStatusBadgeRow());
        statusBadgeAddBound = true;
      }
    }

    function collectDialogSummaryBadges() {
      const errors = [];
      const status = {};
      if (!elements.dialogStatusBadgesList) {
        return { status, errors };
      }
      elements.dialogStatusBadgesList.querySelectorAll('[data-dialog-status-badge]').forEach((row) => {
        const labelInput = row.querySelector('[data-dialog-status-label]');
        const bgInput = row.querySelector('[data-dialog-status-bg]');
        const textInput = row.querySelector('[data-dialog-status-text]');
        const label = labelInput?.value?.trim() || '';
        const bgRaw = bgInput?.value?.trim() || '';
        const textRaw = textInput?.value?.trim() || '';
        if (!label) {
          return;
        }
        let hasError = false;
        if (bgRaw && !isValidHexColor(bgRaw)) {
          bgInput?.classList.add('is-invalid');
          errors.push(`Некорректный цвет фона для статуса «${label}».`);
          hasError = true;
        }
        if (textRaw && !isValidHexColor(textRaw)) {
          textInput?.classList.add('is-invalid');
          errors.push(`Некорректный цвет текста для статуса «${label}».`);
          hasError = true;
        }
        if (hasError) {
          return;
        }
        status[label.toLowerCase()] = {
          background: bgRaw,
          text: textRaw,
        };
      });
      return { status, errors };
    }

    function initialize() {
      initTimeMetricsControls();
      initDialogStatusBadges();
    }

    return {
      collectDialogSummaryBadges,
      collectTimeMetricsConfig,
      initialize,
    };
  }

  const api = {
    mount(options = {}) {
      if (window.__settingsDialogMetricsRuntime) {
        return window.__settingsDialogMetricsRuntime;
      }
      const runtime = createRuntime(options);
      runtime.initialize();
      window.__settingsDialogMetricsRuntime = runtime;
      return runtime;
    },
  };

  window.SettingsDialogMetricsRuntime = Object.freeze(api);
}());
