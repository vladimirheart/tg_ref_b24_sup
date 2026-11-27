# Читаемые ошибки сообщений и загрузок

Действует с 01-331.

- Оператору показывать платформу/слой, понятную причину, безопасный код и request ID,
  если он доступен. Не выводить HTML ответа, URL с секретом, токен или request_params.
- HTTP 200 не доказывает успех API мессенджера. Проверять его envelope; для
  Telegram, VK и MAX использовать `MessagingErrorParser`, для fetch —
  `PanelErrors`. Body ответа читается один раз.
- Различать явное отклонение и неизвестный результат. Network/timeout/невалидный
  ответ после mutation не дают права утверждать, что сообщение не доставлено.
  Не запускать скрытый повтор через другой endpoint/host при неизвестном результате.
- Успешный provider ack сохраняет успех, даже если последующее обновление UI упало.
- Предел размера указывать только из действующего слоя: Nginx request body,
  Spring multipart, bounded questionnaire media или конкретный provider response.
  Не переносить один предел на все мессенджеры/виды вложений.
- Nginx собственные ошибки возвращает JSON; upstream ответы не перехватывает.
  Не добавлять `non_idempotent` к proxy retries. SSE и бинарные GET/HEAD не
  превращать в JSON при ошибке; ошибки POST upload должны быть читаемыми.
- Incoming failure не должен создавать ложный callback ack, сбрасывать claim,
  сдвигать questionnaire state или просить повтор уже принятого файла из-за cleanup.
- Сохранять owner/revision, exact IDs, scope и current credential checks при
  изменении отображения ошибок. Диагностические replay/requeue проверять на fixtures,
  а не на реальных клиентах.

Подробности: `docs/architecture/node-regression-audit-331.md`.
