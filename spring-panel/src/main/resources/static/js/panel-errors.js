(() => {
  'use strict';
  function safe(value) {
    return String(value || '').replace(/https?:\/\/[^\s"'<>]+/gi, '[адрес скрыт]')
      .replace(/\b\d{5,14}:[A-Za-z0-9_-]{20,}\b/g, '[секрет скрыт]')
      .replace(/((?:access_token|token|password|secret|api[_-]?key|authorization)["']?\s*[:=]\s*)("[^"]*"|'[^']*'|(?:bearer\s+)?[^\s,;}&]+)/gi, '$1[скрыто]')
      .replace(/<[^>]*>/g, ' ').replace(/[\u0000-\u001f\u007f\u202a-\u202e\u2066-\u2069]/g, ' ')
      .replace(/\s+/g, ' ').trim().slice(0, 700);
  }
  function messageFrom(data) {
    if (!data || typeof data !== 'object') return '';
    const error = data.error;
    return safe(typeof error === 'string' ? error : error?.error_msg || error?.message
      || data.message || data.description || data.detail);
  }
  function create(response, data, context = {}, invalid = false) {
    const status = Number(response?.status) || 0;
    const source = safe(data?.errorSource || response?.headers?.get?.('X-Error-Source'));
    const retry = Number(data?.retryAfterSeconds || data?.parameters?.retry_after || response?.headers?.get?.('Retry-After'));
    const defaults = {
      400: 'Некорректный запрос. Проверьте введённые данные.',
      401: 'Сессия истекла. Войдите в панель заново.',
      403: 'Недостаточно прав для действия.',
      404: 'Объект не найден или доступ к нему закрыт. Обновите страницу.',
      408: 'Сервер не дождался загрузки запроса. Проверьте соединение.',
      409: 'Данные или права изменились. Обновите диалог.',
      413: 'Размер запроса превышает допустимый для сервера. Уменьшите файл или количество вложений.',
      415: 'Этот формат файла или запроса не поддерживается.',
      429: 'Превышена частота запросов.',
      500: 'Внутренняя ошибка панели.',
      502: 'Прокси не получил корректный ответ панели.',
      503: 'Панель временно недоступна.',
      504: 'Прокси не дождался ответа панели.',
    };
    let message = messageFrom(data) || (invalid && status >= 200 && status < 300
      ? 'Сервер вернул ответ, который не удалось прочитать.' : defaults[status] || 'Не удалось выполнить действие.');
    const label = {nginx:'Nginx',panel:'Панель',telegram:'Telegram',vk:'VK',max:'MAX',bot:'Бот'}[source.toLowerCase()];
    if (label && !/^(Nginx|Панель|Telegram|VK|MAX|Бот):/i.test(message)) message = label + ': ' + message;
    if (status === 429 && Number.isSafeInteger(retry) && retry > 0) message += ' Повтор возможен через ' + retry + ' сек.';
    const unknown = (context.sending === true || context.mutating === true) && (status >= 500 || status === 0 || status === 408
      || invalid && status >= 200 && status < 300 || /не подтвержден|не удалось подтвердить|неизвестен|таймаут|время ожидания|проверьте сеть/i.test(message));
    if (unknown && !/не подтвержден|не удалось подтвердить|неизвестен/i.test(message)) {
      message += context.sending ? ' Результат отправки не подтвержден. Проверьте историю диалога перед повторной отправкой.'
        : ' Результат действия не подтвержден. Проверьте карточку перед повтором.';
    }
    const requestId = String(data?.requestId || response?.headers?.get?.('X-Request-ID') || '');
    if (/^[a-zA-Z0-9_-]{1,96}$/.test(requestId)) message += ' Код запроса: ' + requestId + '.';
    const error = new Error(message);
    Object.assign(error, { isPanelError: true, httpStatus: status, source,
      code: safe(data?.errorCode || data?.code || (invalid ? 'INVALID_RESPONSE' : 'HTTP_' + status)),
      deliveryUnknown: unknown });
    return error;
  }
  async function read(response, context = {}) {
    window.DialogsAuthResponseRuntime?.ensureApiSession(response);
    if (response.status === 204 && response.ok) return null;
    let data = null, invalid = false;
    try {
      // A real Fetch response is consumed exactly once, including HTML proxy errors.
      data = typeof response.text === 'function' ? JSON.parse(await response.text()) : await response.json();
    } catch (_error) { invalid = true; }
    const failed = data?.success === false || data?.ok === false
      || data?.error && data?.success !== true;
    if (!response.ok || failed || invalid) throw create(response, data, context, invalid);
    return data;
  }
  function network(error, context = {}) {
    if (error?.isPanelError) return error;
    return create(null, { errorCode: error?.name === 'AbortError' || error?.name === 'TimeoutError' ? 'REQUEST_TIMEOUT' : 'NETWORK_ERROR',
      error: error?.name === 'AbortError' || error?.name === 'TimeoutError'
        ? 'Истекло время ожидания ответа сервера.' : 'Нет соединения с панелью. Проверьте сеть.' }, context);
  }
  window.PanelErrors = Object.freeze({ read, create, network, safe });
})();
