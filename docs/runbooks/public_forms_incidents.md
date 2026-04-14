# Runbook: внешние публичные формы (incident response)

## Когда использовать

Используйте этот runbook, если есть инциденты по публичным формам:
- форма недоступна (404/410/5xx);
- обращения не создаются или создаются дубли;
- подозрение на спам-флуд;
- клиент не видит историю диалога по `token`.

## Быстрая диагностика (5–10 минут)

1. **Проверить конфиг канала** в UI:
   - Настройки → Диалоги → Каналы → «Внешняя форма»;
   - `enabled`, `disabledStatus`, `captchaEnabled`, поля формы.

2. **Проверить системные runtime-настройки** (Настройки → Диалоги):
   - `public_form_rate_limit_enabled`;
   - `public_form_rate_limit_window_seconds`;
   - `public_form_rate_limit_max_requests`;
   - `public_form_rate_limit_use_fingerprint`;
   - `public_form_idempotency_ttl_seconds`;
   - `public_form_session_polling_enabled`;
   - `public_form_session_polling_interval_seconds`.

3. **Проверить метрики** через API панели:
   - `GET /api/dialogs/public-form-metrics`;
   - рост `submitErrors`, `captchaFailures`, `rateLimitRejections` = симптом деградации или атаки.

4. **Проверить аудит**:
   - в карточке тикета блок «Связанные события» должен содержать `public_form_submit`;
   - отсутствие событий при попытках submit обычно указывает на ошибку до создания тикета.

## Типовые симптомы и действия

### 1) Массовые дубли обращений

Признак: несколько тикетов с одинаковым текстом за короткое окно.

Действия:
1. Убедиться, что фронт отправляет `requestId`.
2. Проверить `public_form_idempotency_ttl_seconds` (рекомендуемо 180–600 сек).
3. При необходимости временно повысить rate-limit жёсткость:
   - уменьшить `public_form_rate_limit_max_requests`;
   - включить `public_form_rate_limit_use_fingerprint`.

### 2) Рост спама

Признак: резкий рост `rateLimitRejections`, мусорные payload.

Действия:
1. Включить CAPTCHA для канала (если выключена).
2. Проверить `public_form_captcha_shared_secret`.
3. Временно сократить окно лимита (`window_seconds`) и/или лимит запросов.
4. Проверить, что санитизация включена (`public_form_strip_html_tags=true`).

### 3) Клиент не видит историю после submit

Признак: тикет создан, но публичная страница пустая.

Действия:
1. Проверить наличие `token` в URL.
2. Убедиться, что `public_form_session_polling_enabled=true`.
3. Проверить `public_form_session_ttl_hours` (не истёк ли токен).
4. Проверить endpoint `GET /api/public/forms/{channel}/sessions/{token}` вручную.

### 4) Форма возвращает 404/410

Признак: публичная ссылка перестала работать.

Действия:
1. Проверить, не был ли выполнен `public-id/regenerate`.
2. Проверить `disabledStatus` и флаг `enabled` в конфиге канала.
3. Если это плановое отключение — уведомить владельцев канала.
4. Если нет — откатить на предыдущий `publicId` невозможно; требуется разослать новую ссылку.

## Эскалация

Эскалируйте в backend-команду, если:
- есть 5xx при `config`/`sessions` API;
- `public_form_submit` отсутствует при успешном HTTP 201;
- наблюдается деградация БД/очередей и потеря входящих обращений.

Минимальный пакет для эскалации:
- channel id/public id;
- диапазон времени (UTC);
- пример `ticketId`/`token`;
- snapshot `/api/dialogs/public-form-metrics`;
- скрин/лог ответа API.
