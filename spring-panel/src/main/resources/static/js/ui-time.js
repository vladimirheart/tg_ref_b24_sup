(function () {
  'use strict';

  const root = typeof window !== 'undefined' ? window : null;
  if (!root || root.iguanaUiTime) return;

  const DEFAULT_TIME_ZONE = 'UTC';
  const prefApi = root.iguanaUiPreferences || null;
  const nativeDateLocale = Object.freeze({
    toLocaleString: Date.prototype.toLocaleString,
    toLocaleDateString: Date.prototype.toLocaleDateString,
    toLocaleTimeString: Date.prototype.toLocaleTimeString,
  });

  function normalizeTimeZone(value) {
    const candidate = String(value || '').trim() || DEFAULT_TIME_ZONE;
    try {
      new Intl.DateTimeFormat('en-US', { timeZone: candidate }).format(new Date(0));
      return candidate;
    } catch (_error) {
      return DEFAULT_TIME_ZONE;
    }
  }

  function getTimeZone() {
    const stored = prefApi && typeof prefApi.get === 'function'
      ? prefApi.get('displayTimeZone')
      : null;
    return normalizeTimeZone(stored);
  }

  function withTimeZoneOptions(options) {
    if (options && Object.prototype.hasOwnProperty.call(options, 'timeZone')) return options;
    return { ...(options || {}), timeZone: getTimeZone() };
  }

  function patchDateLocaleMethod(name, nativeMethod) {
    if (typeof nativeMethod !== 'function') return;
    Object.defineProperty(Date.prototype, name, {
      configurable: true,
      writable: true,
      value: function (locales, options) {
        return nativeMethod.call(this, locales, withTimeZoneOptions(options));
      },
    });
  }

  patchDateLocaleMethod('toLocaleString', nativeDateLocale.toLocaleString);
  patchDateLocaleMethod('toLocaleDateString', nativeDateLocale.toLocaleDateString);
  patchDateLocaleMethod('toLocaleTimeString', nativeDateLocale.toLocaleTimeString);

  function parseDateValue(value) {
    if (value instanceof Date) return Number.isNaN(value.getTime()) ? null : value;
    if (typeof value === 'number' && Number.isFinite(value)) {
      const epochMs = value < 1000000000000 ? value * 1000 : value;
      const parsed = new Date(epochMs);
      return Number.isNaN(parsed.getTime()) ? null : parsed;
    }
    const raw = String(value ?? '').trim();
    if (!raw) return null;
    if (/^\d{10,13}$/.test(raw)) return parseDateValue(Number(raw));
    let candidate = raw.replace(' ', 'T');
    if (/^\d{4}-\d{2}-\d{2}$/.test(candidate)) candidate += 'T00:00:00Z';
    else if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d{1,9})?)?$/.test(candidate)) candidate += 'Z';
    const parsed = new Date(candidate);
    return Number.isNaN(parsed.getTime()) ? null : parsed;
  }

  function format(value, kind, options = {}) {
    const date = parseDateValue(value);
    if (!date) return options.fallback || '—';
    const timeZone = normalizeTimeZone(options.timeZone || getTimeZone());
    const locale = options.locale || 'ru-RU';
    let formatOptions;
    if (kind === 'date') {
      formatOptions = { timeZone, day: '2-digit', month: '2-digit', year: 'numeric' };
    } else if (kind === 'time') {
      formatOptions = { timeZone, hour: '2-digit', minute: '2-digit', hourCycle: 'h23' };
      if (options.includeSeconds) formatOptions.second = '2-digit';
    } else {
      formatOptions = { timeZone, day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' };
      if (options.includeSeconds) formatOptions.second = '2-digit';
    }
    return new Intl.DateTimeFormat(locale, formatOptions).format(date).replace(',', '');
  }

  function formatDate(value, options = {}) {
    return format(value, 'date', options);
  }

  function formatTime(value, options = {}) {
    return format(value, 'time', options);
  }

  function formatDateTime(value, options = {}) {
    return format(value, 'datetime', options);
  }

  function supportedTimeZones() {
    const result = [DEFAULT_TIME_ZONE];
    if (typeof Intl.supportedValuesOf === 'function') {
      try {
        Intl.supportedValuesOf('timeZone').forEach((zone) => {
          if (!result.includes(zone)) result.push(zone);
        });
      } catch (_error) {
        // fall through to compact fallback list
      }
    }
    if (result.length === 1) {
      ['Europe/Moscow', 'Europe/Tallinn', 'Europe/Helsinki', 'Europe/Riga', 'Europe/Vilnius', 'Europe/Berlin', 'Asia/Yekaterinburg', 'Asia/Novosibirsk', 'Asia/Vladivostok'].forEach((zone) => {
        if (!result.includes(zone)) result.push(zone);
      });
    }
    const current = getTimeZone();
    if (!result.includes(current)) result.splice(1, 0, current);
    return result;
  }

  function installTimeZoneControl() {
    const menu = document.getElementById('sidebarActionMenu');
    if (!menu || menu.querySelector('[data-ui-time-zone-control]')) return;

    const control = document.createElement('div');
    control.className = 'px-2 py-2 d-flex flex-column gap-1';
    control.dataset.uiTimeZoneControl = 'true';

    const label = document.createElement('label');
    label.className = 'small text-muted';
    label.textContent = 'Часовой пояс';

    const select = document.createElement('select');
    select.className = 'form-select form-select-sm';
    select.setAttribute('aria-label', 'Часовой пояс интерфейса');
    supportedTimeZones().forEach((zone) => {
      const option = document.createElement('option');
      option.value = zone;
      option.textContent = zone === DEFAULT_TIME_ZONE ? 'UTC (по умолчанию)' : zone;
      select.appendChild(option);
    });
    select.value = getTimeZone();

    select.addEventListener('change', async () => {
      const next = normalizeTimeZone(select.value);
      select.disabled = true;
      try {
        if (prefApi && typeof prefApi.set === 'function') {
          await prefApi.set('displayTimeZone', next, 'time-zone-control');
          if (typeof prefApi.flush === 'function') await prefApi.flush();
        } else {
          try { root.localStorage?.setItem('iguana:display-time-zone', next); } catch (_error) { }
        }
      } finally {
        root.location.reload();
      }

      // 01-278 R32: refresh the current page after the display timezone is persisted
      document.documentElement.dataset.displayTimeZone = next;
      window.location.reload();
    });

    control.appendChild(label);
    control.appendChild(select);
    menu.appendChild(control);
  }

  root.iguanaUiTime = Object.freeze({
    defaultTimeZone: DEFAULT_TIME_ZONE,
    normalizeTimeZone,
    getTimeZone,
    parseDateValue,
    formatDate,
    formatTime,
    formatDateTime,
    supportedTimeZones,
  });

  document.documentElement.dataset.displayTimeZone = getTimeZone();
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', installTimeZoneControl, { once: true });
  else installTimeZoneControl();
})();
