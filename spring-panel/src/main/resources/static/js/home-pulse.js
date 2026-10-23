(function (root) {
  'use strict';
  function hourlySeries(values) {
    return Array.from({ length: 24 }, (_, i) => Math.max(0, Number(values?.[i]) || 0));
  }
  if (typeof module === 'object' && module.exports) module.exports = { hourlySeries };
  if (!root?.document?.querySelector('[data-home-pulse]')) return;
  const doc = root.document;
  const number = new Intl.NumberFormat('ru-RU');
  let chart, busy = false, lastPayload = null;
  function text(selector, value) { const el = doc.querySelector(selector); if (el) el.textContent = value; }
  function metric(key, value, hint) {
    text(`[data-pulse-value="${key}"]`, value == null ? '—' : number.format(value));
    if (hint) text(`[data-pulse-hint="${key}"]`, hint);
  }
  function color(token) { return getComputedStyle(doc.body).getPropertyValue(token).trim(); }
  function drawHourly(values) {
    chart?.destroy();
    const canvas = doc.getElementById('homeHourlyChart');
    const series = hourlySeries(values);
    const stroke = color('--chart-1');
    chart = new root.Chart(canvas, { type: 'line', data: {
      labels: series.map((_, hour) => `${String(hour).padStart(2, '0')}:00`),
      datasets: [{ label: 'Обращения', data: series, borderColor: stroke, backgroundColor: color('--surface-selected'),
        borderWidth: 2, fill: true, tension: 0.3, pointRadius: series.some(Boolean) ? 2 : 0, pointHoverRadius: 5 }]
    }, options: { responsive: true, maintainAspectRatio: false, animation: false,
      interaction: { intersect: false, mode: 'index' }, plugins: { legend: { display: false } },
      scales: { x: { border: { display: false }, grid: { display: false }, ticks: { color: color('--color-text-muted'), maxTicksLimit: 8, maxRotation: 0 } },
        y: { beginAtZero: true, border: { display: false }, ticks: { precision: 0, color: color('--color-text-muted') }, grid: { color: color('--color-border') } } } } });
    canvas.setAttribute('aria-label', `Обращения сегодня по часам: ${series.map((v, h) => `${h}:00 — ${v}`).join('; ')}`);
  }
  function renderChannels(section) {
    const list = doc.querySelector('[data-home-channels]');
    list.replaceChildren();
    if (!section.available) { text('[data-home-channel-state]', section.reason); return; }
    const rows = Object.entries(section.channels || {}).sort((a, b) => b[1] - a[1]);
    const max = Math.max(1, ...rows.map(row => Number(row[1]) || 0));
    rows.slice(0, 6).forEach(([label, count]) => {
      const row = doc.createElement('div'); row.className = 'home-channel-row';
      const name = doc.createElement('span'); name.textContent = label; name.title = label;
      const track = doc.createElement('span'); track.className = 'home-channel-track';
      const bar = doc.createElement('span'); bar.className = 'home-channel-fill'; bar.style.width = `${(Number(count) || 0) / max * 100}%`;
      track.append(bar);
      const value = doc.createElement('strong'); value.textContent = number.format(count);
      row.append(name, track, value); list.append(row);
    });
    text('[data-home-channel-state]', rows.length ? (rows.length > 6 ? `Показаны 6 из ${rows.length} каналов; полный список — в отчётах.` : 'Количество новых обращений за текущий день.') : 'Сегодня обращений ещё нет.');
  }
  function renderIncidents(section) {
    const list = doc.querySelector('[data-home-incidents]'); list.replaceChildren();
    metric('incidents', section.available ? section.active_count : null, section.available ? 'Активные сейчас' : section.reason);
    if (!section.available) { text('[data-home-incident-state]', section.reason); return; }
    [['Критические', 'critical_active_count', 'danger'], ['Ожидают больше часа', 'aged_open_count', 'warning'], ['Ошибки доставки', 'failed_route_count', 'muted']].forEach(([label, key, tone]) => {
      const row = doc.createElement('div'); row.className = `home-incident-row home-incident-row--${tone}`;
      const name = doc.createElement('span'); name.textContent = label;
      const value = doc.createElement('strong'); value.textContent = number.format(section[key] || 0);
      row.append(name, value); list.append(row);
    });
    text('[data-home-incident-state]', `За последние 24 ч: создано ${number.format(section.created_24h_count || 0)}, решено ${number.format(section.resolved_24h_count || 0)}.`);
  }
  function render(data) {
    const d = data.dialogs, runtime = data.runtime;
    text('[data-home-period]', `${data.date.split('-').reverse().join('.')} · день в часовом поясе ${data.zone}`);
    metric('today', d.available ? d.createdToday : null, d.available ? 'Новые обращения за день' : d.reason);
    metric('active', d.available ? d.active : null, d.available ? 'Все незакрытые обращения' : d.reason);
    doc.getElementById('homeHourlyChart').hidden = !d.available;
    if (d.available) drawHourly(d.hourly); else { chart?.destroy(); chart = null; }
    text('[data-home-load-state]', !d.available ? d.reason : d.createdToday ? 'Считаются новые обращения, а не отдельные сообщения переписки.' : 'Сегодня обращений ещё нет. Нулевая линия — реальные данные.');
    renderChannels(d); renderIncidents(data.incidents);
    metric('bots', runtime.available ? runtime.runningBots : null, !runtime.available ? runtime.reason : runtime.unknownBots ? `Неизвестен статус: ${runtime.unknownBots}. По runtime-сигналам.` : 'По последним runtime-сигналам');
    metric('channels', runtime.available ? runtime.activeChannels : null, runtime.available ? `Включено из ${runtime.totalChannels} настроенных` : runtime.reason);
    text('[data-home-updated]', `Обновлено ${new Date(data.generatedAt).toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' })}`);
  }
  async function refresh() {
    if (busy) return;
    busy = true;
    const button = doc.querySelector('[data-home-refresh]'); button.disabled = true;
    try {
      const response = await fetch('/api/home/pulse', { credentials: 'same-origin', cache: 'no-store', headers: { Accept: 'application/json' } });
      if (!response.ok || response.redirected) throw new Error('Unavailable');
      const data = await response.json(); if (!data.success) throw new Error('Unavailable');
      lastPayload = data; render(data);
    } catch (_error) {
      lastPayload = null; chart?.destroy(); chart = null;
      ['today', 'active', 'incidents', 'bots', 'channels'].forEach(key => metric(key, null, 'Не удалось обновить данные'));
      text('[data-home-updated]', 'Нет свежих данных · повторите обновление');
      text('[data-home-load-state]', 'Нагрузка недоступна. Показатели не заменены нулями.');
      doc.querySelector('[data-home-channels]').replaceChildren();
      doc.querySelector('[data-home-incidents]').replaceChildren();
      text('[data-home-channel-state]', 'Нет свежих данных.'); text('[data-home-incident-state]', 'Нет свежих данных.');
    } finally { busy = false; button.disabled = false; }
  }
  doc.querySelector('[data-home-refresh]').addEventListener('click', refresh);
  doc.addEventListener('visibilitychange', () => { if (!doc.hidden) refresh(); });
  function redrawTheme() { if (lastPayload) render(lastPayload); }
  doc.addEventListener('theme:change', redrawTheme);
  doc.addEventListener('theme:palette-change', redrawTheme);
  // Existing theme controls can change data-theme without a page reload.
  new MutationObserver(redrawTheme).observe(doc.documentElement, { attributes: true, attributeFilter: ['data-theme', 'data-theme-palette'] });
  root.setInterval(() => { if (!doc.hidden) refresh(); }, 60000);
  refresh();
})(typeof window === 'object' ? window : null);
