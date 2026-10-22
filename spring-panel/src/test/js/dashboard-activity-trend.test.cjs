const { test } = require('node:test');
const assert = require('node:assert/strict');
const { projectActivity, update } = require('../../main/resources/static/js/dashboard-activity-trend.js');

test('Суммы и подписи берутся только из существующей матрицы API', () => {
  const stats = { matrix: [{ day_label: 'Пн', values: [2, 3, 5, 7] }, { day_label: 'Вт', values: [1, 2, 3, 4] }], business_hours_start: 1, business_hours_end: 3 };
  const original = JSON.stringify(stats);
  assert.deepEqual(projectActivity(stats), { labels: ['Пн', 'Вт'], total: [17, 10], working: [8, 5] });
  assert.equal(JSON.stringify(stats), original, 'Не менять данные для heatmap');
});
test('Пустая матрица не превращается в демонстрационные данные', () => {
  assert.deepEqual(projectActivity(null), { labels: [], total: [], working: [] });
  assert.deepEqual(projectActivity({ matrix: [{ day_label: 'Пн' }] }).total, [0]);
});
test('Рабочие часы с переходом через полночь учитываются отдельно', () => {
  const values = Array(24).fill(1);
  assert.deepEqual(projectActivity({ matrix: [{ day_label: 'Пн', values }], business_hours_start: 22, business_hours_end: 6 }).working, [8]);
});
test('Повторный render уничтожает старый график и сохраняет численные значения', () => {
  let destroyed = false;
  const empty = { hidden: true };
  const canvas = { style: {}, parentElement: { querySelector: () => empty } };
  globalThis.Chart = class { constructor(element, config) { this.config = config; assert.equal(element, canvas); } };
  const chart = update({ destroy: () => { destroyed = true; } }, canvas, { matrix: [{ day_label: 'Пн', values: Array(24).fill(1) }] }, {});
  assert(destroyed);
  assert.equal(chart.config.type, 'line');
  assert.deepEqual(chart.config.data.datasets.map(d => d.data), [[24], [9]]);
  assert.equal(chart.config.data.datasets[0].pointRadius, 0);
  assert.equal(canvas.style.display, 'block');
  assert.equal(empty.hidden, true);
  assert.equal(update({ destroy() {} }, canvas, null, {}), null);
  assert.equal(canvas.style.display, 'none');
  assert.equal(empty.hidden, false);
  delete globalThis.Chart;
});
