# 01-331 — Аудит Node regression и читаемые ошибки ботов

Время: 27.11.2026, 16:53 МСК. Baseline: 6658c0e242e8554c84e392da1d9d518ecd66a969.

## Промт пользователя

> тут появился док (C:\Users\SinicinVV\Downloads\Telegram Desktop\node-regression-checklist.md) с возможными проблемами в коде проекта. провери и подтверди или опровергни.
> да, и нужно возвращать корректно читаемые ошибки при работе с ботами, включая ошибки отправки или приёмки сообщений, например из-за допустимого объёма - как в ответе мессенджера, так и nginx. то есть в целом корректный парсер ошибок, чтобы оператор мог легко понять проблему.

## Изменения

- Проверены семь исходных падений; исправлены test assets/fixtures без удаления
  сценариев exact-ID, scope, методов и доступа. Отдельный отчёт по каждому пункту.
- Исправлена гонка истории клиента и безопасное отображение ошибки.
- Общие browser/Java parsers различают proxy, панель и Telegram/VK/MAX, сохраняют
  причину/код/Retry-After/request ID и скрывают секреты. Невалидный provider ack
  не считается успехом; неоднозначная доставка не запускает скрытый повтор.
- Улучшены ошибки входящих вложений и multipart, Nginx JSON 413/502/503/504;
  числовые лимиты берутся из действующего слоя. Существующие лимиты сохранены.
- Области: panel transport/exception advice, browser send/settings/history,
  child bot core/Telegram/VK/MAX, docker/nginx; тесты Java/Node, tools/error-audit-qa,
  docs/architecture/node-regression-audit-331, правило 15 и task 01-331.

## Проверка и выкладка

Node 210/210; panel Java 456/456; bots 446/446; skips 0. Четыре Edge-группы,
настоящий изолированный Nginx, Java 17 и Bootstrap SRI. Все API mutations
в QA — fixtures, без сообщений реальным клиентам.

После guarded sandbox → source apply обновлены web/worker/bot-runner:
`iguana-panel:error-audit-6658c0e-r331-20261127`,
image `sha256:84d254295b6ed5f5ad524a6bd0ba1e216bc59409cee0a9fc04ec6a0e8f1f4028`.
Healthy; три child JVM; Nginx validated reload. Runtime JAR hashes совпали.
Env/mounts/Flyway 109 неизменны, миграций/backfill/replay нет. Backup/старые images
и rollback config сохранены; реальный rollback не выполнялся.
Stage/commit/push не выполнялись. Статус задачи 🟣, ожидается ручная приёмка.
