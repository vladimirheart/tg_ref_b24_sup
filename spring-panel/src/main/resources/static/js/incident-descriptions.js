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
  const credentials = {
    source_removed: ['Настройка с ключом доступа больше не найдена', 'Источник ключа отсутствует в рабочей конфигурации.', 'Проверьте, было ли удаление интеграции запланировано, и восстановите настройку, если она ещё нужна.'],
    missing_secret: ['Не найдено значение ключа доступа', 'Настройка интеграции есть, но ключ пустой, не сохранён или недоступен приложению.', 'Откройте соответствующую настройку и проверьте сохранение ключа, не публикуя его значение.'],
    expired: ['Истёк срок действия ключа доступа', 'Сохранённый срок действия ключа уже истёк.', 'Перевыпустите ключ в системе-источнике, сохраните его в настройках и проверьте интеграцию.'],
    expires_soon: ['Срок действия ключа скоро закончится', 'До окончания срока действия ключа осталось критически мало времени.', 'Подготовьте новый ключ и замените его до истечения срока.'],
    rotation_overdue: ['Просрочена плановая замена ключа доступа', 'Наступила сохранённая дата обязательной замены ключа.', 'Согласуйте замену с владельцем интеграции, выполните её и обновите дату ротации.'],
    rotation_due_soon: ['Подходит срок плановой замены ключа доступа', 'Близка сохранённая дата обязательной замены ключа.', 'Согласуйте с владельцем интеграции замену ключа до установленного срока.']
  };
  function describe(input) {
    const incident = object(input), type = text(incident.signal_type).toLowerCase(), key = text(incident.signal_key);
    const original = text(incident.summary) || text(incident.description).split(/\r?\n/)[0] || text(incident.title);
    const result = { title: text(incident.title) || 'Инцидент без названия', summary: original || 'Описание проблемы пока не заполнено.', impact: '', action: '', facts: [], known: false };
    const fact = (label, value, suffix = '') => { const n = number(value); if (n !== null) result.facts.push({ label, value: display(n) + suffix }); };
    if (type === 'integration_transport') {
      const payload = latest(incident, ['worker_key', 'stale_checkpoint_count', 'inbound_failed', 'latest_summary']);
      if (key.startsWith('panel-runtime-checkpoints/')) {
        const worker = workers[key.slice('panel-runtime-checkpoints/'.length)] || ['Фоновая обработка выполняется с задержкой', 'Связанные с обработчиком изменения могут поступать в панель позже обычного.'];
        const stale = /checkpoint stale|checkpoint.*TTL/i.test(original) || number(payload.age_minutes) !== null && number(payload.stale_threshold_minutes) !== null && Number(payload.age_minutes) > Number(payload.stale_threshold_minutes);
        Object.assign(result, { known: true, title: worker[0], summary: stale ? 'Фоновый обработчик давно не подтверждал свою работу. Мониторинг не видит свежей отметки выполнения.' : 'Обработчик не успевает за новыми записями или несколько проверок подряд показывает задержку.', impact: worker[1], action: 'Проверьте журнал и последнюю отметку работы обработчика в мониторинге транспорта. Доставка сообщений и работа ботов проверяются отдельно.' });
        fact('С последней отметки работы', payload.age_minutes, ' мин'); fact('Допустимый перерыв', payload.stale_threshold_minutes, ' мин'); fact('Проверок с проблемой подряд', payload.unhealthy_streak);
      } else if (key === 'panel-runtime-checkpoints') {
        Object.assign(result, { known: true, title: 'Фоновые обработчики давно не подтверждали работу', summary: 'Один или несколько обработчиков панели не обновили отметку выполнения в ожидаемый срок.', impact: 'Уведомления и изменения в интерфейсе могут поступать с задержкой. Это не доказывает остановку ботов.', action: 'Откройте мониторинг транспорта и проверьте, у каких обработчиков устарела последняя отметка работы.' });
        fact('Обработчиков без свежей отметки', payload.stale_checkpoint_count); fact('Обработчиков с отставанием', payload.lagging_checkpoint_count);
      } else if (['panel-rabbitmq-bridge', 'panel-transport-sustained-pressure'].includes(key)) {
        const values = { ...counters(key === 'panel-transport-sustained-pressure' ? payload.latest_summary : incident.summary), ...payload };
        const parts = [];
        for (const [names, phrase, label] of [
          [['inbound_failed'], 'Часть входящих событий завершилась ошибкой.', 'Ошибок входящей обработки'],
          [['inbound_stale', 'inbound_stale_processing'], 'Входящие события слишком долго находятся в обработке.', 'Задержавшихся входящих событий'],
          [['outbound_failed'], 'Часть исходящих событий не удалось отправить.', 'Ошибок исходящей обработки'],
          [['outbound_stale', 'outbound_stale_processing'], 'Исходящие события слишком долго находятся в обработке.', 'Задержавшихся исходящих событий'],
          [['outbound_backlog'], 'В очереди накопились события, ожидающие отправки.', 'Событий в очереди на отправку'],
          [['stale_checkpoints'], 'Нет свежих отметок работы фоновых обработчиков.', 'Обработчиков без свежей отметки'],
          [['lagging_checkpoints'], 'Фоновые обработчики отстают от новых записей.', 'Обработчиков с отставанием']
        ]) { const count = names.map(name => number(values[name])).find(n => n !== null); if (count > 0) { parts.push(phrase); fact(label, count); } }
        Object.assign(result, { known: true, title: key === 'panel-transport-sustained-pressure' ? 'Задержки обработки событий повторяются' : 'Ошибки и задержки обработки событий', summary: parts.join(' ') || 'Проверка зафиксировала проблемы с очередью событий или фоновыми обработчиками; подробные счётчики в этом сигнале не сохранены.', impact: 'Некоторые события интеграций и обновления панели могут обрабатываться позже обычного.', action: 'Откройте мониторинг транспорта: проверьте ошибки, очередь и последние отметки работы обработчиков. Перед повтором события убедитесь, что оно не было выполнено.' });
        fact('Проверок с проблемой подряд', payload.unhealthy_streak);
      }
    } else if (type === 'credential_rotation') {
      const data = { ...latest(incident, ['incident_reason', 'last_status']), ...object(incident.signal_context) };
      const explanation = credentials[text(data.last_status).toLowerCase()];
      Object.assign(result, { known: true, title: explanation?.[0] || 'Нужно проверить ключ доступа интеграции', summary: explanation?.[1] || text(data.reason) || text(data.incident_reason) || original || 'Контроль ключей доступа сообщил о проблеме; точное состояние не сохранено.', impact: 'Интеграция может потерять доступ после истечения или отзыва ключа. Плановая дата замены сама по себе не доказывает отказ интеграции.', action: explanation?.[2] || text(data.next_action) || text(data.incident_next_action) || 'Откройте аналитику ключей доступа и проверьте срок действия, источник настройки и владельца.' });
      if (text(data.display_name)) result.facts.push({ label: 'Настройка', value: text(data.display_name) });
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
  return { describe };
});
