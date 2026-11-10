# Bot Runtime Contract

Документ фиксирует текущий runtime contract между `spring-panel` и `java-bot`
после `Phase 5` рефакторинга.

## Source R314: scoped AI queue recovery

WORKER/LEASED UiEventOutboxWatcher выполняет отдельный recovery pass: каждые5s,
lease scoped-ai-reply-recovery30s, до25 QUEUED receipts, независимо от UI cursor.
Используются frozen full owner/event и прежний CAS→SENDING + final delivery proof.
SENDING/UNKNOWN не подбираются, lease expiry не разрешает resend. При global
pause QUEUED сохраняется; missing incoming event приводит к CANCELLED, не recapture.

SENT/CANCELLED/UNKNOWN атомарно публикуют scoped ai_reply_changed с state metadata.
Ошибки возвращаются в scoped panel handoff при актуальном допуске; UNKNOWN notice
требует сначала проверить доставку у провайдера. Никаких исходных текстов в
новом event, LLM/raw reply/group actions или новой Rabbit/provider wire schema.
V91 — только partial queue index; V90 не меняется. java-bot source прежний.

150 tests/5 suites GREEN;2 owned JVM crash probes и native PostgreSQL. Runtime
на другой машине/сессии, здесь не deployed. Для source R314 нужен новый worker
image и migrator91; для ещё не установленного R313 также его web/worker/V90
rollout. Прежний последний документированный R310/V89 checkpoint сохранён.

## Source R313: scoped reviewed auto-reply

Новый backend ingestion сохраняет public generation свежего текста и атомарно
публикует BUSINESS UI outbox event с incoming_history_id. Auto-reply watcher
требует этот typed child; NULL link старых событий не угадывается. Reply service
фиксирует полный owner/business/channel, rule/source versions, control/work
revision и destination fingerprint, затем повторяет proof на provider boundary.
QUEUED→SENDING→terminal; после неопределённого результата автоматического retry
нет. Ответ sender=ai_agent получает точный history owner/public/channel и scoped
ai_reply_changed event. Вложения/LLM/legacy raw reply не используются.

Отдельное reviewed правило создаёт business ADMIN/ALL_BUSINESSES ADMIN с page
right, не системный ROLE_ADMIN сам по себе. APPROVED source не включает отправку.
Режим auto_reply, AI enabled, лимиты и оба scoped блока обязательны. Начальные
анкеты/legacy события без typed link остаются handoff. R313 не меняет bot modules,
Rabbit wire/principal contract или рабочие child процессы.

254 targeted tests/6 suites GREEN; real PostgreSQL/HTTP/ORM, provider doubles.
Рабочее применение требует нового panel-web/ops-worker image и additive V90;
rollout flags не включались. Рабочий экземпляр — другая машина/сессия, последний
документированный runtime R310/V89 и Compose/rollback pointers сохраняются.
R311–R313 deployment здесь не подтверждён. Приёмка: [AI boundaries](runbooks/ai-data-boundaries.md).

## Source R312: метаданные истории и согласованный режим AI

Решение пользователя R312 от10 ноября2026: целевой режим BUSINESS AI —
«Автоответ клиенту по разрешённым правилам». В LLM разрешён только текст,
который оператор явно проверил перед отправкой, без вложений. Это не разрешение
передавать непроверенное входящее сообщение/всю переписку или media по одному
глобальному enabled flag. Review одного источника не подтверждает остальной
payload обращения. Продуктовый допуск auto-reply больше не PENDING; реализация
правил/контроля проверенного текста, scoped client delivery и рабочая активация
ещё впереди. R312 не включает LLM/auto-reply и не выполняет внешних запросов.

AI preview и final createMemory сверяют явно заданные history public ID/channel
с current canonical root; mismatch закрывает чтение и запись до LIMIT/INSERT.
NULL metadata сохраняет прежний ручной composite-owner контракт, без вывода
business/generation и без разрешения egress. 138 targeted tests/5 Surefire XML GREEN, failures/errors/skips=0;1 main/5 test compile
поверх проверенного cache1460 Java. R311 baseline67c3ae12c (10 файлов) проверен.
Рабочий проект на другой машине. Новых migrations/flags нет; R311/R312 ещё
source, нужен новый backend web/worker image. Runtime R310/rollback сохранены.
[Контракт и приёмка](runbooks/ai-data-boundaries.md).

## Source R311: ручная очистка AI-истории актуального BUSINESS обращения

PAGE_DIALOGS и текущий business ADMIN/ALL_BUSINESSES ADMIN получают отдельную
подтверждаемую очистку state/events/handoff refs и прежней control reason.
Control сохраняется с ai_disabled/auto_reply_blocked=true и новой версией;
остаётся один content-free audit. Чат, KNOWLEDGE/MEMORY и backups сохраняются.
CAS фиксирует timestamps и event boundary, текущие account/owner/CONFIGURE
проверяются в SQL; audit/state-CAS failure откатывает всё. Конкурентные новые
события сохраняются. Автоочистка и auto-reply не включены.

81 targeted tests/2 suites GREEN, failures/errors/skips=0; native PostgreSQL и полный dialog HTTP/Thymeleaf/CSRF, включая
настоящий concurrent state/event writer. R310 commit541b97edb проверен,13 файлов.
Рабочий экземпляр на другой машине/в другой сессии; текущий source patch не
deployed. Новых migrations/flags нет. Нужен новый backend image panel-web/ops-worker;
бинарники ботов и инфраструктура не менялись. Runtime R310, Compose/rollback
и внешнее evidence ниже сохранены. [Контракт и приёмка](runbooks/ai-data-boundaries.md).

## Runtime R310 — 2026-11-10: поиск и очистка MEMORY без обращения

R309 проверен в обновлённом репозитории: HEAD/origin/main/actual remote =
09718fa26026f02750ca9fd597683e7b9ce77303. Рабочий выпуск — эта committed база плюс
незакоммиченный R310 patch; отдельного committed R310 SHA нет.

Для бизнес-ADMIN/ALL_BUSINESSES ADMIN с PAGE_KNOWLEDGE_BASE добавлен каталог
«Материалы без обращения». Он находит MEMORY с утраченной точной canonical
связью, не показывая title/body/origin. Страница по25 записей, фильтры до LIMIT,
BIGINT IDs без округления. Подтверждённая очистка проверяет неизменного владельца,
актуальный аккаунт/CONFIGURE, ID/key/revision и отсутствие current origin в том
же SQL, что меняет title/body на заглушки, ARCHIVED,revision+1 и пишет аудит.
Восстановленная связь, stale/подменённая форма и повторная очистка отклоняются.
Чаты, scoped state/events, evidence и backups сохраняются; автоочистки нет.

395 tests/6 suites GREEN, failures/errors/skips0;37 новых service/HTTP cases.
Native PostgreSQL, реальные Spring Security/Thymeleaf/CSRF: матрица прав,
pagination, origin deletion/move/generation/collision, BIGINT>2^53,
restore-before-write, отзыв прав/блокировка/rename-reuse и audit rollback.
Проверены1459 исходников baseline cache,1461 main class payloads против
принятого R309 JAR и2390 неизменённых main/test class hashes; пересобраны
изменённые2 main/2 test Java. Maven resources/jar/repackage и image build GREEN,
1901 packaged payloads сверены. Это targeted compile, не новая полная
Java/Sass сборка и не полный repository suite.

Через Compose --no-deps пересозданы только panel-web и ops-worker, по1 replica.
Оба используют image b2dc2f0fb876, tag iguana-panel:work-r310-09718fa26-20261110.
JAR SHA256:06379e23e6eccb0d43695631be48858a7d2a7eb5160164b63e17f542ac0fe7fa.
Source-input manifest SHA256:aab7e8961121075aab1a55cfdb0e9f5ebd092a57dec16b432417cccddf00ddc4.
Health/readiness200UP, restart0, ERROR/FATAL/OOM0 с начала этих контейнеров.
18 остальных контейнеров и3 bot child PID/start ticks прежние; боты R295.
Схема V89/hash, ENV/HostConfig/mounts/.env и прежние flags сохранены;
migrations/backfill/provision/grants не выполнялись.

Актуальный Compose: ../iguana-runtime/tg_ref_b24_sup/releases/r310-ai-orphan-cleanup-20261110/private/release/compose.final.json.
Rollback tags rollback-r310-panel-web-20261110 и rollback-r310-ops-worker-20261110
сохраняют distinct R309 images ee124b5d137e и9201cfbf6d5a. Откат не разыгрывался.
5 реальных Edge gates GREEN: AI catalog/orphan GET/подтверждённый orphan POST404
при прежних выключенных flags, incidents/inbox200. Положительная очистка проверена
в изолированном HTTP; рабочие AI-данные не удалялись, LLM/provider/auto-reply не запускались.
Это установленный код, не включение AI UI и не общая S6. Staging/commit/push нет.

Evidence вне .tmp: ../iguana-runtime/tg_ref_b24_sup/releases/r310-ai-orphan-cleanup-20261110/
(validation-v2/proof.json, bytecode-proof.json, artifact-proof.json, image-proof.json,
deployment-proof.json, live-gates-proof.json, runtime-final-proof.json).
Практическая инструкция: [AI boundaries](runbooks/ai-data-boundaries.md).
Общая задача🟡. Ниже — исторические checkpoints; текущий остаток обновлён в карточке01-278.


## Runtime R309 — 2026-11-10: R306–R308 выпущены, инциденты проверены

На текущей машине рабочего экземпляра проверены clean HEAD/origin/main/remote
5dd39138a785fe430d078e626f8fb04bba89db18 и три новых commits R306–R308.
Backend пересобран и обновлён через Compose --no-deps, по1 web/worker replica,
healthy/restart0. БД V89, миграции/backfill/flags/principals не запускались
и не менялись.18 прочих контейнеров и3 bot child PID/start ticks сохранены;
боты остаются R295/image29f36986a0db. ENV/HostConfig/mounts прежние.

В ходе приёмки исправлены два реальных дефекта. Incident participants и route
outbox создавали /dialogs?incidentId=INC-..., скрытый строгим inbox R306:
новые уведомления теперь ведут на /incidents?incidentId=<numeric id>.
Workbench открывает точный строковый ID, включая объект вне первой страницы;
обновление списка не возвращает пользователя к прежней ссылке. JS version
обновлён для существующих browser caches. Старые notices не переклассифицировались.
Кроме того, блокировка пользователя передавала integer1/0 в PostgreSQL boolean:
теперь binding boolean; реальная блокировка/разблокировка и stale session
отказ проверены. Подсказка внешнего каталога соответствует общему доступу R306.

Текущий source — указанная база плюс незакоммиченный R309 patch, не отдельный
committed SHA. Worker image9201cfbf6d5a/core manifest
212eeb9ca990b7fb6f7713b02ed66769207b2cd34fca8f51b88ce8c6af63d222.
Web imageee124b5d137e/auth manifest
5b97c4a9c8038feb1448849ae6a47bf3578da5baad482879c3bab811e4404224;
от core отличается один AuthManagementApiController.class.
Текущий Compose web/worker: releases/r309-backend-activation-20261110/private/compose.auth.final.json
в ../iguana-runtime/tg_ref_b24_sup/. Предыдущие distinct R305 images сохранены
в rollback-r309-panel-web-20261110 и rollback-r309-ops-worker-20261110;
rollback-r309-web-core-20261110 указывает на core9201cfbf6d5a.
Откат этого выпуска не разыгрывался; старый R305 DB backup/recovery сохранён.

528 Java tests/21 suites GREEN:525 в20 наборах плюс3 registration/block cases;
полные749 main/710 test sources и Sass собраны.11 новых Node deep-link checks,
прежние create/shared-work UI regressions GREEN.23 real Edge checks GREEN:
обычная numeric сессия без page grants, создание внешнего объекта/инцидента из
UI, reload, реальный notice/SSE/deep link, личная отметка read, запрет bot/channel/
task scope и отзыв common доступа в уже открытой заблокированной сессии.
Monitoring READ/can_edit проверены live; его изменения — в isolated HTTP tests,
рабочие monitoring settings не менялись.2 своих инцидента/2 внешних объекта,
временные users/roles/уведомления удалены. Health/readiness200UP, acceptance
backend ERROR/fatal0. Staging/commit/push не выполнялись.

Код AI R307/R308 установлен; рабочий AI catalog по прежним rollout flags
возвращает404. Live erase/LLM/provider/auto-reply не запускались. AI erase,
business isolation, CAS и invalidation проверены на отдельной PostgreSQL и
реальном Spring HTTP; это не активация скрытого AI UI и не полная S6.
Evidence: ../iguana-runtime/tg_ref_b24_sup/releases/r309-backend-activation-20261110/
(test-gate.json, validation-auth/proof.json, image-auth-proof.json,
deployment-proof.json, deployment-auth-proof.json, live-ui-v5-proof.json,
runtime-final-proof.json). Общая задача🟡; следующие source checkpoints исторические.

## Source R308: AI хранится до ручного удаления

Решение пользователя: «Всё хранить до ручного удаления, без автоочистки».
В редакторе business-owned KNOWLEDGE/MEMORY есть подтверждаемая очистка title/body
с архивированием и новой revision. Действуют PAGE_KNOWLEDGE_BASE, CSRF, текущие
business CONFIGURE, frozen ID/version и атомарный metadata-аудит. Старые AI
кандидаты/handoff references больше не проходят; чужие источники, chat_history
и metadata/evidence не удаляются. Автоматическая очистка не добавлена.
Это не общий purge всех AI state/events или backup. [Контракт и приёмка](runbooks/ai-data-boundaries.md).

312 tests/6 suites GREEN, failures/errors/skips=0;2 main/3 test sources пересобраны, real PostgreSQL/HTTP/Thymeleaf.
R307 baseline3e894783e (12 файлов) committed/pushed пользователем. Новых
migrations/flags нет; backend source update ещё не является runtime выпуском.
Рабочий Docker/проект находится на другой машине и в другой сессии. Для R306–R308
нужен новый image panel-web/ops-worker с текущими Compose/ENV/mounts/rollback;
bot children и инфраструктура этим scope не менялись. Runtime R305/V89 ниже
остаётся последним документированным выпуском.

## Source R307: изоляция старой AI-цепочки

Legacy ticket AI больше не принимает BUSINESS/GENERAL raw ID. Foundation или
strictReadScope отключают этот вход целиком, включая operator feedback/learning,
control/review/suggestions и decision-trace/reclassify/retrieve-debug до media/
history/LLM. Совместимость без обоих флагов допускает только уникальный exact
UNRESOLVED root. Scoped incoming/handoff и curated business sources сохранены;
новый scoped LLM/auto-reply не включён. [Контракт и приёмка](runbooks/ai-data-boundaries.md).

282 tests/15 suites GREEN, failures/errors/skips0. R306 baseline9cf506526 committed/pushed,
проверено24 файла предыдущего commit; изменённые3 main/4 test sources пересобраны
поверх проверенного R306 cache. Новых migrations/flags нет; Docker/рабочий проект
на другой машине. Текущая native PostgreSQL — только nonce-owned test fixture.
R307 не deployed; обновление backend web/worker может включить R306 вместе с ним.
OperatorNotificationWatcher принадлежит роли WORKER, bot children не менялись.
Последний подтверждённый runtime R305 и его rollback pointers сохранены.

## Source R306: общий контур инцидентов, отдельный runtime

По решению «все всем, кроме распределения настроек бота» инциденты, их
monitoring/маршруты/внешний каталог и персональный inbox доступны всем текущим
действующим numeric panel accounts. Source-object READ, bot/channel administration
и распределение настроек бота сохраняют свои проверки. Общие incident/system
notices не теряются при strictReadScope; чужой inbox и private ticket/task/project
уведомления этим не открываются. См. [контракт](runbooks/common-system-access.md).

162 tests/17 suites GREEN в source/test session;127 baseline tests GREEN до правок.
8 прежних failures ChannelApiControllerWebMvcTest подтверждены отдельно на
неизменённом5b3a09013, полный suite не объявляется GREEN. Рабочий Docker/проект
на другой машине и в другой сессии; текущая проверка не является deployment.
Нативный PostgreSQL использован только как изолированная nonce-owned fixture.
Новых migrations/flags нет. R306 требует нового backend image для panel-web/
ops-worker; bot-runner, children, provider и инфраструктура не изменялись.
Исторический Runtime R305 ниже остаётся последним подтверждённым выпуском.

## Launcher Contract

- основной источник правды: `app.bots.*` в `spring-panel/src/main/resources/application.yml`;
- `app.bots.launch-mode` поддерживает `auto`, `jar`, `maven`;
- `auto` сначала пытается запустить явный или найденный executable `jar`, затем
  откатывается на `spring-boot:run` как dev fallback;
- `app.bots.executable-jars` задаёт явный contract `module -> jar path`;
- `app.bots.preferred-production-launcher` фиксирует рекомендуемый production launcher;
- `app.bots.recommended-artifact-directory` задаёт рекомендуемую директорию для runtime jar;
- для production предпочтителен explicit `jar` contract, а не `target` scan.

## Runtime Inputs

Обязательные cross-platform env keys для child runtime, который запускается `bot-runner` в production `rabbitmq` contour:

- `APP_DB_MODE=postgresql`
- `APP_INTEGRATION_TRANSPORT_MODE=rabbitmq`
- `APP_PANEL_INTERNAL_API_BASE_URL`
- `APP_PANEL_INTERNAL_API_TOKEN`
- `APP_BOT_CHANNEL_ID` — canonical positive ID записи канала панели, не внешний chat ID
- `APP_BOT_PLATFORM` — canonical `telegram|vk|max`
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `TELEGRAM_BOT_TOKEN`
- `TELEGRAM_BOT_USERNAME`
- `GROUP_CHAT_ID`
- `APP_BOT_LOG_PATH`
- `SPRING_PROFILES_ACTIVE`
- `JAVA_TOOL_OPTIONS`

`bot-runner` передаёт canonical PostgreSQL URL и ограниченный per-channel SQL login (R291/V86) через защищённую production configuration. Legacy SQLite business paths (`APP_DB_BOT*`, `SUPPORT_BOT_DATABASE_PATH`) не передаются дочерним процессам и не могут быть live fallback для ticket/channel/feedback/blacklist операций.

Business operations в `rabbitmq` режиме используют queue/internal panel API boundary там, где это требуется transport contract. Прямой JDBC доступ child ограничен тремя transport tables canonical PostgreSQL с FORCE RLS; business tables доступны через scoped API/ingestion backend. Это требует отдельного provision, см. `docs/runbooks/child-transport-isolation.md`.

`spring-panel` больше не имеет SQLite runtime mode: `APP_DB_MODE=sqlite` отклоняется до формирования child runtime contract. Legacy `APP_DB_BOT*` / `SUPPORT_BOT_DATABASE_PATH` могут встречаться только в historical/test evidence и не являются runtime inputs для production child process.

Platform-specific:

- `VK`: `VK_BOT_ENABLED`, `VK_BOT_TOKEN`, `VK_OPERATOR_CHAT_ID`,
  `VK_WEBHOOK_ENABLED`
- `MAX`: `MAX_BOT_ENABLED`, `MAX_BOT_TOKEN`, `MAX_CHANNEL_ID`,
  `MAX_SUPPORT_CHAT_ID`, `SERVER_PORT`, `SERVER_ADDRESS`,
  `SPRING_MAIN_WEB_APPLICATION_TYPE`

Опциональные env keys зависят от платформы и network route:

- `VK_GROUP_ID`, `VK_CONFIRMATION_TOKEN`, `VK_WEBHOOK_SECRET`
- `MAX_WEBHOOK_SECRET`
- `APP_NETWORK_*`, `HTTP_PROXY`, `HTTPS_PROXY`, `ALL_PROXY`

Для live `rabbitmq` transport contour обязательны ещё и backend boundary keys:

- `APP_INTEGRATION_TRANSPORT_MODE=rabbitmq`
- `APP_PANEL_INTERNAL_API_BASE_URL`
- `APP_PANEL_INTERNAL_API_TOKEN`

Если эти параметры не настроены, `java-bot` больше не должен молча откатываться
в local `JPA/SQLite` business-path для ticket/channel/feedback/blacklist
операций.

## Runtime R305 — 2026-11-10: общие работы активированы и проверены

В рабочем тестовом экземпляре включены R302–R305: отдельные допуски,
участники и владельцы задач/проектов/Kanban, полный доступ текущего системного
ROLE_ADMIN, журнал изменений доступа, typed уведомления с повторным READ
при чтении/доставке/SSE и trusted auto-close producer. Manager report,
dialog task history и incident task picker используют текущую видимость
до counts/LIMIT; task DTO скрывает incident summaries от обычных участников.
Полная самостоятельная incident/system policy этим не объявляется готовой.

V88/V89 реально применены: рабочая схема87→89, без backfill, начальных grants,
участников и автоматического ownership старых строк. Миграции извлечены из
собранного JAR, предварительно проверены восстановлением backup в отдельную
PostgreSQL. db-migrate и инфраструктура не пересоздавались; flags и bot principals
не менялись. При первом неудачном pre-migration validate backend восстановлен
на R299/V87; следующий исправленный выпуск успешно применил обе миграции.
Это проверенный возврат приложения до миграции, не проверка downgrade V89.

Фактическая база source — bd0d76982b8a8e078ffdde9f6b6833e14c451c89 плюс
незакоммиченный R305 patch; staging/commit/push не выполнялись.
panel-web: image0ccec24e7fd5, source manifest SHA256
045059A41608F89190ED4F3CDBF9C51619FD24B5F4F68CD465529ADBCF138C5C.
ops-worker: imagefe7a2c56160b, core manifest SHA256
1515545F1CF40B01FF8EE7C93D6C98984BAC4EE2488EEE8598DE84E8AD7AE023.
Последующее web-only исправление регистрации связывает registration_date
как OffsetDateTime вместо varchar; остальные backend classes совпадают с core.
Обе роли обновлены через Compose --no-deps, по1 replica/healthy/restart0,
с сохранением ENV/HostConfig/mounts. Боты остаются R295/6bc6ae23a/image29f36986a0db;
18 нетаргетных контейнеров и3 child PID/start ticks прежние.

454 Java tests/19 suites GREEN:452 одним validation-v4 и2 новых PostgreSQL
registration cases отдельно;15 Node flows GREEN. Выполнена полная Maven/Sass
сборка core; финальная web-упаковка включает отдельно скомпилированное
исправление регистрации. Реальный Edge/admin + отдельная обычная сессия:
22 проверки GREEN, включая UI save/reload/owner transfer/revoke, сохранённое
уведомление и настоящий SSE. Временные users/roles/tasks/projects/уведомления
удалены; исходные5 задач/2 проекта/14150 уведомлений сохранены,22 audit events
остались. Health/readiness HTTP200/UP. Ошибочный JSON comment request
проверочного скрипта исправлен на multipart; последующий прогон GREEN.
Отдельный WARN RMS license refresh claim loss не относится к shared-work
и не объявляется исправленным этим выпуском.

Актуальный Compose input для web/worker:
../iguana-runtime/tg_ref_b24_sup/releases/r305-shared-lifecycle-20261109/private/registration-v2/compose.final.json.
Исходный .env неизменен. Rollback images сохранены:
iguana-panel:rollback-r305-r299-20261109 и
iguana-panel:rollback-r305-web-core-20261110.
Backup БД до V88/V89 восстановлен и проверен отдельно; SHA256
ce3f65fec274bbfd783227661330fb5dec70f603ea5db2f99a403ced679dd805.
Восстановление рабочей БД с V89 не разыгрывалось; одно возвращение image
не отменяет миграции. Evidence вне .tmp:
../iguana-runtime/tg_ref_b24_sup/releases/r305-shared-lifecycle-20261109/
(validation-v4, validation-registration-v1, live-ui-v8-proof.json,
working-migration-v2-proof.json, registration-deployment-v2-proof.json,
runtime-final-proof.json). Это локальный выпуск, не полная S6/§7.1 приёмка.

Ниже сохранены исторические checkpoints; их прежние pending/runtime состояния
не заменяют фактический R305. Общая задача01-278 остаётся🟡.

## Source R304 — 2026-11-09: управление доступом из панели завершено в source

На страницах задач и проектов доступны общие допуски к модулю для системного
администратора; внутри карточки — состав, поиск текущих допущенных пользователей,
VIEWER/EDITOR, отзыв и подтверждаемая передача владельца. Результат каждой записи
повторно читается с сервера, включая потерю OWNER после передачи. Пользователю
доступны только разрешённые действия; ROLE_ADMIN находит разделы в навигации
также без PAGE_TASKS. Новые endpoints context/access/people возвращают текущие
capabilities и string BIGINT IDs; кандидаты видны только OWNER/системному admin.

Панель защищена от stale response другой карточки/поиска, повторного click,
XSS в именах и автоматического повтора mutation с неизвестным исходом. При
отказе roster очищается, запись блокируется, карточка закрывается и список
обновляется. CSRF обязателен. Create buttons включаются по текущим capabilities.

374 Java tests/11 suites +13 Node UI flows GREEN:372 одним validation-v1
(18 actual HTTP/JPA cases),2 actual Thymeleaf/navigation в ui-template-validation-v2,
13 UI save/reload/member/revoke/transfer/cancel/stale/error cases там же.
Это targeted compile на frozen R303 bytecode, не image build/browser/provider
приёмка. Все12 source inputs проверены перед применением от411b85067.

UI grants/members/owner больше не является нереализованным пунктом. Остаются
аудит доступа, late notification/SSE READ, прочие projections/background producers
и отдельные incident/system/AI/S6 остатки. Рабочая V87/backend R299/bots R295,
Compose R300, flags/principals прежние; новых migrations нет, V88 не активирована.
Source applied; staging/commit/push/deployment=false, task🟡. Evidence:
../iguana-runtime/tg_ref_b24_sup/releases/r304-work-access-ui-20261109/,
validation-v1/proof.json, ui-template-validation-v2/proof.json, final-suites.json.

## Source R303 — 2026-11-09: основные пути общих работ и полный доступ системного администратора

Исторический checkpoint R303: UI остаток ниже выполнен в source R304;
границы поздней доставки/аудита и рабочей активации сохраняются.

Уточнение пользователя: системный администратор имеет полные права на задачи,
проекты и стадии Kanban. Текущий ROLE_ADMIN получает создание, чтение, изменение,
удаление, управление составом и передачу владельца без PAGE_TASKS/PAGE_SETTINGS,
shared grants или участия. Это заменяет ограничение R302 для системного
администратора. Business ADMIN/ALL_BUSINESSES/portal role этого допуска не дают;
enabled/blocked, точные users.id/raw username и текущая роль проверяются заново.
Отозванная роль в сохранённой сессии не сохраняет полномочия. CSRF и целостность
данных обязательны также для администратора.

Обычному пользователю нужны текущие PAGE_TASKS, shared can_read и участие
VIEWER/EDITOR/OWNER; создание требует отдельный can_create_task/project.
Trusted create записывает numeric owner и OWNER вместе с root в одной транзакции,
игнорируя переданные клиентом creator/author. Подключены основные task/project
CRUD, list/count/pagination до LIMIT, server-rendered tasks, project links,
settings project options, Kanban и task analytics/CSV. Проект не выдаёт READ
задачи; недоступные связи и placements сохраняются при постороннем изменении.
Personal board использует PERSONAL_USER:<users.id>; администратор управляет
также чужой личной доской. OWNER либо администратор передаёт владение через
PUT /api/shared-work/{kind}/{id}/owner/{userId}; администратор восстанавливает
владельца старого unowned объекта, без вывода ownership из creator/lead.

Порядок cross-object writes защищён transaction advisory gate; board projections
имеют scope lock, task_seq выдаётся атомарно. Кандидаты уведомлений фильтруются
по текущему READ до записи. Остаются UI управления допусками/участниками/владельцем,
аудит доступа, повторный READ при поздней доставке и SSE, остальные related-object/
search/report/background producers, incident/system policy, AI privacy и S6.
Основные пути подключены; полная изоляция всех проекций ещё не объявляется.

369 tests/10 suites одним финальным validation-v7 GREEN, failures/errors/skips0:
246 GENERAL regressions,98 ACL cases,15 новых actual HTTP/JPA/security cases,
2 Flyway clean/upgrade и8 project/board/analytics regressions. Все20 изменённых
Java sources скомпилированы JDK17 поверх полного compiled baseline v2;
неизменённый bytecode проверен по hash. Это targeted compile, не новый полный
image build. Disposable PostgreSQL128MiB, bounded JVM, рабочая БД не использована.

Validated source перенесён в checkout от0207e5800; stage/commit/push/deployment=false.
Все20 existing container snapshots неизменны. Рабочая V87/backend R299/bots R295,
Compose R300 и flags/principals прежние; source V88 активирован только в test DB.
Новых migrations нет. Rebuild/рабочая V88 — отдельный выпуск после оставшегося
wiring и проверок; V86/V87/provision повторять не нужно. Task🟡, S2B снятR190.
Evidence: ../iguana-runtime/tg_ref_b24_sup/releases/r303-work-wiring-20261109/,
validation-v7/proof.json, source-inputs.json и final-suites.json.

## Source R302 — 2026-11-09: независимое ядро доступа общих работ

Исторический checkpoint: ограничение прав системного администратора и список
неподключённых путей ниже описывают R302; актуальное решение и wiring — в R303.

По решению R301 подготовлены V88, SharedWorkAccessService и HTTP API управления
общими допусками/участниками. Grant can_read и отдельные can_create_task/
can_create_project не наследуются из BUSINESS/GENERAL/ALL_BUSINESSES. Для объекта
дополнительно нужно участие VIEWER/EDITOR/OWNER; проектное участие не выдаёт
доступ к задачам. Numeric owner FK задаётся только доверенным creation writer,
регистрация OWNER требует внешней транзакции. Старые creator/lead не становятся
владельцами; V88 не классифицирует legacy rows и не выдаёт начальных grants.

Администрирование общих допусков требует текущих ROLE_ADMIN +PAGE_SETTINGS,
без автоматического READ объектов. Состав меняет только текущий OWNER; VIEWER
читает, EDITOR меняет объект, OWNER также управляет составом. Public member API
не удаляет и не передаёт OWNER. Account/grant/root/member locks удерживаются до
commit; после ожидания используется актуальный locked grant, включая отзыв
права создания. Current users.id/raw username, enabled/blocked/ambiguity checks
сохраняются; HTTP BIGINT IDs возвращаются строками, read responses no-store.

Выполнены100 новых PostgreSQL/HTTP/migration cases (98 access +2 полных Flyway
clean/upgrade V87→V88) и250 regression cases/3 suites. Итого350/5 suites GREEN,
failures/errors/skips0; новые случаи — final validation-v5, неизменённые
регрессии — GREEN XML validation-v2. RED5 HTTP fixture cases в общем v2 не
считаются успешными: отсутствовал EnableWebMvc, исправлено до final run.
Проверены CSRF, method security, повторный запрос после revoke, роли, raw IDs,
atomic creation rollback и реальные блокировки параллельного отзыва.

Это source foundation и control API. Старые TaskApiController/TaskQueryService/
ProjectService/TaskBoardService, settings/report/notification projections и UI
ещё не подключены к ядру; их изоляция не объявляется завершённой. Следующий пакет
должен подключить trusted create, filtered SQL/list/count/direct reads/writers,
project links и Kanban, затем UI и доставку с текущим допуском получателя.

Рабочий экземпляр не обновлялся: все20 existing containers full snapshots
неизменны, тестовый PostgreSQL удалён по owner/nonce guard. Source V88 применён
только в disposable test DB; рабочая V87, flags/principals/боты и Compose R300
прежние. Отдельная активация R302 до wiring и согласованной V88 не выполняется.
Evidence: ../iguana-runtime/tg_ref_b24_sup/releases/r302-shared-access-20261109/,
validation-v5/proof.json, final-suites.json. Task🟡; S6/§7.1 pending, S2B снятR190.

## Acceptance R301 — 2026-11-09: MAX PostgreSQL/Rabbit gate завершён

На текущей Windows-машине из baseline f54e88324993d8de2fcaf13efb94ae46579d394f
в detached source/test копии выполнены90 contracts и57 actual Docker IT:
42 MAX media +15 child transport principal. Все147 tests/7 XML GREEN,
failures/errors/skips0; PowerShell5.1 parser/SelfTest GREEN. Проверены BUSINESS/
GENERAL, signed HTTP admission, outbox→Rabbit→listener, sourceLink, quota,
rollback, committed-before-ACK replay и отказ при root drift. Это изолированный
PG/Rabbit gate, без отправки реальному provider и без рабочей БД.

Исправлены только тесты и runner: docker-java ExposedPorts/Volumes {} markers
сериализуются локальным inspection mapper; HTTP-мост декодирует query как servlet,
сохраняя exact padded/Unicode root и исходную HMAC query. Добавлены6 typed-inspect
и9 query regressions. Для duplicate-root ожидается точный R291 principal reject
до inbox, а не старый IllegalStateException/failed inbox; проверки отсутствия
history/metadata сохранены и добавлена неизменность active profile. Другие40
MAX cases и production code не менялись. Runner:90 contracts, Phase=R301,
42+15 IT; имя r292-proof.json у штатного runner сохранено.

Прерванный validation-v3 остановился при недоступном Docker, итогового proof нет.
Docker Desktop восстановлен:15 ранее работавших containers автоматически
перезапущены, bot-runner имеет restart1. Это recovery, не новый release.
Все20 прежних ID/images и значения конфигурации сохранены; различия порядка
Binds/LogConfig сверены по значениям с R300 snapshot. Новый final validation-v10
строго проверил20 полных snapshots до/после: ExistingContainersUnchanged=true.
1444 baseline backend classes и6 test-source inputs hash-exact.
Validation-v9 отдельно сохранил40/42 PASS и2 устаревших exception assertions;
этот RED не выдаётся за завершённую приёмку.

После recovery повторены61 live login/access/SSE/readiness checks: GREEN,
health200/UP, production-readiness200/ready, DLQ0. Backend остаётся R299/e661588b5,
image348066da11f5; bot-runner R295/6bc6ae23a/image29f36986a0db. Compose pointer
R300 прежний. Пересборка/Compose recreate/deploy/migrations/provision/backfill/
flags и provider sends не выполнялись; для test-only исправлений rebuild не нужен.
Evidence вне .tmp: ../iguana-runtime/tg_ref_b24_sup/releases/r301-acceptance-20261109/
validation-v10/r301-proof.json, final-suites.json и recovery-access-result.json.

Пользователь выбрал общие межбизнесовые manual tasks/projects/Kanban с отдельными
правами и участниками. Решение внесено в S0; его ACL/API/query/UI implementation
ещё предстоит. Остались live provider/cross-platform приёмка, AI privacy/delivery,
incident/system policy и отдельный S6/§7.1. Общая задача🟡; S2B снятR190.

## Runtime R300 — 2026-11-09: backend R299 с исправлением deadlock выпущен

На текущей Windows-машине рабочего тестового экземпляра из clean
HEAD/origin/main/actual remote e661588b50b6714e5e47977f4e1ef56e4d3623a6 (R299)
полностью пересобран backend/Sass. Image iguana-panel:account-r299-e661588b5-20261109,
sha256:348066da11f5226c78a1e490a50f5328f0ebe32199606f63a519effc31033ab7.
746 main/705 test sources и98 Sass files compiled;313 targeted tests/8 suites,
failures/errors/skips0. Включены66 actual PG concurrency/rebind/quota/replay cases
Telegram/VK/MAX × BUSINESS/GENERAL и13 PG/V87/Hibernate history cases.
Тестовая PostgreSQL изолирована network=none, без опубликованных портов,
с nonce guard; рабочая БД тестами не использовалась.

Все1444 backend class payloads exact с fresh targets; относительно R295 изменён
только SelectedTicketFollowupMediaService.class. Новый SQL lock присутствует,
V86/V87 bytes и3 inherited bot JAR SHA256 прежние. Новые bot artifacts не нужны.
Через Compose --no-deps обновлены только ops-worker и panel-web, по1 replica,
healthy/restart0. ENV, HostConfig/mount values и исходный .env сохранены.
Все18 прочих container IDs/images/start/restart, bot-runner и PID/start ticks
трёх children прежние. Инфраструктура и db-migrate не перезапускались.

Свежие61 panel access/13 signed API checks и3 child credential contracts GREEN;
12 running JAR hashes exact с соответствующими новым backend/прежним runner.
Health/readiness200UP, production-readiness200/ready, Rabbit healthy, DLQ0.
БД V87, principals, settings/accounts/grants и контрольные domain hashes прежние;
Flyway skip подтверждён для обеих обновлённых ролей. Migration/provision/backfill/
flags и ручной provider send не выполнялись. MAX v4 feature gate не включался.

Actual Compose input web/worker — releases/r300-e661588b5-20261109/private/
compose.final.json: полный snapshot R297 с заменой image/labels только2 ролей;
исходные .env/project directory сохранены. Bot-runner labels по-прежнему
указывают на R297 snapshot. Для дальнейших операций учитывать новый R300
snapshot, не возвращать web/worker на старый image из R297/R280 override.
Rollback aliases rollback-r300-{panel-web|ops-worker}-20261109-29f36986a0db
сохранены и проверены; restore не разыгрывался. Удалены только3 собственных
build/test containers; release/rollback images и application volumes сохранены.

Это выпуск исправления R299, не полный57-case gate:75 contracts и42 MAX +15
principal Docker IT этого runner здесь не запускались. Прежние15 Docker
principal cases R297 GREEN остаются отдельным фактом. Сквозная provider/session/
process/flags/rollback/S6/§7.1 приёмка и остальные блоки задачи ещё требуются.
Задача🟡, S2B снятR190. Evidence:
../iguana-runtime/tg_ref_b24_sup/releases/r300-e661588b5-20261109/.
Ниже Source R299/R298 и Runtime R297 — исторические checkpoints до R300.

## Source R299 — 2026-11-09: selected media без channel lock upgrade

На отдельной nonce-owned PostgreSQL16.15 воспроизведён настоящий deadlock:
два signed ingress в одном канале удерживают FOR SHARE от child principal,
а media verifier раньше повышал каждый channel lock до FOR UPDATE. Шесть
случаев Telegram/VK/MAX × BUSINESS/GENERAL на старом source завершились
PostgreSQL deadlock detected. В require() теперь FOR UPDATE OF ticket,active
и FOR SHARE OF channel. Legacy-ID advisory lock и root/active locks сохраняют
quota/close serialization; channel SHARE по-прежнему запрещает route/active
mutation до commit, но допускает параллельные обращения одного канала.

GREEN:397 targeted JVM tests/13 suites,0 failures/errors/skips,72 новых.
Из них66 новых actual PostgreSQL cases:6 parallel roots,6 last-slot quota,
36 stale-admission DB drift/reconstructed writer,12 channel mutation during
byte read/commit и6 reconstructed-service replay. Ещё13 прежних V87/Hibernate
cases на том же отдельном test cluster, итого79 actual SQL cases. Signed
principal/listener/ingestion, storage bytes, metadata/inbox и transactions —
production beans; repositories используют narrow JDBC adapters. Это не полный
Boot/JPA/broker/Redis/SDK или process/fleet restart proof.

Исправлен также воспроизведённый ClassCastException JDBC varargs в MAX IT
channel/active adapters. Их actual factory methods покрыты6 новыми contracts;
runner теперь требует75 contracts, Phase=R299 (имя r292-proof.json сохранено).
MAX42 и principal15 матрицы/assertions прежние. Пользователь09 ноября подтвердил,
что Docker runner ещё не запускался; полный57 gate и provider/S6 остаются.

Из production source изменён только backend verifier. Рабочий экземпляр
на другой машине/в другой сессии остаётся на Runtime R297/sourceR295: исправление
туда НЕ выпущено. Нужен отдельный выпуск backend, включая inbound worker;
новые child JAR/DTO/API/migrations/provision/flags не требуются. Acceptance runner
проверяет новый source отдельно и сам рабочий runtime не обновляет. Задача🟡.

## Source R298 — 2026-11-06: запуск MAX acceptance на Docker 29

Исправлена тестовая инфраструктура, остановившая 42 MAX PG/Rabbit cases в R297.
PostgreSQL/Rabbit остаются только в nonce-owned internal bridge, без published
ports. Отдельный непривилегированный Socat relay соединён с этой сетью и с
собственным ingress bridge; он пересылает только два фиксированных TCP target.
Host bindings — только 127.0.0.1 и случайные нестандартные порты. У relay
read-only root, capabilities=ALL dropped и ip_forward=0. Перед JDBC/AMQP
проверяются actual Docker inspect: labels/internal/driver, точные memberships,
отсутствие service mappings и точные loopback bindings relay. External DB/AMQP
overrides, Compose и working credentials не используются.

Runner pin API1.44 для Testcontainers1.19.7/Docker29, выполняет MAX42 и principal15
раздельно и сохраняет partial counts, первый failure и failed/startup XML.
Cleanup допускает только свои owner+nonce containers/networks с точными
image/role/isolation guards. Старое имя r292-proof.json сохранено для совместимости;
Phase=R298. Успех требует все57 IT без failures/errors/skips и неизменные прежние
containers; ValidateOnly не закрывает gate.

Локально GREEN: полный main compile backend/core и test compile acceptance,
69 tests/3 suites (26 прежних chain +8 wire +35 новых fixture topology/inspect),
0 failures/errors/skips. PowerShell5.1 parser/SelfTest GREEN: сетевые cleanup
guards и4 real matrix-control-flow cases с fake Maven, включая оба failure.
MaxFollowupMediaChainIT42 и ChildTransportPrincipalIT15 не изменены; assertions
и матрица не ослаблены. Docker daemon здесь недоступен: запуск исправленной
инфраструктуры и42 MAX cases ещё НЕ подтверждён. Docker principal15 GREEN из
другой runtime-сессии R297 сохраняется как отдельный факт.

Изменён только acceptance tooling/documentation; runtime artifacts, POM,
миграции и flags прежние. Рабочий экземпляр — на другой машине/в другой
runtime-сессии, rollout/provision R297 не повторять. После commit/push и
обновления clean checkout на той машине выполнить существующий
java-bot/bot-feedback-acceptance/run-max-media-acceptance.ps1; backend/bots
пересобирать и перезапускать для R298 не требуется. Подробности:
docs/runbooks/max-linked-questionnaire-media.md. Общая задача01-278 остаётся🟡;
cross-platform/provider/restore/rebind и S6/§7.1 acceptance ещё требуется.

## Runtime R297 — 2026-11-06: V86/V87 и изолированные боты активированы

Checkpoint R297 относится к другой машине и runtime-сессии рабочего тестового
экземпляра. Ниже зафиксированы наблюдения той сессии. После явного
«делай» выполнена активация image из Build R296:
iguana-panel:account-r295-6bc6ae23a-20261106,
sha256:29f36986a0dbfd615c1444851567517602ba11bf2729d095a918850b03054d89,
source6bc6ae23a641b4a633d3936501ec1829640f0166 (R295). Baseline checkout/remote
dd58f10bd2b43379a1ff2468c5109639b2538467 (R296) содержит только metadata поверх
этого source. Повторная сборка не требовалась.

Реальный db-migrate завершился0: Flyway V86/V87 success, текущая версия87;
FORCE RLS на3 transport tables и chat_history.source_link_json подтверждены.
Для Telegram1/3 и MAX2 применены отдельные SQL/Rabbit principals iguana_child_1/2/3.
Реальный SQL login, отказ business reads, отсутствие чужих outbox rows и точные
Rabbit permissions проверены. Signed API:13 own/foreign/retired-operator checks
GREEN; в JVM всех3 детей свои API/SQL/Rabbit credentials, control-plane secrets
отсутствуют. Старые DB migrations/provision после обрыва не повторялись.

Compose project tg_ref_b24_sup: panel-web/ops-worker/bot-runner на новом image,
по1 replica, healthy/restart0; запущены2 Telegram и1 MAX child. VK JAR также новый,
активного VK-канала нет. Всего15 работающих project services; db-migrate,
minio-init и shared-config-check exited0. При восстановлении после обрыва Docker
Desktop запущен заново; containers поднялись по прежним restart policies без
повторного recreate. Остальные16 исходных container IDs/images сохранены.
ENV, HostConfig/mount values и исходный .env прежние; auto-start восстановлен.
Фактический application Compose input теперь release private/compose.final.json
(полный resolved snapshot прежних contour+observability+R280 override), с исходным
.env и project directory. Не подменять его старым R280 override при продолжении.

Свежая проверка после восстановления:61 live panel access checks,13 signed API
checks,12 running JAR SHA256 exact; health/readiness200UP,
production-readiness200/ready, Rabbit healthy, все10 очередей пусты/DLQ0.
Settings455, accounts/grants, roots53/pending51/ratings36/ledger17 и контрольные
hashes данных прежние. Flags не менялись: signed MAX v4 choices сейчас404 по
существующему feature gate; live MAX linked-media flow этим не принят.
Ручной provider send, replay/purge и backfill не выполнялись.

До миграций сохранены cold PostgreSQL/Redis, logical PostgreSQL/globals,
Rabbit definitions и shared config; backups и rollback image aliases R296
сверены повторно. Всё same-host, private ACL текущего SID; restore/rollback
не разыгрывались. Принимающая проверка:34 contracts и15 Docker PostgreSQL
principal IT GREEN. Остальные42 MAX PG/Rabbit cases не выполнились:
test fixture PostgreSQL в internal network не получил опубликованный port
(Docker29/Testcontainers1.19.7); это startup error, общий57-case gate не GREEN.
Assertions/isolation не ослаблялись. XML/logs и principal artifacts сохранены
с SHA-256 readback; удалены только4 собственных .tmp каталога/worktree.

Задача🟡: остаются42 MAX cases, cross-platform restore/replay/rebind/provider
приёмка, политики общих задач/проектов/системных данных, AI privacy/delivery,
controlled flags и полная S6/§7.1/rollback приёмка. S2B снятR190.
Evidence и actual Compose: ../iguana-runtime/tg_ref_b24_sup/releases/r297-activation-20261106/.
Ниже Build R296 и прежние source checkpoints — историческое состояние до активации.

## Build R296 — 2026-11-06: backend и все боты R295 пересобраны

Из clean HEAD/origin/main/actual remote 6bc6ae23a641b4a633d3936501ec1829640f0166 полностью
пересобраны backend/Sass и Telegram/VK/MAX с общим core. Готов image
iguana-panel:account-r295-6bc6ae23a-20261106,
sha256:29f36986a0dbfd615c1444851567517602ba11bf2729d095a918850b03054d89.
R294/R295 входят в этот image; R293/sourceR292 сохранён отдельно.
Fresh targeted1679 JVM tests/71 suites, failures/errors/skips0, ещё7 UI cases
GREEN. Проверены private ingress, retirement operator routes/cache replay,
exact service-chat recipient/media fallback и прежние MAX media/questionnaires.
Full runtime main/test compile на JDK17/repo wrapper3.9.6; packaged4 JAR,
nested core всех3 ботов и changed backend/TG/VK classes сверены с fresh targets.
V86/V87 bytes и vendor SRI exact. Это не full test suite, не57 PG/Rabbit IT
и не повтор50 native PG cases sourceR295; live provider/S6 не выполнялись.

На текущей машине Docker доступен. Project tg_ref_b24_sup, прежние3 Compose
inputs/.env, по1 replica panel-web/ops-worker/bot-runner сверены. Все20 исходных
containers остались exited, IDs/images/state/Config/HostConfig/mount values exact.
Rollback aliases rollback-r296-{panel-web|ops-worker|bot-runner}-20261106-b0e12ff574c3
сохранены; R280/R293 images/recovery не удалены. Helpers удалены, volumes прежние.
Deployment=false: новый image не активирован, health/readiness не заявляется.
Новых migrations R294/R295 нет; V86/V87 и per-child SQL/Rabbit provision общего
выпуска ещё не запускались. Для включения нужен согласованный backend/schema/
principals/children rollout по runbooks; старый SDK child не запускать в смеси.
DB/Redis/queues/provider/backfill/flags не использовались и не менялись.
Задача🟡, общий пункт1/S6 и §7.1 pending, S2B снятR190.
Evidence: ../iguana-runtime/tg_ref_b24_sup/releases/r296-6bc6ae23a-20261106/.

## Source R295 — 2026-11-06: операторские действия только в панели

Решение пользователя: «Операторские действия только из панели; группы — для
уведомлений». Членство/admin в Telegram-группе и строка operatorIdentity не
подтверждают учётную запись или business/GENERAL grants панели. Привязка
внешних операторов к panel accounts в обязательный scope не добавляется.

Telegram принимает только private chat с actual positive sender=chat owner
до dedup/канала/вопросника. Групповые /reply, редактирование ответов, auto-bind
и /confirm удалены; группы не могут дописать частную сессию. VK отсекает group/
foreign peer до claim и restore. Ранее выбранный BUSINESS/GENERAL scope не
переопределяется, private сообщения продолжают прежний selected workflow.

Шесть unowned internal operator/config routes retired: authenticated control-
plane получает410 до prepareWrite/idempotency replay, child —403 до ticket
lookup. Это reopen, activity PUT/DELETE, relay, operator edit и support-chat
update. Core HTTP clients не вызывают эти routes; local operator relay/edit
и support-chat update не делают JDBC/JPA fallback. Authenticated panel controls
и R294 уведомления продолжают работать по прежним actor/recipient guards.

460 targeted JVM cases/17 suites GREEN,52 новых; среди них50 реальных
PostgreSQL regression cases на nonce-owned test fixture и24 MVC/cache cases.
Main/test compile backend и bot modules выполнен; working DB/Redis/broker/
provider другой машины не использовались. Новых migrations/flags нет.
Backend и bot jars/image требуют новой согласованной сборки: старый Telegram
может отправить через SDK до обращения к API, одного нового backend мало.
R293 image содержит sourceR292, не R294/R295; deployment=false.

Программный подблок legacy group/operator закрыт. Не планировать его заново.
Следующий общий gate пункта1 — существующие42 MAX +15 principal PG/Rabbit cases
и cross-platform restore/replay/rebind/live acceptance на выделенном стенде.
Local Docker недоступен; этот gate PENDING_DOCKER. S6 и §7.1 full in-flight
restart остаются MANUAL_PENDING. Task🟡; S2B снятR190; delivery/rating R289
не открывается заново. Инструкция: docs/runbooks/panel-only-operator-actions.md.

## Source R294 — 2026-11-06: legacy уведомления Telegram/VK в разрешённый чат

Продолжен пункт1 после sourceR292/buildR293. Legacy сводки обращений, relay
клиентских ответов и запросы/digest разблокировок Telegram/VK больше не используют
canonical ID записи канала как внешний chat/peer ID. Все эти отправки проходят
SupportChatDeliveryService: bound sender/token + текущий exact support_chat_id,
две uncached signed backend decisions R291, повторная локальная сверка после I/O.
Missing/invalid recipient, revoke, rotation/rebind или unavailable API запрещают
SDK handoff без fallback в properties/shared chat. Telegram signed negative
group IDs поддерживаются; VK peer должен быть canonical positive int32.

Telegram media pin сохраняется до materialize; после чтения файла и перед
document fallback проверка повторяется. Временный файл очищается и после
успешного preferred send. Fallback разрешён только после явного Telegram400;
403/429/5xx/transport exception/lost HTTP response не дают автоматический resend.
Изменения не добавляют очередь восстановления service-chat уведомлений.

430 свежих targeted JVM tests/10 suites GREEN,94 новых. Signed loopback HTTP
проверяет actual panel client; Telegram multipart/JSON проходит настоящий SDK
на локальный HTTP stand, включая потерю ответа после получения файла. VK SDK
заменён test double. Все4 bot modules main/test compiled JDK17/repo wrapper;
это не live provider, PostgreSQL/Rabbit или full application restart acceptance.
RED первой версии теста сохранён: subclass Mockito не подменяет final media
execute; fixture заменён actual SDK/HTTP, assertions не ослаблялись.

Source/test-сессия не подключалась к рабочим DB/Redis/Rabbit/провайдерам другой
машины. Local Docker endpoint недоступен; engine не запускался. R293 image
ff8c8f29d64d содержит R292 и этих изменений не содержит; build R293 не является
активацией. Нужна новая сборка bot jars/image и согласованный выпуск по R291/
R292 runbooks; новых schema migrations в R294 нет. Deployment/commit/push=false.
Инструкция: docs/runbooks/legacy-support-chat-delivery.md.

Общий пункт1 остаётся в работе: inbound operator/group permissions и другие
legacy пути, cross-platform restore/replay/rebind и live/provider/rollback gate.
42 MAX +15 transport-principal Docker cases по-прежнему PENDING_DOCKER;
S6/production-e2e-smoke §7.1 MANUAL_PENDING. Общая задача🟡, S2B снятR190.

## Build R293 — 2026-11-06: образ R292 собран, активация отдельно

На текущей Windows/Docker-машине полностью пересобраны backend/Sass и JAR
Telegram/VK/MAX из clean e3ede2da79861fc7bab6f767cccc65a0e998fa4b (R292).
Готов образ iguana-panel:account-r292-e3ede2da7-20261106,
sha256:ff8c8f29d64df42a14ef044c5f24e9c8307a5f37bc024cfdf46c43b53e7851d9.
JDK17/repository wrapper3.9.6/offline, все runtime main sources compiled заново.
1281 JVM tests/57 suites, failures/errors/skips0; ещё7 source-link UI cases GREEN.
Проверены одинаковый nested core во всех3 bot jars, новые backend/MAX classes,
exact V86/V87 bytes, vendor SRI и4 JAR SHA256 внутри нового image.
Это не57 PG/Rabbit integration cases: тот отдельный gate ещё не выполнялся.

Docker Engine восстановлен штатным Desktop restart. Найдены исходные20
containers, все остановлены. Project tg_ref_b24_sup, прежние3 compose-файла и
.env, по1 replica panel-web/ops-worker/bot-runner; итоговая сверка IDs/images/
state/ENV/HostConfig/mount values exact. Docker может менять порядок Mounts;
сравнение выполнено по destination и каждому свойству. Rollback tags
rollback-r293-{panel-web|ops-worker|bot-runner}-20261106-b0e12ff574c3 сохранены
для прежнего imageb0e12ff574c3/sourceR279. Build helpers удалены; data volumes
не менялись. Рабочие DB/Redis/queues/provider не запускались и не использовались.

Deployment=false: контейнеры остаются на прежнем image и остановлены; новый
health/readiness не заявляется. V86/V87, SQL/Rabbit provision, backfill и flags
не выполнялись. Для активации по runbooks child-transport-isolation и
max-linked-questionnaire-media нужны backup/drain, согласованные backend/schema,
отдельные child principals и затем новые children. Старый image restart не
включает R292; build не закрывает этот выпуск или S6. Общая задача🟡, S2B снятR190.
Evidence: ../iguana-runtime/tg_ref_b24_sup/releases/r293-e3ede2da7-20261106/.

## Source R292 — 2026-11-06: MAX forwarded/reply media и их контекст

Продолжен пункт1 после R291: MAX v4 initial/free-answer и published followup
поддерживают canonical forward/reply document/photo/audio/video. Пересылка
использует только link.message.attachments, ответ — только внешние новые файлы;
процитированные вложения не скачиваются. Mixed/alias/nested/неполные формы
отклоняются до I/O/CAS, прежние byte/hash/quota/root проверки сохранены.
Actual outer user остаётся владельцем; исходные MID/author не участвуют в
выборе root/channel/catalog/storage/delivery identity.

Nullable typed sourceLink проходит session JSON/CAS, immutable outbox, signed
wire, owning ingestion, V87 chat_history.source_link_json и BUSINESS/GENERAL
history API/render. Opaque MID и source author ID сохраняются строками; HTML
контекст escaped/textContent, исходный MID не используется как href. Deleted/
foreign history и READ revoke не раскрывают новый контекст. Retry после CAS/
outbox loss использует исходные метаданные без нового download/admission.

Для совместимости введены отдельные max-linked-media-selection/max-linked-batch
routes и followup admission4; direct MAX остаётся3. Новый wire type batch и
initial media marker не дают старому backend молча потерять sourceLink.
Legacy initial/JDBC/generic-active paths отклоняют source context до domain writes;
его нельзя вывести из выбранного question scope и обойти byte validation.
Новый бот читает старые JSON, обратный downgrade новых drafts не гарантирован:
старый reader может игнорировать поле. Coordinated release/rollback обязан
сохранять Redis/session/outbox и оставлять старых детей остановленными.
Инструкция: docs/runbooks/max-linked-questionnaire-media.md; нужны backend/
MAX jar R292, V87 и provision/контракт R291. Flags здесь не менялись.

1090 JVM tests/36 suites GREEN,0 failures/errors/skips: совокупные latest
XML отдельных targeted runs. Native PostgreSQL16.15 nonce/loopback; full Flyway
V1–V87,13 actual Hibernate/V87/history cases,5 новых GENERAL SQL и272 прежних
GENERAL actor checks. 7 новых Node renderer cases и R117/R134/R51/R52 GREEN;
PowerShell5.1 operator self-test/parser GREEN. Provider network mocked; это не
live acceptance полного Boot/Redis/provider fleet. Ошибки ранних fixtures
(routing key, ObjectMapper time module, Hibernate naming) исправлены без
ослабления assertions; RED logs retained; final XML/class hashes сохраняются отдельно. Minimal GENERAL fixtures получили
nullable V87 column; прежняя manual creation/read проверка повторена отдельно.
У неё выявлено stale V84 expectation: feedback_context_epoch теперь входит в
exact column set и отдельно проверяется как distinct UUID для каждого root;
сохранность прежних колонок проверяется прежним строгим сравнением.

Opt-in acceptance теперь содержит34 contracts и42 MAX PG/Rabbit cases
(38 прежних+4 linked source/replay), плюс15 SQL principal IT. Здесь34 contracts
и15 native principal IT GREEN. Docker engine отсутствует;42 media и Docker
variant15 principal cases остаются PENDING_DOCKER. Runner после пользовательского
commit ожидает57 IT и пишет r292-proof.json; ValidateOnly не закрывает gate.

Baseline HEAD/origin/main/actual remote74fb3d466 (R291), parenta7ce504dd;
63 предыдущих project paths проверены, raw guards4129 tracked files. Все правки
готовятся в detached sandbox и переносятся теми же validated bytes отдельным
guarded apply; staging/commit/push/deploy не выполняются. Рабочий проект/Docker
на другой машине/сессии не затронут; runtime остаётся последним подтверждённым
R280/R279. V87 не применялась к рабочей БД; backfill/NetBox/flags не менялись.

Общий пункт1 не закрыт: другие legacy/direct пути, cross-platform GENERAL/
questionnaire restore/replay/rebind и live/provider/rollback приёмка остаются.
Policies/AI/S6 отдельно не завершены; task🟡, S2B снятR190. Программный delivery/
rating R289 не открывается заново, ручной fleet restart остаётся в
production-e2e-smoke §7.1 MANUAL_PENDING.

## Source R291 — 2026-11-06: transport principals каждого child

Выполняется пункт1 нового списка после R290 — оставшаяся сквозная изоляция,
а не уже завершённый R289 программный блок delivery/rating. В rabbitmq contour
каждый channel/platform получает отдельные domain-separated API/SQL/Rabbit
credentials; общий master/signing secret и privileged SQL/Rabbit login не
передаются детям. Launcher очищает inherited configuration overrides. Startup
проверяет реальный SQL session_user, отсутствие business/DDL/role privileges и
наличие FORCE RLS. V86 вводит bot_child_principals и RLS трёх transport tables;
LOGIN roles создаются отдельно администратором, не миграцией или приложением.
JDBC dev contour не объявляется новым production contract.

Signed child API требует live canonical channel/platform/principal и owning
transaction, ограничивает root, pending feedback, media и channel routes до
idempotency replay. Unknown routes denied; child не меняет support-chat config
и не использует retired unowned edit. История и unblock summary ограничены
authenticated channel до SQL LIMIT; inconsistent/duplicate root history не
выдаётся. Remote-only runtime settings не читают общие локальные файлы.
Служебный чат допускается только по свежему exact support_chat_id и sender,
без positive cache/fallback. AMQP user_id дополнен HMAC исходных bytes;
backend повторяет channel/platform/actual user/raw ticket admission под locks.
После revoke запрещены новые действия, но допустима фиксация положительной
квитанции уже принятой отправки, без resend неизвестного результата.

Совокупно 1023 JVM tests/43 suites GREEN,0 failures/errors/skips. Это retained
latest XML разных targeted runs, не один full-suite запуск. В том числе55
реальных PostgreSQL/API/inbound cases с полной Flyway V1–V86,8 native scoped
history SQL cases и15 actual bot startup SQL cases с отдельно provisioned
LOGIN roles. Свой native PostgreSQL16.15, loopback/nonstandard port и nonce
guard; рабочие datasource/config не читались. Provider doubles и loopback HTTP
не заменяют live acceptance. PowerShell5.1 generator checks14 GREEN, runner
self-test GREEN. scripts/prepare-child-transport.ps1 создаёт только закрытые
ACL файлы SQL/Rabbit definitions/manifest в новом .tmp, ничего не применяет.
Инструкция согласованного release/provision/drain/rollback:
docs/runbooks/child-transport-isolation.md.

Baseline HEAD/origin/main/actual remote a7ce504dd (R290), parent3ba33f464.
Source готовился в detached sandbox; raw guards4108 tracked files, exact
file set и перенос тех же bytes проверяются отдельно от staging/commit/push.
Ранние RED сохранены: короткие старые test-secret fixtures, неверный reactor
argument/API контейнера, отсутствующая owned feedback test DB и неполный
JdbcTemplate test double; исправлены точечно, assertions не ослаблены. Проверка
history query обнаружила неверное имя tie-break column: применён настоящий
messages.group_msg_id и actual repository SQL повторён на PostgreSQL.

38 MAX PG/Rabbit cases остаются PENDING_DOCKER: local engine недоступен.
15 principal cases прошли native; Docker variant отдельно pending. Полный
acceptance runner после пользовательского commit ожидает26 contracts и53 IT
(38 media +15 principals), пишет r291-proof.json. Source R291 требует V86 и
согласованного backend/launcher/child release с отдельным provision. На другой
рабочей машине/сессии DB/broker/rebuild/restart/config/flags не менялись;
последний документированный release R280/R279, новый deploy не подтверждён.

Пункт1 целиком не закрыт: MAX forwarded/reply media и сохранение их provenance,
остальные legacy/direct paths, cross-platform GENERAL/questionnaire restore/
replay/rebind и live provider acceptance остаются. Это не полная изоляция ОС/
Redis/object storage. Политики специальных разделов/ролей, AI и первый
production S6 также остаются; task🟡. S2B снятR190. Ручная fleet restart
приёмка R289/R290 остаётся в production-e2e-smoke §7.1 MANUAL_PENDING.

## Source R290 — 2026-11-05: редактирование сообщения с точным автором

Пункт1 доставки/оценок программно завершён. Оставшаяся ручная fleet restart
приёмка перенесена в docs/runbooks/production-e2e-smoke.md, раздел7.1,
MANUAL_PENDING; прежние пользовательские live checks и R265–R267 evidence
сохранены, новые runtime факты не объявлены. Руководство R289 ссылается на чеклист.

Начат следующий блок изоляции. Прежний Telegram client-edit передавал только
channel/provider ID: единственная совпавшая запись другого клиента могла быть
изменена. Теперь private-chat handler передаёт platform/channel/actual author/
provider ID через TicketService и signed PanelTicketWriteClient в отдельный PUT
/internal/api/bot/channels/{channelId}/users/{userId}/messages/{providerId}/client-edit.
Body:platform,message. Весь путь сохраняет positive BIGINT без JS rounding.
Group/foreign-chat/missing author/wrong platform/invalid ID отклоняются; нет
JDBC или старого HTTP fallback. Старый HTTP client-edit без автора возвращает
410 Gone после прежней write authorization/idempotency boundary, без service DML.

Backend выбирает history только данного автора, проверяет единственный raw
canonical root и загруженный channel/platform/active. Owning UPDATE повторяет
exact child tuple, per-author history uniqueness, root public/business/general
snapshot и channel platform/active с FOR SHARE row locks. Другой клиент с тем
же provider ID не блокирует собственное редактирование и не меняется. При
изменении root/history/channel запись и UI handoff запрещены. Raw Unicode ticket
ID не trim; original_message сохраняется при следующем edit. UI exception
откатывает SQL update. Shared API/SQL child principals этим не разделены.

430 targeted JVM tests/14 suites GREEN,0 failures/errors/skips;
110 новых frozen tests +3 MVC cases. Baseline RED:110 tests/61 failures и49
positive controls. Три новых test sources,5 test classes и110 case names после
RED неизменны.81 новая owning-SQL проверка выполнена на собственном native
PostgreSQL16.15 с fresh nonce-owned database/schema и real transactions;
контролируемые drift происходят перед DML, не concurrent MVCC/rebind acceptance.
Ещё50 прежних BotRuntimeTicketWriteServiceTest без изменения assertions прошли
на той же owned БД. PostgresqlJdbcTestSupport сохраняет обычный Docker default;
native override требует exact loopback/nonstandard port/r290_edit/fake role
и случайный cluster nonce до создания schemas. Application datasource не читает.
HTTP loopback, backend MVC и реальный Telegram edit handler проверены отдельно;
repository/outbox/SDK seams замоканы. Wire/TicketService не используют fallback.
Модели и SQL schema/POM/session JSON не меняются. JVM17, Sass; CSS вне scope.

Baseline HEAD/origin/main/remote3ba33f464 (R289), parentae45b515d;9 previous changed
paths/blob content и4104 tracked files сверены. Первый RED reactor остановился
на core failures; второй RED-only run с test.failure.ignore собрал оба модуля:
его exit0 не означает GREEN, отчёты фиксируют все110 tests/61 failures. Обычный
GREEN bot run без ignore. Первый исправленный panel run имел0 failures/50
Docker-fixture errors; его XML/logs/class hashes сохранены. После точного native
test-helper opt-in повторены только эти50 checks; остальные205 panel и175 bot
проверены на том же неизменном product bytecode. Итог430/14 — совокупный proof,
не один Maven запуск. Evidence .tmp/r290-baseline-red.json/r290-java-green.json,
owned PostgreSQL logs, validation/result/final. Scope16, root apply отдельно
от staging/commit/push. Рабочий проект/Docker на другой машине/сессии не затронуты.

Для будущего выпуска нужны backend panel-web/ops-worker R290, затем Telegram
child; old backend не знает owner route (404), old bot после нового backend
получит410, без небезопасного fallback. Release отдельно, migrations/flags нет.
Выбранный Telegram v4 question handler по-прежнему явно запрещает редактирование
ответов; этот пакет не включает такую функцию. VK/MAX ingress edit здесь не
добавлен; backend/client контракт принимает три платформы. R28738 MAX PG/Rabbit
cases остаются PENDING_DOCKER. Остальные legacy/media/service-chat recipient/
child principals/policies/AI/S6 остаются, задача🟡; S2B снятR190.

## Source R289 — 2026-11-05: разбор delivery/unknown без повторной отправки

Промт: «выполни 1-й пункт» — доставка/оценки, restart и неизвестный исход.
На вопрос о живой проверке пользователь ответил: «тесты уже проводились ранее -
всё было ок.». Это принято как подтверждение прежних live delivery/rating checks;
дата/revision самих прогонов не сообщены. Здесь новые provider sends/live tests
и restart рабочего приложения не выполнялись; повтор не требуется ради R289.
Исторические R265 process-kill, R266 persisted rating и R267 cold fleet start
сохранены. Full in-flight restart всего штатного launcher/fleet не объявляется
проверенным: он остаётся в общей release/rollback приёмке S6.

Закрыт программный остаток пункта1: scripts/feedback-delivery-recovery.ps1 и
два scoped SQL дают read-only review одной exact platform/channel/user/event
попытки и явный reconcile только по committed положительной V85 receipt.
SHA256 обеих полных rows, repeatable-read/row locks и повторная проверка всего
ledger/receipt context запрещают stale proof/дубликат/чужую квитанцию. Меняются
только status/delivered_at из receipt/last_error/updated_at; receipt, outbox,
pending/rating, provider/API/Rabbit не затрагиваются. Без квитанции unknown
остаётся blocked; timeout/false/503 не дают права на resend. Retryable допускает
только отдельный штатный replay с новой авторизацией, утилита его не делает.

73 новых проверки GREEN на собственном native PostgreSQL16.15/psql16.6:
три платформы ×sending/uncertain, дубликат, unsafe statuses, missing/prepared/
cross-context receipt, stale snapshots, wrong scope, SQL rollback/native failure,
malformed input, Unicode/raw ID и BIGINT/Long.MAX_VALUE. Временный cluster
на fresh repo/.tmp/random loopback port остановлен; Docker не использовался.
Windows PowerShell5.1 self-test/parser проверены; SQL выполнялся настоящим psql.
Полная Docker-wrapper ветка с рабочим PostgreSQL здесь не запускалась.
Java/POM/schema/runtime config прежние, Maven/JVM regression не повторялись.

Baseline HEAD/origin/main/remoteae45b515d (R288), parent4a026df59;6 prior changed
paths/blob content и4098 tracked files проверены. Scope9: operator,2 SQL,test,
runbook,task/S0/BOT/new changelog. Evidence .tmp/r289-sql-green.json и retained
native cluster logs/proof; версии неуспешной подготовки Windows harness сохранены.
Инструкция: docs/runbooks/feedback-delivery-recovery.md. Оператор вызывается
владельцем на машине проекта с exact running PostgreSQL container ID; пароли,
.env/application YAML и Compose не читает, restart/rebuild для утилиты не нужен.
Root apply отдельно от staging/commit/push; рабочая машина/БД здесь не менялись.
Новых migrations/flags нет. R287:38 MAX PG/Rabbit cases по-прежнему PENDING_DOCKER.
Остальные isolation/policies/AI/первый production S6 остаются, задача🟡; S2B снятR190.

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

## Source R287 — 2026-11-05: подготовлен isolated MAX outbox → inbox gate

Test-only пакет, product code/POM/schema/session JSON не меняются. В существующий
opt-in bot-feedback-acceptance добавлены MAX fixture,38-case ChainIT и26-case
wire/isolation/bytecode contract. Модуль исключён из обычного runtime reactor;
IT запускается только явно. Это подготовка приёмки, не выполненный PG/Rabbit gate.

Здесь fresh JVM137 tests/8 suites GREEN,26 новых, failures/errors/skips0.
Проверены реальные bot publisher → panel DTO wire для4 типов и обоих scope,
opaque Unicode/raw ID и positive BIGINT выше2^53/Long.MAX_VALUE, отказ рабочих/
неоднозначных endpoints и nonce до соединений, exact loaded panel bytecode.
Source baseline6113a034b (R286), parent7aa9642d4; HEAD/origin/main/actual remote
и14 prior changed paths/blob content сверены с сохранённой R286 source-копией.
Hash-pinned panel R285 и core R286 соответствуют всем текущим main Java sources;
2640 retained class hashes unchanged. Core143 main/60 tests и11 acceptance
test sources freshly compiled; после review fixture ещё раз compiled и26/26
contract cases GREEN. Windows PowerShell5.1 parser/self-test проверяет output
containment, hash, missing executable и native exit7. Это не выполнение Docker
веток оператора и не positive/rollback/concurrency proof настоящей инфраструктуры.

На этой source/test-машине Docker CLI указывает на локальный desktop-linux
npipe, engine pipe отсутствует. Docker Desktop/daemon не запускались, remote
Docker не использовался. IntegrationExecuted=false, IntegrationPassed неизвестен,
38 scenarios PREPARED/PENDING_DOCKER; их нельзя прибавлять к137 passed tests.
Рабочий проект и Docker на другой машине/в другой сессии; их state здесь
не измерялся. Исторические R280/R281/R282 и rollback/private archive сохранены.

Prepared IT создаёт только собственные PostgreSQL16/Rabbit3.13, fake credentials,
nonce-labelled internal network, random loopback ports и отдельную r287 schema.
Production admission/controller/HMAC/client, outbox serialization+publisher
confirms, Rabbit converter/listener, inbox transaction advice, advisory/root
locks, byte verifier и metadata JDBC участвуют в цепочке. Ticket/history/channel/
activity repositories — узкие JDBC-backed adapters, не полный JPA/Boot stack.
Синтетическая DDL на owned PG, local filesystem bytes, без Flyway на рабочей БД,
S3/MAX CDN/provider, session Redis или application YAML/.env.

Матрица38:12 positive BUSINESS/GENERAL ×1/2/10 files ×33/Long.MAX_VALUE;
2 committed-before-ACK broker requeues с original bytes/redelivered flag;
6 rollback/retry (history second item/metadata second item/activity);
4 missing/hash second file;2 post-commit service reconstruction;
2 competing quota transactions с real pg_locks barrier;6 canonical root drift;
4 initial-media PostgreSQL JSONB scoped/legacy lookup. Это заявленная матрица
для следующего запуска, не результат. Broker requeue не full JVM kill/launcher
restart; reconstruction не моделирует потерю процесса. Полные process kill,
current binding rebind, quota reservation/drain/uncertain-result/rollback release,
live provider и общий cross-platform/S6 остаются.

Tracked operator:java-bot/bot-feedback-acceptance/run-max-media-acceptance.ps1.
На машине с Linux Docker engine и JDK17, после commit/push и обновления checkout,
из корня выполнить:

```powershell
powershell -NoProfile -File .\java-bot\bot-feedback-acceptance\run-max-media-acceptance.ps1
```

Оператор требует clean source commit, создаёт detached worktree в fresh repo/.tmp,
сначала compile панели из того же commit и26 contracts, затем38 IT. Рабочие
targets не пересобирает; source worktree/logs/XML/source hashes/r287-proof.json
сохраняются. Full mode допускает только локальный Linux Docker context; снимки
прежних containers хранят только hashes config/ENV/mounts и проверяют прежние
image/start/running/restarts после теста. Cleanup касается только точных labels
owner+nonce/двух test images и пустой owned internal network. Testcontainers
может скачать test images при первом запуске. Рабочий Compose не вызывается.
Если Docker недоступен, full mode останавливается до тестов, не объявляет GREEN.
-ValidateOnly выполняет compile/contracts без Docker и пишет IntegrationPassed=null;
-SelfTest проверяет сам оператор без builds/Docker. Эти режимы не закрывают gate.

Scope9:3 new test Java,runner,acceptance README,task/S0/BOT/new append-only
changelog. Root apply отдельно от staging/commit/push/deploy; commit/push остаются
пользователю. Rebuild/restart рабочего приложения для этого test-only пакета
не требуется; R284/R285/R286 source ещё требуют отдельного coordinated release.
Новых migrations/flags нет, V83/V84/V85 не повторять. S2B снятR190, задача🟡.

## Source R286 — 2026-11-05: MAX published followup media и durable outbox replay

Клиент MAX подключён к backend R285: прямые document/photo/audio/video batches
для опубликованного v4 обращения, BUSINESS и GENERAL, включая отсутствующую
session. Signed /questionnaire-media/max-batch допускается только с version3,
exact channel/user/raw ticket, корректным nullable owner/template/catalog/quota.
Текущий catalog/binding не подставляется. Сохранённая выбранная session при её
наличии обязана совпадать с canonical admission; chat mismatch проверяется до
outbox lookup и любого download. Unknown canonical root по-прежнему fail-closed.

Весь batch1–10 описывается до I/O; owned writer R283 сохраняет actual bytes
1байт–10MiB в frozen MAX namespace. После всех файлов один versioned batch
уходит через прежний transactional TicketService в SQL outbox с MAX route,
deterministic event ID и positive BIGINT provider ID, включая Long.MAX_VALUE.
History/metadata пишет только backend R285 после whole-batch verification.
Forward/reply/opaque/mixed/unknown формы явно отклоняются; подпись не становится
текстовым ответом, warning обязан быть подтверждён. Legacy downloader/shared
service-chat/single-message writer не являются fallback для v4.

Durable pending здесь — существующий SQL outbox, а не новый session JSON или
второй Redis journal. До нового admission/download читается committed исходный
batch по channel/user/provider, независимо от current root и потери session/
transport dedup. queued/processing/failed/published означают уже сохранённый
payload, не подтверждённую backend/provider доставку. Retry только ACK исходного
ticket: timestamp/profile/caption/items не пересоздаются, dispatch не запускается
и status не меняется. Исходный initial-media event также распознаётся по scoped
history, чтобы replay первого вопросника не стал новым followup. Ambiguous/
corrupt journal закрывает путь; legacy initial keys не перехватываются SQL filter.
До committed outbox возможен повтор download; session CAS при followup не нужен,
так как состояние вопросника не меняется. Partial storage до отказа не означает
частичную history/outbox acceptance; orphan bytes отдельно не удаляются.

884 fresh JVM tests/40 suites GREEN,80 новых; skips/errors/failures0. Frozen
baseline80:26 assertions +8 expected missing-capability/old-writer exceptions,
46 controls;4 final test source hashes/5 class hashes/case names exact RED→GREEN.
Включены signed loopback HTTP/HMAC и raw ticket encoding, real controller/writer/
filesystem, real outbox serialization+publisher с in-memory JDBC fixture,
commit-before-exception replay после session/dedup loss без изменения payload,
existing MAX/Telegram/VK capability/transport/legacy/session/source-layout tests.
Это не real PostgreSQL JSONB/transaction/concurrent quota proof, Redis/Rabbit/
provider/CDN/S3, full JVM kill или S6. Перед release нужны isolated PostgreSQL/
Rabbit outbox→inbox и concurrent quota/rebind/uncertain-result/drain gates.
Existing tests не ослаблялись. В новом fixture исправлены chat setup и пустые
test Rabbit routes; failed logs/sources сохранены. Первый GREEN-кандидат выявил
прежний chat-before-I/O contract: исправлен product order, не старые tests.
R279 bot cache hash-verified, все stale Java inputs принудительно инвалидированы;
fresh baseline compile143 core/60 core tests/23 MAX/32 MAX tests. Не package.

Fresh HEAD/origin/main/actual remote7aa9642d4 (R285), parentf93973310;10 prior
paths/content сверены с Git blobs и сохранённой R285 source-копией. Detached
worktree, repository Maven wrapper/JDK17. Scope14:6 bot main,4 new tests,
task/S0/BOT/new append-only changelog. POM/schema/flags/session JSON неизменны.
Root apply отдельно от staging/commit/push/deploy; push остаётся пользователю.

Source/test machine:C:\Users\sushi\Git_H\tg_ref_b24_sup. Рабочий экземпляр
проекта и Docker — на другой машине/в другой сессии; здесь они не измерялись
и не изменялись, их текущий state неизвестен. Docker/PG/Redis/Rabbit/provider
не запускались. R280 release/health, R281 cleanup и stopped20 containers R282
сохранены как исторические факты исходной runtime-сессии. Новых migrations/
flags нет, V83/V84/V85 не повторять. Для включения нужен coordinated backend
R284/R285 before bots R284/R286 release с preserved ENV/rollback, не bare restart.
Live provider acceptance, legacy/recipient/child principals/global policies/AI/S6
остаются; S2B снятR190, общий статус задачи🟡.

## Source R285 — 2026-11-05: backend MAX published followup media

Подготовлена backend-часть MAX published followup media. Signed internal
/channels/{channelId}/questionnaire-media/max-batch возвращает Admission version3,
Cache-Control:no-store; TG1/VK2 не являются MAX-допуском. Owner/channel/raw ticket/
BUSINESS|GENERAL/template/catalog/quota читаются из canonical v4 root с прежними
flags, active/status/platform/unique-ID checks; текущие bindings не подставляются.
Consumer принимает только versioned media-batch event, exact deterministic tuple,
positive BIGINT provider ID (включая Long.MAX_VALUE), без forward/reply. Legacy
single-media MAX event по-прежнему не даёт обхода этого gate.

До открытия bytes проверяются все1–10 items: slot/type/safe name и owned
questionnaires/max namespace. Actual bytes1–10MiB/SHA/size проверяются для всего
пакета до первой history/metadata/activity записи. Некорректный второй файл не
оставляет первый history row. Canonical legacy-ID guard/SQL row locks и прежний
markProcessedWithWrites batch contract сохранены; ingress отдельно проверяет
MAX platform канала. TG/VK verifier behavior не расширялся и не заменялся.

315 fresh targeted tests/11 suites GREEN,79 новых (61 verifier,10 ingestion,
8 signed MVC); frozen baseline RED79/29 assertion failures/50 controls,errors/
skips0.3 new sources/4 class hashes/case names exact после product edit.
Real service/row mapper/storage streams/controller/request guard и in-order
ingestion, mocked JDBC ResultSet/repos/object storage. Это не PostgreSQL lock/
rollback/concurrent quota proof, real Redis/Rabbit/S3/MAX provider или S6.
Первый полный test compile выявил неверный TicketId constructor в новом fixture;
исправлен только fixture, failed log/source сохранены. Все прежние tests intact.
Retained R279 cache проверен по hashes; stale Java inputs принудительно
инвалидированы: fresh baseline compile741 main/690 test sources с последующей
докомпиляцией248 test sources после исправления fixture. Не production package.

Fresh HEAD/origin/main/actual remotef93973310 (R284), parentd799f4227;24 prior
paths/content verified, clean root. Detached worktree, Maven wrapper/JDK17;
scope10:3 backend main,3 new tests,task/S0/BOT/new append-only changelog.
POM/resources/schema/flags/bot sources/session JSON не меняются; Sass EOL output
восстановлен вне scope. Root apply отделён от staging/commit/push/deploy.

Source/test machine:C:\Users\sushi\Git_H\tg_ref_b24_sup. Рабочий Docker и
проект находятся на другой машине и в другой сессии; здесь они не измерялись
и не изменялись. Local Docker/PG/Redis/Rabbit/provider не запускались. R280
release/health, R281 cleanup и остановленные20 containers в R282 — записи
исходной runtime-сессии, не свежие observations этого checkout от5 ноября.
Исторические checkpoints, rollback/backups/private archive не затрагивались.
R285 source-only; новых migrations/flags нет, V83/V84/V85 не повторять.

Клиент MAX ещё не вызывает этот endpoint и не публикует scoped followup batch:
вложения опубликованного root пока явно отклоняются. Следующий шаг — client
admission/owned writer + durable pending batch/CAS/outbox/replay, включая потерю
session. Это отдельный пакет, не закрытый R285. Затем нужны coordinated release,
prepared/outbox drain/rollback и live provider acceptance. Остальные политики/
principals/AI/S6 остаются; S2B снятR190, общий статус задачи🟡.

## Source R284 — 2026-11-03: initial MAX v4 media и session CAS

Writer R283 подключён к свободным ответам первого MAX-вопросника v4 для
BUSINESS и GENERAL. Весь прямой batch1–10 проверяется до I/O; каждый файл1байт–
10MiB сохраняется в owned MAX namespace. Forward/reply/opaque/mixed/unknown
формы, choice/reuse prompt и опубликованный root остаются закрыты. Нельзя
превратить неподдерживаемую подпись в ответ или уйти в legacy downloader.
Общий лимит10 файлов на сессию; owner/channel/catalog/event/slot и metadata
проверяются при restore, затем ответ+history+journal сохраняются одним CAS.
Retry после saved CAS/lost ACK не скачивает файл повторно и сохраняет исходный
submission event. Ошибки transport/storage/CAS не продвигают вопрос; явный
size reject подтверждается только после успешной отправки предупреждения.

Signed v4 /max-media-selection требует отдельный точный MAX-Media-Version:1
вместе с writer4/media1 и тем же frozen selection; старый generic v4 не даёт
допуска. Проверка до download и повторно до первого prepareSubmission.
Backend initial ingestion проверяет owned key/actual bytes/SHA/size, сохраняет
history/metadata; scoped reader проверяет canonical owner и hash/size.
MAX v1–v3 legacy contract и старый трёхполевой text JSON сохранены.

Fresh GREEN:1042 core/MAX executions/34 suites на disposable Redis +182 panel/
7 suites =1224/41, failures/errors/skips0. Включены23 direct и те же23 real Redis
media cases, прежние179 session-loss/replay cases и signed HTTP/loopback tests.
Backend:3 изменённых main classes и targeted tests compiled заново с неизменными
R280/R279 classes; это не full Maven/Sass package и не live provider/CDN/S3.
Evidence: ../iguana-runtime/tg_ref_b24_sup/work/r284-max-questionnaire-media/.
Source-only;20 прежних containers остались остановлены/неизменны, тестовые
resources удалены. Рабочие DB/Redis/queues/provider/flags не использовались;
rebuild/deploy/migrations/backfill/staging/commit/push0. Для включения нужен
согласованный backend web/worker + MAX runner/core release, не panel-web-only.
Prepared/outbox drain и rollback с неопределённым исходом, published followup
media, live provider, policies и общий S6 остаются. Задача🟡, S2B снятR190.

## Source R283 — 2026-11-03: scoped byte writer MAX

Реализован отдельный byte/storage primitive, пока не подключённый к handler:
Owner(channel,business|GENERAL,user,catalogSha,eventId) +slot1–10, отдельный
questionnaires/max namespace, actual bytes1–10MiB/SHA256, deterministic owned key,
safe display name без provider URL и проверка точного результата storage.
HTTPS по прежнему CDN perimeter, без userinfo/fragment/custom port/redirect,
только complete200; duplicate/invalid Content-Length, mismatch/empty/oversize,
ошибка/interrupt/full-body deadline отменяют transport/subscription, без legacy
fallback и без URL/transport cause в возвращаемых ошибках. FS и S3 используют
общий ограниченный storage writer. MAX prefix теперь закрыт для legacy reads;
canonical MAX read proof/первое v4 создание/followup admission ещё закрыты.

887 fresh targeted executions/36 suites GREEN:725 core/MAX +162 panel,102 новых.
Реальные FS и JDK loopback/chunked/slow body; HTTP perimeter/S3 — controlled mocks,
не live CDN/S3/SDK. Backend: fresh changed-class/test compile на проверенном
неизменённом R279 compiled baseline, не полный panel build. Legacy TG/VK/MAX
contracts и прежние539 MAX checks сохранены. POM/schema/flags не изменены.
Нужно подключить writer к MAX parser/session CAS/pending journal, добавить
backend owned-byte admission/metadata/read proof и real replay/consumer tests;
до этого v4 media/caption по-прежнему отклоняется. Не планировать raw byte writer
с нуля. Evidence: ../iguana-runtime/tg_ref_b24_sup/work/r283-max-scoped-writer/.
Source-only: все20 прежних containers остались остановлены; рабочие DB/Redis/
queues/provider не использовались. Rebuild/deploy/migrations/backfill/flags/
staging/commit/push0. Прежние R280 release/rollback/recovery сохранены; текущий
health не проверялся. Общая задача🟡, остальные блоки/S6 остаются, S2B снятR190.

## Source R282 — 2026-11-03: MAX после потери Redis session

Test-only пакет подтверждает immutable published root на настоящем Redis/Lua:
BUSINESS/GENERAL v4 после удаления либо принудительного TTL expiry session key
со stale index, controller rebuild, недоступного/изменённого catalog и другого
support-chat fixture. Text сохраняет ticket/provider-message identity при replay
и потере только dedup keys, не начинает новый вопросник и не идёт в shared chat.
Это at-least-once queue command, не exactly-once/backend dedup/provider proof.
Media/caption/opaque/forwarded отклоняются без legacy download/storage. Missing,
wrong owner/channel/old-context и lookup failure освобождают real Redis claim;
после восстановления того же canonical root принимается тот же event ID.
Индексы очищаются, ключи другого user/channel не изменяются.

Два fresh owned Redis прогона:792 executions/27 suites каждый, failures/errors/
skips0;718 MAX (539 default +179 Redis),74 signed loopback HTTP/core.
179 Redis =139 unchanged inherited cases +40 new (4 text/replay,16 media,
20 deny/recover). Это не1584 уникальных теста. Backend/provider mocked в handler
матрице; реальные PostgreSQL/DB rebind/MAX webhook/provider/client не проверялись.
DEL/forced TTL expiry моделируют потерю key, не memory-pressure eviction.

Runner v2: exact cached Redis/Maven images, repository wrapper/JDK17 offline,
отдельный internal network, no host ports/Compose/.env/Docker socket,64MiB Redis/
768MiB builder. Именованные volumes не создаются. Ownership+nonce/sentinel,
bounded own namespaces/no FLUSHDB; в конце DBSIZE2 и удаление только своих
containers/network. Source mounted read-only, build targets только в disposable
container. Evidence: ../iguana-runtime/tg_ref_b24_sup/work/r282-redis-eviction/.

Свежий operational context R282: Docker API сначала отсутствовал, Desktop
запущен для тестов. Все20 существующих containers, включая18 project, уже были
остановлены до шага; ID/image/start/running/restarts unchanged после тестов.
Рабочий контур не запускался/не пересоздавался, working DB/Redis/queues/provider
не использовались. Прежний R280/R279/imageb0e12ff574c3 — исторический release
checkpoint, не свежая health-проверка: сейчас приложение остановлено.
Production sources/POM/resources не менялись, rebuild/deploy не нужны.
Migrations/backfill/flags/sends/staging/commit/push0; R280/recovery/rollback и R281
archives сохранены. Scoped MAX byte writer, actual DB rebind/provider gate,
legacy/principals/policies/AI/S6 остаются; S2B снятR190, задача🟡.

## Очистка хранилища — R281, 2026-11-03

Отдельно разрешённый cleanup: repo/.tmp пуст, удалены268 entries/
133 old worktrees/142.88GiB logical files; остальные23 worktrees сохранены.
Незакоммиченные source changes/patches и evidence сохранены с полным readback
7923 files/SHA256 в private ../iguana-runtime/tg_ref_b24_sup/cleanup/r281-20261103/;
см. archive-proof.json/plan.json/worktrees/. Старые .tmp paths ниже исторические,
восстановление — из archive/HEAD/patches. Release R280/operators/7 DB backups и
R275 quarantine вне .tmp hash-verified, не удалены. Generated builds воспроизводимы.
Удалены8 unreferenced non-rollback project images и cache volume
tg_ref_b24_sup-maven-cache; images61→53, volumes11→10.
Рабочие и все именованные rollback images,9 app-data volumes/старый cache с
container references сохранены. Все20 containers unchanged,3 прежних child PID,
DB/settings/flags прежние.61 fresh access checks GREEN, health/readiness UP,
production-readiness ready/DLQ0. Runtime R279/imageb0e12ff574c3 не менялся.
Build cache/global prune/VHDX/restart/migrations/backfill не выполнялись.
Подробности Task Storage cleanup R281 и отдельный changelog R281.

## Актуальный runtime — R280, 2026-11-03

В исходной Windows/Docker-сессии заново проверен runtime и активирован clean
R279/1fea98958aa4dab74b515020978aaec7862af2be: сначала panel-web/ops-worker,
затем bot-runner со всеми3 children. Imageb0e12ff574c3, tag
iguana-panel:account-r279-1fea98958-20261103, web5e66fae50aa0/worker672c827f4cdc/
runner53de0936724e, по1 replica/healthy/restart0. Backend ready12:38 MSK,
последний child12:41:57. Cold restart, не zero-downtime. Telegram1/3 и MAX2
перезапущены; VK JAR также собран, active VK channel нет. R276–R279 больше не
source-only; ниже сохранены исторические source/test записи другой машины.

Полная Maven/JDK17/Sass production compile/package4 JAR; runtime OS/base826
прежний.718 fresh targeted tests/32 suites,61 live access +33 signed actual-child
checks GREEN: sender/recipient admission, completed absent404/marker1/no-store,
invalid-HMAC без marker, positive canonical root/contextVersion1.12 running JAR
hashes exact; одинаковый новый bot-core во всех3 platform JAR. Это не live MAX
eviction/provider/client acceptance или полный repository test suite/S6.
Общий health/readiness200/UP, production-readiness ready, DLQ0.15 других containers
unchanged, ENV/mounts exact; HostConfig backend raw exact, runner только bind
order/empty DNS с полным восстановлением старого fingerprint. V85/settings455/
accounts/grants/roots53/pending51/ratings36/outbox/ledger17delivered/receipt0/MAX
session fingerprints exact. Web/worker Flyway skipped, runner V85 up-to-date;
migrations/backfill/flags/provider acceptance sends0. Parent ERROR0, worker TLS
WARN1, runner WARN8 —6 missing PID file при cold start +2 legacy mirror routes.

Backup10340761 bytes/SHA256
98419B5532395E622591B193DDDE10B111CE221DAFE9EC71C6A7C477931C688B
isolated restore GREEN.3 rollback R280 pointers сохраняют backend826/runner85a;
coordinated rollback обязателен, не исполнялся. Backup/Compose/operators/proofs:
../iguana-runtime/tg_ref_b24_sup/releases/r280-1fea98958-20261103/, overlay требует
preserved-ENV operator/private snapshot, не bare up.6 old DB backups и private
DLQ wire archive скопированы с hash verify в recovery/, защищены current-user ACL.
Никаких .tmp mounts/Compose labels в существующих containers после выпуска.
Старый .tmp142,9GiB/156 Git worktrees оставлен для отдельного controlled cleanup;
не удалять целиком без проверки локальных изменений. Только7 own build/check
containers удалены, logs/artifacts/Sass cache retained; образы/volumes/глобальный
cache prune/Docker-WSL/VHDX не трогались. Task Runtime R280/sealed-proof.json.

## Source R279 — 2026-11-03: доступность active-ticket lookup

Поиск active ticket больше не превращает сбой internal API в Optional.empty.
Backend /internal/api/bot/tickets/active после authorization и успешного service
read выставляет X-Iguana-Active-Lookup-Version:1 и Cache-Control:no-store, включая
штатный404. Auth/read/binding/routing failure не получает completion marker.
Общий PanelTicketReadClient допускает только200/404 с ровно одним marker1;
404 с ним означает отсутствие по существующему resolver, без него — неизвестный
результат. Disabled API, другие statuses, old/future/empty/duplicate marker,
timeout/disconnect, malformed/null/blank-ID/trailing response бросают исключение.
Raw ticket/user identity и BIGINT query сохраняются. Остальные read methods
сохраняют прежний Optional contract. JPA/direct TicketService не меняется.

На проверенном MAX followup path ошибка прерывает обработку до bootstrap,
нового меню, session mutation, history publish или legacy support-chat. Claim
освобождается; тот же event после восстановления API продолжает исходный root
без дублирования, raw session остаётся exact. Positive active и доказанный empty
lookup сохранены. Marker подтверждает завершённый read, не scope grant/row lock;
R236 canonical resolver/R276 root checks и backend owning-write guards остаются.

718 tests/32 suites GREEN:539 MAX/24,99 core/4,80 panel/4;118 новых
(37 MAX chain,61 core HTTP,20 MVC). Frozen RED:118 cases/89 assertion failures/
29 controls,errors/skips0;3 sources/classes и case names exact после product edit.
Loopback HTTP проверяет HMAC и exact query/BIGINT; MAX использует real read client,
Rabbit-mode TicketService, direct session CAS/delivery guard с mocked publishers/
repositories/provider. Проверены absent/BUSINESS/GENERAL restored session, old/
unavailable API, повтор и duplicate. MVC проверяет реальный controller с mocked
guard/service; прежний MVC suite с настоящим request guard также GREEN. Это не
real PostgreSQL/Redis/Rabbit, настоящий server/provider/client или S6 acceptance;
Telegram/VK handlers отдельно не перепринимались. Новые fixtures исправлены до
первого прогона (syntax/visibility); их исходная версия сохранена.

Fresh HEAD/origin/main/actual remote5730f9579 (R278), parentdb5859a36;6 prior
paths/content verified, clean checkout. Detached source worktree/pinned R278 cache
(2679 classes), Maven wrapper/JDK17, bot-max -am и targeted panel. Scope9:2 main,
3 new tests, task/S0/BOT/new append-only changelog. Existing tests/POM/resources/
schema/flags/session JSON не меняются; Sass EOL-only output не включён в scope.
Source apply отдельно от Git/runtime, проверены scope/content/class/report hashes
и сохранность historical runtime blocks.

Source/test machine:C:\Users\sushi\Git_H\tg_ref_b24_sup. Рабочий проект на
другой машине и в другой сессии; local Docker/PG/Redis/Rabbit/provider здесь не
запускались, working runtime не измерялся и не менялся. Последний записанный
runtime R275/R274/R272 относится к исходной машине, не новое observation здесь.
R278 уже опубликован; R279 source-only, stage/commit/push/deploy не выполняются.
Будущий release:backend с completion marker сначала, затем все active children
с новым shared bot-core; старый child игнорирует новый header, новый child со
старым backend fail-closed. MAX child также требует backend context R276.
Panel-web-only не обновляет bot-core. Новых migrations/flags нет; V83/V84/V85 не
повторять, backup/rollback/private DLQ archive сохраняются. Scoped MAX byte writer,
полный Redis/live gate, legacy isolation/child principals и остальные policies/
AI/S6 остаются. S2B снятR190, DLQ gate закрытR275, общий статус задачи🟡.

## Source R278 — 2026-11-03: подтверждение MAX recovery instruction

MAX подтверждает restart_questionnaire только после подтверждённой отправки
подсказки о /start. Общая проверка используется при недоступном/изменённом catalog,
выборе BUSINESS/GENERAL, восстановлении legacy session и финализации вопросника.
Warning false/exception, повторная ошибка channel resolution или markProcessed
освобождают claim и допускают повтор того же события. Исключение отправки не
переинтерпретируется как новая ошибка runtime-config с повторным warning в том же
запросе. В outer catch добавлено явное освобождение: sibling RuntimeException
catch не обрабатывает исключения, брошенные внутри другого catch. Текст подсказки
и успешный HTTP200/restart_questionnaire contract сохранены.

540 tests/26 suites GREEN:502 MAX/23 (все386 прежних +116 новых),38 core/3.
Frozen baseline RED:116 cases,79 assertion failures/37 controls,errors/skips0.
Тот же test source,2 class hashes и case names exact после исправления.23 сценария
включают bootstrap, legacy restore, BUSINESS v1–v4/GENERAL v2–v4 menu choice,
restored complete session и новый последний ответ; false/exception/scoped-exception/
dedup failure/retry/duplicate, плюс отдельный повторный channel resolution failure.
Real handler/JSON/local CAS и delivery guard, mocked runtime/provider; последние
ответы и frozen scope/journal сохраняются без повторной записи ответа или заявки.
Положительные controls сохраняют прежний результат после доставленного warning.
Это не real Redis eviction/rebind, live SDK/customer acceptance или полный S6.

Fresh HEAD/origin/main/actual remotedb5859a36 (R277), parent14c00a87d;6 prior
paths/content verified, checkout clean. Detached worktree и pinned R277 cache
(2677 classes), Maven wrapper/JDK17/bot-max -am. Scope6:1 main,1 new test,
task/S0/BOT/new append-only changelog. Existing tests/POM/resources/schema/flags/
session JSON не меняются. Sandbox validation и root apply раздельны; exact scope,
content/class/report hashes, inverse source plan и runtime history проверены.

Source/test machine:C:\Users\sushi\Git_H\tg_ref_b24_sup. Рабочий экземпляр
на другой машине и в другой сессии; local Docker/PG/Redis/Rabbit/provider здесь
не запускались, рабочая среда не измерялась и не менялась. Последний записанный
runtime R275/R274/R272 относится к исходной машине; это не свежая проверка здесь.
R277 уже опубликован; R278 source-only, stage/commit/push/deploy не выполняются.
Для будущей выкладки нужен MAX child в bot-runner и backend context R276;
panel-web-only не активирует fix. Новых migrations/flags нет, V83/V84/V85 не
повторять; backup/rollback/private DLQ archive сохранены. Scoped MAX byte writer,
полный Redis/live gate, legacy isolation, остальные permissions/policies/AI/S6
остаются. S2B снятR190, DLQ gate закрытR275, общий статус задачи🟡.

## Source R277 — 2026-11-03: MAX attachment-only admission

MAX больше не подтверждает как пустое сообщение объявленное вложение без
подписи, имени и URL. Прямые и пересланные attachments/body.attachments/media/files
доходят до существующего v4 admission по сохранённой session либо canonical root.
Missing/mismatched root по-прежнему возвращает retryable503 без ACK. Известный
legacy root или отсутствие active/session получают отдельное явное предупреждение
о нераспознанном файле, без фиктивного текстового followup/нового questionnaire.
Warning false/exception освобождает claim; повтор того же события разрешён.
После подтверждённого warning событие помечается обработанным, следующий replay
идемпотентен. Обычный текст и действительно пустой payload сохраняют поведение.
Scoped MAX byte writer этим не реализован; файлы/metadata не сохраняются.

424 tests/25 suites GREEN:386 MAX/22 (все336 прежних +50 новых),38 core/3.
Frozen RED/GREEN:50 cases,45 baseline assertion failures/5 controls,errors/skips0;
new test source/class/case names exact после product edit. Real handler, Jackson
session snapshots и production local CAS/delivery guard в direct mode; repositories,
canonical API и provider mocked. Проверены BUSINESS/GENERAL/absent session,
8 прямых/пересланных layouts, unknown root/retry, warning false/exception/replay,
legacy NULL/v1–v3, отсутствие active и сохранность raw session. Это не real Redis
eviction/rebind, live MAX/provider/customer acceptance или S6. Первый новый fixture
не прошёл существующий saved catalog/resources contract; исправлен только новый
fixture до product edit, failed logs/source/report/class сохранены отдельно.

Fresh HEAD/origin/main/actual remote14c00a87d (R276), parent1b7c51ee2;13 prior
paths/content verified, checkout clean. Detached source worktree, pinned R276
cache/2676 classes и frozen baseline; Maven wrapper/JDK17, bot-max -am. Product
scope:только MaxWebhookController +1 new test, task/S0/BOT/new append-only changelog.
Existing tests/POM/resources/schema/flags/session JSON не меняются.

Source/test machine:C:\Users\sushi\Git_H\tg_ref_b24_sup. Рабочий Docker/проект
на другой машине и в другой сессии; здесь они не измерялись и не изменялись.
PostgreSQL/Redis/Rabbit/Docker/provider здесь не запускались. Последний известный
runtime остаётся R275/R274/R272 на исходной машине, не новое observation этого
checkout. Исторические checkpoint, backup/rollback и private DLQ archive сохраняются.
R276 уже committed/pushed владельцем; R277 source-only, stage/commit/push/deploy
не выполняются. Для будущей выкладки нужен MAX child в bot-runner вместе с
backend context R276; panel-web-only не активирует fix. Новых migrations/flags нет,
V83/V84/V85 не повторять. S2B снятR190, DLQ gate закрытR275; задача остаётся🟡.

## Source R276 — 2026-11-03: frozen MAX active followup

Source R276: MAX active followup больше не определяет media/notification policy
по наличию Redis session или текущему questionnaire catalog. Internal unique-root
lookup передаёт channelId/questionnaireVersion/followupContextVersion=1 из
canonical Ticket; default/old three-field DTO не является доказательством.
Старые Java constructors сохранены. Signed HTTP client сохраняет новые nullable
поля и exact BIGINT/raw ticket ID; отсутствующий context не превращается в legacy.
Положительный protocol marker выдаётся только для positive owner/channel IDs.
MAX проверяет exact ticket/user/channel, известные context/questionnaire versions
и status до clear-active/download/store/history publish/shared-chat notification.
При missing/ambiguous/unavailable/mismatched/unknown root возвращается retryable
503 и release delivery claim, без очистки active или нового questionnaire.
Immutable v4 root запрещает media/caption, включая forwarded/opaque attachments,
после потери session и при старом session protocol. Предупреждение клиенту должно
быть подтверждено; null session не требует prompt. V4 text идёт в прежний canonical
writer без legacy service-chat shortcut; ошибки/rebind текущего catalog не
переопределяют frozen root. Positive legacy NULL/v1–v3 behavior сохранён только
при подтверждённом backend context. Direct-storage/old-server DTO без context
для MAX active followup fail-closed; silent local/version inference нет.

434 tests/27 suites GREEN:336 MAX/21 suites (включая все252 прежних),
38 core/3 и60 panel/3.119 новых:84 MAX handler,13 signed loopback HTTP client,
22 canonical DTO mapping/serialization;92 baseline assertion failures/27 controls,
errors/skips0 на неизменённом product. Новые sources,3 class hashes и case names
после этой RED/GREEN пары exact. Проверены missing/old/future context, owner/
channel/raw-ID mismatch, unknown status/version, positive nullable legacy protocol,
BIGINT>2^53/Long.MAX, media/caption/forward/opaque и отсутствие legacy effects.
Backend tests используют real service с mocked repositories и Jackson; HTTP —
локальный server с проверкой HMAC. Это не real PostgreSQL/Redis eviction/SDK/
customer callback или concurrent admission lock через provider I/O. R236 unique
root resolver и final backend owning-write guards сохраняются. Два прежних MAX
fixtures дополнены положительными metadata/channel stubs, assertions сохранены.
Первый core compile выявил отсутствующую spring-web test dependency; выбран
эквивалентный Jackson mapper без POM changes. Первый MAX harness неверно mock-нул
token null вместо configured empty string; исправлены только новые fixtures/menu
до product edit, failed artifacts сохранены. Финальная RED/GREEN пара отдельная.

Baseline HEAD/origin/main/actual remote1b7c51ee2 (R275), parenteb64df46b;
subject/4 prior metadata paths/blobs/content verified, checkout clean. Detached
source worktree; Maven wrapper/JDK17, explicit bot-max -am reactor и targeted
panel test selection. Scope13:4 main,2 old fixtures,3 new tests,task/S0/BOT/new
append-only changelog. POM/resources/DB schema/flags/session JSON не изменены.
Source class/input hashes, exact scope, inverse plan и runtime-history guards
сохраняются в ignored r276-* artifacts; Sass EOL-only output не входит в scope.

Среда: C:\Users\sushi\Git_H\tg_ref_b24_sup — source/test session. Runtime
R275/R274/R272 относится к другой машине и исходной runtime-сессии, а не к этому
checkout, даже если старый документ пишет «эта машина/сессия». Здесь рабочий
Docker/Redis/БД/queues/provider не проверялись и не изменялись; local Docker и
тестовые серверы PostgreSQL/Redis/Rabbit не запускались. Последний документированный
рабочий runtime: web/workerR274/R273/f78708f35/image826bf025417d; runnerR272/R271/
044d9bcec/image85a47a3b975f; R275 health/readiness/production-readinessUP, DLQ0.
Это цитирование checkpoint, не новые runtime observations. Исторические blocks,
backup/rollback pointers и private quarantine archive сохранены; V83/V84/V85 не
повторять. Source R276 ещё не deployed: требуется согласованное обновление
backend и MAX child в bot-runner; panel-web-only не активирует client gate.
Новых migrations/flags нет. Commit grant30 октября истёк; stage/commit/push здесь
не выполняются, push остаётся владельцу. Source apply отдельно от Git/runtime.

Остаются scoped MAX byte writer, полный Redis session eviction/rebind/live
provider acceptance, legacy protocols isolation, остальные child/history/media/
recipient paths и отдельные child principals. Live feedback/client callback,
manual/global/project/system policies, AI privacy/retention/delivery и полный
controlled S6 остаются. S2B снятR190, задача🟡; R275 DLQ gate не возвращать в план
как незакрытый. Никакой external/working mutation этим source step не разрешена.

## Актуальный operational checkpoint — R275, 2026-11-03

Без rebuild/restart: web/worker остаются R274/R273/f78708f35/image826bf025417d,
runner R272/R271/044d9bcec/image85a47a3b975f. Все18 containers/config/БД V85
и доменные fingerprints unchanged. Общий health200/UP, readiness200/UP,
production-readiness200/ready/Rabbit healthy; прежний inboundDLQ gate закрыт.
Один219-byte malformed outbound-feedback DTO от28 октября в inbound route
сохранён в приватный same-host файловый карантин, включая все AMQP properties,
затем ACK ровно одного manual delivery после сверки body/full wire archive.
Не purge/не replay; get/requeue диагностики и validate явно учитываются.
Отправок клиенту, migrations/backfill/flags нет.61 fresh access checks GREEN,
новые ERROR0; отдельный worker WARN1 invalid Notion API token не исправлялся.
Все DLQ0/другие queue counts/consumers прежние. Published outbox, старые4 failed
Inbox rows, roots/pending/ratings/ledger/receipt не менялись; событие не объявлено
успешно обработанным. S6/live callback/eviction/rebind остаются.
Evidence: task Runtime R275/.tmp/01-278-r275-dlq/sealed-proof.json.
Private wire archive SHA2569EFE1108A7D40991A9EB86F0D4A5065A3E3D50DBD69B9773FF468B4251C1292E;
private/message.amqp содержит header/body, ACL только текущему Windows пользователю,
вне Git. Не удалять при cleanup .tmp; реальный restore/replay не выполнялся.

## Предыдущий тестовый выпуск — R274, 2026-11-03

Clean R273/f78708f3588a8264b2a7055f14772ff886d2c59c выложен только на
panel-web/ops-worker: image826bf025417d, tag iguana-panel:account-r273-f78708f35-20261103,
webbfe2c70540bf/workeraebc1a28d277, по1 replica/healthy/restart0. Bot-runner
не обновлялся: R272/R271/044d9bcec/image85a47a3b975f/b76f36ca98b0 и3 child PID
прежние. Backend initial-v4 media gate теперь загружен, flags не включались.
Scoped MAX media writer, eviction admission и полный legacy isolation остаются.

COPY-only/network-none pinned85a image, не full Maven/Sass build: panel JAR2
class payloads заменены,2229 других payloads exact,1435 frozen main classes exact.
Verified record отличается только compiler CP order/indices/source lines после
полного symbolic javap proof, не raw byte-exact. TG/VK/MAX/core JAR unchanged.
1179 inputs/1441 classes/222 tests5 suites на source/replay — проверенный reused
proof, не новый прогон. Compose --no-deps только2 роли/4 старых Compose/env inputs
unchanged, ENV/mounts/HostConfig raw exact. Overlay требует preserved-ENV operator.
Backend cold stop09:02/ready09:04 MSK, live backend consumers0 до новых,
3 feedback consumer tags/channel PIDs unchanged. Не zero-downtime/не bot restart.

61 live login/access/SSE/readiness +24 signed actual-child checks GREEN,
ROLE_ADMIN с PAGE_CHANNELS/PAGE_SETTINGS подтверждены,8 running JAR hashes exact.
Все16 нетаргетных контейнеров unchanged. Новые backend ERROR/fatal/WARN0;
Flyway skipped для обеих ролей. ReadinessUP, health503/inboundDLQ1 прежний.
V85/85/settings455/roots53/pending51/ratings36/accounts/grants/history/outbox/
ledger17delivered/receipt0/MAX session fingerprints unchanged. db-migrate/
migrations/backfill/flags/bootstrap/repair/purge/requeue/provider acceptance sends
не запускались. Backup10238933 bytes/SHA256
E08136B0A22D48DD28B07529EB55E07EBF637EB210154E72C9DF249FAE8EC66C
восстановлен в isolated PG192MiB/network-none, same-host retained; owned verifier
container/volume удалены.2 rollback R274 tags на85a сохранены, старые R272/R269/
R267/R264/R261 pointers/backups verified. Working DB не восстанавливалась,
rollback не выполнялся; backend cold stop/queues0/ledger+receipt+V85 обязательны,
возвращается старый security behavior. Evidence: task Runtime R274/
.tmp/01-278-r274-rollout/. Ниже исторические checkpoints; R273 уже не source-only,
полный S6/live callback/eviction/rebind не закрыт.

## Backend media первого v4 обращения — source R273, 2026-11-03

SelectedQuestionnaireMediaValidator при первом создании v4 root отклоняет media
MAX/unknown/null platform, даже если attachmentPath является legacy key или URL.
Media без key не считается plaintext, в том числе Telegram/VK; text с attachment
также обязан пройти owned-byte proof. Отказ до root/child/metadata writes,
inbox failed без processed. Canonical channel platform проверяется ingestion:
подмена event.platform не выбирает другой writer. Plain MAX text, ранние legacy
protocols и matching frozen-root replay прежние. Telegram/VK scoped bytes/SHA/
size/owner verification сохранены. Это запрет bypass, не MAX upload/download.

Baseline00125192c, RED52 → GREEN222 tests/5 suites на каждую независимую source/
replay копию,138 новых/84 неизменённых regression tests; failures/errors/skips0.
Real validator/ingestion, mocked storage/persistence, не PostgreSQL/SDK/provider.
1179 input/1441 class hashes сверены;1433 main unchanged,1 functional main class
и перекомпилированный Verified record (только CP ordering/indices/source lines,
full symbolic javap proof, не raw byte-exact).570 text LF/CRLF-only differences
проверены отдельно, binary raw exact. Offline bounded compile, не full build.

Source НЕ активирован: R272/R271/044d9bcec/image85a47a3b975f/all18 containers,
config/V85/ledger17delivered/receipt0/MAX session fingerprints прежние. ReadinessUP,
прежний health503/DLQ1 не исправлялся. R272 backup/rollback сохранены. Нужен
отдельный clean-commit panel-web/ops-worker release: ticket-created listener
исполняется в WORKER, web ops replay использует тот же service; bot-runner/children
ради этого server-only fix пересоздавать не требуется. Нет POM/resource/schema/
flags/DTO/session-format changes/deploy/migrations/backfill/provider sends.
Scoped MAX media, legacy isolation/live provider/callback/rebind/S6 остаются.
Evidence: task Source R273/.tmp/01-278-r273-evidence/.

## Исторический тестовый runtime — R272, 2026-11-03

Clean R271/044d9bcecab2284b2dd66a72ee2ff3b2ee7d3511 согласованно выложен на
panel-web, ops-worker и bot-runner, по1 replica, healthy/restart0. Image:
sha256:85a47a3b975f2adb97e3619f442a2f026505d5d06ef2a2430a423b3d9ccb069c,
tag iguana-panel:account-r271-044d9bcec-20261103. Web6476462123bd/
worker5ca72450180d/runnerb76f36ca98b0; Telegram1/MAX2/Telegram3 fresh startup.
MAX v4 unsupported-media отказ и backend immutable v4 gate включены вместе,
не panel-web-only. Scoped MAX media writer не реализован; legacy media отдельно.
R268 journal/R266 ack сохранены.458/23 source/replay tests переиспользованы,
не новый прогон,995 inputs/1703 classes exact. COPY-only/network-none image:
1 panel/6 MAX class payloads изменены, TG/VK/core JAR byte-exact прежние.
Не full Maven/Sass build. Старые web/worker panel JAR не содержали только уже
применённый V85 SQL resource относительно runner;2230 общих payloads exact.

Coordinated cold stop3 ролей/children, queues0, затем worker → web → runner,
без mixed fleet; maintenance около07:02–07:09 MSK. Compose --no-deps,
tg_ref_b24_sup, прежние production-contour/production-observability/.env и новый
.tmp/01-278-r272-rollout/compose.services.json. Overlay требует preserved-ENV
operator; bare up с placeholder ENV недопустим. ENV/mounts exact; HostConfig
web/runner raw exact, worker semantic exact с bounded binds/empty DNS proof.
15 нетаргетных контейнеров/5 старых Compose/env inputs unchanged.

61 fresh live login/access/SSE/readiness +24 signed actual-child admission GREEN,
ROLE_ADMIN с PAGE_CHANNELS/PAGE_SETTINGS подтверждены, session logout;12 running
JAR hashes exact.3 feedback consumers/ready0/unacked0, ERROR/fatal0 у3 ролей и
children. ReadinessUP; общий health503/inboundDLQ1 и TLS/iiko WARN прежние.
V85/85/checksum -2092309160, settings455/roots53/pending51/ratings36/ledger17delivered/
receipt0/history/accounts/grants/flags unchanged. Web/worker Flyway skipped;
runner normal Flyway validation85/no migration necessary, новых миграций не
применено. db-migrate не запускался; backfill/flags/bootstrap/repair/purge/requeue
не выполнялись. Redis EVAL_RO MAX session/index/journal exact,0 sessions.
Acceptance provider/customer сообщений не отправляли.

Backup10297747 bytes/SHA256
023063666B682A455F0B8EB03F7C017708D773635826A96D95DF07543B75FB0B
изолированно восстановлен на V85, retained same-host; owned PG verifier container/
volume удалены.3 новых R272 rollback tags сохранены: web/worker image90cd11900131,
runner7259bf144f18. R269/R267/R264/R261 backups/pointers проверены. Working DB
не восстанавливалась, rollback не выполнялся; cold stop/queues0/ledger+receipt+
V85/session compatibility обязательны, старый security behavior возвращается.
Это не live client callback/scoped media/rebind/первый production S6. Evidence:
task Runtime R272/.tmp/01-278-r272-rollout/sealed-proof.json. Ниже исторические
source/runtime checkpoints; прежнее «source-only R271» описывает момент подготовки.

## MAX v4 media без silent loss — source R271, 2026-11-02

Пока scoped MAX media writer не реализован, выбранный v4 вопросник явно
отклоняет файл вместе с подписью. Menu/answer/followup не продвигаются, raw session/
scope/answers/journal прежние, download/AttachmentService/publication не вызываются.
Клиенту предлагается отдельное текстовое сообщение; first media создаёт лишь
меню, без bootstrap caption/history. Warning delivery false/exception бросает
исключение/release claim, retry восстанавливает тот же отказ/меню. Existing chat
mismatch проверяется раньше. /start и /cancel остаются явными командами.
Pure decoder распознаёт объявленное media даже без готового URL, в direct и
forwarded payload; legacy extraction/filtering/record signatures не менялись.

InboundClientMessageIngestionService для media canonical v4 root всегда требует
scoped verifier — даже для MAX/unknown platform/missing catalog proof. Подмена
event.platform не отключает gate. Unsupported MAX event отклоняется до child
history/metadata/profile/activity; нет legacy fallback/reroute. Plain text,
старые MAX v1–v3/NULL и проверенный Telegram single writer сохранены; VK batch
ветка прежняя. Это запрет небезопасного пути, не поддержка MAX uploads/downloads.
Legacy MAX media вне v4 остаётся отдельным незакрытым путём.

458 выполнений/23 suites на каждую независимую source/replay копию:252 MAX,
64 backend,142 Redis; failures/errors/skips0.995 input hashes/1703 class hashes
exact, оригинальные тесты/assertions сохранены. Три функциональных main classes;
4 nested record class отличаются только source line numbers (full javap proof).
212 core/1434 backend main classes unchanged. Backend проверен bounded compile
одного класса и двух test files с exact verified baseline dependencies, не full
Maven/Sass/package; новый Redis64MiB/CPU1/AOF удалён после tests.

Пакет ещё НЕ выложен. Для активации нужен отдельный clean-commit согласованный
release panel-web/ops-worker и MAX child в bot-runner: inbound listener имеет
RuntimeRole.WORKER, panel-web-only не включает серверный gate в очереди;
web ops replay также использует общий ingestion service. Новых POM/schema/flags/
DTO/session-format changes нет. Перед выпуском нужны fresh actual ENV/replicas/
images/backup/rollback и queue/session compatibility guards, не слепой mixed
fleet update. Runtime R269 ниже, все18 working containers/DB V85/ledger17/receipt0/
MAX session fingerprints прежние, readinessUP/3 consumers/ready0/unacked0.
Прежний health503/inboundDLQ1 не исправлялся. Нет deploy/migrations/backfill/
flags/provider sends. Scoped MAX media/live SDK/rebind/full application/S6 остаются.

## Изолированная Redis-приёмка MAX — test-only R270, 2026-11-02

MaxQuestionnaireRedisIT наследует109 исходных handler scenarios без копирования/
ослабления assertions. Реальные session store/delivery guard используют Redis
Lua/CAS; runtime/API/provider — test doubles, Spring application не запускается.
BUSINESS1–4/GENERAL2–4, answer/back/optional skip после ACK/prompt failure/dedup
reset и final/optimistic/legacy cases проверяются на Redis. Дополнительно две
v4 ветки BUSINESS/GENERAL проходят настоящий graceful Redis restart с AOF,
fresh Lettuce connection и independent store/loadAll; raw session/scope/selection/
journal/index/positive TTL/processed markers сохраняются. Удаляются только
delivery keys, старое событие не меняет ответ/позицию, fresh messages завершают
исходный выбранный command. Ещё1 проверка — stale CAS и channel/user/namespace
isolation.112 Redis executions, не112 новых уникальных handler scenarios.

Воспроизводимый вход: java-bot/bot-max/run-redis-acceptance.ps1 с обязательным
параметром RedisImage — exact sha256 ID уже закэшированного Redis7 image.
Mutable tag/pull/default live address не используются. Требуются Windows
PowerShell/Docker, JDK/Maven wrapper и заранее закэшированные Maven dependencies;
прогон offline. Harness создаёт только собственные новые container/network/volume
с owner+nonce labels, loopback port (выбранный свободный и закреплённый для
restart),64MiB/CPU1, AOF/appendfsync always/noeviction и одноразовый пароль.
Dedicated bridge не является network-none/internal: Docker Desktop не публиковал
host port для internal network. Подключения к project network/Compose/.env нет.

Harness передаёт только fixture keys QUESTIONNAIRE_REDIS_OWNER/PASSWORD/
CONTAINER_ID/IMAGE_ID/PORT; адрес в Java строго127.0.0.1/database0. Перед записью
проверяются exact container ID/name/image/labels/port/memory и Redis sentinel.
Перед restart повторяется guard; неполная настройка explicit IT — ошибка, не
skip/local fallback. Каждый тест имеет уникальный namespace, удаление только
его ключей через bounded SCAN; при dedup reset session/index exact, нет FLUSHDB.
В конце DBSIZE2 (только sentinels), finally удаляет только owned resources и
восстанавливает ENV. Proof/Redis log и обычный Surefire XML лежат в bot-max/target.
Default Surefire/Max*Test не запускает IT; POM/main/runtime resources не менялись.

214 default/20 suites +112 Redis/1 suite =326 выполнений на каждую независимую
source/replay копию, failures/errors/skips0.251 inputs/265 class hashes exact,
244 runtime classes byte-exact R268. Maven96MiB/test JVM192MiB/CPU2, последовательно.
Runtime R269 ниже и все18 рабочих containers/DB V85/ledger17delivered/receipt0/
MAX session fingerprints прежние; readinessUP/3 consumers/ready0/unacked0.
Общий health503/inboundDLQ1 прежний. Backup/rollback сохранены, rebuild/deploy/
migrations/flags/provider acceptance sends не выполнялись и для test-only пакета
не нужны. Это не live MAX webhook/provider/client callback, session eviction,
полный bot JVM/launcher/fleet restart/rebind или первый production S6.

## Рабочий тестовый bot runtime — R269, 2026-11-02

Clean R268/da7a1523190c21ae89b377f12320725e330954bb, только bot-runner:
sha256:7259bf144f18701df2c5393f6ae87863f962c80ef81cc834f8d4dbc913fd8215,
tag iguana-panel:account-r268-da7a15231-20261102/containerd6948323c681,
healthy/restart0/1 replica. MAX2 использует durable acceptedEventKeys выбранного
BUSINESS/GENERAL вопросника.32 frozen main classes exact;3 functional payloads
и1 nested record class с изменением только source line numbers.273 остальных
MAX ZIP payloads/entry set277 exact. Panel/Telegram/VK/core JAR byte-exact прежние.
COPY-only/network-none incremental release, не full Maven/Sass build;
249 inputs/264 class hashes/214 tests в20 suites — reused source/replay proof,
не новый прогон. Test/acceptance classes в image не включены.

Old runner/все children cold-stopped,3 feedback queues0 consumers/ready/unacked
до нового fleet. Telegram1/MAX2/Telegram3 fresh startups и3 consumers/ready0/
unacked0, live VK не запущен.61 ENV/mounts exact, HostConfig semantic exact;
raw hash отличается только bind order/empty DNS — полный старый hash восстановлен.
5 старых Compose/env inputs exact; новый runner-only overlay .tmp/01-278-r269-
rollout/compose.services.json требует preserved-environment operator.
17 остальных containers exact, web/worker R257/image90cd, db-migrate прежний.

Backup pg_dump custom10232724 bytes/SHA256
69A465915709A31E986D8E728AA8A23F413D97AB3AFA871F83495DB454DC9D40
восстановлен в isolated PG192MiB/network none; same-host сохранён, temporary
verifier resources удалены, working DB не восстанавливалась. V85/85/checksum
-2092309160/settings455/accounts/grants/roots/pending/ratings/outbox/ledger17
delivered/receipt0 unchanged. Redis EVAL_RO:0 MAX sessions/index/journals до/
после, aggregate hashes exact; keys/answers/users/secrets не сохранялись.

61 live access/SSE/readiness +24 signed actual-child API checks GREEN;4 running
JAR hashes exact, fresh runner/child ERROR/fatal0/restart0, logout выполнен.
ReadinessUP, общий healthDOWN/inboundDLQ1 прежний. Нет migrations/flags/backfill/
bootstrap/repair/purge/requeue/provider acceptance messages. Новый rollback tag
iguana-panel:rollback-r269-bot-runner-20261102-9cc3a7c69d95 сохранён на R266/image9cc3,
старые R267/R264/R261 pointers/backups exact. Это не выполненный rollback;
нужны cold stop/queues0/совместимые ledger+receipt+V85 и MAX Redis state. При новых
session journals нельзя автоматически откатываться на не поддерживающий их код.
Live questionnaire/Redis eviction/provider/client acceptance и S6 не доказаны.
Task Runtime R269/.tmp/01-278-r269-rollout/ — evidence; ниже исторические записи.

## Тестовый bot runtime — R267, 2026-11-02 (исторический checkpoint)

Clean R266/d0b467204c68b205033bee0070119568ce377245, только bot-runner:
sha256:9cc3a7c69d9595094e4d372de868ea6cd33a3dc8302f327c7909d7d745cb53e1,
tag iguana-panel:account-r266-d0b467204-20261102, container9612381dedaa,
healthy/restart0/1 replica. Shared FeedbackService fix активен: неподтверждённая
запись не разрешает platform success acknowledgement, unknown не считается
доказанной недоставкой.212 tested core classes exact,211 unchanged; заменён
только FeedbackService.class в nested STORED core трёх platform executable JAR.
Все outer payloads/panel JAR неизменны; offline core package/COPY/network none,
не full Maven/Sass build.501/5 — reused source/replay proof, не новый прогон;
296 inputs/232 class hashes сверены, test-only module не включён в runtime.

Согласованный cold stop всех old children/queues0, затем fresh Telegram1/MAX2/
Telegram3;3 consumers/ready0/unacked0. Live VK не запущен.61 actual ENV/mounts
exact, HostConfig semantic exact; raw hash отличается лишь порядком binds и
empty DNS, полный исходный hash восстановлен bounded guard.5 старых Compose/
env inputs прежние. Новый runner-only overlay .tmp/01-278-r267-rollout/
compose.services.json требует preserved-environment operator.17 нетаргетных
containers exact unchanged, web/worker R257/image90cd, исходный db-migrate exited.

Свежий pg_dump custom10208054 bytes/SHA256
E0813789EB95886C2B864FABF9EAA3004AD9F1C794A5C44C2661C195EEC9068B
восстановлен в isolated PG192MiB/network none, same-host backup сохранён,
verifier container/volume удалены. Working DB не восстанавливалась. V85/85/
checksum -2092309160,14 columns/10 constraints/FK прежние; settings455/ledger17
delivered/receipt0 и accounts/grants/roots/pending/ratings/outbox/flags unchanged.
Нет migrations/backfill/flags/bootstrap/repair/purge/requeue/provider messages.

61 live access/SSE/readiness и24 signed admission GREEN, logout выполнен;
4 running JAR hashes exact, fresh runner/child ERROR/fatal0/restart0.
ReadinessUP; общий healthDOWN/inboundDLQ1 остаётся отдельным operational gate.
Rollback tag iguana-panel:rollback-r267-bot-runner-20261102-9898448385a0 сохранён
на R263/image9898, старые pointers прежние. Это не выполненный rollback;
необходимы cold stop/queues0/совместимые ledger+receipt+V85, не удаление данных.
Live SDK/client callback/in-flight application recovery/unknown review/retention/
full S6 не доказаны. Task Runtime R267/.tmp/01-278-r267-rollout/ — evidence;
следующие записи описывают исторические source/runtime checkpoints.

## MAX questionnaire event replay — source R268, 2026-11-02

MaxConversationSession.Selection сохраняет acceptedEventKeys тем же session CAS,
что choice/page/answer/back/skip. Prompt и webhook ACK идут после сохранения.
MaxWebhookController на повторе возвращает ok/questionnaire_replayed, повторяет
только текущий prompt и не меняет raw state, answers/history/scope/template/
catalog. Это не exactly-once prompt delivery. При потере ответа после final-answer
CAS submission восстанавливается с последним исходным accepted event ID; новое
сообщение не становится финальным событием и не теряется в регистрации root.

Не более4096 уникальных непустых IDs, immutable copy; malformed/overflow и
расхождение нового submission запрещены. Старый JSON без поля сохраняет scope/
progress с пустым журналом, без выдуманных старых IDs; пустое поле опущено при
сериализации. Legacy direct/webhook reply для финального ticket_id и root binding
сохранены. Нет новых migrations/API/SQL/feature flags или transport principals.

214/20 full MAX tests GREEN (81 новая), source/replay независимо: BUSINESS v1–v4,
GENERAL v2–v4; ACK/prompt failure и потеря dedup cache, back/skip/restore, final
CAS gap, optimistic retry, delayed replay и JSON guards. RED regression1/1 →
GREEN,249 frozen inputs/264 class hashes exact;212 core classes unchanged.
Реальные handler/settings normalizer/local JSON store/CAS/delivery guard, fake
runtime/provider/TicketService; это не real Redis/provider/client acceptance.
Первая матрица corrected fixture GENERAL сохраняет обязательный соседний
BUSINESS choice, production catalog validation не ослаблен.

На source R268 runtime R267/R266 не менялся. В R269 выше fix выложен отдельным
clean-commit bot-runner release; повторять выпуск не требуется, acceptance classes
в образ не включены. Пять крупных блоков/S6 и прежний operational health gate остаются.
Task Source R268/.tmp/01-278-r268-evidence/ — evidence.

## Тестовый bot runtime — R264, 2026-11-02 (исторический checkpoint)

Clean R263/8dbb083206c36426d5aab229d6ae36510bf57cd3, только bot-runner:
image9898448385a0/tag iguana-panel:account-r263-8dbb08320-20261102,
containerc068584505b2/healthy/restart0/1replica. Telegram1/MAX2/Telegram3
запущены после cold stop всех прежних children/3 queues0 consumers/ready/unacked
и применения V85. Нет mixed old/new fleet. Live VK child не запущен.
212 core/72 platform frozen tested classes exact,1435 panel classes прежние;
panel JAR дополнен только V85 SQL.4 running JAR hashes exact. COPY/network-none,
не full Maven/Sass build;769/21 — reused source proof, не новый прогон.

V85/checksum -2092309160/85 successful history rows применена schema-only
Flyway10.17/one-shot Compose db-migrate --no-deps, не полным Spring boot:
нет security bootstrap/legacy normalize/repair/baseline/clean. Оригинальный
db-migrate и все17 остальных containers прежние, web/worker R257/image90cd.
61 ENV/mounts/HostConfig и5 существующих Compose/env inputs exact. Новый overlay
.tmp/01-278-r264-rollout/compose.services.json требует preserved-environment
operator, не самостоятельный Compose up с отсутствующими placeholders.

Backup восстановлен изолированно; V85 сохраняет V1–V84 history/данные, old84 SQL
validate/migrate0 на V85 проверены. Settings455/ledger17delivered/receipt0;
accounts/grants/roots/pending/ratings/outbox/flags unchanged.61 live access/SSE/
readiness и24 signed admission GREEN,3 fresh startups/3 consumers/ready0/unacked0.
Fresh ERROR/fatal0; общий healthDOWN/inboundDLQ1 прежний, operational gate не
закрыт. Нет backfill/flags/purge/requeue или внешних acceptance messages.

Rollback tag iguana-panel:rollback-r264-bot-runner-20261102-904fba5c4e40 сохранён
на R260, не выполнен. Только cold stop всех новых children/consumers + пустой
receipt journal + exact ledger/inflight + V85 compatibility. Не удалять журнал
и не восстанавливать working DB автоматически. Same-host backup сохранён,
temporary restore container/volume и migrator удалены. Старые pointers прежние.
Receipt reconciliation на replay с committed positive evidence активна; нет
startup/batch scanner, unknown без receipt, live SDK/client rating callback,
in-flight process kill/restart proof, scoped review/retention или первого S6.
Подробности/evidence — task Runtime R264/.tmp/01-278-r264-rollout/.

## Rating callback acceptance и неподтверждённая запись — source R266

RabbitMQ FeedbackService.storeFeedback теперь бросает IllegalStateException,
если PanelTicketWriteClient не подтвердил updated. Все3 platform call sites
выполняют thank-you/feedback_saved только после нормального возврата; false,
stale context и unknown HTTP не разрешают ложное подтверждение. Это не означает,
что unknown точно не записан. Void API/success/legacy JDBC path сохранены.

Изолированный prompt/receipt → shared bot service → signed read/write HTTP →
backend controllers/JPA/native V84 locks/transactional scoped UI outbox проверен:
501 executions/5 suites GREEN,242 новых (241 callback +1 unit), source/replay
независимы. Atomic rating/UI event/expiry, duplicate/concurrent replies, lost
response/idempotent retry, context drift, Unicode/bigint, HMAC/API refusal и3
SQL failure stages без new provider. Ticket owner adapter читает real JDBC rows;
это не live platform webhook/SDK/client callback или full Spring Security/TLS.
211 unrelated core classes/1435 panel exact, одна main class изменена.
В R267 выше выполнен отдельный clean-commit runner release без новых migrations/
flags; acceptance module в image не включён. Остальные
launcher/fleet/unknown review/retention/S6 gates остаются. Task Source R266/
.tmp/01-278-r266-evidence/.

## Process-loss acceptance — source R265, 2026-11-02

Test-only delivery worker запускается отдельной JVM с production listener,
dispatcher, MessagingService, ledger/receipt и signed HTTP clients. Родитель
принудительно убивает процесс после committed claim/prepare, ответа provider,
committed receipt/delivered-before-ACK. RabbitMQ автоматически возвращает исходное
unacked сообщение; новая JVM/другой PID получает redelivered=true/тот же payload
SHA256. Нет ручной публикации/replay. Positive receipt завершает delivered без
SDK/admission, unknown без receipt остаётся blocked, уже delivered/receipt/
outbox rows и timestamps неизменны, redelivery ACKed. Revoke channel/catalog и
API503 не превращают историческую квитанцию в новое разрешение доставки.

60 matrix cases на4 scope ×3 routes ×5 crash points,8 input guards,1 smoke;
323 выполнения/4 suites GREEN с прежней регрессией. Source/replay независимы,
не суммируются.212 core/1435 panel classes exact, product main не меняется;
runtime R264/V85 остаётся прежним, test-only acceptance не включать в образы.
Worker принимает только isolated loopback endpoints/owned schema/fake credentials,
не наследует Spring/provider/datasource ENV и не читает application/shared-config.
Это не live SDK, Spring boot/launcher/fleet restart, client callback, exactly-once,
startup/batch recovery, unknown review или S6. Подробности: acceptance README,
task Source R265/.tmp/01-278-r265-evidence/.

## Исторический тестовый bot runtime — R261, 2026-11-01/02

Clean R260/5731c6d483010990aebe0a35df21089defce0d9a выложен только в
bot-runner: image904fba5c4e40/tag iguana-panel:account-r260-5731c6d48-20261101,
containerccae25c43d88, healthy/restart0/1replica. Telegram1/MAX2/Telegram3
стартовали с новым core после остановки всех старых children/consumers.
Cold gate0 consumers/ready/unacked исключил mixed fleet. Web/worker остаются
R257/7b9fea55a/image90cd11900131; все17 нетаргетных контейнеров exact unchanged.
61 ENV/mounts/полный HostConfig exact; старые overlays/rollback tags сохранены.
Новый .tmp/01-278-r261-rollout/compose.services.json требует preserved ENV,
не самостоятельного Compose up с потерей process placeholders.

В трёх executable JAR заменён ровно один nested bot-core;208 tested classes и
все остальные entry payloads exact, panel JAR R257 не менялся.4 JAR hashes
проверены в образе и работающем runner. Это incremental COPY/network-none,
593/11 — прежний R260 proof, не новый прогон/full build. Live VK child отсутствует.
24 signed admission и61 access/SSE/readiness checks GREEN,3 fresh startup/3
canonical consumers, ready/unacked0. Readiness UP, ERROR/fatal0; общий health
DOWN из-за прежнего inbound DLQ1. Миграций/backfill/flags/purge/requeue и внешних
acceptance messages нет; ledger17 delivered/точный MD5 прежний.

Rollback tag iguana-panel:rollback-r261-bot-runner-20261101-d9c597493338 сохранён,
но откат не выполнен: R252 не понимает новые статусы. Нужны остановка новых
children/consumers и проверка ledger/inflight compatibility. Backup успешно
восстановлен изолированно; рабочая БД не восстанавливалась. Reconciliation по
receipt, live rating callback/customer feedback и full in-flight process restart
остаются. Этот локальный test release не является первым production S6.
Подробный checkpoint/evidence — task Runtime R261/.tmp/01-278-r261-rollout/.

## Durable provider receipts — source R263

Для новой runtime receipt path обязательна backend/Flyway V85. Она сохраняет
прежние ledger rows и добавляет integration_outbound_delivery_receipts. Java-bot
не создаёт receipt table: отсутствие схемы останавливает runtime bean до SDK.
В исходном source-only checkpoint R263 рабочий стенд оставался R261 без V85.
В R264 выше отдельный schema gate выполнен, V85 и новые children уже активны.
Это не разрешает обходить schema/backup/cold-fleet gates при новом выпуске.

Runtime dispatcher использует5-argument constructor с обязательным receipt
service; старый4-argument constructor и boolean Messaging API остаются source
compatibility contract, не новым способом отключить runtime journal. Observer
фиксирует committed nonce после durable sending и до SDK, pinned identity
проверяется после DB I/O. В journal — exact kind/route/channel/user/raw ticket/
request/platform/sender fingerprint/prompt SHA256, не prompt/token/full body.

Telegram/VK/MAX receipt adapters возвращают ProviderSendResult с подтверждённым
ID ответа и не повторяют SDK. Telegram проверяет chat+positive message_id, VK
positive SDK Integer для исходного peer, MAX — message.body.mid + точный integral
recipient.user_id из [официального POST /messages](https://dev.max.ru/docs-api/methods/POST/messages).
Compatibility default может вернуть boolean success без ID, но не создаёт
фиктивную квитанцию; legacy/support-chat sends не переделаны в receipt flows.

Положительный receipt сохраняется отдельной REQUIRES_NEW транзакцией до
markDelivered. Nonce/sender/user/полный ledger context и неизменность provider ID
проверяются; prepare/record/reconcile имеют максимум3 persistence attempts.
После восстановления service/consumer явный replay sending/uncertain с таким
receipt записывает delivered/observed accepted_at, не обращаясь к SDK/admission.
Нет автоматической разблокировки unknown без evidence, startup scan, requeue,
текущей recipient policy evaluation или выдачи новых прав. Receipt — локально
сохранённый результат ответа, не cryptographic proof и не read receipt клиента.

769/21 suites GREEN/135 новых, включая V85 Flyway/real PG fault injection,
SDK/HTTP response validation и48 recovery/unknown E2E на4 scope ×3 routes.
Source/replay независимы. При source-only R263 working migration/deploy не было;
фактическая V85/выкладка отдельно зафиксирована R264 выше, без backfill/flags/messages.
Full process kill/restart, live provider/client callback, неизвестный исход без
сохранённого receipt и operational review/retention/S6 остаются. Для будущего
релиза нужны отдельные V85 backup/rollback и coordinated fleet gates.

## Подтверждение уже известного успеха — source R262

Если provider вернул true, а запись результата или ответ commit потеряны,
markDelivered повторяет только ledger-транзакцию — максимум3 попытки, каждая
REQUIRES_NEW/READ_COMMITTED/timeout10s. Повторяются DataAccessException и
TransactionException; interrupt/ownership/programming error останавливают цикл.
SDK/recipient/sender admission/beginDelivery в этом цикле не вызываются.
Повторно найденный committed delivered с delivered_at подтверждается read-only,
без перезаписи исходных timestamps/metadata. sending → delivered — единственный
новый переход; uncertain и неизвестные результаты не переоткрываются.

625/12 suites GREEN (419 core +206 isolated PG/Rabbit/HTTP),32 новых real-PG
cases, включая потерю acknowledgement после настоящего commit и точный предел3.
Это не receipt reconciliation после process loss, не live provider/callback и
не exactly-once. Schema/DTO/API signatures прежние; source-only/no deploy,
рабочий runtime остаётся R261. Все прежние неизвестные исходы fail-closed.

## Feedback claim и неопределённый результат — source R260

INSERT ON CONFLICT DO NOTHING и CAS по status/прочитанному updated_at дают
одного claimant для наблюдавшейся retryable-генерации. Только новый pre-provider
`retryable` разрешён для явного replay с новой recipient/sender авторизацией.
Старые `failed`, processing (включая stale), sending, uncertain, delivered и
неизвестные статусы не разрешают новую отправку. Автоматического reclaim по
15-минутному таймауту больше нет; старые failed не классифицируются эвристикой.

MessagingService сохраняет processing → sending через durable hook до SDK,
независимо от caller transaction. Sender identity закреплён до admission и
перепроверен после DB I/O. Provider true + сохранённый результат → delivered;
false/exception/нет ответа после handoff → uncertain, сбой сохранения результата
может оставить sending. Оба состояния запрещают повтор. До handoff отказ →
retryable; поздний markFailed не открывает sending/uncertain/delivered.
Boolean false, в том числе локальный provider503, не универсальная гарантия
недоставки live SDK. Receipt reconciliation/операторское разрешение блокировки
не реализованы; нельзя предполагать успех/недоставку по времени ожидания.

593 executions/11 suites GREEN:387 core +206 isolated PostgreSQL/Rabbit/HTTP,
включая deterministic race/rollback и36 unknown-result cases. Это не exactly-once,
live SDK/callback/full-process/fleet/S6. Production changes только3 bot-core
services, schema/Rabbit DTO не меняются. R260 source активирован в R261 выше.
Выкладка исключила одновременную работу R252–R259 и R260 children; старый ledger
не знает новых статусов. Rollback при новых ledger rows требует отдельной
проверки, не автоматического запуска старого consumer. В самом source-пакете
R260 не было деплоя, внешних сообщений, миграций, flags или рабочей очереди;
последующий R261 deploy фиксируется отдельно.

## Изолированная feedback delivery chain — source R259

Opt-in `feedback-chain-acceptance` подключает `bot-feedback-acceptance`; обычные
четыре bot modules и runtime images не меняются. Подготовка и ограничения —
[README модуля](../java-bot/bot-feedback-acceptance/README.md).

157 unique cases GREEN: реальный panel outbox/V84, PostgreSQL и RabbitMQ с
publisher confirm, production container factory/listener/converter, persistent
delivery ledger, реальные signed HTTP clients и panel controllers/HMAC/recipient
SQL/channel JPA/credential resolver, MessagingService и loopback provider HTTP.
BUSINESS/GENERAL/SHARED_GENERAL/LEGACY × Telegram/VK/MAX; replay после consumer
restart, late revoke/expiry/ABA/rotation, API/HMAC deny, подмена child/user/prompt,
explicit retry после известного provider503. Unicode/raw IDs и bigint >2^53
сохранены, private feedbackTicketContext не уходит в broker wire DTO.

Catalog и чтение канала ботом — контролируемые seams; provider — имитатор, не
platform SDK. HTTP bridge standalone MockMvc с UTF-8, не full Boot security/TLS/
proxy. Consumer restart создаёт новый dispatcher/ledger, но не новый JVM или
launcher lease. Sequential replay не доказывает concurrent failed/stale claim,
exactly-once или recovery при неизвестном provider outcome. Live client/rating
callback/fleet/S6 ещё впереди. Рабочий runtime остаётся R258; пересборки, deploy,
миграций, изменений flags/рабочих очередей и внешних отправок в R259 нет.

## Прямые Telegram-отправки панели — source R255

ChannelTransportService (admin test, configured support/broadcast/custom send и
recipient preview) использует общий BotChannelCredentialResolver. Explicit binding
positive/unique/active/same-platform/nonblank; broken reference не берёт inline
token. Inline допустим только без binding. Каталог перечитывается для каждого
recipient и после network setup; late mismatch credential snapshot/captured
channel ID запрещает HttpClient.send. Это не lease на persisted channel и не
policy прав участников внешнего чата.

Удаление credential сохраняет ссылки каналов broken до явного rebind/clear.
Создание учитывает максимум catalog IDs и всех channel references, включая
inactive: новая запись не должна случайно занять ещё referenced ID удалённой.
Long.MAX_VALUE даёт409 без catalog write; concurrent catalog writers этим не
сериализуются. Неизвестный configured target отклоняется, без support-chat fallback.
Provider failure не считается success, internal retry/fallback нет.
GetMe/metadata и остальные direct paths в этот packet не включены.

R255 source proof:598 tests/12 suites. В R256 (2026-11-01) код выложен из clean
0df04ecf6 на web/worker image e02456c19ea4; runner/три child JVM остаются R252/
d286cd6cd/image d9c597493338. Bot JAR unchanged; panel JAR incremental, не full
Maven/Sass build.61 live access +24 signed child/backend admission GREEN,
один реальный diagnostic send через новый resolver в сохранённую Telegram-группу
подтверждён provider ok=true. Это не positive MessagingService/feedback chain
и не participant policy внешних чатов. Подробности/rollback — task checkpoint R256;
повторный deploy/migrations/flags этим документом не разрешены.


### Свежий channel delivery snapshot — source R257

testChannel/sendConfiguredMessage фиксируют transport context до выбора targets,
один на весь batch. Captured object и свежая scalar projection строки channels
сверяются до network setup и перед каждым send. Native REQUIRES_NEW/read-only/
READ_COMMITTED query не использует прежний JPA/OSIV entity или caller snapshot.
id/active/platform/token/credential binding/supportChatId/deliverySettings/
platformConfig должны совпадать; missing/mismatch/read error deny, без reroute
или retry. R255 shared credential rechecks сохранены, snapshot.toString redacted.
Catalog credentials разрешаются заново на каждого получателя, не заморожены batch.

835/15 tests GREEN,35 real PostgreSQL cases подтверждают cached REPEATABLE_READ
boundary. Provider mocked; positive feedback и реальные права участников чатов
не проверены. Нет lease/атомарности последней проверки и provider I/O, ABA/
exactly-once не обещаются. Global network config/прочие direct paths не включены.
В R258 (2026-11-01) R257 выложен из clean7b9fea55a на web/worker image90cd11900131;
runner/три child JVM остаются R252/d286cd6cd.61 live access +24 signed admission
GREEN; один diagnostic TG send подтвердил native fresh snapshot на рабочей БД
и provider ok=true. Полная feedback/revoke chain этим не проверена.
Rollback/evidence — task R258; повторный rollout/migrations/flags не разрешены.

## Привязка отправителя к provider credentials — source R252

Новые `APP_BOT_CHANNEL_ID`/`APP_BOT_PLATFORM` передаются launcher и связываются через
shared YAML с immutable `BotRuntimeSenderIdentity`. Нельзя подставлять сюда
`GROUP_CHAT_ID`/`VK_OPERATOR_CHAT_ID`; `MAX_CHANNEL_ID` уже canonical, но не заменяет
общий contract. Missing inputs/provider identity запрещают send; default метода
`OutboundMessenger.senderIdentity()` — deny, не event-derived fallback.

Telegram/VK/MAX adapters получают fingerprint именно из token фактического
provider; в новом signed POST `/internal/api/bot/outbound/authorize-sender` идут
только `channelId`, `platform`, `tokenSha256`. Перед каждой отправкой через
MessagingService backend заново читает current channel и разрешает effective
credential общим с launcher resolver. Explicit binding должен быть positive,
unique, active, same-platform, с nonblank token; missing/duplicate/inactive/foreign
binding не переключается на inline token. Inline token допустим только без binding.
Digest сверяется exact; response содержит только channelId/platform/boolean allowed.
API read-only использует прежний internal token/HMAC/body guard, не write-idempotency
cache. HTTP error/404/unavailable/malformed/mismatch/deny запрещают provider call.

После I/O повторяются local channel/proof checks: mutation/rotation во время
authorization не отправляет сообщение. Support-chat ID также не должен поменяться.
Raw provider token не передаётся в новом API; proof.toString редактирован. Прежние
recipient authorization R249 и child route R251 не заменяются sender admission.

Это закрывает credential matching для MessagingService, не direct legacy provider
calls и не права всех участников внешнего service-chat. Shared internal API/SQL
credentials не стали отдельными per-child principals; API→provider atomicity и
exactly-once/in-flight revoke не обещаются. Сначала нужны оба новых panel-web API
R249/R252, затем launcher bot-runner R252 и совместимые три bot JAR. Старый web404
или старый launcher без canonical identity намеренно запрещает отправку.

Исторический R252 candidate с тремя bot JAR и incremental panel JAR затем
развёрнут в R253 из clean d286cd6cd: web/worker/runner и3 child JVM, V84/flags
прежние. R254 подтвердил diagnostic panel→Telegram send, не feedback chain.
R256 обновил только web/worker до R255; runner/bot artifacts не менялись.
Positive feedback/revoke/replay и первый production S6 остаются отдельной приёмкой.

## Child feedback route admission — source R251

`OutboundFeedbackPromptListener` фиксирует `outbound-routing-key` и
`outbound-queue` при создании из `IntegrationRabbitProperties`. До dispatcher
проверяются positive canonical `event.channelId`, exact lowercase platform
`telegram|vk|max`, received routing key и обе настройки child process:

- `integration.outbound.feedback.prompt.<platform>.channel.<channelId>`;
- `iguana.integration.outbound.feedback-prompt.<platform>.channel.<channelId>.bot`.

Все значения должны совпасть точно с существующим canonical launcher contract:
без topic wildcard, trim, case folding, leading-zero aliases или default channel.
Null/blank/mismatch отклоняется generic exception до ledger/API/provider side
effects. Mutable properties не перепривязывают созданный listener; valid event
передаётся прежнему dispatcher один раз, его exception не глотается/не ретраится
listener. DTO/env/bindings/container DLQ/requeue policy не меняются.

Это защита от foreign/misrouted consumer delivery, не authenticated per-child API
principal и не проверка соответствия фактических provider credentials настроенному
route. Согласованно неверный launcher contract/credentials и API→provider TOCTOU
ещё требуют отдельного gate. R248–R251 source-only, runtime остаётся R246/R247;
будущим новым bot artifacts сначала нужен panel-web API R249.

## Авторизация отложенного feedback prompt — source R249

Дополнение R250 (source-only): `MessagingService` требует resolved positive
channel ID, active channel и explicit platform с exact registered messenger для
user/support-chat send. Missing/blank/unknown platform/provider не переключает
отправку на Telegram; numeric recipient IDs нельзя интерпретировать в другой
платформе. Case-insensitive registry lookup остаётся. Duplicate providers одного
normalized platform отвергаются при создании service вместо выбора credentials
порядком списка; provider false/exception не запускает другую платформу.

Feedback dispatcher проверяет ID==event.channelId/active/platform до и после
authorization call. Изменившийся за время API I/O локальный Channel запрещает
send и отмечается failed. Это не удостоверение channel/credential конкретного
child process, не блокировка PostgreSQL через provider I/O и не проверка прав
всех получателей внешних служебных чатов; эти gates остаются отдельными.
Стенд R246/R247 не обновлялся, R248–R250 пока source-only.

Новый bot-core перед `MessagingService.sendToUser` выполняет signed JSON POST
`/internal/api/bot/feedback-prompts/authorize-delivery`. Body: прежний feedback
event и полученный routing key; формат Rabbit DTO не меняется. Применяются
существующие internal token/HMAC/body digest rules. Ответ содержит только
`eventId` и boolean `allowed`; несовпадающий ID/не-boolean allow/неполный JSON/
HTTP error/API unavailable запрещают отправку. Авторизация не кешируется и не
использует write idempotency replay. Локальная БД/старое разрешение не являются
fallback. Дубликат, уже обработанный delivery ledger, API повторно не вызывает.

Backend читает private snapshot из persisted owning outbox, а не из request.
Exact event JSON/route/envelope/type/source/status `processing|published`
сверяются с V84 pending/root tuple+epoch/created_at, владельцем/активным каналом/
платформой; pending должен быть активен, raw ticket ID однозначен. `processing`
нужен для consumer, опередившего commit published marker. Историческое сообщение
без captured metadata не разрешается и не перепривязывается к сегодняшнему root.

Проверяются существующие scope switches/bindings: BUSINESS требует foundation,
active business и его active SINGLE_BUSINESS/EXPLICIT_SELECTION channel binding;
standalone GENERAL — foundation/general resolution и explicit active GENERAL
binding без business/shared conflict; shared GENERAL — foundation/shared resolution
и explicit active shared GENERAL binding без standalone conflict. Legacy unresolved
путь возможен только foundationOFF без active scoped bindings. Отсутствие бизнеса
не означает GENERAL. Это customer recipient admission, не operator grant.

После ledger claim/channel resolution бот проверяет также local active/platform,
затем обращается к backend непосредственно перед provider call. При отказе/
исключении ledger отмечается failed; действуют прежние listener retry/DLQ rules,
без новой purge/quarantine policy. Миграций и новых flags не требуется, минимумV84.

Проверка не удерживает PostgreSQL locks через HTTP/provider I/O и не обещает
atomic delivery/exactly-once/in-flight revocation. Между решением API и provider
call остаётся TOCTOU; per-child authenticated channel identity, provider retry и
сквозная приёмка всех платформ — отдельные gates.

Runtime R247 пока остаётся на прежних артефактах. Для будущего обновления сначала
нужен panel-web с этим API, затем проверенные bot artifacts/supervisor. Новый бот
со старым web получает404 и намеренно не отправляет prompt. Source work R249 не
выполняет rollout, не меняет flags и не разрешает миграции/очистку очередей.

## Readiness Contract

- startup timeout задаётся через `app.bots.startup-readiness-timeout`;
- poll interval задаётся через `app.bots.startup-poll-interval`;
- success signal: Spring Boot `Started ...Application in ...`;
- failure signal: banner `APPLICATION FAILED TO START`.

Если success marker не найден до timeout, panel считает запуск неподтверждённым
и завершает процесс как startup failure.

## Diagnostic API

Для проверки контракта без реального старта используется endpoint:

- `GET /api/bots/{channelId}/runtime-contract`

Он возвращает:

- модуль бота;
- configured launch mode;
- resolved launcher kind;
- источник артефакта (`explicit-config`, `target-scan`, `maven-fallback`);
- путь к executable jar, если найден;
- required/optional environment keys;
- warnings по текущему launcher contract;
- readiness expectations;
- production readiness и blocking reasons;
- lifecycle expectations (`running/stopped/error`, startup/timeout behavior).

Diagnostic payload должен также явно показывать DB boundary:

- explicit `APP_DB_MODE=sqlite` у панели отвергается до runtime diagnostics; diagnostic contract проверяет canonical PostgreSQL backend и отдельно сигнализирует неподдержанный non-PostgreSQL external vendor;
- production-ready статус для bot runtime допустим только при canonical PostgreSQL backend + `APP_INTEGRATION_TRANSPORT_MODE=rabbitmq` + `APP_DB_MODE=postgresql`;
- child process получает canonical datasource через production configuration; прямое хранение business data в local SQLite считается нарушением production boundary.
- normal runtime панели является PostgreSQL-only; SQLite panel contract отсутствует, а legacy SQLite остаётся за пределами live runtime.
- per-channel `bot-<channelId>.db` больше не считается допустимым live runtime-path: legacy shard-файлы являются historical/test evidence и не имеют first-party live import path в текущем runtime.
- в `APP_INTEGRATION_TRANSPORT_MODE=rabbitmq` bot-side business reads/writes должны идти через internal panel API или queue boundary; silent fallback в local business storage больше не считается допустимым live поведением.

## Production Recipe

Рекомендуемый production path сейчас такой:

1. Собрать prebuilt runtime jar для каждого bot module.
2. Положить артефакты в `dist/` внутри `java-bot` или в другой заранее известный каталог.
3. Заполнить `app.bots.executable-jars` явными путями к jar.
4. Оставить `app.bots.launch-mode=auto` или `jar`.

Production-ready contract считается выполненным, когда:

- canonical panel runtime работает на PostgreSQL;
- child transport явно равен `rabbitmq`;
- child database mode равен `postgresql` и использует canonical datasource;
- launcher резолвится в `jar`;
- executable jar найден;
- jar взят из `explicit-config`, а не через `target` scan;
- panel не зависит от `spring-boot:run` для боевого запуска.

## Dynamic Supervisor

В Docker production contour процесс `bot-runner` имеет роль `APP_RUNTIME_ROLE=bot-runner` и является единственным supervisor для всех активных каналов. После старта он читает их конфигурацию из PostgreSQL и поднимает отдельный prebuilt runtime JAR для каждого канала, поэтому число Telegram, VK и MAX-ботов не ограничено Compose services.

- `bot-runner` должен иметь ровно одну реплику; `panel-web` и `ops-worker` масштабируются независимо.
- Для Telegram Redis lease строится по SHA-256 fingerprint токена; для MAX/VK ownership привязан к channel. Это исключает логирование token в coordination keys.
- Canonical production compose не содержит static `bot-telegram`, `bot-vk` или `bot-max`. Emergency compatibility services находятся только в `docker-compose.production-legacy-bots.yml`; guarded helper отказывает в start при работающем `bot-runner` и откатывает legacy start, если runner появляется конкурентно.
- Child runtimes обращаются к internal panel API по `http://panel-web:8080`. Клиент повторяет только временные transport failures и HTTP `5xx`, с ограничением существующими `retry-attempts` и `retry-backoff`; write requests сохраняют один idempotency key.
- Ручные `start/stop` из web UI не создают child JVM в `panel-web`: web-роль отправляет signed + idempotent internal command на `http://bot-runner:8080/internal/api/bot/runtime/{channelId}/{action}` и принимает результат только при совпадающих `commandId`, `channelId`, `action` и непустом `runnerInstanceId`.
- Internal lifecycle endpoint выполняет команду только в роли `bot-runner` (или локальной `all`), поэтому ownership процесса остаётся single-owner.
- Изменение числа или набора каналов не требует добавления Docker service. Изменения токена, активности либо module configuration вносятся через settings/API и подхватываются supervisor lifecycle.

## Lifecycle Contract Test

В test-layer есть адресный lifecycle contract test, который:

- собирает runnable test jar;
- запускает его через `BotProcessService`;
- подтверждает `running` после readiness marker;
- проверяет `stop/status` и cleanup pid-файла.

## Remaining Gaps

- Нужен полноценный automated end-to-end process contract test: создать несколько каналов, проверить запуск, остановку и замену token без duplicate lease.
- Перед каждым go-live остаётся обязательной ручная проверка доставки входящего текста и media в каждом реально подключённом внешнем канале.
- Emergency static compatibility contour остаётся ручным recovery-инструментом и должен сохранять source-contract coverage для pre/post ownership guard.
