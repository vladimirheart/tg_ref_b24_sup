# Аудит Node checklist и ошибок мессенджеров — 01-331

Дата: 27.11.2026, МСК. Baseline: `6658c0e242e8554c84e392da1d9d518ecd66a969`.
Источник: пользовательский `Downloads/Telegram Desktop/node-regression-checklist.md`.
Проверен именно этот commit; сведения документа о другой машине не использованы как
доказательство состояния Docker в текущей сессии.

## Вердикт по семи пунктам

Исходный результат подтверждён: 197 записей Node, 190 успешных, семь падений.
Некоторые записи запускают файл с несколькими самостоятельными сценариями.
Семь падений сами по себе не доказывали семь неисправностей рабочего приложения.

| Пункт / тест | Подтверждение и результат |
| --- | --- |
| 1. `dialog-ai-query-requests-r54` | Устаревшая дата JS и ожидание общей переменной версий. Реальные AI URL/payload корректны; подключение существующего ресурса и порядок проверяются без привязки к исторической дате. |
| 2. `dialog-details-query-api-r50` | Устаревшая дата ресурса. Builder и вызов сохраняют точный ticketId, канал и буквальный `+`. |
| 3. `dialog-query-navigation-r48` | Старая source assertion больше не соответствует серверному `dialogUrl`. PostgreSQL + настоящий Thymeleaf + Edge подтвердили BUSINESS query URL и GENERAL public-ID URL. Неоднозначная identity не получает ссылку. Дополнительно обнаружена и исправлена реальная гонка истории A/B. |
| 4. `dialog-read-query-requests-r53` | Неполная DOM-заглушка: нет `replaceChildren`. Дополнена с сохранением assertions; настоящий DOM подтверждает пустые/непустые заметки, поиск, очистку, HTML-безопасность, 401/403/500, network и смену диалога. Настоящий session helper при 401 переводит на login. |
| 5. `dialogs-secondary-exact-legacy-ticket-id-r29` | Старая source assertion относится к прежнему workflow. Проверены нынешние BUSINESS owner/revision/publicId и отдельный UNRESOLVED workflow. Все 11 исходных случаев выполняются. Edge дополнительно проверил CSRF, BIGINT как строку, отказ прав, conflict, закрытие модалки и двойной submit. |
| 6. `incident-descriptions-r13` | Устаревшая дата workbench. Каталог описаний и runtime загружаются в нужном порядке; даты разных ресурсов не обязаны совпадать. Проверки содержимого сохранены. |
| 7. `incidents-transport-exact-ticket-id-r34` | У boot fixture отсутствовал `window.location.search`. После исправления выполняются все 36 случаев. Edge проверил valid/invalid deep link и inspect/replay/requeue исключительно на локальном fixture API. |

ID не обрезаются перед передачей. Сохраняются пробелы, табуляция, кириллица,
emoji, `/ ? & % #`, кавычки и строковое значение `' null '`.
Legacy ticketId, publicId и числовой businessId остаются разными контрактами.
GENERAL в карточке клиента имеет прямую ссылку в свой workspace; прежний BUSINESS
history preview ему не навязывается.

## Реальные исправления

- История клиента теперь проверяет номер запроса и текущий ticketId: поздний ответ
  A не подменяет B. Ошибка отображается с HTML escaping.
- Общий браузерный `PanelErrors` читает body один раз, разбирает JSON/HTML,
  логические ошибки при HTTP 200, сетевые сбои, 413/429/502/503/504.
  Его используют отправка текста/файлов, внутренняя переписка, настройки/проверка
  ботов и диагностика transport.
- Сообщение содержит источник, понятную причину, доступный код и безопасный
  request ID; для rate limit учитывается Retry-After. URL/ключи/токены скрываются.
- Подтверждённая отправка не становится «не отправлено» из-за последующего сбоя
  обновления UI. Неоднозначная доставка помечается «Доставка не подтверждена»;
  предлагается проверить историю. Автоматического повтора после такого сбоя нет.
- Единый Java parser учитывает Telegram `ok/error_code/description/parameters`,
  VK `error.error_code/error_msg` и MAX `code/message`. Невалидный HTTP 200
  больше не считается успехом. Сохраняются реальные причины и платформа.
- MAX `attachment.not.ready` классифицируется до общего HTTP 400: это обработка
  вложения, а не окончательный отказ. Явный отказ API отличается от обрыва связи.
  Неоднозначный сбой шлюза/медиа больше не вызывает скрытую отправку через другой
  Telegram endpoint или MAX host. Существующие credential/scope guards сохранены.
- Для входящих вложений есть безопасные коды размера, timeout, доступа,
  недоступного файла и сохранения. MAX сохраняет реальный HTTP статус скачивания.
  VK/MAX callback получает читаемый JSON 503, сохраняя возможность повторной
  доставки события вместо ложного подтверждения. Telegram сообщает клиенту
  причину непринятого вложения; ошибка cleanup уже принятого файла не просит
  отправить его повторно. Состояния и claims не сбрасываются.

## Ограничения размера и прокси

Фактический Nginx текущей машины: **25 МиБ на весь HTTP body**, включая multipart.
Spring: **50 МиБ на файл / 60 МиБ на запрос**. Это разные ограничения; лимиты
в рамках задачи не увеличивались. Для загрузки через Nginx сначала действует 25 МиБ.
Для защищённых вложений анкеты отдельный действующий предел: 1 байт — 10 МиБ.

Nginx выдаёт JSON для собственных 413/502/503/504 с `errorSource=nginx` и
`requestId`. Ответы приложения сохраняются благодаря `proxy_intercept_errors off`.
Spring сообщает bound multipart limits; JSON ошибки POST attachments больше
не подавляются правилом для бинарных GET/HEAD. SSE остаётся без JSON body.

Число в сообщении показывается только когда оно известно из действующей
конфигурации/контракта. Ошибка мессенджера «слишком большой файл» не получает
выдуманный универсальный предел. Если мессенджер отклонил пользовательскую
загрузку ещё до передачи update боту, приложение этого события не получает.

## Проверки и воспроизведение

- Полный Node: **210/210**, без skips; исходные 36 transport и 11 secondary случаев
  выполняются, а не обходятся.
- Целевые Java панели: **456/456**, 12 suites, без skips; PostgreSQL fixtures,
  credential boundaries, persisted state, provider parser и exception advice.
- Боты: **446/446**, без skips, reactor package core/Telegram/VK/MAX.
- Edge Chromium: четыре группы в `tools/error-audit-qa.cjs`; реальный
  Thymeleaf profile и production JS, остальные компоненты на markup из шаблонов.
  Данные API синтетические, все запросы loopback; uncaught errors = 0.
- Изолированный настоящий Nginx 1.30.7: собственный 413 до upstream, сохранённый
  application 413, 504 и ровно один POST, собственный 502. Для быстрого 504 только
  fixture read timeout сокращён с 60 до 1 секунды. Рабочий timeout не менялся.
- Panel/child JAR: Java 17, Bootstrap SRI проверены в собранном архиве.

```powershell
node --test spring-panel/src/test/js/*.test.js
# Java: repository mvnw.cmd; целевой список/nonce-owned PostgreSQL описаны в task 01-331.
# После ErrorAuditRenderTest создаётся target/error-audit-qa/client-profile.html:
node tools/error-audit-qa.cjs
```

Для browser harness задаются `PLAYWRIGHT_MODULE` и `CHROMIUM_PATH` либо используются
установленный playwright и его Chromium. Артефакты текущего прогона:
`C:/1C/backup/deploy-feedback-4ca73f33b-20261119/error-audit-6658c0e-r331-20261127`
(`node-baseline.log`, `node-final.log`, `java.log`, `bots.log`,
`browser-report.json`, `nginx-fixture-report.json`). Полный набор Java проекта
не запускался; результаты не означают ручную приёмку реальных мессенджеров.

## Контракты внешних систем

Форматы ошибок проверены по первичным источникам:
[Telegram Bot API](https://core.telegram.org/bots/api#making-requests),
[официальная схема VK](https://github.com/VKCOM/vk-api-schema/blob/master/errors.json),
[MAX messages](https://dev.max.ru/docs-api/methods/POST/messages).
Прокси-поведение:
[Nginx proxy_intercept_errors / proxy_next_upstream](https://nginx.org/en/docs/http/ngx_http_proxy_module.html),
[Nginx client_max_body_size](https://nginx.org/en/docs/http/ngx_http_core_module.html#client_max_body_size).

## Применение

Sandbox GREEN, source применён в основной checkout; stage/commit/push не выполнялись.
В этой сессии обновлены web/worker/bot-runner образом
`iguana-panel:error-audit-6658c0e-r331-20261127`
(`sha256:84d254295b6ed5f5ad524a6bd0ba1e216bc59409cee0a9fc04ec6a0e8f1f4028`).
Сервисы healthy; supervisor + три child JVM. Nginx validated reload; ошибки
запуска ERROR 0. Env/mounts, Flyway 109 и checksum fingerprint сохранены.
Миграций и backfill не было. Ручная приёмка мессенджеров остаётся за пользователем.

Прежние images, nginx config, dump и проверяемая конфигурация rollback сохранены
во внешнем каталоге артефактов. Тестовый PostgreSQL и worktree удаляются только
по их точным identity/nonce/content guards; runtime/rollback images сохраняются.
