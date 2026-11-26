(() => {
  'use strict';
  const editButton = document.getElementById('dialogDetailsLocationBusinessBtn');
  const modalElement = document.getElementById('dialogLocationBusinessModal');
  const form = document.getElementById('dialogLocationBusinessForm');
  const select = document.getElementById('dialogLocationBusinessBusiness');
  const error = document.getElementById('dialogLocationBusinessError');
  const save = document.getElementById('dialogLocationBusinessSave');
  if (!editButton || !modalElement || !form || !select || !window.bootstrap?.Modal) return;
  const modal = bootstrap.Modal.getOrCreateInstance(modalElement);
  const api = window.DialogOperatorActions;
  let active = null, serial = 0, saving = false;
  const row = () => document.querySelector('tr.dialog-row-active');
  const message = value => { error.textContent = value; error.classList.toggle('d-none', !value); };
  const snapshot = () => { const r = row(); return r ? {
    ticketId: r.dataset.ticketId, ticketPublicId: r.dataset.ticketPublicId, scopeKind: r.dataset.scopeKind,
    businessId: String(r.dataset.businessId || ''),
  } : null; };
  const same = c => c && JSON.stringify(snapshot()) === JSON.stringify(c);
  function fill(items, current) {
    select.replaceChildren();
    const placeholder = document.createElement('option');
    placeholder.value = ''; placeholder.textContent = 'Выберите бизнес'; select.append(placeholder);
    for (const item of items) {
      const id = String(item.id);
      if (!/^[1-9][0-9]*$/.test(id) || !item.displayName || id === current) continue;
      const option = document.createElement('option'); option.value = id;
      option.textContent = item.displayName; select.append(option);
    }
  }
  editButton.addEventListener('click', async () => {
    const c = snapshot(), turn = ++serial;
    active = null; message(''); fill([], ''); save.disabled = true; select.disabled = true;
    modal.show();
    if (!c || !/^[a-f0-9]{32}$/.test(c.ticketPublicId) || !['BUSINESS', 'UNRESOLVED'].includes(c.scopeKind)) {
      message('Смена бизнеса недоступна для этого обращения.'); return;
    }
    try {
      let payload;
      if (c.scopeKind === 'BUSINESS') {
        payload = await api.request('/api/operator-dialogs/' + c.ticketPublicId + '/business');
        if (!api.matches(payload.owner, row()) || !payload.canManage) throw new Error('Для смены бизнеса нужно право работы с открытым диалогом.');
      } else {
        payload = { businesses: await api.request('/api/unresolved-scope/businesses') };
      }
      if (turn !== serial || !same(c)) return;
      fill(payload.businesses, c.businessId);
      if (select.options.length <= 1) throw new Error('Других доступных бизнесов с правом работы нет.');
      active = { context: c, owner: payload.owner, revision: payload.revision };
      select.disabled = false; save.disabled = false;
    } catch (e) { if (turn === serial && same(c)) message(e.message); }
  });
  modalElement.addEventListener('hidden.bs.modal', () => { serial++; active = null; });
  form.addEventListener('submit', async event => {
    event.preventDefault();
    if (saving) return;
    const saved = active, target = select.value;
    if (!saved || !same(saved.context)) { message('Диалог изменился. Закройте и откройте выбор бизнеса.'); return; }
    if (!/^[1-9][0-9]*$/.test(target)) { message('Выберите бизнес.'); return; }
    if (saved.context.scopeKind === 'BUSINESS' && !window.confirm('Перенести обращение в выбранный бизнес? История и анкета сохранятся; ответственный и категории прежнего бизнеса будут сброшены.')) return;
    saving = true; save.disabled = true; select.disabled = true; message('');
    try {
      const c = saved.context;
      if (c.scopeKind === 'BUSINESS') {
        await api.request('/api/operator-dialogs/' + c.ticketPublicId + '/business',
          { ...saved.owner, revision: saved.revision, businessTargetId: target });
      } else {
        // Preserve the existing unresolved-selection contract; its endpoint accepts a JSON BIGINT.
        if (!Number.isSafeInteger(Number(target))) throw new Error('Идентификатор бизнеса не поддерживается этой формой.');
        await api.request('/api/unresolved-scope/tickets/' + c.ticketPublicId + '/business', { businessId: Number(target) });
      }
      if (same(c)) { modal.hide(); window.location.reload(); }
    } catch (e) { if (same(saved.context)) message(e.message); }
    finally { saving = false; if (saved === active) { save.disabled = false; select.disabled = false; } }
  });
})();
