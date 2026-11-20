(function () {
  if (window.PassportDialogPreviewRuntime) return;
  function mount(options = {}) {
    const dialog = document.getElementById('passportDialogPreview');
    if (!dialog || typeof dialog.showModal !== 'function' || dialog.dataset.bound) return;
    dialog.dataset.bound = 'true';
    let frame = dialog.querySelector('iframe');
    const loading = dialog.querySelector('[data-passport-dialog-loading]');
    let activeLink = null;
    let token = '';

    function close() {
      if (!dialog.open) return;
      if (window.history.state?.passportDialog === token) window.history.back();
      else dialog.close();
    }

    document.addEventListener('click', event => {
      const link = event.target.closest('a[data-passport-open-dialog]');
      if (!link || event.defaultPrevented || event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
      const url = new URL(link.href, window.location.href);
      if (url.origin !== window.location.origin || !((url.pathname === '/dialogs' && url.searchParams.get('ticketId'))
          || /^\/general-dialog-work\/[0-9a-f]{32}$/.test(url.pathname))) return;
      event.preventDefault();
      if (dialog.open) return;
      activeLink = link;
      token = `passport-dialog-${Date.now()}`;
      dialog.querySelector('h2').textContent = link.dataset.requestNumber ? `Обращение № ${link.dataset.requestNumber}` : 'Обращение';
      loading.hidden = false;
      frame.src = url.href;
      window.history.pushState({ ...window.history.state, passportDialog: token }, '', window.location.href);
      dialog.showModal();
      frame.focus();
    });
    dialog.querySelector('[data-passport-dialog-close]').addEventListener('click', close);
    dialog.addEventListener('cancel', event => { event.preventDefault(); close(); });
    window.addEventListener('popstate', () => {
      if (dialog.open && window.history.state?.passportDialog !== token) dialog.close();
    });
    function bindFrameLoad() { frame.addEventListener('load', () => { loading.hidden = true; }); }
    bindFrameLoad();
    window.addEventListener('message', event => {
      if (!dialog.open || event.origin !== window.location.origin || event.source !== frame.contentWindow
          || event.data?.type !== 'iguana-passport-dialog') return;
      if (event.data.action === 'close') close();
      if (event.data.action === 'ready') loading.hidden = true;
    });
    dialog.addEventListener('close', () => {
      const replacement = frame.cloneNode(false);
      replacement.removeAttribute('src');
      frame.replaceWith(replacement);
      frame = replacement;
      bindFrameLoad();
      const href = activeLink?.getAttribute('href');
      activeLink = null;
      Promise.resolve(options.onClose?.()).catch(() => {}).finally(() => {
        if (!dialog.open) Array.from(document.querySelectorAll('a[data-passport-open-dialog]'))
          .find(link => link.getAttribute('href') === href)?.focus({ preventScroll: true });
      });
    });
  }
  window.PassportDialogPreviewRuntime = Object.freeze({ mount });
}());
