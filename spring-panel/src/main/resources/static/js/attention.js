(function () {
  const panel = document.querySelector('[data-attention-panel]');
  if (!panel) return;
  let inFlight = false;
  async function refresh() {
    if (inFlight || document.hidden) return;
    inFlight = true;
    try {
      const response = await fetch('/api/attention', { credentials: 'same-origin', cache: 'no-store' });
      if (!response.ok) return;
      const data = await response.json();
      let pending = 0;
      panel.querySelectorAll('[data-attention-kind]').forEach(link => {
        const counts = data[link.dataset.attentionKind];
        const count = Number(counts?.pending || 0);
        const unseen = Number(counts?.unseen || 0);
        link.hidden = count === 0;
        link.querySelector('[data-attention-count]').textContent = String(count);
        link.querySelector('[data-attention-new]').hidden = unseen === 0;
        link.setAttribute('aria-label', `${link.title}: ожидает ${count}, новых ${unseen}`);
        pending += count;
      });
      panel.hidden = pending === 0;
    } catch (_) { /* Preserve the last confirmed counts during a network failure. */ }
    finally { inFlight = false; }
  }
  async function seen(kind, ids) {
    if (!ids.length) return;
    const csrf = document.querySelector('meta[name="_csrf"]')?.content || '';
    try {
      const response = await fetch('/api/attention/seen', {
        method: 'POST', credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json', [document.querySelector('meta[name="_csrf_header"]')?.content || 'X-XSRF-TOKEN']: csrf },
        body: JSON.stringify({ kind, ids: ids.slice(0, 300) })
      });
      if (response.ok) await refresh();
    } catch (_) { /* A failed read marker leaves the item new. */ }
  }
  window.iguanaAttention = { refresh, seen };
  window.addEventListener('panel:sse:attention_changed', refresh);
  window.addEventListener('panel:sse:sidebar_unblock_changed', refresh);
  window.addEventListener('focus', refresh);
  document.addEventListener('visibilitychange', refresh);
  let timer = window.setInterval(refresh, 15000);
  window.addEventListener('pagehide', () => { window.clearInterval(timer); timer = null; });
  window.addEventListener('pageshow', () => { if (!timer) timer = window.setInterval(refresh, 15000); refresh(); });
  function markPageSeen() {
    const page = document.querySelector('[data-attention-page="unblock"]');
    if (page) seen('unblock', Array.from(page.querySelectorAll('[data-request-id]')).map(row => Number(row.dataset.requestId)));
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', markPageSeen, { once: true });
  else markPageSeen();
  refresh();
})();
