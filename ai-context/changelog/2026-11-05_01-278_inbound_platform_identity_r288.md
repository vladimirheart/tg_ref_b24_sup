# 01-278 / R288 — платформа входящего сообщения

Промт пользователя:

> давай дальше

Дата:2026-11-05, Europe/Moscow +03.

## Source R288 — 2026-11-05: платформа входящего сообщения и canonical channel

InboundClientMessageIngestionService до ticket advisory lock, чтения обращения,
media verifier и domain writes требует exact event.platform=channel.platform
из telegram|vk|max. NULL/empty/unknown/case/whitespace и чужая платформа дают
явный отказ; нет default, trim или автоматического reroute. Это общий gate для
текста, Telegram single media и VK/MAX selected batch, включая прежние версии
вопросника и BUSINESS/GENERAL. Поздняя частичная MAX-only проверка удалена.
Существующие owner/channel/media checks сохранены; отказ использует прежний
failed inbox path, без history/profile/activity/metadata и processed marker.
Уже обработанный inbox event остаётся no-op без повторного чтения channel.

В detached sandbox fresh JVM 410 tests/12 suites GREEN, failures/errors/skips0,
95 новых. Baseline RED:95 tests/57 ожидаемых failures и38 positive controls.
Тот же test source, обе test classes и все95 case names после RED неизменны.
Покрыты все6 cross-platform pairs для текста/вложений, malformed identities,
matching text для NULL/v1/v2/v3/v4, оба scope, verifier dispatch, raw Unicode ID,
positive BIGINT выше2^53/Long.MAX_VALUE и явный повтор того же failed envelope
после исправления canonical channel. Репозитории/inbox/byte verifier замоканы;
это не real PostgreSQL/Rabbit/Redis или транзакционная/concurrent acceptance.

Baseline HEAD/origin/main/actual remote4a026df59 (R287), parent6113a034b;
9 previous changed paths/blob content и4096 tracked files проверены. Между
RED/GREEN изменяется только InboundClientMessageIngestionService.class;
остальные2346 panel classes неизменны. Full baseline compile:741 main/691 test
Java sources под JDK17, Sass; generated CSS сохранены вне scope без изменения.
Root apply отделён от staging/commit/push; ручной guarded local commit operator
в .tmp/r288-commit.ps1 не делает push. Evidence: .tmp/r288-baseline-red.json,
r288-java-green.json, r288-validation.json, r288-result.json, r288-final.json.

Это source-only backend fix, без deployment/миграций/flags/session JSON.
Рабочий проект/Docker находятся на другой машине и в другой сессии; их state
здесь не измерялся. Будущий выпуск требует согласованной backend версии в обеих
ролях panel-web/ops-worker, а не только web; runtime release отдельно.
R287:38 PG/Rabbit cases всё ещё PREPARED/PENDING_DOCKER, не новые passed tests.
Gate проверяет загруженный channel snapshot, не fresh DB после concurrent
platform rebind и не отдельный child transport principal. Полные rebind/kill,
provider acceptance, остальные policies и первый production S6 остаются.
Исторические runtime checkpoints/rollback/private archive сохранены, задача🟡,
S2B снятR190; автоматического исправления данных/requeue не добавлено.

Scope6: ingestion service,95-case test,task/S0/BOT и этот append-only changelog.
Коммит/пуш не выполнены; push остаётся пользовательским действием.

Изменённые файлы:

- `ai-context/changelog/2026-11-05_01-278_inbound_platform_identity_r288.md`
- `ai-context/rules/03-multibusiness-s0-contract.md`
- `ai-context/tasks/task-details/01-278.md`
- `docs/BOT_RUNTIME_CONTRACT.md`
- `spring-panel/src/main/java/com/example/panel/service/integration/InboundClientMessageIngestionService.java`
- `spring-panel/src/test/java/com/example/panel/service/integration/InboundClientPlatformIdentityTest.java`
