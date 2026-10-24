# 2026-10-24 12:13 +03 —01-281 R14: конкретные операции за счётчиками

## Промт пользователя:

> уже понятнее, но не до конца. например:
> **Задержки обработки событий повторяются**
> По последней сохранённой проверке. Это описание наблюдаемой проблемы, не подтверждение её первопричины.
> Часть входящих событий завершилась ошибкой. Нет свежих отметок работы фоновых обработчиков. Фоновые обработчики отстают от новых записей.
> **На что влияет**
> Некоторые события интеграций и обновления панели могут обрабатываться позже обычного.
> **Что проверить**
> Откройте мониторинг транспорта: проверьте ошибки, очередь и последние отметки работы обработчиков. Перед повтором события убедитесь, что оно не было выполнено.
> Ошибок входящей обработки
> 4
> Обработчиков без свежей отметки
> 3
> Обработчиков с отставанием
> 1
> Проверок с проблемой подряд
> 10

## Фактические изменения

- Вместо одних абстрактных счётчиков объясняются входящие данные, задачи
  уведомлений/обновления экрана и повторные проверки;10 проверок не равны
  10 новым сбоям. Для aggregate transport открыт отдельный current details
  блок из уже существующего read-only analytics GET.
- Показаны операции, причины известных ошибок, обращения, даты и названия
  задач. Текущая выборка не подменяет historical snapshot; limit, stale
  refresh fallback, analytics availability и drafts учитываются. Кнопки
  только навигируют к существующим записям/задачам, не вызывают replay.
- Read-only проверено:4 failed client_message.active_ticket записи от24.08.2026,
  bad SQL grammar при записи chat_attachment_metadata. Объясняется ошибка
  сохранения сведений о вложении, без вывода о потере файла или текущем отказе БД.
  Записи не менялись/не удалялись/не повторялись.
- Изменены UI module/workbench/template/cache, rule06, wording/cache
  expectations R13; добавлены R14 unit fixtures/test/browser QA.
  Task details и docs/ui/incident-concrete-problems-r14.md содержат полный12-file scope.

## Проверки и runtime

- Fresh clean HEAD=origin/main=remote4218db92cc5321291f867981bfdfb31cb070fc4f.
  9 paths validated в sandbox, затем guarded-applied и content-сверены.
- 6 новых +22 прежних unit cases,42 legacy JS checks;3 Java tests/2suites
  без ошибок/пропусков.30 новых browser combinations и R11/R12/R13 regressions,
  всего90PNG; source audit74templates/47pages GREEN.
- Только panel-web: iguana-panel:incidents-r14-20261024, healthy,
  sha256:15e6544eab1f7a997ab2d11f97078032d264989f74eae68450f944e05313ee72.
  JAR355779d7a9a6766b0375365a01ae88dd4b7ea52ba1a5de82211d3ba8d2115db6;
  2176 entries, ровно3 UI changes, all other entry hashes прежние.
- Rollback-incidents-r14-web-20261024 → R13 483445cb… доступен, не исполнялся,
  прежние tags сохранены. Full env до/после и4 mounts совпали; HTTP/runtime
  SHA сверены, anonymous protected endpoints=302.
- Worker/bots container IDs/images/start и4 JVM PID/start неизменны;
  FlywayV81. Миграций/backfill/flags/policy/replay/requeue/ручных DB/external
  business writes нет; worker/bots/infra/db-migrate не перезапускались.
- Artifacts ignored: spring-panel/target/incidents-r14-evidence/ и
  incidents-r14-image/. Staged/committed/pushed=false.
  Рабочая authenticated приёмка владельца ещё требуется; 🟣.
