/* Общая геометрия подписей диаграмм: данные и формулы серверной аналитики не меняются. */
(function (root) {
  'use strict';
  const fontSize = 12;
  const gap = 8;
  function valueLabels(data) {
    const total = data.reduce((sum, value) => sum + (Number(value) || 0), 0);
    return data.map(value => `${value} (${total > 0 ? ((Number(value) || 0) / total * 100).toFixed(1) : '0.0'}%)`);
  }
  function labelPadding(ctx, data, horizontal, family) {
    ctx.save(); ctx.font = `600 ${fontSize}px ${family}`;
    const width = Math.max(0, ...valueLabels(data).map(text => ctx.measureText(text).width));
    ctx.restore();
    return horizontal ? { top: 8, right: Math.ceil(width) + gap + 6, bottom: 8, left: 0 }
      : { top: fontSize + gap + 10, right: 12, bottom: 8, left: 8 };
  }
  function labelLayout(chart, family) {
    const horizontal = chart.options.indexAxis === 'y';
    const ctx = chart.ctx;
    ctx.save(); ctx.font = `600 ${fontSize}px ${family}`;
    const labels = valueLabels(chart.data.datasets[0]?.data || []);
    const result = chart.getDatasetMeta(0).data.map((bar, index) => {
      const text = labels[index];
      const width = ctx.measureText(text).width;
      const x = horizontal ? Math.max(bar.x, bar.base) + gap : bar.x;
      const y = horizontal ? bar.y : Math.min(bar.y, bar.base) - gap;
      return { text, x, y, width, height: fontSize, left: horizontal ? x : x - width / 2,
        right: horizontal ? x + width : x + width / 2, top: horizontal ? y - fontSize / 2 : y - fontSize,
        bottom: horizontal ? y + fontSize / 2 : y, horizontal };
    });
    ctx.restore();
    return result;
  }
  function valueLabelsPlugin(getTheme, family) {
    return {
      id: 'NeutralOpsValueLabels',
      afterDatasetsDraw(chart) {
        const ctx = chart.ctx;
        const layout = labelLayout(chart, family);
        ctx.save(); ctx.font = `600 ${fontSize}px ${family}`; ctx.fillStyle = getTheme().text;
        for (const label of layout) {
          ctx.textBaseline = label.horizontal ? 'middle' : 'bottom';
          ctx.textAlign = label.horizontal ? 'left' : 'center';
          ctx.fillText(label.text, label.x, label.y);
        }
        ctx.restore();
      }
    };
  }
  function showEmpty(canvas, title, description) {
    canvas.style.display = 'none';
    let state = canvas.parentElement.querySelector('.no-data-message');
    if (!state) { state = document.createElement('div'); state.className = 'chart-empty-state no-data-message'; state.setAttribute('role', 'status'); canvas.parentElement.append(state); }
    state.replaceChildren();
    const heading = document.createElement('strong'); heading.textContent = title;
    const copy = document.createElement('p'); copy.textContent = description;
    state.append(heading, copy);
  }
  const api = { valueLabels, labelPadding, labelLayout, valueLabelsPlugin, showEmpty };
  root.IguanaChartPresentation = api;
  if (typeof module === 'object' && module.exports) module.exports = api;
})(typeof window === 'object' ? window : globalThis);
