(function () {
  const statuses = { PENDING: 'Ожидает решения', NEEDS_REVIEW: 'Требует уточнения: УЗ не найдена', APPROVED: 'Одобрено', REJECTED: 'Отклонено', MERGED: 'Объединено', SUPERSEDED: 'Обработано в другой заявке' };
  const kinds = { password: 'Забыл пароль', blocked: 'Доступ заблокирован', other: 'Другая проблема' };
  const actions = { RECEIVED: 'Получено обращение', IDENTITY_VERIFIED: 'Подтверждена проверка личности', LINKED: 'Определена учётная запись', PASSWORD_RESET: 'Пароль сброшен', ACCOUNT_UNBLOCKED: 'Блокировка снята', REJECTED: 'Отклонено' };
  const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const date = value => value ? esc(new Date(value).toLocaleString('ru-RU')) : '—';
  const pending = item => ['PENDING', 'NEEDS_REVIEW'].includes(item.status);

  function init(container) {
    const endpoint = container.dataset.passwordResetEndpoint || '/api/password-reset-requests';
    const body = container.querySelector('[data-password-reset-body]');
    const error = container.querySelector('[data-password-reset-error]');
    const success = container.querySelector('[data-password-reset-success]');
    const empty = container.querySelector('[data-password-reset-empty]');
    const filter = container.querySelector('[data-recovery-filter]');
    let items = [];
    let busy = false;
    function showError(message = '') {
      error.textContent = message;
      error.classList.toggle('d-none', !message);
    }
    async function request(path, payload) {
      const response = await fetch(path, {
        method: payload ? 'POST' : 'GET', credentials: 'same-origin', cache: 'no-store',
        headers: { 'Content-Type': 'application/json', [document.querySelector('meta[name="_csrf_header"]')?.content || 'X-XSRF-TOKEN']: document.querySelector('meta[name="_csrf"]')?.content || '' },
        ...(payload ? { body: JSON.stringify(payload) } : {})
      });
      const data = await response.json();
      if (!response.ok || data.success === false) throw new Error(data.error || 'Не удалось выполнить запрос');
      return data;
    }
    function metadata(item) {
      const agent = String(item.requested_user_agent || '');
      const browser = agent.match(/(Edg)\/([\d.]+)/) || agent.match(/(Firefox)\/([\d.]+)/) || agent.match(/(Chrome)\/([\d.]+)/) || agent.match(/(Version)\/([\d.]+)/);
      const os = /Android/.test(agent) ? 'Android' : /iPhone|iPad/.test(agent) ? 'iOS' : /Windows/.test(agent) ? 'Windows' : /Macintosh/.test(agent) ? 'macOS' : /Linux/.test(agent) ? 'Linux' : '';
      const description = browser ? `${({ Edg: 'Edge', Version: 'Safari' })[browser[1]] || browser[1]} ${browser[2]}${os ? ' · ' + os : ''}` : os;
      return `<details class="mt-3"><summary>Технические сведения</summary><dl class="row small mt-2 mb-0">
        <dt class="col-sm-3">IP источника</dt><dd class="col-sm-9 text-break">${esc(item.requested_by_ip || '—')}</dd>
        <dt class="col-sm-3">Адрес соединения</dt><dd class="col-sm-9 text-break">${esc(item.connection_ip || '—')}</dd>
        <dt class="col-sm-3">Браузер / ОС</dt><dd class="col-sm-9 text-break">${esc(description || 'Не определены')}<div class="text-muted">${esc(agent || 'User-Agent отсутствует')}</div><div class="text-muted">Данные клиента; не подтверждают личность.</div></dd>
        <dt class="col-sm-3">ID запроса</dt><dd class="col-sm-9 text-break">${esc(item.correlation_id || '—')}</dd>
        <dt class="col-sm-3">Проверка УЗ</dt><dd class="col-sm-9">${esc(({ FOUND: 'УЗ найдена', UNKNOWN: 'УЗ не найдена', AMBIGUOUS: 'Неоднозначный логин', LINKED: 'УЗ определена сотрудником' })[item.lookup_result] || item.lookup_result)}</dd>
        <dt class="col-sm-3">Последняя отправка</dt><dd class="col-sm-9">${date(item.last_requested_at)}</dd>
      </dl></details>`;
    }
    function details(item) {
      const open = pending(item);
      const known = item.user_id != null && item.linked_username != null;
      return `<tr data-recovery-detail="${item.id}" hidden><td colspan="6"><div class="p-3">
        <p><strong>Обращение №${item.id}:</strong> ${esc(kinds[item.request_kind] || 'Восстановление доступа')}</p>
        <p class="mb-1">ФИО: ${esc(item.requester_name || '—')} · Контакт: ${esc(item.requester_contact || '—')}</p>
        <p class="mb-1 text-break">Введённый логин: ${esc(item.requested_by_username || item.username_snapshot)}</p>
        <p class="text-break">Комментарий: ${esc(item.requested_note || '—')}</p>
        ${known ? `<p>УЗ: <strong>${esc(item.linked_username)}</strong> · ${item.account_blocked || item.account_enabled === false ? 'Доступ заблокирован' : 'Доступ разрешён'}</p>` : '<p class="text-warning">Сначала определите существующую учётную запись. Выдача нового доступа рассматривается отдельно.</p>'}
        ${open ? `<form data-recovery-form="${item.id}">
          <label class="form-label d-block">Основание решения / комментарий
            <textarea name="note" class="form-control" rows="2" maxlength="1000" placeholder="Как проверена личность или почему заявка отклоняется. Без паролей и кодов." required></textarea>
          </label>
          <label class="form-check mb-3"><input name="verified" type="checkbox" class="form-check-input"><span class="form-check-label">Личность проверена по известным данным или через ответственного руководителя</span></label>
          ${known ? `<div class="d-flex flex-wrap gap-3 mb-3">
            <label><input name="reset" type="checkbox" ${item.request_kind === 'password' ? 'checked' : ''}> Сбросить пароль</label>
            <label><input name="unblock" type="checkbox"> Снять блокировку УЗ</label>
          </div><button type="button" class="btn btn-success btn-sm me-2" data-recovery-action="approve">Выполнить выбранные действия</button>` : `<label class="form-label d-block">Логин существующей УЗ<input name="username" class="form-control" maxlength="255" autocomplete="off"></label>
            <button type="button" class="btn btn-primary btn-sm me-2" data-recovery-action="link">Привязать УЗ после проверки</button>`}
          <button type="button" class="btn btn-outline-danger btn-sm" data-recovery-action="reject">Отклонить с комментарием</button>
        </form>` : `<p>Решение: ${esc(item.resolution_note || '—')}</p>`}
        ${metadata(item)}
        <button type="button" class="btn btn-link btn-sm px-0 mt-2" data-recovery-history="${item.id}">История обработки</button>
        <div data-recovery-history-body="${item.id}" class="small text-muted"></div>
      </div></td></tr>`;
    }
    function render() {
      const visible = filter?.value === 'pending' ? items.filter(pending) : items;
      empty?.classList.toggle('d-none', visible.length > 0);
      body.innerHTML = visible.map(item => `<tr>
        <td class="text-break"><strong>${esc(item.username_snapshot)}</strong><div class="small text-muted">№${item.id} · ${esc(kinds[item.request_kind] || '')}</div></td>
        <td><span class="badge ${pending(item) ? 'text-bg-warning' : 'text-bg-secondary'} text-wrap">${esc(statuses[item.status] || item.status)}</span></td>
        <td>${date(item.created_at)}<div class="small text-muted">Отправок: ${Number(item.repeat_count || 1)}</div></td>
        <td>${item.resolved_by_username && window.EmployeeProfiles ? window.EmployeeProfiles.actor(item.resolved_by_username) : '—'}</td><td>${date(item.resolved_at)}</td>
        <td><button type="button" class="btn btn-outline-primary btn-sm" data-recovery-open="${item.id}" aria-expanded="false">Открыть</button></td>
      </tr>${details(item)}`).join('');
    }
    async function load() {
      if (busy) return;
      showError();
      try { items = (await request(endpoint)).items || []; render(); }
      catch (failure) { showError(failure.message); }
    }
    function showResult(data) {
      // This area survives list reloads. Secrets remain only in the current document.
      success.replaceChildren();
      success.classList.remove('d-none');
      const message = document.createElement('p');
      message.textContent = data.message || 'Заявка обработана.';
      success.append(message);
      if (data.temporary_password) {
        const label = document.createElement('span'); label.textContent = 'Временный пароль: ';
        const secret = document.createElement('code'); secret.textContent = data.temporary_password;
        const copy = document.createElement('button'); copy.type = 'button'; copy.className = 'btn btn-outline-success btn-sm ms-2'; copy.textContent = 'Скопировать';
        copy.addEventListener('click', async () => {
          try { await navigator.clipboard.writeText(secret.textContent); copy.textContent = 'Скопировано'; }
          catch (_) { copy.textContent = 'Выделите пароль и скопируйте вручную'; }
        });
        success.append(label, secret, copy);
      }
      const close = document.createElement('button'); close.type = 'button'; close.className = 'btn btn-outline-secondary btn-sm ms-2'; close.textContent = 'Скрыть результат';
      close.addEventListener('click', () => { success.replaceChildren(); success.classList.add('d-none'); });
      success.append(close);
    }
    body.addEventListener('click', async event => {
      const opener = event.target.closest('[data-recovery-open]');
      if (opener) {
        const row = body.querySelector(`[data-recovery-detail="${opener.dataset.recoveryOpen}"]`);
        row.hidden = !row.hidden;
        opener.setAttribute('aria-expanded', String(!row.hidden));
        if (!row.hidden) window.iguanaAttention?.seen('recovery', [Number(opener.dataset.recoveryOpen)]);
        return;
      }
      const history = event.target.closest('[data-recovery-history]');
      if (history) {
        try {
          const data = await request(`${endpoint}/${history.dataset.recoveryHistory}/events`);
          body.querySelector(`[data-recovery-history-body="${history.dataset.recoveryHistory}"]`).innerHTML = data.items.map(item => `<div>${esc(new Date(item.created_at).toLocaleString('ru-RU'))} · ${esc(actions[item.action] || item.action)} · ${item.actor !== 'public' && window.EmployeeProfiles ? window.EmployeeProfiles.actor(item.actor) : 'Заявитель'}${item.note ? ': ' + esc(item.note) : ''}</div>`).join('') || 'История отсутствует';
        } catch (failure) { showError(failure.message); }
        return;
      }
      const button = event.target.closest('[data-recovery-action]');
      if (!button || busy) return;
      const form = button.closest('form');
      const values = new FormData(form);
      const action = button.dataset.recoveryAction;
      if (!form.reportValidity()) return;
      if (action !== 'reject' && !values.has('verified')) { showError('Подтвердите проверку личности заявителя.'); return; }
      if (action === 'link' && !values.get('username')) { showError('Укажите логин существующей учётной записи.'); return; }
      if (action === 'approve' && !values.has('reset') && !values.has('unblock')) { showError('Выберите необходимые действия.'); return; }
      busy = true; showError();
      form.querySelectorAll('button').forEach(element => { element.disabled = true; });
      try {
        const data = await request(`${endpoint}/${form.dataset.recoveryForm}/${action}`, {
          note: values.get('note'), identity_verified: values.has('verified'), username: values.get('username'),
          reset_password: values.has('reset'), unblock_account: values.has('unblock')
        });
        showResult(data);
        busy = false;
        await load();
        window.iguanaAttention?.refresh();
      } catch (failure) { showError(failure.message); }
      finally { busy = false; form.querySelectorAll('button').forEach(element => { element.disabled = false; }); }
    });
    container.querySelector('[data-password-reset-refresh]')?.addEventListener('click', load);
    filter?.addEventListener('change', render);
    const modal = container.closest('.modal');
    if (modal) modal.addEventListener('shown.bs.modal', load);
    else load();
  }
  document.addEventListener('DOMContentLoaded', () => document.querySelectorAll('[data-password-reset-requests]').forEach(init));
})();
