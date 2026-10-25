/* Только представление сохранённого сигнала: не меняет причину, статус или данные инцидента. */
(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.IncidentDescriptions = api;
})(typeof window === 'undefined' ? globalThis : window, function () {
  'use strict';
  const text = value => typeof value === 'string' ? value.trim() : '';
  const object = value => value && typeof value === 'object' && !Array.isArray(value) ? value : {};
  const parse = value => { try { return object(typeof value === 'string' ? JSON.parse(value) : value); } catch (_) { return {}; } };
  function latest(incident, keys) {
    const events = Array.isArray(incident.events) ? incident.events : [];
    for (let i = events.length - 1; i >= 0; i--) {
      const payload = parse(events[i]?.payload_json);
      if (keys.some(key => Object.hasOwn(payload, key))) return payload;
    }
    return {};
  }
  const number = value => (typeof value === 'number' || typeof value === 'string' && value.trim() !== '') && Number.isFinite(Number(value)) && Number(value) >= 0 ? Number(value) : null;
  const display = value => new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 1 }).format(value);
  function endpoint(value) {
    const address = text(value);
    if (!address) return '';
    try { return new URL(address.includes('://') ? address : 'http://' + address).host; } catch (_) { return ''; }
  }
  function counters(summary) {
    const result = {};
    for (const part of text(summary).split(',')) {
      const match = part.trim().match(/^([a-z_]+)\s*=\s*(\d+(?:\.\d+)?)$/);
      if (match) result[match[1]] = Number(match[2]);
    }
    return result;
  }
  const workers = {
    'operator-notification-watch.chat-history': ['Уведомления о новых сообщениях обновляются с задержкой', 'Уведомления операторов о новых сообщениях могут появляться позже обычного.'],
    'operator-notification-watch.feedbacks': ['Уведомления об оценках обновляются с задержкой', 'Операторы могут с задержкой видеть уведомления о новых оценках клиентов.'],
    'ui-event-outbox-watch': ['Изменения не доходят до интерфейса вовремя', 'Изменения в панели могут появляться с задержкой или только после обновления страницы.']
  };
  const workerInfo = {
    'operator-notification-watch.chat-history': { name: 'Уведомления операторам о новых сообщениях', component: 'OperatorNotificationWatcher', purpose: 'Читает новые записи переписки и формирует уведомления операторам.', source: 'chat_history' },
    'operator-notification-watch.feedbacks': { name: 'Уведомления операторам об оценках клиентов', component: 'Сохранённая позиция наблюдателя оценок', purpose: 'Это сохранённая позиция чтения оценок клиентов, а не задача доставки сообщений клиенту.', source: 'feedbacks' },
    'ui-event-outbox-watch': { name: 'Обновление данных на открытых страницах панели', component: 'UiEventOutboxWatcher', purpose: 'Читает события об обращениях, сообщениях и оценках и передаёт изменения в открытый интерфейс панели.', source: 'ui_event_outbox' }
  };
  function duration(minutes) {
    const value = number(minutes);
    if (value === null) return '';
    if (value < 60) return display(value) + ' мин';
    const days = Math.floor(value / 1440), hours = Math.floor(value % 1440 / 60), rest = Math.floor(value % 60);
    return [days ? days + ' д' : '', hours ? hours + ' ч' : '', rest ? rest + ' мин' : ''].filter(Boolean).join(' ') + ' (' + display(value) + ' мин)';
  }
  // Формулировки соответствуют условиям observability/prometheus/rules/iguana-alerts.yml.
  // Недоступность метрик не доказывает остановку самого сервиса.
  const alerts = {
    IguanaPanelWebReplicaDown: ['Не удаётся проверить веб-панель', 'Мониторинг не получает метрики одного экземпляра веб-панели более двух минут.', 'Доступность этого экземпляра не подтверждена. Причиной может быть как сервис, так и доступ к его метрикам.', 'Проверьте состояние panel-web и доступ Prometheus к его адресу метрик.'],
    IguanaOpsWorkerReplicaDown: ['Не удаётся проверить фоновые процессы', 'Мониторинг не получает метрики одного экземпляра фонового обработчика более двух минут.', 'Работа фоновых задач этого экземпляра не подтверждена.', 'Проверьте состояние ops-worker и доступ Prometheus к его адресу метрик.'],
    IguanaProductionReadinessNotReady: ['Панель не прошла проверку готовности', 'Одна или несколько проверок рабочего окружения не проходят не менее пяти минут.', 'Часть функций может работать с ограничениями. Конкретный компонент нужно уточнить в проверках готовности.', 'Откройте Production readiness в настройках и посмотрите, какая проверка не проходит.'],
    IguanaReadinessRefreshFailed: ['Не удаётся обновить проверку готовности', 'Обновление сведений о готовности рабочего окружения завершается ошибкой не менее пяти минут.', 'Данные о готовности могут быть устаревшими; это не подтверждение отказа всех компонентов.', 'Проверьте журнал ops-worker и выполнение обновления Production readiness.'],
    IguanaDlqBacklog: ['В очереди остались необработанные события', 'В очереди событий, обработка которых завершилась ошибкой, есть записи уже не менее десяти минут.', 'Часть событий интеграций не обработана автоматически.', 'Откройте мониторинг транспорта, изучите ошибки событий. Перед повторной отправкой проверьте, не были ли они уже выполнены.'],
    IguanaIncidentDeliveryFailed: ['Уведомления об инцидентах не доставлены', 'Ошибки отправки уведомлений об инцидентах остаются в очереди не менее десяти минут.', 'Наблюдатели или внешние получатели могут не получить уведомление.', 'Проверьте ошибки маршрутов доставки инцидентов и доступность получателей.'],
    IguanaIncidentDeliveryStale: ['Отправка уведомлений об инцидентах задержалась', 'В очереди уведомлений есть записи, обработка которых не завершается вовремя; условие сохраняется не менее пяти минут.', 'Уведомления могут приходить с задержкой.', 'Проверьте очередь доставки инцидентов и журнал фонового обработчика.'],
    IguanaAlertmanagerDeliveryFailed: ['Alertmanager не смог передать уведомление панели', 'При отправке уведомлений из Alertmanager в панель зарегистрированы ошибки.', 'Часть внешних сигналов могла не попасть в список инцидентов.', 'Проверьте сетевую доступность, авторизацию приёма уведомлений и журнал Alertmanager.'],
    IguanaPanelP95LatencyHigh: ['Веб-панель отвечает медленнее обычного', 'Панель регулярно отвечает дольше двух секунд; повышенное время ответа держится не менее пятнадцати минут.', 'Операторы могут замечать медленное открытие страниц и выполнение действий.', 'Проверьте время ответа запросов, нагрузку и медленные обращения к базе данных.'],
    IguanaPanel5xxRateHigh: ['Веб-панель часто отвечает ошибкой', 'Доля ответов сервера с кодом 5xx превышает 2% не менее пяти минут.', 'Часть действий операторов может завершаться ошибкой.', 'Проверьте журнал panel-web и запросы с кодами 5xx. Сообщение само по себе не указывает первопричину.'],
    IguanaPostgresMetricsUnavailable: ['Не поступают метрики PostgreSQL', 'Prometheus не получает метрики PostgreSQL более двух минут.', 'Состояние базы данных по этим метрикам неизвестно. Это не доказательство её остановки.', 'Проверьте postgres-exporter и его подключение к базе, затем доступ Prometheus к exporter.'],
    IguanaRedisMetricsUnavailable: ['Не поступают метрики Redis', 'Prometheus не получает метрики Redis более двух минут.', 'Состояние Redis по этим метрикам неизвестно. Это не доказательство его остановки.', 'Проверьте redis-exporter, его подключение к Redis и доступ Prometheus.'],
    IguanaRabbitMqMetricsUnavailable: ['Не поступают метрики очереди сообщений', 'Prometheus не получает метрики RabbitMQ более двух минут.', 'Состояние очередей по этим метрикам неизвестно. Доставка рабочих сообщений при этом могла продолжаться.', 'Проверьте адрес метрик RabbitMQ и доступ Prometheus к нему.'],
    IguanaMinioMetricsUnavailable: ['Не поступают метрики хранилища файлов', 'Prometheus не получает метрики MinIO более пяти минут.', 'Состояние хранилища по этим метрикам неизвестно. Это не подтверждает потерю файлов.', 'Проверьте доступ к адресу метрик MinIO и настройки авторизации мониторинга.'],
    IguanaLokiUnavailable: ['Не удаётся проверить хранилище журналов', 'Prometheus не получает метрики Loki более пяти минут.', 'Работа хранилища журналов не подтверждена; доступ к самим журналам нужно проверить отдельно.', 'Проверьте Loki и сетевой доступ к его метрикам.'],
    IguanaAlloyUnavailable: ['Не удаётся проверить сборщик журналов', 'Prometheus не получает метрики Alloy более пяти минут.', 'Работа сборщика журналов не подтверждена; часть журналов может поступать с задержкой.', 'Проверьте Alloy и сетевой доступ к его метрикам.'],
    IguanaAlertmanagerDeliverySmoke: ['Проверка доставки тестового уведомления', 'Получен тестовый сигнал для проверки пути Alertmanager → панель.', 'Сам по себе этот сигнал не означает поломку рабочего сервиса.', 'Убедитесь, что тестовый инцидент появился в панели и уведомление дошло до нужных получателей.']
  };
  function credentialDescription(data) {
    const kind = text(data.credential_kind), status = text(data.last_status).toLowerCase();
    const name = text(data.display_name);
    const labels = { proxy_password: 'Пароль прокси', proxy_token: 'Токен VLESS-прокси', bot_token: 'Токен бота', api_token: 'Токен API', integration_token: 'Токен интеграции', external_auth_token: 'Токен авторизации внешнего сервиса', webhook_secret: 'Секрет проверки вебхука', webhook_confirmation_token: 'Код подтверждения вебхука VK', webhook_url: 'Адрес вебхука с авторизацией', api_secret: 'Секрет API', api_login: 'Ключ доступа iiko API', shared_secret: 'Токен внутреннего API', signature_secret: 'Секрет подписи запросов', remember_me_key: 'Ключ подписи сохранённого входа', password: 'Пароль' };
    const label = labels[kind] || 'Учётные данные';
    const channel = name.match(/^Channel network route · (.+) proxy (?:password|token)$/);
    const bot = name.match(/^Channel token · (.+)$/);
    const profile = name.match(/^Network profile · (.+) proxy (?:password|token)$/);
    let resource = name, location = 'Настройка-источник в аналитике ротации учётных данных';
    if (channel || bot) {
      resource = `канал «${(channel || bot)[1]}»`;
      location = `Настройки → Каналы и запуск ботов → «${(channel || bot)[1]}»` + (channel ? ' → Сетевой маршрут → Прокси' : ' → Токен');
    } else if (profile) {
      resource = `профиль «${profile[1]}»`;
      location = 'Настройки → Профили прокси и VPN → «' + profile[1] + '»';
    } else if (/^(Project|Bots) network route proxy (password|token)$/.test(name)) {
      resource = name.startsWith('Project') ? 'маршрут проекта' : 'маршрут ботов';
      location = 'Настройки → Сетевые маршруты интеграций → ' + resource;
    } else if (name.startsWith('Bot credential · ')) {
      resource = 'учётная запись бота «' + name.slice('Bot credential · '.length) + '»';
      location = 'Настройки → Каналы и запуск ботов → Учётные данные ботов';
    } else if (/^(VK confirmation token|VK webhook secret|MAX webhook secret) · /.test(name)) {
      resource = 'канал «' + name.slice(name.indexOf(' · ') + 3) + '»';
      location = 'Настройки → Каналы и запуск ботов → ' + resource + ' → Настройки вебхука';
    } else if (name === 'NetBox sync API token') {
      resource = 'синхронизация с NetBox'; location = 'Настройки синхронизации NetBox → API token';
    } else if (name === 'Notion knowledge base token') {
      resource = 'база знаний Notion'; location = 'Настройки базы знаний → Токен Notion';
    } else if (text(data.source_type) === 'environment') {
      resource = ({ 'Panel internal bot API token': 'связь панели с ботами', 'Panel internal bot API signature secret': 'подпись запросов ботов к панели', 'Panel remember-me key': 'сохранение входа в панель' })[name] || name;
      location = 'Конфигурация запуска приложения (переменные окружения)';
    }
    const subject = label + (resource ? ' — ' + resource : '');
    const titles = { missing_secret: 'Не заполнено значение', source_removed: 'Настройка больше не найдена', expired: 'Истёк срок действия', expires_soon: 'Скоро истечёт срок действия', rotation_overdue: 'Просрочена плановая замена', rotation_due_soon: 'Подходит срок плановой замены', not_required: 'Не требуется выбранному маршруту' };
    const summaries = { missing_secret: 'В сохранённой проверке значение пустое, не сохранено или не прочитано приложением.', source_removed: 'Источник не найден в рабочей конфигурации.', expired: 'Сохранённый срок действия уже истёк.', expires_soon: 'Близка сохранённая дата окончания срока действия.', rotation_overdue: 'Наступила сохранённая дата плановой замены.', rotation_due_soon: 'Приближается сохранённая дата плановой замены.', not_required: 'Локальные поля не используются выбранным маршрутом или прокси не требует такой авторизации.' };
    let impact = 'Возможны ограничения доступа к указанному подключению. Запись о сроке или плановой замене сама по себе не подтверждает отказ подключения.';
    let action = 'Проверьте указанную настройку и её владельца. Не публикуйте значение учётных данных.';
    if (['expired', 'expires_soon', 'rotation_overdue', 'rotation_due_soon'].includes(status)) action = 'Согласуйте замену с владельцем указанного подключения, сохраните новые учётные данные и обновите дату в реестре.';
    if (kind === 'proxy_password' || kind === 'proxy_token') {
      if (status === 'missing_secret' || status === 'not_required') {
        impact = 'Пустое поле локального прокси не доказывает сбой канала. При выборе профиля, наследования, VPN или прямого подключения оно не используется. Прокси без авторизации не требует пароля.';
        action = 'Сначала проверьте выбранный режим сетевого маршрута. Если используется профиль — проверяйте его, а не локальные поля. Пароль нужен только для прокси с именем пользователя; токен — для VLESS. Не заполняйте неиспользуемые поля ради устранения предупреждения.';
      }
    }
    return { title: (titles[status] || 'Проверить настройку') + ': ' + subject, summary: subject + '. ' + (summaries[status] || text(data.reason) || 'Точное состояние не сохранено.'), impact, action, facts: [{ label: 'Учётные данные', value: label }, ...(resource ? [{ label: 'Подключение', value: resource }] : []), { label: 'Где проверить', value: location }] };
  }
  function describe(input) {
    const incident = object(input), type = text(incident.signal_type).toLowerCase(), key = text(incident.signal_key);
    const original = text(incident.summary) || text(incident.description).split(/\r?\n/)[0] || text(incident.title);
    const result = { title: text(incident.title) || 'Инцидент без названия', summary: original || 'Описание проблемы пока не заполнено.', impact: '', action: '', facts: [], known: false };
    const fact = (label, value, suffix = '') => { const n = number(value); if (n !== null) result.facts.push({ label, value: display(n) + suffix }); };
    if (type === 'integration_transport') {
      const payload = latest(incident, ['worker_key', 'stale_checkpoint_count', 'inbound_failed', 'latest_summary']);
      if (key.startsWith('panel-runtime-checkpoints/')) {
        const workerKey = key.slice('panel-runtime-checkpoints/'.length), details = workerInfo[workerKey];
        const data = text(payload.worker_key) && text(payload.worker_key) !== workerKey ? {} : payload;
        const worker = workers[workerKey] || ['Проверка служебной задачи', 'Влияние этой задачи нужно уточнить по её назначению.'];
        const taskName = details?.name || workerKey || 'Неизвестная задача';
        const lag = number(data.cursor_lag), age = number(data.age_minutes);
        Object.assign(result, { known: true,
          title: 'Проверка задачи: ' + taskName,
          summary: taskName + '. ' + (details?.purpose || 'Назначение неизвестной задачи не сохранено.') + ' ' + (age === null ? 'Возраст сохранённой позиции не указан.' : 'Позиция чтения данных не менялась ' + duration(age) + '.') + ' ' + (lag === 0 ? 'По сохранённой проверке после неё более новых записей нет.' : lag > 0 ? 'В источнике есть более новые записи, до которых эта задача ещё не дошла.' : 'Неизвестно, появились ли более новые записи.'),
          impact: lag === 0 ? 'Отставание по данным в сохранённой проверке отсутствует. Долгий возраст позиции не означает, что интерфейс всё это время не работал: эта отметка меняется при продвижении по данным, а не при каждом запуске задачи.' : lag > 0 ? worker[1] + ' Разница номеров записей не показывает точное число пропущенных уведомлений.' : 'По одной старой позиции нельзя подтвердить остановку или задержку. ' + worker[1],
          action: 'Смотрите ниже данные именно этой задачи и дату её последнего продвижения. ' + (details?.component ? 'Компонент: ' + details.component + '. ' : '') + 'Если новые данные есть, проверьте журнал этой задачи в ops-worker. Не сбрасывайте позицию и не повторяйте сообщения без проверки.' });
        if (age !== null) result.facts.push({ label: 'Позиция не менялась', value: duration(age) });
        fact('Порог возраста позиции', data.stale_threshold_minutes, ' мин'); fact('Проверок с проблемой подряд', data.unhealthy_streak);
        result.facts.push({ label: 'Задача', value: taskName }, { label: 'Служебное имя', value: workerKey });
        if (details) result.facts.push({ label: 'Источник данных', value: details.source }, { label: 'Компонент', value: details.component });
        if (number(data.unhealthy_streak) > 0) result.meaning = [`В ${display(Number(data.unhealthy_streak))} проверках подряд повторяется условие мониторинга. Это не число новых ошибок и не число неудачных запусков задачи.`];
      } else if (key === 'panel-runtime-checkpoints') {
        Object.assign(result, { known: true, title: 'Фоновые обработчики давно не подтверждали работу', summary: 'Один или несколько обработчиков панели не обновили отметку выполнения в ожидаемый срок.', impact: 'Уведомления и изменения в интерфейсе могут поступать с задержкой. Это не доказывает остановку ботов.', action: 'Откройте мониторинг транспорта и проверьте, у каких обработчиков устарела последняя отметка работы.' });
        fact('Обработчиков без свежей отметки', payload.stale_checkpoint_count); fact('Обработчиков с отставанием', payload.lagging_checkpoint_count);
      } else if (['panel-rabbitmq-bridge', 'panel-transport-sustained-pressure'].includes(key)) {
        const values = { ...counters(key === 'panel-transport-sustained-pressure' ? payload.latest_summary : incident.summary), ...payload };
        const parts = [];
        for (const [names, phrase, label] of [
          [['inbound_stale', 'inbound_stale_processing'], 'Операций приёма данных, не завершившихся вовремя: {n}.', 'Долго обрабатываются входящие данные'],
          [['outbound_stale', 'outbound_stale_processing'], 'Операций отправки, не завершившихся вовремя: {n}.', 'Долго выполняется отправка'],
          [['outbound_backlog'], 'Записей, ожидающих отправки: {n}.', 'Ожидают отправки'],
          [['stale_checkpoints'], 'Задач без свежего подтверждения работы: {n}.', 'Давно не менялось подтверждение работы задач'],
          [['lagging_checkpoints'], 'Задач, не дошедших до новых данных: {n}.', 'Есть ещё не обработанные новые данные']
        ]) { const count = names.map(name => number(values[name])).find(n => n !== null); if (count > 0) { parts.push(phrase.replace('{n}', display(count))); fact(label, count); } }
        Object.assign(result, { known: true, title: key === 'panel-transport-sustained-pressure' ? 'Задержки обработки событий повторяются' : 'Ошибки и задержки обработки событий', summary: parts.join(' ') || 'Проверка зафиксировала проблемы с очередью событий или фоновыми обработчиками; подробные счётчики в этом сигнале не сохранены.', impact: 'Некоторые события интеграций и обновления панели могут обрабатываться позже обычного.', action: 'Откройте мониторинг транспорта: проверьте ошибки, очередь и последние отметки работы обработчиков. Перед повтором события убедитесь, что оно не было выполнено.' });
        fact('Проверок с проблемой подряд', payload.unhealthy_streak);
        result.meaning = [
          'Ошибки отдельных сообщений доступны в диагностике транспорта. Они не являются основанием автоматического инцидента и не повышают его приоритет.',
          'Подтверждение работы — служебная запись о том, до какого места задача обновила уведомления или экран панели. Если запись давно не менялась, это ещё не доказывает, что задача остановилась.',
          'Отставание означает, что для задачи уже появились более новые данные, до которых она ещё не дошла. Это не обязательно задержка доставки сообщения клиенту.'
        ];
        const streak = number(payload.unhealthy_streak);
        if (streak > 0) result.meaning.push(`Проблема отмечена в ${display(streak)} проверках подряд. Это число проверок, а не число новых сбоев: одни и те же записи с ошибкой могут попадать в них повторно.`);
        result.title = key === 'panel-transport-sustained-pressure' ? 'Устойчивое ухудшение работы очереди или фоновых задач' : 'Проверка очереди и фоновых задач';
        result.impact = 'Зависшая обработка, накопление очереди или отставание фоновых задач могут задерживать обновления панели. Наличие ошибок отдельных записей само по себе не доказывает системный сбой.';
        result.action = 'Проверьте очередь и конкретные фоновые задачи. Подробности ошибок отдельных сообщений находятся только в диагностике транспорта; не запускайте их повтор без проверки.';
      }
    } else if (type === 'credential_rotation') {
      const data = { ...latest(incident, ['incident_reason', 'last_status']), ...object(incident.signal_context) };
      Object.assign(result, { known: true, ...credentialDescription(data) });
    } else if (type === 'provider_delivery') {
      const payload = latest(incident, ['channel_name', 'short_window_attempts']);
      const limited = /rate.limit|throttl/i.test(key);
      Object.assign(result, { known: true, title: limited ? 'Канал ограничивает частоту отправки сообщений' : 'Участились ошибки отправки сообщений в канал', summary: limited ? 'Контроль доставки зафиксировал повторяющиеся ограничения частоты отправки со стороны канала.' : 'Контроль доставки зафиксировал повышенную частоту ошибок при отправке ответов оператора.', impact: 'Некоторые ответы могут не дойти до клиента или прийти с задержкой.', action: 'Откройте аналитику доставки: проверьте ответы канала, ошибки и время последних попыток. Перед повтором проверьте статус предыдущей отправки.' });
      if (text(payload.channel_name)) result.facts.push({ label: 'Канал', value: text(payload.channel_name) });
      fact('Попыток в коротком окне', payload.short_window_attempts); fact('Ошибок в коротком окне', payload.short_window_errors);
    } else if (type === 'alertmanager') {
      const payload = latest(incident, ['alertname']);
      const name = text(payload.alertname) || Object.keys(alerts).find(name => new RegExp('(?:^|[^A-Za-z0-9_])' + name + '(?:$|[^A-Za-z0-9_])').test(text(incident.title))) || '';
      const explanation = alerts[name];
      if (explanation) Object.assign(result, { known: true, title: explanation[0], summary: explanation[1], impact: explanation[2], action: explanation[3] });
      else Object.assign(result, { title: 'Получен сигнал внешнего мониторинга', summary: /[А-Яа-яЁё]/.test(original) ? original : 'Внешний мониторинг сообщил о проблеме. Для этого типа сигнала пока нет готового пояснения.', impact: 'Влияние на работу нужно уточнить по исходному уведомлению.', action: 'Откройте технические подробности и проверьте описание и правило срабатывания сигнала.' });
      const address = endpoint(object(payload.labels).instance);
      if (address) result.facts.push({ label: 'Адрес проверки', value: address });
    }
    result.historical = ['resolved', 'closed'].includes(text(incident.status).toLowerCase());
    result.listSummary = result.known ? result.summary.split(/(?<=[.!?])\s+/)[0] : result.summary;
    return result;
  }
  function transportDetails(raw, options = {}) {
    const overview = object(raw), inbound = object(overview.inbound), outbound = object(overview.outbound);
    const list = value => Array.isArray(value) ? value.map(object) : [];
    const tasks = list(overview.runtime_checkpoints).filter(item => text(options.workerKey) ? text(item.worker_key) === text(options.workerKey) : item.stale === true || item.lagging === true || ['stale', 'lagging'].includes(item.health_status)).map(item => {
      const label = { 'operator-notification-watch.chat-history': 'Уведомления операторам о новых сообщениях', 'operator-notification-watch.feedbacks': 'Уведомления операторам об оценках клиентов', 'ui-event-outbox-watch': 'Обновление данных на открытых страницах панели' }[text(item.worker_key)] || 'Служебная задача панели';
      const lag = number(item.cursor_lag), lagging = item.lagging === true || item.health_status === 'lagging';
      return { workerKey: text(item.worker_key), component: workerInfo[text(item.worker_key)]?.component || '', title: label, explanation: lagging ? 'Есть новые данные, до которых эта задача ещё не дошла.' : lag === 0 ? 'После последней обработанной записи более новых нет. Давно не менялась только служебная запись о ходе работы; задержка данных этим не подтверждается.' : 'Давно не менялась служебная запись о ходе работы. Данных недостаточно, чтобы сказать, остались ли необработанные записи.', updatedAt: text(item.updated_at), ageMinutes: number(item.age_minutes), limitMinutes: number(item.stale_threshold_minutes) };
    });
    return { inboundStale: number(inbound.stale_processing), outboundStale: number(outbound.stale_processing), outboundBacklog: number(outbound.queued), tasks };
  }
  return { describe, transportDetails };
});
