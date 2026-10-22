const { test } = require('node:test');
const assert = require('node:assert/strict');
const { valueLabels, labelPadding, labelLayout, valueLabelsPlugin } = require('../../main/resources/static/js/dashboard-chart-presentation.js');
const ctx = { save() {}, restore() {}, measureText: text => ({ width: text.length * 6 }), fillText() {} };
const chart = horizontal => ({ ctx, options: { indexAxis: horizontal ? 'y' : 'x' }, data: { datasets: [{ data: [27, 19, 1] }] }, getDatasetMeta: () => ({ data: horizontal ? [{ x: 250, base: 20, y: 40 }, { x: 180, base: 20, y: 80 }, { x: 30, base: 20, y: 120 }] : [{ x: 70, y: 30, base: 200 }, { x: 150, y: 70, base: 200 }, { x: 230, y: 190, base: 200 }] }) });
test('Количество и проценты не меняются при переносе подписей', () => {
  assert.deepEqual(valueLabels([27, 19, 1]), ['27 (57.4%)', '19 (40.4%)', '1 (2.1%)']);
  assert.deepEqual(valueLabels([0]), ['0 (0.0%)']);
});
test('Максимальный вертикальный столбец тоже имеет подпись сверху', () => {
  const target = chart(false);
  const labels = labelLayout(target, 'sans-serif');
  labels.forEach((label, i) => assert(label.bottom < target.getDatasetMeta().data[i].y));
  assert(labelPadding(ctx, target.data.datasets[0].data, false, 'sans-serif').top >= 24);
});
test('Горизонтальные подписи расположены после столбца и имеют отдельный резерв', () => {
  const target = chart(true);
  const labels = labelLayout(target, 'sans-serif');
  labels.forEach((label, i) => assert(label.left > target.getDatasetMeta().data[i].x));
  const padding = labelPadding(ctx, target.data.datasets[0].data, true, 'sans-serif');
  assert(padding.right >= Math.max(...labels.map(label => label.width)) + 8);
});
test('Один общий плагин рисует все подписи цветом текста текущей темы', () => {
  const target = chart(true), calls = [];
  target.ctx = { ...ctx, fillText: (...args) => calls.push(args) };
  valueLabelsPlugin(() => ({ text: '#edf3f1' }), 'sans-serif').afterDatasetsDraw(target);
  assert.equal(target.ctx.fillStyle, '#edf3f1');
  assert.equal(calls.length, 3);
  assert.equal(calls[0][1], 258);
});
