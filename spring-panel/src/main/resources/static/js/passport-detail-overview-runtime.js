(function () {
  if (window.PassportDetailOverviewRuntime) {
    return;
  }

  function createRuntime(options = {}) {
    const getPassport = typeof options.getPassport === 'function' ? options.getPassport : () => ({});
    const coreRuntime = options.coreRuntime && typeof options.coreRuntime === 'object' ? options.coreRuntime : null;
    if (!coreRuntime) {
      throw new Error('PassportDetailOverviewRuntime requires coreRuntime');
    }
    const { DAY_LABELS, text, escapeHtml, normalizeKey, first, renderProperties } = coreRuntime;

    function resolvePassport() {
      const value = getPassport();
      return value && typeof value === 'object' ? value : {};
    }

    function renderOverview() {
        const passport = resolvePassport();
        renderProperties('passportCoreProperties', [
            { label: 'Бизнес', value: passport.business },
            { label: 'Департамент', value: passport.department },
            { label: 'Тип партнёра', value: passport.partner_type },
            { label: 'Юридическое лицо', value: passport.legal_entity },
            { label: 'Страна', value: passport.country },
            { label: 'Город', value: passport.city },
            { label: 'Адрес', value: passport.location_address },
            { label: 'Статус', value: passport.status }
        ]);

        renderProperties('passportLifecycleProperties', [
            { label: 'Дата запуска', value: passport.start_date },
            { label: 'Дата закрытия', value: passport.end_date },
            { label: 'Приостановлен', value: passport.suspension_date },
            { label: 'Возобновлён', value: passport.resume_date },
            { label: 'Общее время работы', value: passport.total_work_time },
            { label: 'Время в приостановке', value: passport.status_history_total }
        ]);

        const history = Array.isArray(passport.status_history) ? passport.status_history : [];
        const historyTarget = document.getElementById('passportStatusHistory');
        if (historyTarget && history.length) {
            historyTarget.classList.remove('d-none');
            historyTarget.innerHTML = history.slice().reverse().slice(0, 8).map((entry) => {
                const label = first(entry.status, entry.state, entry.name, 'Изменение статуса');
                const when = first(entry.changed_at, entry.created_at, entry.date, entry.from, '');
                const note = first(entry.note, entry.comment, entry.reason, '');
                return `<div class="passport-timeline-item">
                    <span class="passport-timeline-dot"></span>
                    <div><strong>${escapeHtml(label)}</strong>${when ? `<span>${escapeHtml(when)}</span>` : ''}${note ? `<small>${escapeHtml(note)}</small>` : ''}</div>
                </div>`;
            }).join('');
        }

        const contacts = [
            ['Управляющий', passport.it_manager_name, passport.it_manager_phone],
            ['Телефон объекта', passport.it_object_phone, '']
        ].filter((item) => text(item[1]) || text(item[2]));
        const contactsTarget = document.getElementById('passportContacts');
        contactsTarget.innerHTML = contacts.length ? contacts.map((item) => `
            <div class="passport-contact">
                <span>${escapeHtml(item[0])}</span>
                <strong>${escapeHtml(first(item[1], item[2]))}</strong>
                ${text(item[1]) && text(item[2]) ? `<a href="tel:${escapeHtml(item[2])}">${escapeHtml(item[2])}</a>` : ''}
            </div>
        `).join('') : '<div class="passport-inline-empty">Контакты не указаны.</div>';

        renderSchedule();
        renderQuality();
    }

    function renderSchedule() {
        const passport = resolvePassport();
        const target = document.getElementById('passportSchedule');
        const schedule = Array.isArray(passport.schedule) ? passport.schedule : [];
        const visible = schedule.filter((item) => item && (item.is_24 || text(item.from) || text(item.to)));
        if (!visible.length) {
            target.innerHTML = '<div class="passport-inline-empty">Расписание не заполнено.</div>';
            return;
        }
        target.innerHTML = visible.map((item) => {
            const day = DAY_LABELS[text(item.day).toLowerCase()] || text(item.day) || '—';
            const value = item.is_24 ? '24 часа' : `${text(item.from) || '—'}–${text(item.to) || '—'}`;
            return `<div class="passport-schedule-row"><span>${escapeHtml(day)}</span><strong>${escapeHtml(value)}</strong></div>`;
        }).join('');
    }

    function completionInfo() {
        const passport = resolvePassport();
        const checks = [
            ['бизнес', passport.business],
            ['департамент', passport.department],
            ['город', passport.city],
            ['адрес', passport.location_address],
            ['статус', passport.status],
            ['телефон объекта', passport.it_object_phone],
            ['управляющий', passport.it_manager_name],
            ['телефон управляющего', passport.it_manager_phone],
            ['провайдер', passport.network_provider],
            ['договор провайдера', passport.network_contract_number],
            ['расписание', Array.isArray(passport.schedule) && passport.schedule.length ? 'yes' : ''],
            ['оборудование', Array.isArray(passport.equipment) && passport.equipment.length ? 'yes' : ''],
            ['титульное фото', Array.isArray(passport.photos) && passport.photos.some((p) => normalizeKey(p && p.category) === 'title') ? 'yes' : '']
        ];
        const filled = checks.filter((item) => text(item[1])).length;
        const pct = Math.round((filled / checks.length) * 100);
        return { pct, missing: checks.filter((item) => !text(item[1])).map((item) => item[0]) };
    }

    function renderQuality() {
        const passport = resolvePassport();
        const info = completionInfo();
        document.getElementById('passportCompletionValue').textContent = `${info.pct}%`;
        document.getElementById('passportCompletionHint').textContent = info.missing.length ? `Нет: ${info.missing.slice(0, 2).join(', ')}` : 'Ключевые поля заполнены';
        document.getElementById('passportQuality').innerHTML = `
            <div class="passport-quality-score"><strong>${info.pct}%</strong><span>ключевых полей заполнено</span></div>
            <div class="passport-quality-bar"><span style="width:${info.pct}%"></span></div>
            ${info.missing.length ? `<div class="passport-quality-missing">Не хватает: ${escapeHtml(info.missing.slice(0, 5).join(', '))}${info.missing.length > 5 ? '…' : ''}</div>` : '<div class="passport-quality-ok">Ключевые данные заполнены.</div>'}
        `;
    }


    return Object.freeze({
      renderOverview,
    });
  }

  function mount(options = {}) {
    return createRuntime(options);
  }

  window.PassportDetailOverviewRuntime = Object.freeze({
    mount,
  });
}());
