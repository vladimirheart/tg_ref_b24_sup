/* Визуальная проекция существующей activity_stats.matrix. Источник и фильтры не меняются. */
(function (root) {
  'use strict';

  function projectActivity(stats) {
    const rows = Array.isArray(stats?.matrix) ? stats.matrix : [];
    const start = Number(stats?.business_hours_start ?? 9);
    const end = Number(stats?.business_hours_end ?? 18);
    const count = raw => Math.max(0, Number(raw) || 0);
    const inWorkingHours = hour => start <= end ? hour >= start && hour < end : hour >= start || hour < end;
    return {
      labels: rows.map(row => String(row.day_label || '—')),
      total: rows.map(row => (Array.isArray(row.values) ? row.values : []).reduce((sum, value) => sum + count(value), 0)),
      working: rows.map(row => (Array.isArray(row.values) ? row.values : []).reduce((sum, value, hour) => sum + (inWorkingHours(hour) ? count(value) : 0), 0))
    };
  }

  function update(previous, canvas, stats, theme) {
    previous?.destroy();
    if (!canvas) return null;
    const data = projectActivity(stats);
    const empty = canvas.parentElement.querySelector('[data-trend-empty]');
    const hasData = data.total.some(value => value > 0);
    canvas.hidden = !hasData;
    canvas.style.display = hasData ? 'block' : 'none';
    if (empty) empty.hidden = hasData;
    if (!hasData) return null;
    return new root.Chart(canvas, {
      type: 'line',
      data: {
        labels: data.labels,
        datasets: [
          { label: 'Все обращения', data: data.total, borderColor: theme.primary, backgroundColor: theme.fill, fill: true },
          { label: 'В рабочее время', data: data.working, borderColor: theme.secondary, backgroundColor: 'transparent', fill: false }
        ].map(dataset => ({ ...dataset, borderWidth: 2, tension: 0.25, pointRadius: 0, pointHoverRadius: 4, pointHitRadius: 14 }))
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        animation: false,
        interaction: { mode: 'index', intersect: false },
        scales: {
          x: { grid: { display: false }, border: { display: false }, ticks: { color: theme.muted, maxRotation: 0 } },
          y: { beginAtZero: true, grid: { color: theme.grid }, border: { display: false }, ticks: { color: theme.muted, precision: 0, maxTicksLimit: 5 } }
        },
        plugins: {
          legend: { position: 'bottom', align: 'start', labels: { color: theme.muted, usePointStyle: true, boxWidth: 7, boxHeight: 7, padding: 18 } },
          tooltip: { backgroundColor: theme.tooltipBackground, titleColor: theme.tooltipText, bodyColor: theme.tooltipText, cornerRadius: 6, padding: 10 }
        }
      }
    });
  }

  const api = { projectActivity, update };
  root.IguanaActivityTrend = api;
  if (typeof module === 'object' && module.exports) module.exports = api;
})(typeof window === 'object' ? window : globalThis);
