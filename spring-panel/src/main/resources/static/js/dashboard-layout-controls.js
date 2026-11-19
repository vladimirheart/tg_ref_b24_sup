/* Layout editing is transient; saved order, hidden panels and pins use the existing dashboard runtime. */
(function () {
  function init() {
    const toggle = document.getElementById('customizeDashboardLayout');
    const tools = document.getElementById('dashboardLayoutTools');
    if (!toggle || !tools) return;
    toggle.addEventListener('click', function () {
      const editing = toggle.getAttribute('aria-expanded') !== 'true';
      toggle.setAttribute('aria-expanded', String(editing));
      document.body.dataset.dashboardEditing = String(editing);
      tools.hidden = !editing;
      toggle.querySelector('span').textContent = editing ? 'Готово' : 'Настроить';
    });
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init, { once: true });
  else init();
}());
