# S0-контракт: независимые бизнесы Iguana

## Статус

S0-контракт от `2026-10-10`; S1 source foundation подготовлен и проверен в
изолированной PostgreSQL-схеме. Документ фиксирует технический контракт,
который должен быть реализован по стадиям задачи `01-278`; он не разрешает
запуск Flyway-миграции, backfill, изменение production/runtime или внешних
систем.

Требующие бизнес-решения пункты помечены **PENDING**. Пока они не утверждены,
код не должен подставлять бизнес по текстовому имени, каналу, последнему выбору
оператора или иной эвристике.

## Source R308 — ручная очистка содержимого AI-материалов

Решение пользователя от 10 ноября 2026: «Всё хранить до ручного удаления, без
автоочистки». Для AI-данных нет автоматического срока удаления; задания очистки
не добавляются. Срок больше не является PENDING product decision.

В редакторе существующего KNOWLEDGE/MEMORY добавлено отдельное действие
«Очистить содержимое». Нужны PAGE_KNOWLEDGE_BASE, текущий business ADMIN либо
ALL_BUSINESSES ADMIN, CSRF и явное подтверждение. Заголовок и текст заменяются
фиксированными заглушками, статус ARCHIVED, revision+1. Одна SQL-команда повторно
проверяет numeric/raw actor, active business и права, immutable business/kind/key/
source ID и ожидаемую revision; запись metadata-аудита атомарна. ROLE_ADMIN сам
по себе не даёт доступа к AI бизнеса. Старые формы не восстанавливают прежний
текст, старые retrieval/handoff references перестают подходить по revision/status.

Для очистки MEMORY проверяется неизменный бизнес материала, без чтения текущего
обращения: смена/удаление origin не мешает владельцу очистить текст из ранее
открытой формы. Просмотр/редактирование и получение кандидатов сохраняют проверку
текущего origin. Это не полный purge: ID/key/origin, metadata-аудит, fingerprints,
chat_history и резервные копии сохраняются; старых title/body в source-аудите нет.
Archive без очистки по-прежнему сохраняет текст. Общая ручная очистка scoped
state/events и обнаружение осиротевших материалов ещё не реализованы.

312 tests/6 suites GREEN, failures/errors/skips=0. Реальные PostgreSQL и полный Spring HTTP/Thymeleaf/CSRF:
две разновидности материала, business/admin matrix, старые/подменённые формы,
отзыв прав/блокировка/rename-reuse до финального SQL, ошибка аудита с откатом,
сохранность чужого бизнеса/чатов/evidence, invalidation старого handoff.
1459 Java sources cache сверены с R307; изменённые2 main/3 test sources
пересобраны, HTML загружен реальным Thymeleaf. Полный repository suite/image
build не выполнялся; прежние channel-test failures R306 вне этого scope.

Baseline HEAD/origin/main/actual remote=3e894783e3d2c84f3cd4575fbbedba0b6ddada82,
parent9cf506526d9ca8ed5d44297579015a00effa024f; проверены12 файлов R307.
R306/R307 committed/pushed пользователем, deployment ими не подтверждён.
Рабочий проект/Docker на другой машине и в другой сессии. Здесь только source
и nonce-owned native PostgreSQL fixture; рабочие БД/очереди/provider не трогались.
Нет новых migrations/flags/config, staging/commit/push/deployment. Нужен новый
backend image для panel-web/ops-worker; последний документированный runtime
R305/V89 и его rollback pointers сохранены. Задача01-278 остаётся🟡.
Подробности и приёмка: [AI boundaries](../../docs/runbooks/ai-data-boundaries.md).

## Source R307 — legacy AI отделён от бизнес-диалогов

Воспроизведён и закрыт обход: уникальный raw ID BUSINESS/GENERAL ранее открывал
старую общую AI-цепочку через DialogAiAssistantService. Теперь ticket-bound
incoming/replies/learning/suggestions/control/review/events и три debug entrypoints
AiOpsRuntimeService используют один guard. При foundation.enabled либо
strictReadScope legacy ticket AI отключён полностью. При обоих выключенных
допустим только один точный UNRESOLVED root (business_id NULL, is_general FALSE).
Коллизия со строкой другого scope, неизвестный marker, отсутствие policy/schema
или ошибка SQL закрывают доступ до history/media/context/LLM/delegates.
Общие hasUniqueTicket/hasUniqueNonGeneralTicket для других модулей не изменены.

Scoped incoming/handoff, business-owned KNOWLEDGE/MEMORY и их actor/owner
проверки сохранены. Нет fallback к legacy LLM/learning/client reply; отправка
внешнему LLM и новый auto-reply этим шагом не разрешены. Сроки retention,
удаление и полный scoped LLM/media/delivery остаются отдельной работой.

282 tests/15 suites GREEN, failures/errors/skips0. Два regression cases сначала RED на прежней
реализации, затем GREEN;33 legacy facade cases включают24 HTTP debug requests
с настоящими PostgreSQL guard/facade/runtime. Целевое ожидание — отсутствие
загрузки контекста/вложений и вызовов LLM даже при разрешении внешнего page gate.
Legacy exact-ID round trips и scoped incoming regression сохранены. Использован
проверенный bytecode cache R306 (1459 Java sources сверены, LF/CRLF нормализован);
изменённые3 main/4 test sources пересобраны. Полный repository suite/image build
не объявляется GREEN/выполненным.

Baseline HEAD/origin/main/remote=9cf506526d9ca8ed5d44297579015a00effa024f:
R306 уже committed/pushed пользователем, ровно24 файла, parent5b3a09013.
Текущая сессия — только source/test; рабочий проект/Docker на другой машине
и в другой сессии. Использована отдельная nonce-owned native PostgreSQL fixture,
рабочие БД/Redis/Rabbit/provider не трогались. Новых migrations/flags/principals
нет, deployment не выполнялся. Последний документированный runtime остаётся R305;
активация R306/R307 требует нового backend image для panel-web/ops-worker.
Проверки и остаток: [AI boundaries](../../docs/runbooks/ai-data-boundaries.md).

## Source R306 — общие инциденты и системные уведомления

Решение пользователя от 10 ноября 2026: «все всем, кроме распределения настроек бота».
Инциденты — общий контур для всех действующих пользователей панели, без
business scope, списка участников или отдельного page/module grant. Доступны
чтение, создание, изменение, события, наблюдатели, маршруты/повтор доставки,
внешний каталог и управление направлениями incident monitoring. Проверяется
numeric PanelUserPrincipal и точное текущее имя в users, enabled/is_blocked;
удаление, переименование, блокировка и повторное использование имени закрывают
устаревшую сессию. Справочник назначения содержит только минимальные данные
действующих однозначных сотрудников и доступен этому общему контуру.

Уведомления доступны всем действующим пользователям; колокольчик и отметки
прочтения остаются персональными, аудиторию доставки определяют маршруты и
наблюдатели. Общие incident/system notices (/incidents и /settings) доступны
и при strictReadScope, включая list/count/cursor/read mutations. Это не
переклассификация untyped dialog/GENERAL/task/project или неизвестных старых
уведомлений. Права исходного диалога, задачи, проекта и других страниц
проверяются отдельно; incident summaries доступны обычному читателю самой
задачи. Управление ботами/каналами и распределение их настроек сохраняют
ROLE_ADMIN плюс PAGE_CHANNELS или PAGE_SETTINGS; бизнес-роль их не заменяет.

R306 проверен в detached source worktree:162 tests/17 suites GREEN,
failures/errors/skips0, включая40 actual PostgreSQL/JPA/HTTP cases. До правок
baseline R305 прошёл127 tests/3 suites и полную компиляцию748 main/710 test
sources; R306 компилирует изменённые sources поверх этой базы (новый main749).
Расширенный ChannelApiControllerWebMvcTest имеет8 прежних failures/50 tests:
те же8 воспроизведены на неизменённом5b3a09013; полный suite не объявляется GREEN.
Ранние R306 RED были ошибками bean/normalizer fixtures; логи сохранены.

Это source/test session на другой машине относительно рабочего проекта.
Тесты использовали только nonce-owned disposable native PostgreSQL;
Docker, рабочие БД/очереди/provider и конфигурация не затрагивались.
Runtime остаётся документированным R305/V89: новый backend ещё не выпущен.
Новых migrations/flags/principals нет; для активации нужен новый backend image
для panel-web и ops-worker, простой restart старого image недостаточен.
Боты и инфраструктура не требуют обновления этим шагом.
Подробности: [общая модель](../../docs/runbooks/common-system-access.md).

## Первое production развёртывание и тестовые данные — R190

Пользователь уточнил 2026-10-27 (R190): проект ещё не запускался в production;
все имеющиеся данные тестовые и не имеют ценности. Сопоставление старых aliases,
ручная классификация пустых/спорных записей и исторический backfill S2B исключены
из обязательного плана. Сохранение старых ID, counts и связей не является
критерием выпуска. Цель — корректная новая модель и первый production запуск.
Это решение не утверждает политики будущих manual/global задач, проектов,
системных событий или права доступа к ним.

Ниже сохранены исторические checkpoint и результаты тестового стенда. Слова
production/рабочий в старых записях, именах Compose и образов не означают,
что production уже запускался. Эта оговорка и актуальная приёмка R301 имеют
приоритет над прежними требованиями переноса тестовых данных. Само уточнение
не выполняет очистку БД и не меняет среду.

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

На исходной Windows/Docker-машине C:\Users\SinicinVV\git_h\tg_ref_b24_sup
свежим измерением подтверждены прежние R274/R272, затем clean R279/1fea98958
выложен сначала в backend, затем в bot-runner/все3 children. Это новая runtime
приёмка здесь; записи Source R276–R279 ниже относятся к другой source/test-машине.
Web5e66fae50aa0/worker672c827f4cdc/runner53de0936724e, по1 replica/healthy/restart0,
imageb0e12ff574c3/tag iguana-panel:account-r279-1fea98958-20261103. Telegram1/3 и
MAX2 перезапущены; VK JAR тоже собран, active VK channel нет. Backend готов12:38,
последний child12:41:57 MSK. Cold restart, не zero-downtime.

Полная bounded Maven/JDK17/Sass production сборка4 JAR, runtime OS/base826 прежний.
718 fresh targeted tests/32 suites (80 panel/99 core/539 MAX),61 live access +33
signed actual-child/read checks GREEN;12 running hashes exact. Общий health и
readiness200/UP, production-readiness ready, DLQ0.15 нетаргетных containers exact;
ENV/mounts exact, HostConfig backend raw exact/runner только bind order/empty DNS
с восстановлением исходного hash. V85/85/settings455/accounts/grants/roots53/
pending51/ratings36/outbox/ledger17delivered/receipt0/MAX sessions exact. Web/worker
Flyway skipped, runner V85 up-to-date; migrations/backfill/flags/sends0.
Новых parent ERROR0; worker TLS WARN1, runner cold-start PID/mirror-route WARN8.

Свежий backup10340761 bytes/SHA256
98419B5532395E622591B193DDDE10B111CE221DAFE9EC71C6A7C477931C688B
isolated restore GREEN;3 rollback R280 pointers на backend826/runner85a retained.
6 historical backup copies и private DLQ wire archive вынесены с hash verify в
../iguana-runtime/tg_ref_b24_sup/recovery/, same-host/current-user ACL.
Новые Compose/operator/proof/backup вне .tmp, в releases/r280-1fea98958-20261103/;
используется preserved-ENV operator, не bare up. На .tmp больше не ссылаются mounts
или Compose labels существующих containers. Старый .tmp142,9GiB/156 Git worktrees
не удалялся: нужна отдельная проверка незакоммиченных данных/retention. Глобального
Docker prune/volumes/image deletion/VHDX compaction не было, убраны только7 own
build/check containers с сохранением logs/artifacts/Sass cache. Остальные пять
блоков/S6 остаются, S2B снятR190. Task Runtime R280/sealed-proof.json.

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

Общий health200/UP и readiness200/UP; production-readiness200/ready/Rabbit healthy.
Один malformed outbound-feedback DTO от28 октября в inboundDLQ сохранён в
приватный same-host файловый карантин с исходным body и полными AMQP properties,
затем удалён только точечным manual ACK после независимой сверки архива.
InboundDLQ0/прочие DLQ0, без replay/reconstruction/отправки клиенту или purge.
HTTP inspect и validate делали get/requeue, это не строго read-only операции.
61 fresh access checks GREEN; новые ERROR0, worker WARN1 — отдельный invalid
Notion API token. Все18 контейнеров/настройки/V85/доменные fingerprints прежние,
rebuild/restart/deploy/migrations/backfill/flags не нужны и не выполнялись.
Web/worker остаются R274/R273/f78708f35/image826bf025417d; runner R272/R271/044d.
Root/pending/rating/outbox/inbox/ledger/receipt не исправлялись, исторические4
failed Inbox rows остаются. Health UP не означает полное закрытие S6/политик.
Evidence: task Runtime R275/.tmp/01-278-r275-dlq/sealed-proof.json.
Архив private/message.amqp (body+wire header), SHA256
9EFE1108A7D40991A9EB86F0D4A5065A3E3D50DBD69B9773FF468B4251C1292E,
ACL только текущему Windows пользователю, вне Git; не удалять при cleanup .tmp.
Из архива можно восстановить экземпляр, реальный restore/replay не проверялся.

## Предыдущий тестовый выпуск — R274, 2026-11-03

Clean R273/f78708f3588a8264b2a7055f14772ff886d2c59c выложен только в
panel-web/ops-worker: image826bf025417d, tag
iguana-panel:account-r273-f78708f35-20261103, containers bfe2c70540bf/aebc1a28d277,
по1 replica/healthy/restart0. Код media gate первого v4 root загружен в backend;
feature flags прежние, foundation-disabled контур не включали. Bot-runner
остаётся R272/R271/044d9bcec/image85a47a3b975f/b76f36ca98b0,3 children/PIDs/
feedback consumer connections unchanged. Все16 нетаргетных containers exact.
Scoped MAX media writer/session eviction admission/legacy isolation ещё нужны.

Сборка COPY-only/network-none из pinned cached85a, не full Maven/Sass build.
Panel JAR меняет только2 class payloads,2229 других exact, все1435 frozen main
classes exact; TG/VK/MAX/core JAR unchanged. Verified record отличается CP
ordering/indices/source lines, symbolic javap proof, не raw byte-exact.1179 inputs/
1441 classes/222 tests5 suites на source/replay сверены — reused, не новый прогон.
Compose --no-deps только2 роли,4 старых фактических Compose/env inputs и ENV/
mounts/HostConfig raw exact; overlay требует preserved-ENV operator. Backend
cold stop около09:02/готов09:04 MSK, live backend consumers0 перед новыми,
3 bot consumer tags/channel PIDs прежние. Это не zero-downtime/не новый bot restart.

Backup10238933 bytes/SHA256
E08136B0A22D48DD28B07529EB55E07EBF637EB210154E72C9DF249FAE8EC66C
изолированно восстановлен на V85 в owned PG192MiB/network-none, retained same-host;
verifier container/volume удалены.2 новых rollback R274 tags на85a сохранены,
R272/R269/R267/R264/R261 pointers/backups verified. Working DB не восстанавливалась,
rollback не выполнялся; backend cold stop/queues0/ledger+receipt+V85 обязательны,
возвращается старый bypass.61 fresh login/access/SSE/readiness +24 signed actual-
child checks GREEN,8 running JAR hashes exact, новые backend ERROR/fatal/WARN0.
ReadinessUP; общий health503/inboundDLQ1 прежний. Flyway skipped web/worker,
db-migrate/migrations/backfill/flags/bootstrap/repair/purge/requeue не запускали.
V85/settings455/roots53/pending51/ratings36/accounts/grants/history/outbox/ledger17/
receipt0/MAX session fingerprints unchanged. Нет provider acceptance sends/
commit/push; пять блоков остаются, S2B снятR190. Task Runtime R274/evidence
.tmp/01-278-r274-rollout/. Следующие checkpoints исторические, в том числе
прежняя source-only оговорка R273; повторять выпуск не требуется.

## Media первого выбранного обращения — source R273, 2026-11-03

Первое создание v4 root имеет ту же границу owned bytes, что и followup:
attachmentPath либо non-text messageType нельзя пропускать как текст. Без
storage key media отвергается. MAX/unknown/null platform с v4 media не переходят
в legacy validator; сохранённый URL или unscoped key не являются proof. Отказ
до первой записи root/ticket/span/activity/attributes/history/attachment metadata,
inbox получает failed, а не processed. Ingestion также сверяет platform с
canonical channel, подмена event.platform не даёт другой writer. Plain text,
ранние MAX protocols и matching frozen-root replay сохраняют прежнее поведение.
Telegram/VK media по-прежнему требуют scoped bytes/SHA/размер/владельца. Это
server-side запрет bypass, не scoped MAX media writer и не поддержка файлов MAX.

Baseline00125192c (R272). RED52 → GREEN222 tests/5 suites на каждую независимую
source/replay копию,138 новых/84 прежних regression tests; failures/errors/skips0.
Real validator/ingestion с mocked persistence/storage, не PostgreSQL/SDK/provider.
1179 inputs/1441 classes exact между копиями;1433 main unchanged,1 functional
class изменён, вложенный Verified record отличается только compiler constant
pool ordering/indices/source lines (full symbolic javap proof, не byte-exact).
570 text-only LF/CRLF differences подтверждены UTF-8 round-trip/content guards;
binary inputs raw exact. Bounded offline compile/test, не full Maven/Sass build.

Source пока НЕ выложен: текущий runtime R272/R271/044d9bcec/image85a47a3b975f,
все18 containers/config/DB V85/ledger17/receipt0/MAX session fingerprints прежние,
readinessUP. Health503/inboundDLQ1 прежний. R272 rollback pointers сохранены.
Для активации нужен отдельный clean-commit release panel-web/ops-worker;
bot-runner/children не требуется пересоздавать ради этого server-only fix.
Нет migrations/backfill/flags/provider sends/commit/push. Остальные пять блоков
задачи не закрыты, S2B снятR190; task Source R273/.tmp/01-278-r273-evidence/.

## Исторический тестовый runtime — R272, 2026-11-03

По согласованию пользователя panel-web, ops-worker и bot-runner выпущены вместе
с clean R271/044d9bcecab2284b2dd66a72ee2ff3b2ee7d3511, по1 replica:
image sha256:85a47a3b975f2adb97e3619f442a2f026505d5d06ef2a2430a423b3d9ccb069c,
tag iguana-panel:account-r271-044d9bcec-20261103. Контейнеры соответственно
6476462123bd/5ca72450180d/b76f36ca98b0, healthy/restart0. MAX v4 media отказ и
backend canonical v4 verifier активны одновременно; scoped MAX media writer
ещё не реализован. R268 journal/R266 feedback ack сохранены, legacy v1–v3/NULL
остаётся отдельным путём.458/23 source/replay tests — проверенный reused proof,
не новый прогон;995 inputs/1703 classes exact. Сборка COPY-only/network-none,
не full Maven/Sass:1 panel/6 MAX class payloads заменены; TG/VK/core unchanged.
Старые web/worker JAR отличались от runner только существующим V85 SQL resource,
2230 общих payloads exact; новых migration resources относительно runner нет.

Сначала cold stop всех3 старых ролей/children и broker queues0, затем worker →
web → runner без mixed fleet. Окно около07:02–07:09 MSK, не zero-downtime.
Compose --no-deps, проект tg_ref_b24_sup/два прежних production Compose/.env;
новый compose.services.json требует preserved-ENV operator, не bare up.
ENV/mounts exact, HostConfig web/runner raw exact, worker только binds/empty DNS
с восстановлением прежнего полного hash bounded guard. Все15 остальных
контейнеров и5 старых Compose/env inputs unchanged; db-migrate не запускался.

Backup10297747 bytes/SHA256
023063666B682A455F0B8EB03F7C017708D773635826A96D95DF07543B75FB0B
восстановлен в owned PG192MiB/network-none, сохранён same-host; verifier resources
удалены. Working DB не восстанавливалась. V85/85/settings455/roots53/pending51/
ratings36/accounts/grants/history/flags exact; ledger17delivered/MD5 exact, receipt0.
Web/worker Flyway skipped, runner normal startup validation85/no migration
necessary; новых миграций не применено. MAX session/index/journal EVAL_RO exact,0
sessions. Нет backfill/flags/bootstrap/repair/purge/requeue/provider acceptance sends.

61 live login/access/SSE/readiness +24 signed actual-child checks GREEN,
3 fresh children/по1 feedback consumer/ready0/unacked0,12 running JAR hashes exact.
ERROR/fatal0, известные TLS/iiko WARN остаются. ReadinessUP, общий health503/DOWN
из-за прежнего inboundDLQ1; operational gate не закрыт. Три новых rollback tags
R272 сохранены: panel-web/ops-worker на90cd11900131, bot-runner на7259bf144f18;
R269/R267/R264/R261 backups/pointers проверены. Откат не выполнялся; требуется
coordinated cold stop/queues0/ledger+receipt+V85/session compatibility, возвращение
старого security behavior оценивается отдельно. Пять блоков задачи и первый
production S6 остаются, задача🟡, S2B снятR190. Evidence: task Runtime R272,
.tmp/01-278-r272-rollout/sealed-proof.json. Следующие checkpoints исторические,
включая source-only оговорки R271 на момент подготовки; повторять их выпуск не надо.

## Исторический тестовый runtime — R269, 2026-11-02

Clean R268/da7a1523190c21ae89b377f12320725e330954bb выложен только в bot-runner:
sha256:7259bf144f18701df2c5393f6ae87863f962c80ef81cc834f8d4dbc913fd8215,
tag iguana-panel:account-r268-da7a15231-20261102/containerd6948323c681,
healthy/restart0/1 replica. В MAX2 активен durable event journal выбранного
BUSINESS/GENERAL вопросника. В image заменён только MAX JAR:32 frozen tested
classes exact,3 functional classes изменены +1 nested record только source line
numbers (full javap verbose proof);273 иных payloads и entry set277 exact.
Panel/Telegram/VK/core JAR byte-exact прежние.249 inputs/264 classes и214/20
source/replay tests сверены — reused proof, не новый прогон/full Maven/Sass build.

Согласованный cold stop всех old children/queues0, затем Telegram1/MAX2/Telegram3
fresh startups/3 consumers/ready0/unacked0, нет mixed fleet.61 ENV/mounts exact;
HostConfig semantic exact, raw hash отличается только binds/empty DNS, полный
старый hash восстановлен bounded guard.5 старых Compose/env inputs unchanged,
новый runner-only overlay требует preserved-environment operator.17 нетаргетных
containers exact, web/worker R257/image90cd; db-migrate не запускался.

Свежий backup10232724 bytes/SHA256
69A465915709A31E986D8E728AA8A23F413D97AB3AFA871F83495DB454DC9D40
восстановлен в owned PG192MiB/network none; same-host сохранён, verifier container/
volume удалены. Working DB не восстанавливалась. V85/85/checksum -2092309160,
settings455/accounts/grants/roots/pending/ratings/outbox/ledger17delivered/receipt0
прежние. Redis EVAL_RO aggregate session/index fingerprints exact:0 MAX sessions/
journals. Нет migration/backfill/flags/bootstrap/repair/purge/requeue/customer sends.

61 live access/SSE/readiness +24 actual-child signed admission GREEN,4 running
JAR hashes exact, fresh runner/child ERROR/fatal0. ReadinessUP, общий healthDOWN/
inboundDLQ1 прежний; operational gate не закрыт. Rollback R269 на R266/image9cc3/
tag iguana-panel:rollback-r269-bot-runner-20261102-9cc3a7c69d95 сохранён, старые
R267/R264/R261 pointers/backups exact. Откат не выполнялся; необходимы cold stop/
queues0/ledger+receipt+V85 и MAX session compatibility. Новые journal sessions
нельзя слепо отдавать старому runner или удалять ради отката. Нет live answers/
Redis eviction/provider callback/full S6. Пять блоков остаются, задача🟡;
task Runtime R269/.tmp/01-278-r269-rollout/. Следующие checkpoints исторические.

## Граница MAX v4 media — source R271, 2026-11-02

Выбранный MAX v4 question flow остаётся text-only до реализации scoped byte
writer. Вложение с подписью не превращается в ответ, bootstrap/history или
legacy followup. До download/storage/publication клиенту явно сообщается:
вложение не сохранено, подпись не принята, нужен отдельный текст. Menu/answer/
submitted session сохраняет exact scope/answers/journal. First media создаёт
только меню. Warning false/exception не подтверждает delivery; повтор восстанавливает
отказ/меню. Existing chat mismatch проверяется раньше; /start и /cancel — явное
управление. Pure payload owner также распознаёт объявленные opaque descriptors
без download URL, включая forwarded body; прежний legacy decoder не расширен.

На backend любое media canonical root с frozen questionnaire_version=4 обязано
пройти scoped verifier, независимо от current channel platform/catalog proof.
MAX/unknown/подмена event platform/неполная proof не дают legacy fallback;
отказ до history/metadata/profile/activity. Это не разрешение MAX media, не новая
миграция и не изменение immutable owner. Прежние MAX v1–v3/NULL contracts и
plain text сохранены; legacy media остаётся отдельным незакрытым путём.

Baseline6161e99a7 (R270). RED22 handler+48 backend → GREEN252 MAX/20 suites,
64 backend/2 suites и142 real Redis/1 suite =458 выполнений на каждую независимую
source/replay копию, failures/errors/skips0.995 inputs/1703 classes exact;
оригинальные assertions byte-exact, включая MAX legacy regression. Три
функциональных main classes,4 records — только LineNumberTable.212 core/
1434 других backend classes unchanged; bounded backend compile, не full build.

Пакет ещё source-only: runtime R269 выше прежний. Нужен отдельный согласованный
clean-commit выпуск backend panel-web/ops-worker и MAX child в bot-runner;
panel-web-only не обновляет WORKER inbound listener. POM/schema/flags/DTO/session
format прежние. Все18 working containers/config/DB V85/ledger17/receipt0/MAX
session fingerprints unchanged, readinessUP/3 consumers/ready0/unacked0;
прежний health503/DLQ1 не закрыт. Owned Redis удалён, backup/rollback сохранены;
нет deploy/migration/backfill/flags/provider sends/commit/push. Это не scoped MAX
media support/live provider/full bot restart/rebind/S6; задача🟡, task Source R271.

## MAX restore/replay на настоящем Redis — test-only R270, 2026-11-02

Baseline7423a4919 (R269).109 исходных MAX questionnaire handler сценариев
выполняются повторно с настоящими Redis Lua/CAS session store и delivery guard,
не с local fallback. Assertions и test bodies прежние, изменены только test
factories/reset seams. BUSINESS1–4/GENERAL2–4, answer/back/optional skip,
ACK/prompt failure/dedup reset, final CAS gap/optimistic retry/legacy GREEN.
Дополнительно2 BUSINESS/GENERAL v4 restart cases и1 stale CAS/channel/user/
namespace isolation:112 Redis executions. После graceful restart отдельного
Redis/AOF, fresh Lettuce connection и независимого store loadAll сохранены raw
JSON/frozen scope/template/catalog/answers/journal/index/positive TTL/processed
markers. Удаление только delivery keys не позволяет повтору события двигать
questionnaire ещё раз; выбранный ticket command прежний.

Opt-in fixture/PowerShell harness требуют exact cached image ID, owned resource
labels+nonce и Redis sentinel; только loopback, отдельные container/network/volume,
64MiB/CPU1, AOF/fsync always/noeviction. Default tests не поднимают Redis; explicit
IT без настройки fail closed, без app.env/Compose/working DB/provider. Уникальные
namespaces, bounded SCAN, нет FLUSHDB. В конце только2 sentinels, disposable
resources удалены/ENV restored.214 default/20 suites +112 Redis/1 suite =326
выполнений на каждую независимую source/replay копию, failures/errors/skips0;
не652 уникальных теста.251 inputs/265 classes exact,244 runtime classes прежние.

Runtime R269 выше unchanged, rebuild/restart рабочих контейнеров не нужны.
Все18 container/config fingerprints, DB V85/85/ledger17delivered/receipt0/MAX
session fingerprints exact; readinessUP,3 consumers/ready0/unacked0. Прежний
health503/inboundDLQ1 не исправлялся, backup/rollback сохранены. Нет migrations/
flags/provider sends/commit/push. Это не live webhook/provider, session eviction,
crash/full bot application/launcher restart, rebind, ретроактивная дедупликация
старых IDs или полный cross-platform/S6 gate. Пять блоков остаются; task Source
R270/.tmp/01-278-r270-evidence/, задача🟡.

## Тестовый runtime — R267, 2026-11-02 (исторический checkpoint)

Clean R266/d0b467204c68b205033bee0070119568ce377245 выложен только в bot-runner:
sha256:9cc3a7c69d9595094e4d372de868ea6cd33a3dc8302f327c7909d7d745cb53e1,
tag iguana-panel:account-r266-d0b467204-20261102/container9612381dedaa,
healthy/restart0/1 replica. Отказ/unknown feedback write не даёт ложного success
ack в Telegram/VK/MAX. Активны Telegram1/MAX2/Telegram3, live VK не запущен.
Все old children остановлены/3 queues0 consumers/ready/unacked до нового fleet.
212 frozen tested core classes exact,211 unchanged; в3 executable JAR изменён
только FeedbackService.class внутри STORED core. Outer payloads и panel JAR
прежние. Bounded offline core package + COPY/network-none image, не full build;
501/5 source/replay GREEN — прежний proof, не новый прогон. Acceptance module
в runtime не включён.296 inputs/232 class hashes source/replay exact.

17 остальных containers прежние, web/worker R257/7b9fea55a/image90cd.
61 actual ENV и mounts exact; HostConfig raw hash отличается, но полный старый
hash восстановлен только порядком binds/null-vs-empty DNS.5 Compose/env inputs
не менялись. Новый runner-only overlay требует preserved-environment operator.
Свежий backup10208054 bytes/SHA256
E0813789EB95886C2B864FABF9EAA3004AD9F1C794A5C44C2661C195EEC9068B
изолированно восстановлен в PG192MiB/network none; same-host файл сохранён,
verifier container/volume удалены. Working DB не восстанавливалась. V85/85,
checksum -2092309160/14 columns/10 constraints/FK, settings455, accounts/grants/
roots/pending/ratings/outbox/flags/ledger17delivered/receipt0 unchanged.
Миграции/db-migrate/bootstrap/repair/backfill/flags/purge/requeue не запускались.

61 live access/SSE/readiness и24 actual-child signed admission GREEN;3 fresh
startups/consumers, ready/unacked0,4 running JAR hashes exact, ERROR/fatal0.
ReadinessUP; общий healthDOWN/inboundDLQ1 прежний. Live provider/client callback,
external acceptance messages не выполнялись. Новый rollback R267 на R263/
image9898/tag iguana-panel:rollback-r267-bot-runner-20261102-9898448385a0 сохранён,
старые R264/R261 pointers прежние. Rollback не выполнялся; только после cold
stop/queues0 и проверки ledger/receipt/schema compatibility, без удаления данных.
Пять крупных блоков/S6 остаются, статус🟡, S2B снятR190. Подробности/evidence —
task Runtime R267/.tmp/01-278-r267-rollout/. Следующие checkpoint исторические.

## События выбранного вопросника MAX — source R268, 2026-11-02

Baseline bfe70a8599ff9c2b4bdf254427bae75a72f0e7df (R267). MAX Selection хранит
immutable acceptedEventKeys вместе с selected scope/template/catalog/answers в
одном session CAS. Choice/page/answer/back/skip становятся durable до prompt/ACK;
повтор после JSON restore/потери transport dedup cache не отвечает на следующий
вопрос, не делает второй «Назад»/skip и не меняет raw state. Повтор prompt разрешён,
exactly-once доставки не обещается. Final-answer ambiguous CAS восстанавливает
submission с исходным последним accepted ID, не с ID следующего сообщения.
Исторический ticket marker/root binding не заменяется текущим выбором бизнеса.

Журнал ограничен4096 уникальными непустыми IDs, не эвиктит старые ключи;
malformed/overflow и несогласованный новый submission fail closed. Старый JSON
без поля читается с пустым журналом/прежним прогрессом; старые IDs не выдумываются.
Пустое поле не сериализуется. Legacy direct/SQL/API/DTO/policies не расширялись.
214/20 full MAX suites GREEN,81 новая проверка; source/replay независимы.
7 допустимых version/scope сочетаний: BUSINESS1–4/GENERAL2–4. Проверены answer/
back/optional skip после ACK failure/prompt failure/dedup reset, final CAS gap,
optimistic retry, delayed replay, JSON/limits/legacy. RED1/1 → GREEN;249 inputs/
264 classes source/replay exact,212 core classes unchanged. Это real local JSON/
CAS/handler proof, не Redis eviction/live MAX provider/webhook или общий S6.

При подготовке R268 runtime R267/R266 не менялся. В R269 выше main fix выложен
отдельным clean-commit runner release без новых migrations/flags; этот source
больше не требует повторной выкладки. Нет backfill/flags/provider sends/staging/
commit/push; остальные gates и пять блоков прежние. Task Source R268/
.tmp/01-278-r268-evidence/, задача🟡.

## Тестовый runtime — R264, 2026-11-02 (исторический checkpoint)

Clean R263/8dbb083206c36426d5aab229d6ae36510bf57cd3 выложен только в bot-runner:
sha256:9898448385a0d685e57891b8e94696b46a4dd14edde9ad77eaf8d5eb1070d48b,
tag iguana-panel:account-r263-8dbb08320-20261102/containerc068584505b2,
healthy/restart0/1replica.212 core/72 platform frozen tested classes/1435 panel
classes exact; panel JAR дополнен только V85. Incremental COPY/network-none,
769/21 — прежний source proof, не новый прогон/full Maven/Sass build.
Все3 old children остановлены,3 queues0 consumers/ready/unacked до V85 и
старта новых Telegram1/MAX2/Telegram3; нет mixed fleet. Live VK не запущен.

Backup pg_dump custom10196390 bytes/SHA256
08742695C7D6F8BD98193F650F9262ACD2F491738337EF8AF4482652B3F8B35B
восстановлен в owned isolated PG192/network none. На копии V85/validate и
старые84 SQL validate/migrate0 на V85 GREEN; same-host backup сохранён,
временные container/volume удалены. Working DB не восстанавливалась.
Schema-only Flyway10.17 через one-shot Compose db-migrate --no-deps применил
ровноV85/checksum -2092309160; без Spring/bootstrap/legacy normalize/repair.
Оригинальный db-migrate и все17 нетаргетных контейнеров прежние; web/worker
R257/7b9fea55a/image90cd.61 ENV/mounts/HostConfig и5 Compose/env inputs exact.

V85/85/history1–84 exact/settings455/accounts/grants/roots53/pending51/ratings36/
outbox126/flags прежние. Ledger17delivered/MD5 04787691279b925af193b11e95c0c9f0,
receipt journal0.61 свежая live access/SSE/readiness +24 signed admission GREEN,
3 fresh startups/3 consumers/ready0/unacked0. ReadinessUP, runner/children fresh
ERROR/fatal0. Общий health503/DOWN из-за прежнего inbound DLQ1; TLS/iiko WARN
не исправлялись. Нет backfill/flags/purge/requeue или внешних acceptance sends.

Rollback R264 → R260/image904f/tag
iguana-panel:rollback-r264-bot-runner-20261102-904fba5c4e40 сохранён, не выполнен.
Только cold stop всех новых children/consumers + empty receipt journal + exact
ledger/inflight + V85 compatibility разрешают рассматривать откат; не drop
journal и не automatic working DB restore. Старые R261/R258 pointers сохранены.
Receipt reconciliation работает на явном replay committed positive evidence,
но нет startup/batch scanner, unknown без receipt, scoped review/retention,
live provider/client callback или полного in-flight process restart. Остальные
изоляция/GENERAL/policies/AI/S6 остаются; задача🟡, S2B снятR190.
Подробности/evidence — task Runtime R264/.tmp/01-278-r264-rollout/.
Ниже исторические source/runtime checkpoints, не инструкция повторить выпуск.

## Ответ на запрос оценки — source R266, 2026-11-02

Baseline f1c02b1faee83a84d88e6f9d26f78f3c9a024318 (R265). Общий bot FeedbackService
теперь не возвращается нормально при backend false: исключение до thank-you/
feedback_saved. Не утверждает отсутствие записи при unknown; успех не выдумывается.
Void API/успешный и legacy JDBC paths прежние. Unit RED6/1 → GREEN6.
Real prompt/receipt → shared feedback service → signed read/write API → real
JPA/native locks/rating/UI event/expiry проверены на4 scope ×3 routes.241 новых
callback cases, всего501/5 suites (242 новых), source/replay независимы. Duplicate,
context ABA/recreate/expiry, wrong lookup, rating0/6, HMAC/API refusal, concurrent
replies, lost commit response/idempotency и3-stage SQL rollback — без new provider.
Узкий Ticket JDBC adapter/provider/неполный Spring bootstrap — test seams, не
live SDK/webhook/client acceptance или отдельная child actor authorization.
211 unrelated core/1435 panel classes exact;296 inputs/232 classes frozen.
Runtime R264/V85/18 containers/config/settings455/ledger17/receipt0/queues прежние;
PG192/Rabbit256MiB удалены. Main fix требует отдельного clean-commit bot-runner
release, новых migrations/flags нет; acceptance module не развёртывать.
Live SDK/client callback/launcher/fleet/unknown review/privacy и S6 остаются;
задача🟡, S2B снятR190. Task Source R266/.tmp/01-278-r266-evidence/.

## Проверка process loss — source R265, 2026-11-02

Baseline ae1743e51fa703e5d40c908fc6fb4f4002169a18 (R264).60 сочетаний4 scope ×3
routes ×5 crash points в отдельной delivery JVM: после committed claim/prepare,
ответа провайдера, committed receipt/delivered до ACK. Родитель force-kill PID;
RabbitMQ сам redelivers original unacked payload в новую JVM/другой PID.
Positive persisted receipt завершает delivered без SDK/admission при revoked
channel/catalog/API503; отсутствие receipt остаётся blocked. Already delivered,
receipt/outbox rows и timestamps exact; ACK подтверждён после закрытия consumer.
323 выполнения/4 suites GREEN,69 новых; source/replay независимы, не суммируются.
212 core/1435 panel classes exact,17 test classes/9 inputs frozen. Product код
не менялся; test-only модуль не развёртывать, рабочий runtime R264/V85 прежний.
PG192/Rabbit256MiB/1CPU удалены;18 working containers/config/данные/flags/queues
unchanged, receipt0/ledger17delivered/readinessUP, прежний inboundDLQ1 сохраняется.
Не full-stack Spring/SDK, не live provider/client callback, штатный platform
application/launcher/fleet restart или S6. Unknown без receipt, operational
review/retention/startup recovery и остальные блоки остаются; задача🟡/S2B снятR190.
Подробности/evidence — task Source R265/.tmp/01-278-r265-evidence/.

## Квитанции отправки feedback — source R263 (исторический checkpoint до R264)

Baseline249acbf535b82fc694363120c04561cb1768f79b,2 ноября. Backend-owned V85
принимает прежний ledger под Flyway без изменения строк и добавляет private
receipt journal. В рабочей БД V85 НЕ применена; receipt bean требует готовую
таблицу и не выполняет DDL. Не обновлять children простым rebuild без schema gate.

До SDK committed nonce связан с exact ledger kind/route/channel/user/raw ticket/
request и platform/sender fingerprint/prompt SHA256; после SDK положительный
message ID сохраняется отдельно до финального delivered. Текст/token/body в
журнале не сохраняются. Messaging pinned sender recheck после prepare сохранён;
legacy boolean/support-chat API не менялись. TG exact chat+positive message_id,
VK positive SDK ID без сужения bigint, MAX integral exact recipient + textual mid.

Replay sending/uncertain с committed positive receipt и полным SQL context join
восстанавливает delivered без provider/admission/new claim. Unknown/нет receipt/
context drift/unsupported status остаются blocked. Historical acceptance не
является текущим delivery permission, read receipt или exactly-once. Нет поиска
по тексту/каталогу, reset/requeue, startup/batch scanner или автоответа клиенту.

769 executions/21 suites GREEN:484 core +31 SDK adapter +254 PG/Rabbit/HTTP,
135 новых. Flyway10.17 V85/validate, pre-V85 preservation, rollback/lost commit,
nonce/context/recipient/ID poison,3-attempt cap/256-char boundary,4 scope ×3 route
recovery/unknown после service+consumer restore и явного replay проверены.
292 frozen inputs/284 classes/205 unrelated core exact,1435 panel reused с
provenance; независимый source/replay, не full Maven/assets или live SDK/process.

Source-only/no working migration/deploy/flags/backfill/messages/commit/push.
18 рабочих containers/config/БД V84/settings455/ledger17delivered/queues прежние,
readinessUP; runtime R261/rollback/backup pointers ниже сохранены. Изолированные
PG192/Rabbit256MiB и данные удалены. Нужны отдельный V85 release/rollback gate,
unknown без durable receipt, scoped operational review/retention, live callback,
full process restart и оставшиеся S3–S6. S2B снятR190/задача🟡.
Подробности — task Source R263/.tmp/01-278-r263-evidence/.

## Подтверждение известного результата — source R262

Baseline a3e9348824cafd2f56d75905e3aa881c6ab5d416,2 ноября. Только ledger service:
после известного provider true максимум3 попытки записи delivered, независимые
REQUIRES_NEW/READ_COMMITTED/timeout10s transactions. Повторяются persistence
exceptions, не provider/admission/claim; прерывание/ошибка состояния/программирования
не повторяются. Один timestamp результата; уже committed delivered с delivered_at
подтверждается read-only. Не открывает uncertain/sending после потери процесса,
не трактует false/timeout как недоставку, не выполняет автоматический replay.

625 executions/12 suites GREEN:419 core, включая32 новых real-PG cases, и206
PG/Rabbit/HTTP. Потеря commit-ответа моделируется после фактического commit;
provider/admission ровно1,3-attempt cap и неизменность delivered проверены.
Frozen RED32/3failures/10 ожидаемых runtime errors → GREEN32/0/0. Source/replay
независимы,202 inputs/208 classes/206 unrelated exact,1435 panel classes reused
с provenance; acceptance на reactor core. Без новой схемы/DTO/public API.

Source-only/no deploy; runtime R261 и rollback/backup pointers ниже не менялись.
18 рабочих контейнеров/config/БД/flags/ledger17delivered сохранены; readinessUP.
Изолированные PG192/Rabbit256MiB удалены. Нет migrations/backfill/provider sends/
staging/commit/push. Receipt reconciliation неизвестного результата, live callback,
in-flight process restart и остальные S3–S6 блоки остаются; S2B снятR190/задача🟡.
Подробности — task Source R262/.tmp/01-278-r262-evidence/.

## Среда выполнения и сессии

Исторический runtime R261 — выкладка1 ноября/фиксация2 ноября, тот же тестовый
Windows/Docker Desktop экземпляр этой сессии. Проверен clean HEAD/origin/main/
actual remote5731c6d483010990aebe0a35df21089defce0d9a (R260,parenta3cec82d1).
Обновлён только bot-runner: image
sha256:904fba5c4e405614ed9fa0a96dae961b7a2d15028663d5b416f67310b9090e8f,
tag iguana-panel:account-r260-5731c6d48-20261101, containerccae25c43d88,
healthy/restart0/1replica. Telegram1/MAX2/Telegram3 работают с новым core.
До старта новых все старые children остановлены; feedback consumers0 и
ready/unacked0 подтверждены. Нет mixed old/new ledger fleet.

Web4a828963846e/worker35472b2e278f остаются R257/7b9fea55a/image90cd11900131,
все17 нетаргетных containers exact unchanged.61 runner ENV/mounts/HostConfig,
5 прежних compose/env files и config сохранены. Compose project tg_ref_b24_sup,
новый .tmp/01-278-r261-rollout/compose.services.json — только с preserved ENV;
старые R253/R256/R258 overlays не удалять. Пересоздан только runner через
--no-deps/--no-build/--pull never, scale1; повторный deploy не требуется.

Новый nested core208 tested classes exact; заменён ровно один core в каждом
Telegram/VK/MAX executable JAR, все остальные entry payloads exact. Panel JAR
R257 не менялся.4 JAR hashes проверены в образе и работающем runner.593/11 —
reused R260 source proof, не rerun; incremental network-none COPY, не full build.
VK artifact обновлён, но live VK child не проверен, он здесь не запущен.

61 свежая live access/SSE/readiness +24 signed admission checks GREEN;
3 свежих startup timestamps/3 canonical consumers, ready/unacked0. ReadinessUP,
runner ERROR/fatal0/WARN8. Общий health503/DOWN — прежний inbound DLQ1;
TLS/iiko WARN не исправлялись. Внешних acceptance messages не отправляли.
V84/84/settings455/accounts/grants/roots53/pending51/ratings36/outbox126 прежние;
ledger17 delivered/MD5 04787691279b925af193b11e95c0c9f0, новых состояний0.
No migrations/backfill/flags/db-migrate/web/worker/infra/NetBox/purge/requeue.

Rollback pointer iguana-panel:rollback-r261-bot-runner-20261101-d9c597493338
на R252/d286cd6cd/image d9c597493338 сохранён, но rollback не выполнялся.
Старый consumer не знает новых ledger states: откат только после остановки
всех новых children/consumers и проверки ledger/inflight compatibility.
pg_dump10165894 bytes/SHA256
0AFDDC8E07A623F14E41525685C33836E6DBACA3CA9E7592281379CEB201C3B0
успешно restored в owned isolated PostgreSQL network none/192MiB; одноразовые
container/volume удалены, same-host backup сохранён. Рабочая БД не restored.
Receipt reconciliation/операторское разрешение uncertain пока не реализованы;
live callback/positive customer chain/full in-flight process restart/остальные
изоляция, GENERAL, политики, AI и S6 остаются. Задача🟡, S2B снятR190.
Подробности/evidence — task Runtime R261/.tmp/01-278-r261-rollout/.
Ниже source-only/runtime checkpoints исторические, не инструкция их повторить.

Исторический runtime R258 —1 ноября, существующий рабочий тестовый экземпляр
Windows/Docker Desktop этой сессии. По «давай дальше. что остаётся по задаче?»
выложен clean R257/7b9fea55abf6568c00891cd69db906025891685a, image
sha256:90cd11900131b1cf3fd5d0904a6d38be931af78455b6b73aabbef6b84926896c,
tag iguana-panel:account-r257-7b9fea55a-20261101, только web/worker по1 replica.
Web4a828963846e/worker35472b2e278f healthy/restart0/readinessUP. Все16 остальных
containers, включая runner783fd187513e/d9c597493338/d286cd6cd и его3 JVM, прежние.
Compose/.env/ENV/user/mounts/HostConfig equivalent; новый overlay R258 требует
preserved ENV operator. Старые overlays R256/R253 runner не удалять.
Incremental panel JAR1435 tested classes/795 unchanged payloads/три bot JAR exact,
не full Maven/Sass build.835/15 и35 PostgreSQL — reused source proof, не rerun.
61 unique real access +24 signed admission GREEN; child queues1 consumer/0 ready/
0 unacked. Startup markers подтверждены полными журналами, old recent-tail и
provenance probe errors исправлены versioned operators без product changes.
Ровно1 diagnostic send через R257 fresh DB snapshot в сохранённую TG1 group:
provider ok/sent1/failed0, marker R258-20261101-220314-493a1929.
Не MessagingService/feedback chain/визуальное подтверждение/revoke acceptance.
V84/84/settings455/accounts/grants/roots53/pending51/ratings36/outbox126 прежние;
no migrations/backfill/flags/db-migrate/infra/runner/NetBox. HealthDOWN —
прежний inbound DLQ1, TLS/iiko WARN сохраняются; ERROR/fatal0 в окне.
Rollback R258 web/worker → e02456c19ea4/0df04ecf6, старые pointers сохранены.
pg_dump10120961 bytes/SHA256 02929BA178E68158225F40CE9FD780F302BADA11BAC0F78D1D06855B69567B5B
изолированно восстановлен; same-host, working DB/rollback не восстанавливались.
Детали/evidence — task R258/.tmp/01-278-r258-rollout/. Предыдущие source-only/
runtime checkpoints ниже исторические; новый snapshot не закрывает5 крупных
блоков feedback/изоляции+GENERAL/политик/AI/сквозной приёмки и первого S6.
Исторический S2B снятR190, задача🟡. Повторный deploy/migrations не разрешены.


Source R260 —1 ноября, baseline a3cec82d1 (R259), runtime остаётся R258.
Feedback ledger: CAS-захват только нового retryable (доказанный pre-provider
отказ), processing/sending/uncertain/старый failed не перехватываются по таймауту.
Durable REQUIRES_NEW sending сохраняется до SDK; pinned sender recheck после
handoff DB I/O. Неопределённый post-handoff результат не даёт права повторить.
593 executions/11 suites GREEN, включая детерминированную PG гонку (RED11/12
на baseline, GREEN12/12 после fix), rollback и36 E2E unknown-result cases.
Это не exactly-once, receipt reconciliation, live callback/full process/fleet/S6.
Рабочая схема/flags/очереди не менялись; миграций нет. Для будущей выкладки и
отката нужен отдельный gate без mixed old/new children: старый код не знает
новых состояний. Запрет слепого replay не означает разрешение блокировки без
результата провайдера. Подробный контракт/остаток — task Source R260 и README.

Source R259 —1 ноября, baseline a88cd2d09 (R258 metadata), runtime остаётся R258.
Добавлен opt-in bot-feedback-acceptance, обычный reactor/runtime code прежние.
157 unique tests GREEN без skips: реальная outbox/V84 → PostgreSQL/RabbitMQ с
confirm → production listener/converter/ledger → signed recipient/sender HTTP,
реальные controllers/HMAC/SQL/JPA/credential resolver → MessagingService →
loopback HTTP provider. Четыре scope × Telegram/VK/MAX; поздний revoke/expiry/
ABA/rotation, poisoned child/user/prompt, persistent sequential replay после
consumer restart и explicit retry после известного provider503.
Fixture catalog/channel reader контролируемые; это не live provider SDK,
rating callback, full process restart, concurrent failed/stale claim, recovery
unknown outcome/exactly-once, full security/TLS/fleet или S6. Эти проверки
остаются, не переносить на них частный replay proof. Изолированные disposable
PG192/Rabbit256MiB удалены; рабочие18 containers/config/БД/flags/queues прежние.
Пересборка/deploy/migration/backfill не нужны и не выполнялись. Подробности и
границы — task Source R259 и java-bot/bot-feedback-acceptance/README.md.

Source R257 —1 ноября, clean da881c6d5 (R256 metadata,parent0df04ecf6/scope4).
Прямые Telegram test/configured send фиксируют immutable channel delivery
snapshot один раз до выбора получателей и держат его на весь batch. До network
setup и каждого provider handoff проверяются captured object и свежие scalar
id/active/platform/token/binding/recipient/delivery/network/API config поля БД.
Native exact-ID query в REQUIRES_NEW/read-only/READ_COMMITTED не берёт старый
JPA/OSIV entity или caller REPEATABLE_READ snapshot. Missing/mismatch/query error
deny; R255 credential rechecks сохранены, нет reroute/fallback/retry/save/refresh.
Это не lease и не атомарная DB/catalog→provider операция, не business/participant
policy и не изменение global network/других direct/getMe paths.
835/15 suites GREEN:237 новых/598 прежних,35 real isolated PostgreSQL cases;
outer repeatable-read/cached entity остаётся старым, новая query видит committed
изменение и запрещает send. RED197/187 failures/10 controls,0 errors/skips,
frozen source/bytecode. Old12 assertions intact,2 fixture-only query stubs.
Incremental2 main sources/1435 classes/1429 unrelated exact;17 inputs exact.
Не full web build/JAR/image/release. Собственная test DB/container удалены;
working18/DB controls/config прежние, только periodic backup status обновился.
Source-only/no deploy/migrations/backfill/flags/реальных provider messages;
рабочий runtime/rollback/backup по R256 ниже. Positive feedback/политики/GENERAL/
AI/fleet/S6 и operational gates остаются, S2B снятR190/статус🟡.
Детали/evidence — task R257/.tmp/01-278-r257-evidence/.


Исторический runtime R256 —1 ноября, существующий тестовый экземпляр Windows/
Docker Desktop машины текущей сессии. По «делай что необходимо для продолжения
выполнения текузей задачи» обновлены ТОЛЬКО panel-web/ops-worker, по1 replica,
до clean R255/0df04ecf6ec86923aff3e3e2d47ec41c977c6db1,
image sha256:e02456c19ea4c796a5eca493ec0921d9f7e35b09693ef43e5bfb0850b9972de4,
tag iguana-panel:account-r255-0df04ecf6-20261101.
Web91e820e12c10/worker3114fafdaea0 healthy/restart0/readinessUP.
Все16 нетронутых контейнеров, включая runner783fd187513e/d9c597493338/d286cd6cd,
unchanged; children TG1/MAX2/TG3 прежние. ENV/user/mounts/full HostConfig exact,
canonical Compose/.env прежние; новый overlay R256 требует preserved ENV
operator (fail-closed без vars), R253 overlay runner не удалять.
Panel JAR incremental:1433 tested classes/3 changed/1430 exact,795 nonclass entries
и3 bot JAR прежние. Не full Maven/Sass build;598/12 — source proof R255.
61 real access/SSE/readiness +24 signed admission checks GREEN; три child feedback
queues по1 consumer/ready0/unacked0. Ровно1 diagnostic Telegram send в сохранённую
группу через новый R255 resolver: HTTP200/sent1/failed0/provider ok, marker
R256-20261101-210709-e93186a7. Не MessagingService/positive feedback chain,
не визуальное подтверждение; реальные участники внешних чатов не проверены.
V84/84/settings455/accounts/grants/roots53/pending51/ratings36/outbox126 прежние;
no migrations/backfill/flags/db-migrate/NetBox. Health503/DOWN — прежний inbound
DLQ1, worker TLS/iiko WARN сохраняются; ERROR/fatal0 в окне.
Rollback R256 web/worker tags → d9c597493338/d286cd6cd, прежние pointers сохранены.
pg_dump10093531 bytes/SHA256 35B5F99078FB7853DAC31CC23A978C3C9AA394BD0642D9537969D43ABCD30058
изолированно восстановлен; same-host, working DB/rollback не восстанавливались.
Подробности/evidence task R256/.tmp/01-278-r256-rollout/. Подготовка сохранила
изменившийся ДО deploy locations.json, свежий baseline и прежние snapshots.
Source R255 теперь выложен; historical source-only записи ниже не описывают
текущий runtime. Это НЕ первый production/S6, остальные gates в task остаются;
исторический S2B снятR190, статус🟡. Документ не разрешает повторный rollout.


Source R255 —1 ноября, clean HEAD/origin/main/remotea379ac13a (metadata R253/R254,
parentd286cd6cd/scope4). ChannelTransportService test/configured send и recipient
preview используют общий launcher/admission credential resolver. Broken explicit
binding не даёт inline fallback, bound token работает без inline. Credentials
переоцениваются для каждого send и после network setup вместе с captured channel
ID; проверенные local disable/rebind/revoke/rotate/ID changes закрывают handoff.
Unknown configured target не становится support chat. Catalog errors deny,
send logs не включают exception text/token URL, provider failure не success/retry.
Удаление credential сохраняет explicit refs до явного rebind/clear; allocation
учитывает все references и catalog, не оживляет dangling binding новым token.
BIGINT exact, Long.MAX_VALUE409 без write. Concurrent JSON writers не сериализованы.
GREEN598/12 suites (157 новых/441 прежних), RED132/92 +20/20 +5/4 последовательно,
0 errors/skips; frozen source/RED bytecode, old9 test sources/assertions unchanged.
Native offline wrapper96/128MiB,1 main source incremental1433 classes/1430 exact.
Только source: working runtime/rollback/backup по R253, R254 diagnostic не повторён;
no deploy/migrations/backfill/flags/provider messages. Не full web build/release.
Evidence/task R255/.tmp/01-278-r255-evidence/. Captured channel не является
persisted DB lease, participant recipient policy/direct getMe/другие provider
paths/strict fresh snapshots/GENERAL/AI/policies/fleet/S6 остаются. S2B снятR190,🟡.


Приёмка R254 —1 ноября: по «как и сеохранено сейчас» штатной диагностикой
панели отправлено1 test message в единственную сохранённую группу Telegram1.
HTTP200/success/sent1/failed0; backend подтвердил sendMessage ok=true, без
message_id/подтверждения визуального получения. Метка R254-20261101-191434-d70955d5.
Первый probe403 с raw cookie CSRF не дошёл до controller; corrected rendered
meta token, как common.js, дал success без изменения/отключения защиты.
MAX2/Telegram3 не имеют сохранённой support group; чужие адресаты не выбирались.
Это admin → ChannelTransportService → Telegram, НЕ MessagingService/feedback
recipient/provider chain. Positive feedback требует контролируемого клиентского
диалога; группа уведомлений не подменяет user ID. Все18 containers и контрольные
DB/config/flags unchanged; no rebuild/restart/deploy/migrations/backfill/queue
mutations. Runtime/rollback/backup по R253 ниже, S2B снятR190/статус🟡/не S6.
Детали/evidence — task R254/.tmp/01-278-r254-acceptance/. Эта приёмка обновляет
только результат diagnostic delivery, не расширяет прежние acceptance claims.


Исторический runtime R253 —1 ноября, тестовый стенд машины той сессии,
не первый production S6. По «согласовано» выполнена совместимая выкладка
clean R252/d286cd6cd6f8f1764a84d583fdc7c65d04977306 (HEAD/origin/actual remote),
image sha256:d9c597493338db62f7bd3bb2952504b92e5657b9740eafd7990922838fdb58bb,
tag iguana-panel:account-r252-d286cd6cd-20261101.
Только web/worker/runner по1 replica, последовательный cold start;15 остальных
контейнеров exact unchanged. ENV/user/mounts/full HostConfig сохранены, bind order
и пустые DNS проверены независимым восстановлением исходного fingerprint.
Оператор сначала остановился на перестановке Binds, затем продолжил без
повторного recreate web/worker; это не изменение продуктовых тестов.
Новый временный Compose overlay R253 без секретов блокирует запуск без сохранённого
ENV; до следующей выкладки его и operator не удалять. Четыре JAR exact из R252
candidate; panel incremental, не full Maven/Sass build.600/19 — source proof R252.
Все роли healthy/readinessUP/restart0; три child JVM Telegram1/MAX2/Telegram3
имеют canonical identity/JAR/startup marker и по1 canonical queue consumer.
61 live read/access/SSE checks +24 signed admission checks GREEN: actual child
credentials allow, invalid digest/platform/child deny, bad HMAC/no token401,
R249 invalid recipient event deny. Tokens/digests не выводились в admission reports. VK live child
нет, произвольным внешним адресатам тестовые сообщения не отправлялись.
V84/84/checksum-783018026/settings455/accounts/grants/roots53/pending51/ratings36/
published outbox126/actionable0/active pending0 прежние; no migrations/backfill/flags.
ReadinessUP, overall health503/DOWN из-за прежнего inbound DLQ1; ERROR/fatal0,
SSL/iiko WARN сохраняются. Known RMS command теперь succeeded/attempt4 на прежнем
worker, нового claim-loss в окне нет; это не устранение общей RMS/TLS проблемы.
Rollback R253: web/worker fd9b7ab03a1d/R246, runner97ccb6fc7a34/source revision
неизвестна; tags перечислены в task checkpoint. Старые R247 pointers сохранены.
pg_dump10055697 bytes/SHA256 AE358A58D1B921C7380A971C852106257BB8C272D85AF3FAF42AA70C4F17A7A1
изолированно восстановлен (network none/192MiB); working DB не восстанавливалась,
working rollback не выполнялся, off-host backup не заявляется. Dump может содержать
секреты, хранится локально в ignored .tmp.
Source R248–R252 теперь развёрнут. Следующий gate — указанный пользователем
тестовый получатель для реальной доставки; recipient/revoke/replay/provider,
остальные policies/isolation/GENERAL/AI/fleet/flags/S6 остаются. S2B снятR190,
статус🟡. Детали и evidence — task R253/.tmp/01-278-r253-rollout/.
Этот checkpoint имеет приоритет над историческими source-only записями ниже.

Source R252 —1 ноября, clean HEAD/origin/main/remote03d538f61812394c340d8686013238114507e747,
R251,parent978f6995d/scope6. Credential matching для MessagingService user/support
отправок реализован: immutable canonical child ID/platform из новых launcher env
APP_BOT_CHANNEL_ID/APP_BOT_PLATFORM, fingerprint фактического provider token всех
трёх adapters и свежий signed /internal/api/bot/outbound/authorize-sender.
Общий launcher/backend resolver не даёт inline fallback при broken explicit
credential binding; positive/unique/active/platform/token обязательны. Raw token
не передаётся в новом API, proof string redacted. Response exact channel/platform/
real boolean, no cache/local fallback/retry; deny/API error не вызывает provider.
После authorization повторяется local proof/channel check, включая token rotation.
Прежние recipient R249 и child route R251 проверки остаются.
GREEN600/19 suites,242 новых/358 старых; RED90/78 assertion failures/12 controls,
frozen source/bytecode/cases. Old2 fixtures только setup с checked inverse; остальные
assertions прежние. Full bot reactor441/12, panel141/6, native Windows launcher18/1;
no real PG/provider. Helper512MiB/1.5CPU, native heaps96/128. Bot180 main Java/280
classes (core208/203 unrelated exact); panel incremental1433/1417 unrelated exact,
216 inputs exact. Три executable bot JAR и incremental panel JAR с795 unchanged
entries проверены. Local candidate image63ba7ea78a38/source03d538f61+r252.8a275f3a1520,
не clean Git release и НЕ deploy; evidence .tmp/01-278-r252-evidence/.
Scope32/detached sandbox+replay/exact guards/no stage/commit/push/deploy. Working18/
config/DB84/fingerprints/flags прежние,126 published/actionable0/active pending0,
web/worker R246 healthy/restart0/readinessUP; rollback/backup R247 сохранены.
Working migrations/backfill/queues/provider writes не выполнялись. Source R248–R252
ещё не развёрнут; новым ботам сначала оба API R249/R252, затем новый runner+bot JAR.
Credential matching этого пути закрыт; separate per-child transport principal,
direct legacy provider paths/recipient chat rights/child-media-rating attribution,
API→provider TOCTOU/retry semantics, AI/policies/GENERAL/fleet/S6 acceptance и
operational gates остаются. S2B снятR190, задача🟡.

Source R251 —1 ноября, clean HEAD/origin/main/remote978f6995d7ecbdd31f686feafbe027782d22c8f7
(пользовательский R250,parentb9700927f/scope8). Только source, runtime R246/R247.
Feedback listener до dispatcher сверяет event platform/positive channel ID,
received routing key и captured immutable canonical child routing key/queue.
Missing/foreign/wildcard/padded/noncanonical route/queue не дают dispatch и side
effects ledger/API/provider. Mutable properties не перепривязывают running child;
valid event/dispatcher exception передаются без local retry/swallow. DTO/launcher
env/bindings/container DLQ/requeue rules/schema/flags прежние.
162 новых listener boundary cases с dispatcher double,3 platforms/42 и BIGINT>2^53;
RED162/150 assertion failures/12 controls, frozen source/bytecode/cases.
GREEN312/6 suites,162 новых+150 старых R250 без изменения fixtures/assertions.
Wrapper/reactor137 main/6 tests,205 classes/1 changed/204 exact; helper512MiB/1.5CPU,
no test DB. Classes archive независимо проверен. Scope6/detached sandbox+replay/
exact guards, no stage/commit/push/deploy; evidence .tmp/01-278-r251-evidence/.
Working18/config/DB84/fingerprints/flags прежние,126 published/actionable0/
active pending0, обе роли R246 healthy/restart0/readinessUP; rollback/backup R247
сохранены. Working migrations/backfill/queues/provider mutations не запускались.
Configured transport route admission не удостоверяет provider credentials или
per-child authenticated API principal; согласованно неверный launcher contract
не закрыт. TOCTOU/revoke/expiry/recipient chat rights/exactly-once/lifecycle/
child-rating/media/AI/прочие S3–S6 и operational gates остаются открытыми.
Source R248–R251 не развёрнут; новым ботам сначала нужен panel-web API R249.
S2B снятR190, задача🟡.

Source R250 —1 ноября, clean HEAD/origin/main/remoteb9700927f9e4d37d3de6c8a8da63d30cb8a54ed2
(пользовательский R249,parent83eb8e0ae/scope12). Только source, runtime R246/R247.
MessagingService не выбирает Telegram при missing/unknown platform/provider.
User/support-chat требуют resolved positive channel ID/active channel/explicit
platform с exact registered messenger; case-insensitive lookup сохранён. Duplicate
normalized providers fail при construction вместо выбора credentials list order.
Provider false/exception не запускает другую платформу. Feedback dispatcher
проверяет channel ID==event.channelId/active/platform до и после authorization I/O;
изменившийся локальный Channel не доставляется, ledger failed/retry прежние.

92 новых cases:47 actual router/DM/support-chat и45 actual dispatcher+router/
early-late channel mutation,3 platforms/BIGINT>2^53; API/provider doubles.
RED92/52 assertion failures/40 controls, frozen source/bytecode/cases.
GREEN150/5 suites,92 новых+58 прежних R249,0 failures/errors/skips; старые tests/
fixtures не менялись. Не backend/full fleet/provider rerun. Wrapper/reactor
137 main/5 tests,205 classes/2 changed/203 exact, helper512MiB/1.5CPU, no test DB.
Classes archive независимо проверен. Scope8/detached sandbox+replay/exact guards,
без stage/commit/push/deploy; evidence .tmp/01-278-r250-evidence/.
Первый fresh remote read потерял сеть, повтор exact guards подтвердил baseline.
Working18/config/DB84/fingerprints/flags прежние,126 published/actionable0/
active pending0, оба healthy/restart0/web readinessUP. Миграции/backfill/working
queues/provider mutations не запускались; rollback/backup R247 сохранены.
Не закрыты per-child authenticated channel/credential identity, DB/API→provider
TOCTOU/in-flight revoke/expiry/последующая мутация, external service-chat recipient
rights/provider retry/exactly-once/uniqueness/lifecycle/child-rating/media/AI/прочие
S3–S6/operational gates. R248–R250 ещё не развёрнуты; для новых ботов сначала
нужен panel-web API R249. S2B снятR190, задача🟡.

Source R249 —1 ноября, clean HEAD/origin/main/remote83eb8e0aee977baeff305e6582d4b9883696db2f
(пользовательский R248, parent2ffd7c1b2/scope5). Runtime ниже остаётся R246/R247;
R248/R249 source ещё не развёрнуты. Migration/flags/API defaults не менялись.

Bot feedback dispatcher проверяет recipient admission через новый signed POST
/internal/api/bot/feedback-prompts/authorize-delivery после ledger/channel и
непосредственно перед sendToUser. Прежний token/HMAC/body guard, read-only без
idempotency cache. Response только eventId/boolean allowed, без private payload.
DTO Rabbit прежний; private snapshot читается исключительно из owning outbox.
Exact received JSON/envelope/route/type/source/status processing|published,
V84 tuple+epoch/created_at/expiry/unique raw owner проверяются одним SQL вместе
с current active channel/platform и scope. Processing допускает consumer до
commit published marker. Missing/corrupt snapshot/schema/DB/API failure/deny не
дают allow и не включают local/raw fallback или recapture сегодняшнего бизнеса.

BUSINESS: foundationON+active business+active SINGLE_BUSINESS/EXPLICIT_SELECTION
binding без standalone GENERAL conflict. GENERAL: сохранённый explicit marker,
foundation+general resolution+active standalone GENERAL без active business/shared;
либо foundation+shared resolution+active shared GENERAL без standalone conflict.
Legacy UNRESOLVED только foundationOFF без любых active scoped bindings. Это
customer-own-ticket/channel admission, не подмена operator memberships/grants.
Bot дополнительно проверяет local active/platform. Новое allow не кешируется;
HTTP/malformed/mismatched response/string allow/exception не вызывают provider,
ledger failed и прежний listener retry/DLQ сохраняются; duplicate не делает API call.

RED unchanged dispatcher54/51 assertion failures/3 controls, frozen source/
bytecode/cases. GREEN556/9 targeted suites двумя sequential runs:58 bot+498 panel,
371 новых (54 loopback bot+312 PostgreSQL/V84+5 signed controller)/185 прежних.
Old fixture checked inverse сохраняет assertions/bodies. Не full R2481246 rerun,
не live broker/provider/fleet. Minimal owning schema+canonical V84; no data writes
при authorization, одинаковый idempotency key не кеширует прежний allow.
Bot wrapper/reactor137 main/3 tests,203→205 classes/202 exact/1 changed/2 added;
panel incremental2 main/8 tests,1424→1428/все1424 exact. Helper512MiB/1.5CPU,
PG192MiB/1CPU до readiness. Оба classes archive независимо проверены.
Scope12/detached sandbox+replay/exact guards, без commit/push/deploy; evidence
.tmp/01-278-r249-evidence/. Working18/config/DB84/fingerprints/flags прежние,
126 published/actionable0/active pending0, обе роли healthy/restart0/web readinessUP.
Рабочие migrations/backfill/queue/provider mutations не запускались,
rollback/backup R247 сохранены, working services/WSL/VS Code не останавливались.

Не закрыты SQL→API→provider TOCTOU/in-flight revoke/expiry, per-child channel
identity/provider retry/exactly-once, pending same-created_at reuse/DB uniqueness/
phantoms/lifecycle/child-rating/media/AI/другие S3–S6/operational gates R247.
Будущий порядок: новый panel-web API до новых bot artifacts; web404 у нового
бота fail-closed. Это описание rollout prerequisite, не разрешение/факт выкладки.
SourceR249 не развёрнут, S2B снятR190, задача🟡; следующие source/runtime записи
сохраняют свои исторические границы.

Source R248 —1 ноября, baseline clean HEAD/origin/main/remote2ffd7c1b2226887bd1141dfb213655ed3fcf19b7
(пользовательский metadata commit R247, parent9f163dfec). Runtime по-прежнему
R246/R247 ниже; новый source не развёрнут, миграций/flags нет.

Feedback prompt relay теперь удерживает существующие pending/root до broker
confirm и commit/rollback. Отдельная JDBC REQUIRES_NEW/READ_COMMITTED транзакция
на delivery: pending FOR UPDATE → owning root FOR SHARE (не KEY SHARE), после
ожидания final proof tuple/epoch/pending created_at/expiry; затем send/confirm/
markPublished. SQL budget30s и прежний confirm timeout15s не означают жёсткий
общий deadline. Claim/load/recovery и failed/retry вне этой транзакции; при NACK
rollback освобождает locks, failed/retry сохраняется отдельно. Enqueue/private
snapshot/API/bot DTO/конструктор прежние, schema/resources не менялись, минимумV84.

42 новых PostgreSQL/V84 cases:24 двухточечных NOWAIT55P03 probes на pending/root
во время send/confirm и реальные update/delete с rollback после release,
12 ACK/NACK release/status/retry controls,6 expiry changes при pending-lock wait.
3 scopes×2 exact raw IDs/BIGINT>2^53. RED42/30 assertion failures/12 controls;
frozen source/bytecode/case sets. GREEN1246/19 suites/42 новых+1204 прежних/
0 failures/errors/skips; старые tests/assertions/fixtures не менялись.
Incremental1 main/22 test sources;1424 classes/3 changed одной family/1421 exact.
Helper512MiB/1.5CPU, PG192MiB/1CPU до readiness, sequential; Rabbit — doubles.
Classes archive независимо распакован/сверен, image не собирался.
Scope5/detached sandbox+replay/exact guards, без stage/commit/push/deploy.
Evidence .tmp/01-278-r248-evidence/. Read-only control:18 working containers/
config и DB84/settings/accounts/grants/roots/pending/ratings/outbox прежние,
126 published/actionable0/active pending0, оба healthy/restart0/web readinessUP.
Рабочие migrations/backfill/flags/queue/provider writes не запускались,
rollback/backup R247 сохранены; WSL/VS Code/working services не останавливались.

Это исключает update/delete существующих owning rows во время relay, но не
атомарный DB→Rabbit commit и не consumer/provider authorization. Crash после
send/ACK до DB commit может повторить событие; queued delivery, recipient rights,
естественное expiry после final check, same-created_at pending-ID reuse до relay,
duplicate-root phantoms/uniqueness/lifecycle/owning child-rating и прочие
media/policies/AI/fleet/questionnaires/flags/S6/operational gates R247 открыты.
Следующий delivery gate — revalidation перед bot/provider send. R248 source-only,
S2B снятR190, задача🟡; runtime-факты далее не подменены source validation.

Актуальный runtime R247 —1 ноября, rollout00:37+03/acceptance11:47+03,
текущая Windows/Docker-сессия. По отдельному «разрешаю» web+worker до clean R246:
revision9f163dfecbb39e428a6b1d26c8a9136c587314b1,
image sha256:fd9b7ab03a1d28d4f827c8d807304c6df65909731b263c44741df37515ac2add,
web61d6199d6b1445fad1337472de83d48f60088730c8229d658a4d62e662c377a7,
workerae2d31cc92f6953ca9436c334beb515302d79367d73717889125990784e16540.
JAR SHA256ec2ab147c2c4fe19c55f6e06324548bf949947f179d7507d060c3384cd12a51b.
Verified R244 JAR+1424 tested classes/678 unchanged other entries/19 base layers,
только JAR layer добавлен. Incremental assembly, не full Maven build.
Compose tg_ref_b24_sup/contour+observability/.env/project directory прежние,
overlay .tmp/01-278-r247-rollout/compose.services.json. Обе old роли сначала
stopped, затем только web+worker --no-deps/--no-build/--pull never/force-recreate/
scale1/1. Оба healthy/restart0/readinessUP; web9/worker36 workloads, enforcement,
0 SINGLETON; R244/R246 dispatcher/outbox только в worker. Это уже не workerR198.

Остальные16 containers/IDs/images/start/restarts/ENV/mounts/HostConfig прежние,
db-migrate/bots/infra не обновлялись. ENV/mounts ролей точны, bootstrap preserved
через process-only variables без записи secrets; web HostConfig exact, worker
только Dns/DnsOptions/DnsSearch []→null, иной drift исключён. Env/Compose/shared
inputs прежние, heartbeat отдельно. DB84/V84checksum -783018026/foundationOFF,
Flyway/settings/accounts/grants/root/pending/ratings fingerprints прежние;
126 published feedback outbox/actionable0/active pending0 до/после. Миграции/
backfill/flags и ручные queue/provider/domain/grant writes не запускались;
штатные background monitoring/incident задачи продолжались.

1204/18 suites/126 new source proof,61 live access/readiness GREEN, admin
ROLE_ADMIN+PAGE_CHANNELS или PAGE_SETTINGS/anonymous/foundationOFF/SSE/logout.
Оба live JAR совпали; Flyway skip/0ERROR/web0WARN. Worker30WARN на acceptance:
SSL/iiko TLS и exact RMS claim-loss2cb28cc7-4af1-47c6-9b74-7c988591e749.
Такие TLS и тот же ID есть в old worker log; services/claim source прежние.
RMS running/attempt4/current worker/fresh heartbeat; manual retry/fix не делали.
Общий health503DOWN/DLQ1 до/после; очередь не чистили/не переотправляли.
TLS/RMS/DLQ остаются operational gates, не full fleet/provider/revoke/S6 acceptance.

Rollback tags iguana-panel:rollback-r247-web-20261101-a38d4ccb0992 на R244 и
iguana-panel:rollback-r247-worker-20261101-47ccd302c74f на R198 сохранены,
старые pointers/R245 backup также. Fresh same-host dump10178953 bytes/SHA256
7157D1219F89998D170BFDF83DACD6C58BF9FF0A3D3F271CA5ED0BF839268159,
.tmp/01-278-r247-rollout/postgres-before-services.dump; catalog readable, restore
нового dump/реальный rollback не тестировались. DB restore автоматически не
разрешён, перед rollback нужен контроль новых pending/private outbox данных.
Task/evidence — R247/.tmp/01-278-r247-rollout/. Metadata scope3/detached replay/
exact hashes, без commit/push: разрешение31 октября истекло, новое только runtime.
WSL/VS Code/infra ради ОЗУ не останавливали. R246 уже в обеих ролях: не повторять
rollout. Queued/provider delivery/recipient authorization/TOCTOU/same-created_at
ID reuse/uniqueness/phantoms/lifecycle/child-rating/media/policies/AI/fleet/
questionnaires/flags/S6/DLQ/TLS/RMS и actual rollback открыты, S2B снятR190, задача🟡.
Нижние «не развёрнуто/webR244/workerR198/runtimeR245» — исторические фазы.

Предыдущий runtime R245 —31 октября,19:45+03, текущая Windows/Docker-сессия:
panel-web R244, revision2d55cd9ae3d0e7c4358eb1853ad5df921a04cfe1,
image sha256:a38d4ccb099236a44c23b6a8a5228ac44b7420de15ad8f0cfa8bea14a54ca157,
container249cb7e70b3b93720bc19724e76ace419a0bdf538b24478d5866a53d34c188b1.
JAR SHA25692d228c49e210f051c738dcbc3dd7c3fe462710fd2297c19a3cf5958ef8c4da1.
Compose tg_ref_b24_sup/contour+observability/.env/project directory прежние,
overlay .tmp/01-278-r245-rollout/compose.web.json; только web --no-deps/
--no-build/--pull never/1 replica healthy/restart0/readiness200UP. Verified
R241 JAR+1423 tested classes/677 unchanged other entries/+V84 SQL,18 base layers
прежние; incremental artifact, не full Maven build.1067/16 suites source proof
и61 live read/access/readiness GREEN; реальные ROLE_ADMIN+PAGE_CHANNELS или
PAGE_SETTINGS, foundationOFF/anonymous/SSE boundaries и logout, без domain/grant
writes.0WARN0ERROR/явный web Flyway skip; общий health503DOWN прежний inboundDLQ1.

По отдельному «разрешаю» применена только V84, checksum -783018026, DB84/84
migrations. Одноразовый Compose db-migrate256MiB/1CPU: strict target83 validate,
единственная pending84, migrate84/validate84; canonical libs/SQL, без Spring
bootstrap/history normalizer/repair/baseline/backfill. Старые83 history rows,
roots/pending/ratings fields/settings/accounts/grants прежние.53 root epochs,
51 historical pending unbound,36 ratings,455 settings/0 automation runs.
Pre-V84 pg_dump10150128 bytes/SHA256
471D13E31AE8F6107F1CD90406DCA966EDCEA278D770F642053DF5D5C4A4862F сохранён
в .tmp/01-278-r245-rollout/postgres-before-v84.dump; restore/fingerprints/V84/
15 SQL checks/3 scopes на owned PG192MiB GREEN, scratch PG/volume/network
удалены. Это same-host backup, не off-host DR; рабочую БД не восстанавливали.
Все17 других containers/IDs/images/start/restarts/ENV/mounts/HostConfig прежние,
включая штатный exited db-migrate; workerR198/bots/infra не обновлялись.
R244 prompt dispatcher в worker НЕ активирован. Web ENV/mounts/полный
HostConfig точны, Compose/env/shared прежние, heartbeat отдельно.
Rollback iguana-panel:rollback-r245-web-20261031-38f548259598 на R241 сохранён,
старые R242/R240/R235 pointers также; фактический rollback/DB restore не делали.
Backfill/flags/queues/provider writes нет; WSL/VS Code/working services ради
ОЗУ не останавливали. Checkpoint/evidence — task R245/.tmp/01-278-r245-rollout/.
Worker rollout/queued provider delivery/recipient authorization/non-cooperating
writers/DB uniqueness/phantoms/pending-ID reuse/lifecycle revision/owning child
rating attribution и media/policies/AI/fleet/questionnaires/flags/S6/DLQ1 открыты.
S2B снятR190, задача🟡. Нижние source/runtime фазы исторические; их «не
развёрнуто/V83» не отменяет фактическое V84/webR244 из этого checkpoint.

Предыдущий runtime R242 —31 октября,14:32+03, текущая Windows/Docker-сессия:
только panel-web обновлён с R239 до clean R241
94c2d812cfec81ad9ba1b4c331e418375d9b75da (commit/push отдельно),
image sha256:38f548259598c70ff6afc87c9afeabed971fa9d99142b78e0c252e8336f86feb,
containercc4d452ad41e351daa3e352be149ca19b051a06f76ab0580d656a899171441c3,
JAR SHA25679815e522ea82f9bb211ec77b7ad6cd88c37789a26e44a02b0e1ffaf09ebbf3f.
Compose project tg_ref_b24_sup, прежние contour/observability/.env/project directory,
новый overlay .tmp/01-278-r242-rollout/compose.web.json; только web --no-deps/
--no-build/--pull never/1 replica, healthy/restart0/readiness200UP.
Verified R239 JAR+1422 tested classes/677 прочих entries прежние; incremental
artifact assembly, не full Maven build. POM/schema/resources/bots/entrypoint
прежние,17 base layers сохранены, добавлен JAR layer; image/live JAR hash совпал.
GREEN900/12 suites/0 failures/errors/skips;61 live read/access/readiness checks,
реальный admin ROLE_ADMIN вместе с PAGE_CHANNELS или PAGE_SETTINGS, settings
controls/ID/foundationOFF denies/SSE headers. Logout, domain/grant writes нет.
0WARN0ERROR/явный Flyway skip; общий health503DOWN из-за прежнего inboundDLQ1,
очередь не менялась. Не live feedback/provider/revoke/full fleet/S6 acceptance.
Все17 других containers и web ENV/mounts/полный HostConfig прежние, heartbeat
отдельно; DB fingerprints/83 migrations/V83/foundationOFF прежние. Working
migrations/backfill/flags/queues/provider/external writes не выполнялись.
Rollback iguana-panel:rollback-r242-web-20261031-09b20a52c8a4 на R239 сохранён;
R240/R235 и более ранние pointers также, фактический rollback не запускался.
Owned512MiB helper удалён после proof/artifact, test DBs завершены reaper;
WSL/VS Code/working services ради ОЗУ не останавливались. Pending generation/
non-cooperating writers/DB uniqueness/cache/phantoms/ABA/epoch/owning child-rating
attribution и прочие media/policy/AI/fleet/questionnaire/provider/flags/S6/DLQ1
остаются; S2B снятR190, задача🟡. Task01-278 R242/evidence
.tmp/01-278-r242-rollout/. Нижние source-фазы исторические: R241 уже в web.

Предыдущий runtime R240 —31 октября,12:35+03, текущая Windows/Docker-сессия:
только panel-web обновлён с R234 до clean R239
f09f29f9543e94eaf736497ec5ac662f7dea4a9d (source commit/push выполнены отдельно),
image sha256:09b20a52c8a41c28158e81b4f90168506e59d5ec7e54beb14b37b24242229cb4,
container0357f2a4a4ff9d4f661d89be935333c719bdd15991ab68d824ecae1a16a13a6f.
Compose project tg_ref_b24_sup, те же contour/observability/.env/project directory,
новый isolated web overlay .tmp/01-278-r240-rollout/compose.web.json;
--no-deps/--no-build/--pull never/1 replica. Web1 healthy/restart0/readiness200UP.
Все17 прочих containers, workerR198/db-migrate/bots/infra прежние; web ENV/mounts/
полный HostConfig совпали точно, Compose/env/shared inputs прежние, heartbeat отдельно.
Verified trusted R235 JAR +1422 tested classes,677 прочих entries прежние;
incremental artifact assembly, не full Maven build. POM/schema/resources/bots/
entrypoint не менялись;16 base layers сохранены, добавлен только JAR layer.
SHA256 JAR31d13466cfb1ab72eb7067c30ccb86b07f02340c6a51b791d57c22ebf5aa9763
совпал в artifact/image/live. GREEN846/10 suites/0 failures/errors/skips;
61 live read/access/readiness checks, реальный admin login с ROLE_ADMIN вместе
с PAGE_CHANNELS или PAGE_SETTINGS, SSE headers, foundationOFF deny/anonymous
routes. Logout выполнен, domain/grant writes нет.0WARN0ERROR/явный Flyway skip.
Общий health503DOWN из-за прежнего inboundDLQ1; очередь не менялась. Это не
live bot/provider/revoke/full fleet acceptance. FoundationOFF,83 migrations/V83
и Flyway/settings/users/authorities/roles fingerprints прежние; migration/backfill/
flags/queue/provider/external writes не выполнялись. Новый rollback
iguana-panel:rollback-r240-web-20261031-2643c85d1874 на R234 image2643c85d1874
сохранён, старые pointers также; реальный rollback не выполнялся. Owned640MiB
helper удалён после proof/artifact, рабочие сервисы ради ОЗУ не останавливались.
R236–R239 теперь только в web; pending generation/разные request IDs/rating owning
persistence, relay/operator-edit/activity/прочие producers/children/media,
PENDING policies/AI/fleet/questionnaire/provider/flags/S6/DLQ1 прежние.
S2B снятR190, задача🟡. Подробный task checkpoint R240/evidence
.tmp/01-278-r240-rollout/. Ниже исторические source/runtime фазы до R240;
их «ещё не развёрнуто» не отменяет этот checkpoint.

Предыдущий runtime R235 —30 октября, текущая Windows/Docker-сессия SinicinVV:
только panel-web обновлён с R222 до clean R234
e1b85101f2f52f051080a35cb2a7ea222c39ae0a,
image sha256:2643c85d1874a0627c5211dd5e582d96aad3b684449c466bc0e47ce7512a27f3.
Web1 healthy/restart0; workerR198 и остальные17 containers прежние. ENV/mounts/
Compose/env/shared inputs сохранены, mutable backup heartbeat отдельно;
HostConfig совпал либо доказана только Binds permutation. JAR/1421 classes,
1391 Java/83 SQL/1870 inputs проверены,4 generated CSS EOL-only. POM/schema/
entrypoint/bots прежние, сохранены15 base layers, добавлен только JAR layer.
3578 tests/21 suites GREEN, failures/errors/skips0;61 live read/access/readiness
checks, включая R233 SSE handshake, прошли без domain/grant writes. GENERAL
HTTP138 GREEN; старый долг89 failures закрыт R227/R228. Readiness200UP,
logs0WARN0ERROR/явный Flyway skip; общий health503DOWN из-за прежнего inboundDLQ1.
FoundationOFF; Flyway/settings/users/authorities/roles fingerprints прежние,
83 migrations/V83 не повторялась. Migration/backfill/flags/queue/provider не
трогались. Rollback iguana-panel:rollback-r235-web-20261030-0558a3411df9 сохранён,
как и предыдущие pointers/backups; фактический rollback не запускался.
Полный checkpoint/evidence — task01-278 R235; это не fleet/flags/S6 acceptance.
Далее bot-runtime edit/feedback root/context proofs (transport-token не равен
operator Authentication), оставшиеся child/media/delivery, PENDING policy/AI
privacy/context/retention и fleet/provider acceptance. S2B снятR190, задача🟡.

Source R246 — историческая подготовка private feedbackTicketContext до R247:
enqueue читает один exact current V84 pending/root с persisted full tuple/epoch/
active expiry, сохраняет private epoch/public/business/GENERAL/request created_at
и повторяет proof после serialization. Relay требует этот snapshot, сверяет
envelope и current pending/root перед Rabbit publish; не recapture сегодняшний
контекст для old/unbound/expired/recreated/ABA/reused-ID с другим created_at.
Private node удаляется до прежнего transport DTO: bot/API/wire constructors,
schema/migrations прежние, минимум V84. Historical queued payload без context
остаётся failed по прежнему retry path, не удаляется/перепривязывается; quarantine
policy не добавлена.126 новых cases на real PostgreSQL/V84,3 scopes×2 raw IDs:
RED126/114 assertion failures/12 controls/0 errors/skips, frozen source/bytecode/
case names. GREEN1204/18 suites/0 failures/errors/skips одним run:126+старые1067+
outbox11. Один fixture adapted под V84, old test bodies/assertions exact inverse.
ABA tests меняют public ID/channel; business change проверяется отдельно.
Compiled1 main/21 tests;1424 classes/1421 unrelated прежние,3 class paths одной
source family. Helper512MiB/PG192MiB before readiness/sequential, не full build.
Rabbit publish/confirm doubles; не live broker/provider. Scope7/detached replay/
hash guards/apply отдельно от Git publisher; evidence .tmp/01-278-r246-evidence/.
RuntimeR245/webR244/workerR198/DB84/foundationOFF/18 containers/config/DB fingerprints
прежние, heartbeat отдельно; R245 backup/rollback сохранены. Working migration/
backfill/flags/queues/provider/rebuild/restart/deploy не делались; WSL/VS Code/
working services ради ОЗУ не останавливали. Это outbox admission, не атомарность
DB→Rabbit: TOCTOU после SQL, broker-queued delivery/retry/recipient authorization,
same-created_at request-ID reuse/DB uniqueness/phantoms/lifecycle/child-rating,
прочие media/policies/AI/fleet/questionnaires/flags/S6/DLQ1 открыты. Worker rollout
требует отдельного scope; old worker не проверяет private context, смешанный
rollout не принят. RuntimeR245 не повторять. S2B снятR190, задача🟡.

Source R244 — историческая source-фаза до R245 (теперь web; не workerR198):
native projection допускает один exact root с теми же owner/raw/channel/public/
business/GENERAL/epoch и читает persisted expiry/sent_at. Unbound/старое поколение
не подменяется сегодняшним root. Active lookup делает final proof после rating
read, включая fallback. Dispatch блокирует pending→root, проверяет context/expiry/
unsent ещё раз после template, берёт recipient из locked request. Writer сохраняет
R241 locks и сверяет persisted context с frozen tuple до rating lookup/после
ожиданий; final expiry из native Instant projection. API DTO/constructors прежние.
132 новых cases:RED132/114 assertion failures/18 controls/0 errors/skips;
frozen source/bytecode/cases. GREEN1067/16 suites одним accepted run,0 failures/
errors/skips:132+прежние900+read27/dispatch7+R243 contract1 (156 SQL assertions
внутри).8 fixture adaptations checked inverse exact; прежние assertions не
менялись. Mock helper — только admission plumbing старых fixtures, не native proof.
Compiled5 main/18 tests,1423 classes/1409 unrelated unchanged. Helper512MiB/
PG192MiB/sequential. Первый Instant projection defect исправлен без правки tests.
После full1067 без resource proof повторены все1067 с shared-read watcher;
null Start-Process ExitCode отдельно воспроизведён, acceptance-v3 проверяет
native bound JSON/stdout/ID/session/time/empty stderr/fresh XML/frozen hashes.
Scope18/task/evidence .tmp/01-278-r244-evidence/, отдельные apply и publish;
commit/push разрешены до конца31 октября. WSL/VS Code/working services не трогали.
Working18 containers/DB83/settings/readinessUP/runtimeR242 прежние; webR241/
workerR198, rollback сохранён. Новой migration нет, но source требует V84 изR243;
working migration/rebuild/restart/deploy/backfill/flags/queues/provider не запускались.
Не queued/provider delivery/recipient authorization/lifecycle revision/DB uniqueness/
phantom/pending-ID reuse/owning persisted rating proof и не S6 acceptance. Эти
и прочие media/policies/AI/fleet/questionnaires/flags/DLQ1 gates открыты, S2B снятR190.

Source R243 — историческая подготовка хранения до применения V84 в R245:
V84 добавляет PostgreSQL-owned UUID epoch для identity/scope корня: новый INSERT
и смена user/raw/channel/public/business/GENERAL дают новый токен, включая ABA
и попытку передать прежний токен при пересоздании. Status/work не меняют epoch:
это не lifecycle revision. Старым корням при будущем применении создаётся
технический epoch; историческим pending не назначается ни он, ни бизнес.
DB BEFORE INSERT захватывает один exact root FOR SHARE, сверяет user/channel,
сохраняет epoch/public/business/GENERAL; missing/duplicate/foreign/partial/spoof
отклоняются23514. UPDATE замораживает owner/raw/channel/created_at/context,
включая запрет upgrade старого unbound pending; source/expiry/sent_at меняются
без перепривязки. Canonical schema только в backend, java-bot schema не менялась.
Native V1..V83+V84/156 SQL assertions GREEN, frozen V83 assertion RED; реальный
двухсессионный root-lock55P03/release/rollback proof. Затем30 JUnit/5 suites GREEN,
0 failures/errors/skips, в том числе1 новый Flyway contract с теми же156 checks
и29 прежних cases. Старые assertions сохранены, V82→V83 fixture pinned target83.
Compiled8 test sources/verified1422 unchanged main classes; не full rebuild.
Helper512MiB/PG192MiB; после missing test dependencies и Metaspace144MiB RED
повторены все30 при heap160/meta224/code32/stack512k в том же budget, tests frozen.
WSL/VS Code/working services не останавливались. Detached sandbox/replay/scope8/
content guards перед отдельным apply/publish; commit/push разрешены до конца
31 октября. Evidence .tmp/01-278-r243-evidence/, task R243.
Рабочие V83/webR241/runtimeR242 не обновлялись: миграция/backfill/flags/provider/
queues/worker/bots/infra/deploy не запускались, rollback pointers сохранены.
Это только persisted capture contract. Далее mapping и fail-closed epoch/tuple
proof в pending read/prompt dispatch/storeFeedback; consumers пока его не читают.
Не end-to-end isolation, не recipient authorization/DB uniqueness/phantom/cache/
lifecycle generation/owning rating proof и не S6 acceptance. Остальные gates
runtimeR242 сохраняются, S2B снятR190, задача🟡.

Source R241 (развёрнут R242; ниже сохранена исходная source-фаза):
Bot-runtime до existing-rating lookup получает FOR UPDATE на frozen root с
прежними full tuple/count guards; это сериализует разные pending request IDs.
Одна rating row, два валидных запроса могут обновить её и опубликовать два events.
Selected existing rating читается через JPA PESSIMISTIC_WRITE до setters/save;
повторный root proof после read сохранён. Fresh now/expiry после всех locks:
истёкший во время root/rating ожидания request не меняет rating/event/expiry.
Validation/raw/BIGINT/nullable scope/identity/R238 outbox/R239 rollback прежние.
42 real Hibernate cases: split RED42/30 failures/12 controls/0 errors/skips;
предварительный GREEN888. Затем12 отдельных scripted repository-wait cases
RED12/12 на предварительном R241, fresh clock-check перемещён после всех locks.
GREEN900/12 suites/0 failures/errors/skips,846 старых cases/assertions/source
не менялись,0 adapted fixtures. Новые source/4 class hashes/cases frozen после
своих RED; дополнительные12 — mocked JPA +real root SQL, не full MVCC proof.
Verified R239 JAR/1422 baseline classes; green1422/1417 unrelated прежние,
incremental2 main/13 test sources, не full build. User разрешил доступные мощности:
owned helper512MiB/1.5CPU, JVM224/144/48MiB; owned test PG192MiB/1CPU. WSL/VS Code/
working services не останавливались.83 migrations только isolated test schema.
Все18 runtime containers/config/DB fingerprints прежние, heartbeat отдельно;
web R239/readinessUP/health503DLQ1, новый rollback R242 на R239, старые сохранены.
Working migrations/backfill/flags/queues/provider/worker/bots/infra прежние.
Scope7, guarded source apply без commit/push/deploy; отдельная публикация разрешена
до конца31 октября по Москве, факт — publish.json; rollout/cleanup — R242.
Cooperating bot-runtime writers, не DB uniqueness/исторические дубли/epoch proof.
Pending/public/business generation provenance, другие writers/cache/phantoms/ABA,
child/rating attribution/relay/operator-edit/activity/other producers/media/AI/
PENDING policies/fleet/questionnaire/provider/flags/S6/DLQ1 остаются. S2B снятR190,
задача🟡. Task01-278 R241/evidence .tmp/01-278-r241-evidence/.

Source R239 (развёрнут R240; ниже сохранена исходная source-фаза):
Bot-runtime получает pending request через отдельный JPA PESSIMISTIC_WRITE query
до root/expiry/rating lookup; обычные read/dispatch findById callers прежние.
Row lock держится до commit/rollback, повторный ответ на тот же request ID после
ожидания видит expiry и не пишет вторую оценку/event. Pending-save exception
больше не превращается в успешный результат: rating/outbox/expiry откатываются
вместе. Validation/raw/BIGINT/nullable scope/result/existing identity и R238
root/outbox proofs сохранены; новых migrations/колонок/flags нет.
54 новых real Hibernate/PostgreSQL cases:12 two-thread duplicate/replay,
6 pending NOWAIT locks и36 rollback (rating/event/expiry, new/existing).
Frozen RED54/30 assertion failures/24 controls/0 errors/skips; GREEN846/10 suites,
0 failures/errors/skips. Новые source/3 bytecode hashes/case names неизменны;
3 old fixtures изменены только в имени lookup stub, все assertions прежние.
Предварительные compile/proxy test-fixture ошибки исправлены до валидного RED-v2.
JpaRepositoryFactory/SharedEntityManager/JpaTransactionManager+JDBC используют
одну изолированную datasource; working DB не использовалась. Baseline1422/
green1422 classes,1417 unrelated прежние, incremental3 main/11 test sources.
Не full build. Owned640MiB helper удержан для отдельной artifact/rollout фазы.
Все18 working containers/config/DB fingerprints прежние; web R234/1 healthy/
readinessUP/health503DLQ1. Новый rollback R240 указывает на R234, старые сохранены.
83 working migrations/V83 прежние; migrations/backfill/flags/queues/provider/
worker/bots/infra/deploy не выполнялись. Scope9, guarded source apply без
staging/commit/push/deploy; пользователь разрешил отдельную публикацию до конца
31 октября по Москве. Факты публикации — отдельный publish.json, deploy — R240.
Далее persisted pending/public/business generation, разные request IDs одного
обращения/owning rating persistence, relay/operator-edit/activity/other raw
producers/children и ABA/phantom/epoch. PENDING/AI/fleet/provider/flags/S6/DLQ1
прежние, S2B снятR190, задача🟡. Task01-278 R239/evidence
.tmp/01-278-r239-evidence/; R236–R239 ещё не в рабочем web на source-фазе.

Source R238 (историческая source-фаза до R239, тогда ещё не развёрнут):
Reopen/client-edit/operator-edit/feedback передают captured TicketContext в
outbox. INSERT сохраняет исходный raw ID/owner/channel/public/business/kind,
повторно проверяя exact root tuple/count; raw-ID recapture для этих4 callers
нет. Nullable-marker suppression/payload/temporal semantics сохранены;
старый append/overloads других producers и recipient/GENERAL validators прежние.
Feedback после existing-rating read сверяет frozen root и берёт FOR SHARE до
entity setters/save; deny не меняет rating/pending expiry/event. Транзакционный
root lock держится через save/outbox/request save, но pending generation и его
atomic claim/expiry не реализованы; JPA owning persistence остаётся отдельным gate.
RED332/246 assertion failures/0 errors/skips,86 controls; GREEN792/9 suites,
332 новых+460 прежних cases,0 failures/errors/skips. Новые source/bytecode/cases
frozen;2 fixtures адаптированы только к typed context, data/deny assertions прежние.
6 real two-connection lock checks/2 SQL rollback; остальные boundaries scripted.
JPA mocked/не Hibernate acceptance, controller filters=false/не full security chain.
Verified baseline1421/green1422 classes, incremental2 main/10 tests,
1416 unrelated classes прежние; не full build/deploy. Owned640MiB helper удалён.
Все18 working containers/config/DB fingerprints прежние, heartbeat отдельно;
web R234/readinessUP/health503DLQ1, rollback сохранён.83 migrations только test
schema; working migrations/backfill/flags/queue/provider/worker/bot не трогались.
Далее pending provenance/generation/claim/expiry, rating owning persistence,
relay/operator-edit/activity/other raw producers/children, ABA/phantom/epoch.
PENDING/S6 прежние, S2B снятR190, задача🟡. Task01-278 R238/evidence
.tmp/01-278-r238-evidence/, scope8, commit/push/deploy=false.

Source R237 (ещё не развёрнут):
Bot-runtime root snapshot сохраняет также exact public ID/business ID/GENERAL.
Owning reopen UPDATE повторяет весь root tuple и raw-ID uniqueness; client-edit
UPDATE — root tuple/uniqueness и channel/provider/client history uniqueness.
Nullable markers/projection сравниваются без scope inference. Client root
FOR SHARE удерживается до transaction end/outbox handoff; reopen держит строку
своим UPDATE. Два независимых connection tests подтверждают lock timeout55P03
во время handoff и освобождение после commit. Owning deny не пишет downstream,
сохранён прежний нулевой MutationResult(false,true); grants/token/URI/body прежние.
Frozen RED157/109 assertion failures/0 errors/skips,48 controls; GREEN346/5 suites,
157 новых+189 прежних cases,0 failures/errors/skips, tests/assertions не ослаблены.
Scripted boundary matrix не является полным concurrent MVCC/phantom/ABA proof;
controller filters=false не доказывает security chain. Verified R235 JAR/R236
1421 classes, incremental1 main/6 test sources,1417 unrelated classes прежние.
Все18 рабочих containers/config/DB fingerprints прежние, backup heartbeat отдельно;
web R234/readinessUP/health503DLQ1. Owned640MiB helper удалён, rollback сохранён.
Migration/backfill/flags/queue/provider/worker/bot/deploy не выполнялись.
Далее frozen outbox context/pending/JPA feedback, relay/operator-edit/activity
owning bindings, child attribution и full-tuple ABA/phantom boundaries; epoch
протокол не введён. PENDING/S6 прежние, S2B снятR190, задача🟡.
Task01-278 R237/evidence .tmp/01-278-r237-evidence/, scope5; commit/push/deploy=false.

Source R236 (ещё не развёрнут):
BotRuntimeTicketWriteService.loadTicket повторно доказывает однозначность в
owning SELECT: LIMIT2/extractor отвергает вторую строку, даже если ранний
hasUniqueTicket видел только одну. Общий resolver обслуживает reopen/relay/
client edit/operator edit/feedback. Raw ID, BIGINT owner/channel, nullable поля,
BUSINESS/GENERAL/UNRESOLVED и ранний fail-closed guard сохранены; transport-token
не подменяет operator Authentication, grants/URI/body/signatures прежние.
Frozen RED96/18 assertion failures/0 errors/skips,78 controls GREEN; GREEN157/
3 suites,96 новых+61 прежний SQL/controller test,0 failures/errors/skips.
Новые source/bytecode/case names неизменны, старые tests/assertions не менялись.
Controller filters=false: token/delegation proof, не full security chain.
Scripted statement boundary, не concurrent MVCC. Проверенный JAR R235/1421
classes и отсутствие main source diff подтверждены; incremental compile1 main/
4 tests,1417 unrelated classes прежние. Это не full build/deploy.
Все18 рабочих containers и config/DB fingerprints прежние (backup heartbeat
отдельно); web R234/readiness200UP/health503DLQ1. Migration/backfill/flags/queue/
provider не трогались, owned640MiB helper удалён, rollback сохранён.
Далее owning DML root/context, frozen business/public/kind/generation outbox,
pending/JPA feedback binding и post-lookup смена root; register/clear activity
не покрыты этим fix. Общий остаток/PENDING/S6 прежний; S2B снятR190, задача🟡.
Полный source/runtime checkpoint — task01-278 R236,
evidence .tmp/01-278-r236-evidence/; scope5, commit/push/deploy=false.

Source R234 (развёрнут в R235):
R234: все14 API операций BusinessAdministrationService принимают исходный
Authentication; unbound overloads удалены. Controller передаёт тот же principal
для overview/channel inventory/binding preview, business create/rename/status,
membership/ALL grant/revoke, location create/status и channel binding save/disable.
Package-private BusinessAdministratorSql проверяет исходные BIGINT ID и exact
raw-name, enabled/COALESCE(blocked,FALSE), отсутствие folded-дубликата и текущие
ROLE_ADMIN + PAGE_SETTINGS в каждом owning SQL. Предварительное чтение assigned
role — только snapshot: raw id/name/permissions повторно сверяются в CTE с locks;
direct authorities также читаются/блокируются в том же statement. Семантика role
name/permissions совпадает с login/GeneralTicketAccessService через существующий
PanelRolePermissionSnapshot. Session authorities не заменяют текущие DB grants.
Каждый DML зависит от administrator до изменения данных, а outer result отдельно
различает отказ403 и допустимый пустой результат/отсутствующую цель. Overview
проверяет права в каждом из5 SELECT; отказ не возвращает частичный catalog.
Сериализация channel edits и прежние validation/status/identity/grant policies
сохранены. Это statement-level account/rights proof, не новая lifetime/session
epoch или serializable policy против изменения после snapshot.

615 tests /5 suites GREEN одним selected run v5.
589 новых:505 SQL/service и84 HTTP с реальным security chain/R189/controller/SQL.
Проверены все14 operations, rename/delete reuse, case/state/duplicates, revoked
direct rights, invalid/unbound/anonymous/unauthenticated auth; no-write snapshots.
Положительные raw edge spaces/Unicode, BIGINT>2^53/Long.MAX, NULL-blocked,
direct/mixed/assigned role sources и13 login-compatible role/permissions forms.
56 controlled mutations между role snapshot и owning statement; это scripted
boundary tests, не concurrent MVCC proof. HTTP early revocations сохраняют
прежний R189 redirect/403; valid HTTP path использует реальные service queries.
Frozen baseline: 589 tests/323 assertion failures/0 errors/
266 controls на неизменённом product; source/class/case names
новых тестов после baseline не менялись. 3 прежних fixtures адаптированы только
для Authentication: отдельный administrator без business grants и Mockito args;
старые assertions сохранены и suites выполнены целиком.

Scope11:3 main (service/controller/new SQL helper),3 old fixtures,2 new tests,
task/S0/new append-only changelog. Prior R23390902715b, parent5d2ceefc1,
subject/15 paths/content проверены. HEAD/origin/actual remote совпадали: R232/R233
опубликовал владелец. Cache1388 Java/2265 classes сверены; untouched inputs/classes
прежние. Изолированный detached worktree, Maven wrapper/JDK17, pinned ignored
native supplierR231 с UTF8/ICU und guards, PostgreSQL16.15/loopback54167,
iguana_r231_unicode/fresh schemas. Docker не использовался; owned PG остановлен.
UI/schema/migrations/deps/flags прежние; Sass EOL-only outputs восстановлены.
Первый unfrozen harness остановлен до evidence для reuse одной owned connection
на case. Следующий fixture run выявил GENERATED ALWAYS setup error и неверное
ожидание403 вместо прежнего R189 login redirect; fixtures исправлены до product,
предыдущие sources/logs сохранены, финальная RED/GREEN пара отдельная. Product
operator previewv1 остановлен до записи из-за неоднозначного overview() anchor;
v2 использует declaration anchor и WHERE TRUE перед INSERT SELECT ON CONFLICT.
При первом сохранении RED metadata source snapshot не успел завершиться перед
адаптацией fixtures; основной checkout не затронут. Premature v4 дал только
compile failure без test execution. Все sessions завершены; fixtures восстановлены
exact baseline bytes, product source/classes сверены прежними, frozen v3 reports
и test classes сохранены повторно перед product edit. Evidence sequence recovery
сохранено; эта ошибка orchestration не подменяется product/test failure.

Source/test сессия C:\Users\sushi\Git_H\tg_ref_b24_sup; рабочий экземпляр на
другой машине/сессии C:\Users\SinicinVV\git_h\tg_ref_b24_sup; доступа к нему не
было. Последний документированный runtimeR223: webR222/workerR198,
foundationOFF/readiness200UP/health503DOWN из-за прежнего inboundDLQ1.
Runtime/rollback blocks сохранены. Working deploy/restart/DB/flags/provider/queue
mutations не выполнялись; V83 не повторять. SourceR224–R226 и R228–R234 ещё требуют
будущего panel-web image, R227 tests/docs отдельного rollout нет. Commit grant
до конца30 октября Europe/Moscow; push оставлен владельцу по его последнему
указанию, автоматический push не возобновлять. Apply/Git факты — ignored
r234-result/final JSON; source validation не является deployment/acceptance.

Далее: оставшиеся BUSINESS edit/feedback account boundaries и page/notification
решения; task/project/system/global PENDING, AI privacy/context/retention/delivery,
fleet/flags/S6 acceptance и DLQ gate. S2B снятR190; общая задача остаётся 🟡.

Source R233 (историческая source-сводка, не развёрнут):
R233: legacy/BUSINESS SSE connection принимает исходный Authentication и
сохраняет immutable UiEventRecipient(ID, exact raw-name), без String fallback.
UiEventsController не пересобирает identity по имени. Каждый ticket-bound send
повторно проверяет именно этот account в owning SELECT: ID/raw-name, enabled,
strict blockedFALSE, fold uniqueness; READ берётся из прежнего membership/ALL
policy того же account. SQL lower вычисляется от найденного account, а не
Java-folded имени. Два account с переиспользованным именем имеют разные cohorts;
targeted routing использует прежний ROOT-fold key и не является grant. Cleanup
старого cohort не удаляет соединение нового account. Между emitters не кэшируется
READ. Формат fanout/Redis envelope, payloads, producer owner capture/recheck,
composite tuple/public ID/uniqueness/GENERAL boundary и strict compatibility прежние.
Системные invalidations/sidebar/notification refresh/heartbeat сохраняют прежнюю
политику; это account binding ticket events, не новая page/notification policy,
не session-epoch/logout invalidation и не блокировка отзыва после SQL snapshot.

398 tests /9 suites GREEN одним финальным selected run v4.
193 новых:179 SQL/stream и14 real form-login/R189/open servlet SSE HTTP.
Проверены exact raw names/Unicode/edge spaces, BIGINT>2^53/Long.MAX, invalid,
anonymous/unbound/unauthenticated principal, rename/reuse/delete/case/state/
NULL-blocked/duplicate, membership и ALL. 28 controlled SQL boundary mutations,
70 local/captured/broadcast/target/JSON delivery cases, separate replacement
connection/cleanup, per-emitter changes и publication fallback. SQL/script
boundaries не являются доказательством concurrent lock/session lifetime policy.
Frozen baseline193/108 assertion failures/0 errors/85 controls; положительные
raw-name/BIGINT/real-login cases GREEN на неизменённом product. После этого
source/class/case names новых тестов не менялись.
Прежние 5 fixtures и fanout/runtime contract прошли целиком. Полный v2 был RED:
398 tests/3 failures/0 errors, все193 новых GREEN. Legacy fixture ожидал
trim адресата; isolated surefire:test на неизменённых 2260 R232 classes дал
те же6 cases/3 failures (class hashes до/после прежние). Adapter v2 использует
точный canonical target и добавляет отдельный deny для spaced neighbor; старые
assertions не удалялись. Затем весь selection повторён для финального GREEN,
не собран из failed run. Preview product v1 остановлен до записи: tuple WHERE
был также в producer query; v2 сузил anchor к lateral alias. Evidence сохранено.

Scope15:4 main (включая новый package-private recipient),5 old fixtures,
3 new test sources,task/S0/new append-only changelog. R232 cache1384 Java/
2260 classes сверены; не затронутые inputs/classes прежние. Maven wrapper/JDK17,
ignored native R231 supplier с pinned source/class и UTF8/ICU und guards.
Owned PostgreSQL16.15/loopback54167/iguana_r231_unicode, fresh test schemas,
Docker не запускался, owned PG остановлен. UI/schema/migrations/deps/flags прежние;
Sass EOL-only outputs восстановлены exact baseline bytes в sandbox.

Source/test сессия C:\Users\sushi\Git_H\tg_ref_b24_sup; рабочий экземпляр на
другой машине/сессии C:\Users\SinicinVV\git_h\tg_ref_b24_sup, доступа к нему
не было. Последний документированный runtimeR223: webR222/workerR198,
foundationOFF/readiness200UP/health503DOWN из-за прежнего inboundDLQ1.
Runtime/rollback blocks сохранены. Working deploy/restart/DB/flags/provider/
queue mutations не выполнялись; V83 не повторять. Source R224–R226 и R228–R233
ещё требуют будущего panel-web image, R227 tests/docs отдельного rollout нет.
Baseline HEAD5d2ceefc1 локально содержит R232, origin/actual main при старте
f1ad1d5ab (R231), локальная ветка ahead1. Продолжение разрешено от этого
проверенного local commit; pending push не подменяется утверждением публикации.
Прежний commit grant действует до конца30 октября Europe/Moscow. Push оставлен
владельцу; автоматический push не возобновлять без нового указания.
Фактические apply/Git состояния — ignored r233-result/final JSON.

Далее: оставшиеся BUSINESS edit/feedback/admin account boundaries и page/
notification decisions, PENDING task/project/system/global policies, AI privacy/
context/retention/delivery, fleet/flags/S6 acceptance и DLQ gate. Исторический
S2B снятR190; общая задача остаётся 🟡, deployment не заявляется.

Source R232 (историческая source-сводка, не развёрнут):
R232: BUSINESS catalog/list/page/export/status/category summaries и manual create
принимают исходный Authentication вместо публичного ID-only API. ReadActor
сохраняет ID/exact raw-name; 4 owning SELECT проверяют их в users вместе с
enabled=TRUE, strict is_blocked=FALSE и fold uniqueness. Membership/ALL_BUSINESSES,
active business, filters/search/order/limits/413 и private headers прежние.
ManualActor не разрешает unbound/anonymous/stale principal; первый owning INSERT
manual_business_clients атомарно проверяет исходные ID/raw-name, current account,
business и прежнюю OPERATOR/MANAGER/ADMIN роль. Транзакция, namespace allocation,
location/input validation и дальнейшие message/ticket/span writes прежние.
У отказа нет новых client/ticket/message/span rows; sequence может расходоваться.
Это граница owning statement snapshot, не обещание блокировки позднейшего отзыва.
R224 early entry SQL и весь R231 card/child/media блок побайтно сохранены.

627 tests /13 suites GREEN одним финальным selected run (v5).
388 новых:303 SQL и85 real form-login/R189 HTTP с реальными controller/service/
SQL, включая manual writes. Проверены exact raw names, Unicode/edge spaces,
BIGINT>2^53/Long.MAX, wrong ID/name/unbound/anonymous/state, rename/reuse/delete,
strict NULL blocked/duplicate/revoke/archive и membership/ALL_BUSINESSES.
112 SQL cases меняют account/scope непосредственно перед owning statement;
56 HTTP cases — после early entry. Positive VIEWER reads, forbidden manual
VIEWER, flags/PAGE_DIALOGS/CSRF и immutable owner attribution сохранены.
Прежние 8 изменённых fixtures пройдены целиком; дополнительно R224 SQL41 и
выбранные R231 SQL52/HTTP24 child/media cases. Остальные прежние R228–R231
матрицы целиком не повторялись; исторические totals не суммируются с этим run.
Baseline на неизменённом product:388 cases /225 assertion failures /
0 errors /163 controls. HTTP v1:85/42 failures; после исправления
только SQL positive controls весь SQL повторён v2:303/183.
HTTP/fixture sources и classes не менялись между этими baseline runs.
ALL_BUSINESSES корректно сохраняет видимость второго active empty business,
даже при архивировании первого; zero-ID manual ожидает AccessDeniedException.
Первый v1 (231 assertion failures/1 input error) и correction evidence сохранены.
После окончательной фиксации sources/class/case names новых тестов неизменны.

Maven wrapper/JDK17, проверенный R231 cache1381 Java/2251 classes; effective
ignored native supplier R231 Unicode ICU und pinned по SHA source/class.
Native PostgreSQL16.15/loopback54167/owned iguana_r231_unicode, fresh test schemas;
tracked supplier и migrations неизменны, Docker не запускался, owned PG остановлен.
Первый product v3 остановился на testCompile: adapter не распознал Mockito
.when(reads) receiver.8 fixtures восстановлены checked inverse, adapter v2
добавляет этот receiver; product/new tests не менялись. Полный v4:627 tests/
1 failure/0 errors, все388 новых GREEN; общий run RED. Прежний revocation test
ожидал ID-only чтение при неоднозначном логине. V3 fixture transform добавляет
явный deny assert, затем восстанавливает уникальность второго test account
перед прежними positive read/revocation asserts. Ни один прежний assert не удалён.
Все137 API/import/schema/fixture edits проверены replay/inverse. Затем весь
тот же selection повторён для финального GREEN, не собран из failed v4 run.
Новых migrations/UI/deps/feature flags нет. Scope18:4 main/8 old fixtures/
3 new tests/task/S0/new append-only changelog. Replay/inverse/compile/SQL/HTTP/
scope/content/source/class/XML/log checks выполнены до отдельного root apply.

Source/test сессия: C:\Users\sushi\Git_H\tg_ref_b24_sup. Рабочий экземпляр
на другой машине/сессии C:\Users\SinicinVV\git_h\tg_ref_b24_sup; доступа к нему
не было. Последние документированные факты — runtimeR223: webR222, workerR198,
foundationOFF, readiness200UP, health503DOWN из-за прежнего inboundDLQ1.
Все runtime/rollback blocks сохранены; working DB/deploy/restart/flags/provider/
queues не затрагивались; V83 повторно не запускать. R224–R226 и R228–R232 ещё
требуют будущего panel-web image; R227 tests/docs отдельного rollout не требует.
Пользователь оставил push себе: прежнее дневное разрешение на agent push
заменено этой инструкцией; не возобновлять автоматический push без нового
указания владельца. Agent push в этом продолжении не выполнялся.
Commit разрешён до конца30 октября Europe/Moscow прежним явным ответом;
apply и commit разделены, факты Git — ignored r232-result/final JSON.

Далее: оставшиеся BUSINESS edit/feedback/realtime/admin account boundaries,
PENDING product policies task/project/system/global, AI privacy/context/
retention/delivery, fleet/flags/S6 acceptance и операционный DLQ gate.
Исторический S2B снятR190, общая задача остаётся 🟡; deployment не заявляется.

Source R231 (историческая source-сводка, не развёрнут):6 BUSINESS card/child/media entries принимают исходный
Authentication и private immutable ID/exact raw-name snapshot, без Long fallback.
Каждый owning SELECT проверяет current enabled/strict blockedFALSE/fold uniqueness
и прежний business scope. Controller сохраняет early entry и тот же actor после
hashed bytes; fallback/metadata/owner/limits/statuses/private headers прежние.
Catalog/list/summary/export/manual-create остаются следующим отдельным пакетом.
567 tests /10 suites GREEN одним selected run:412 новых (308 SQL/104 real-login
HTTP) и155 прежних cases. Включены R224 entry SQL41/HTTP23, old controller13,
download8/category21/read12/GENERAL read7 и30 selected GENERAL/BUSINESS media/
followup cases из6 methods. Frozen baseline на unchanged product:412 cases/
232 assertion failures/0 errors/180 controls; все positive controls GREEN.
Финальный baseline v3 повторён на UTF8/ICU und; дополнительно прежний search
control1 GREEN на неизменённом product. Source/class/cases новых412 те же, чтоv1.
Те же new source/class SHA и case names до/после. 6 прежних fixtures адаптируют
102 API-input/import edits с checked replay/inverse; result assertions прежние.
SQL:8 surfaces, membership/ALL sources, raw names/BIGINT>2^53/Long.MAX,
stale/unbound/current disabled/blocked/NULL/duplicate identity, scope revoke,
business archive;96 pre-statement mutations и4 legacy fallback mutations.
HTTP: real form login/R189/real controller+SQL,64 late changes,8 storage rechecks,
16 positives/flags/page right. Прежний R230 run780 и его334 новых остаются
историческими и не суммируются с этим run; новый R231 содержит412.
R228529/R229365 и R230334 целиком не rerun; их неизменённые sources/classes
проверены, GENERAL read7/download subset/media30 повторены явно выше.

Maven wrapper/JDK17, verified R230 cache1378 Java/2242 classes;2 main и6 old
fixtures recompiled, frozen3 test sources/classes прежние; не затронутые product/
test class hashes прежние, кроме явно pinned ignored native supplier variant.
Native PostgreSQL16.15/loopback54167/fresh schemas в отдельной scratch database
iguana_r231_unicode (UTF8/ICU und), ignored R231 supplier с SHA/version/exact
data-directory/locale/Unicode-search guards. Original R200 adapter source и
его C database сохранены; class substitution проверяется отдельно. Tracked supplier/83 migrations
не менялись; existing migrations только в test schemas. Docker не запускался,
owned PG остановлен, artifacts retained; JS не rerun. Scope14:2 main/6 existing
tests/3 new tests/task/S0/append-only changelog. UI/schema/deps/flags прежние.
Preview v1 method pattern пропустил card и fixture scanner — var mock receiver;
оба guards остановились до записи. V2 previews проверили6 methods/6 files;
исходные operators/JSON сохранены. Final v4 GREEN, exact scope/content/
source/class/XML/log/replay/inverse proof перед отдельным root apply.

Первый selected v2:567/1 failure/0 errors, все новые412 GREEN, общий run RED.
Единственный прежний textSearch test ожидал caseless Cyrillic Иван/иВан; native
C database lower оставлял1048, search0. На ICU und lower1080/search1; root search/
catalog implementation побайтно прежний. Создана отдельная owned scratch DB,
не менялись product SQL/collations/migrations/тестовые assertions. Все8 sandbox
product/fixture files восстановлены checked inverse, новый ignored adapter
compiled с pinned source/class; прежние v1/v2 XML/log/JSON сохранены. Baselinev3
на unchanged product дал те же232 RED и positive search1 GREEN, затем тот же
product/fixture transform повторён для финального v4. Это recovery test
environment, не фиксация GREEN по частям из failed run. Proof-emitter v1
остановился до записи из-за повторного substring guard; v2 исправлен и сохранён.

Source/test машина C:\Users\sushi\Git_H\tg_ref_b24_sup; рабочий экземпляр на другой
машине/сессии C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Последний документированный
runtimeR223: webR222/workerR198/foundationOFF/readiness200UP/health503DOWN из-за
прежнего inboundDLQ1. Полные R184/R198/R204/R207/R216/R223 runtime/rollback/V83
blocks сохранены. Working DB/deploy/restart/flags/provider/queue не затрагивались;
V83 не повторять. R224–R226 и R228–R231 требуют будущего panel-web image;
R227 tests/docs отдельного rollout не требует. Развёртывание R231 не заявляется.
Далее — BUSINESS catalog/list/summary/export/manual-create late actor binding,
оставшиеся edit/feedback/realtime/admin boundaries, PENDING product policies
task/project/system/global, AI privacy/context/retention/delivery, совместимый
fleet/flags/S6 acceptance и операционный DLQ gate. S2B снятR190. Общий статус — 🟡.
R230 опубликован 56daeb6cb1be2b07de6c17a90381edb6e78c1094; его прежние записи об истёкшем разрешении
описывают подготовку до ответа владельца. Владелец затем разрешил commit/push
до конца30 октября Europe/Moscow (2026-10-30T21:00:00Z); это разрешение действует
и для текущего продолжения. Source apply, отдельный Git publisher и рабочий
deployment разделены; фактические Git-флаги — r231-result/final.json.

Source R230 (не развёрнут):5 GENERAL read entries принимают исходный
Authentication; immutable ID/exact raw-name snapshot проходит через root/child/
document/questionnaire/legacy SQL, без public long-ID fallback. Controller
передаёт тот же actor, включая recheck после hashed bytes. READ/CURRENT/owner/
metadata/private headers/statuses/streaming semantics прежние; admin R229 не менялся.
780 tests /11 suites GREEN одним selected run:334 новых (272 SQL/62 real-login
HTTP) и446 прежних cases, включая30 selected GENERAL/BUSINESS media/followup
cases из6 methods. Три новых sources и compiled classes frozen до product edit;
case names и SHA проверены. Unchanged-product baseline:334/123 assertion failures/
0 errors/211 controls (SQL v1 272/104, HTTP v2 62/19). Первый run v1 334/127/0
сохранён:4 positive HTTP assertions неверно требовали отсутствие UTF-8 charset
у octet-stream. До product edit исправлена только совместимость MIME, сохранены
byte/status assertions; HTTP62 повторены. Это исправление нового fixture, не
изменение product headers. Девять прежних fixtures адаптируют73 API-input edit;
result assertions сохранены с checked replay/inverse. Прежние R228529/R229365
здесь не rerun: их sources/classes и work/write/SSE/admin policy прежние.
Старые suite totals не суммируются с нынешним selected run.

Maven wrapper/JDK17, verified R229 cache1375 Java/2234 classes; changed sources
перекомпилированы, не затронутые class hashes прежние. Native PostgreSQL16.15,
loopback54167/fresh dedicated schemas, ignored R200 supplier с SHA/version/data
directory guards; tracked supplier и83 migration sources прежние. Existing
migrations выполнены только в test schemas. Docker не запускался; owned server
остановлен, evidence retained. JS не rerun. Scope17:2 main,9 existing tests,
3 new tests,task/S0/append-only changelog. UI/schema/dependencies/flags прежние.
Sandbox_GREEN и exact content/scope/source/class/XML/log proof до root apply.
Source operator v1 остановлен до записи из-за substring guard; v2 исправил
уникальный full-line anchor. Selected v3/v4 остановились на javac до tests:
пропущены verify(reads) и local late receiver старого API. Диагноз inline-call
в v3 metadata был неточен; v4 evidence уточняет local late. Все9 fixtures
восстановлены checked inverse перед каждым scanner fix; исходные операторы,
compile logs/JSON сохранены. Scanner v3/final selected v5 GREEN.

Source/test машина C:\Users\sushi\Git_H\tg_ref_b24_sup; рабочий экземпляр на другой
машине/сессии C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Последний документированный
runtimeR223: webR222/workerR198/foundationOFF/readiness200UP/health503DOWN из-за
прежнего inboundDLQ1. Полные R184/R198/R204/R207/R216/R223 runtime/rollback/V83
blocks сохранены. Working DB/deploy/restart/flags/provider/queue не затрагивались;
V83 не повторять. R224–R226 и R228–R230 требуют будущего panel-web image;
R227 tests/docs отдельного rollout не требует. Развёртывание R230 не заявляется.
Далее — оставшиеся child/media/edit/feedback/realtime и admin boundaries,
PENDING product policies task/project/system/global, AI privacy/context/retention/
delivery, совместимый fleet/flags/S6 acceptance и операционный DLQ gate.
СнятыйR190 S2B не возвращён. Общий статус — 🟡.
Разрешение commit/push действовало до29 октября21:00 UTC (конец суток Moscow);
на30 октября не продлено. Этот пакет подготовлен без staging/commit/push;
source apply и рабочий deployment — отдельные факты. Git-факты — r230-result.json.

Source R229 (не развёрнут):5 GENERAL grant/list entries принимают исходный
Authentication, без actorless public fallback. Owning SQL проверяет current
ID/raw name/status/ambiguity и effective ROLE_ADMIN+PAGE_SETTINGS: direct,
assigned-role, mixed. Новый PanelRolePermissionSnapshot использует существующие
UserRepositoryUserDetailsService page parsers; SQL сверяет role ID/raw values/
current assignment и блокирует account/authority/role rows до конца statement.
Snapshot read не является разрешением; изменения после capture закрыты SQL proof.
GENERAL READ не требуется админу и не назначается ему автоматически. Target READ/
OPERATE eligibility, revoke cascade/idempotency/URL/payload/flags/CSRF прежние;
late deny403. 1108 tests /14 suites GREEN:365 frozen new cases и743
existing (30 selected media/followup included), errors/failures/skips0.
Expanded unchanged baseline365/200failures/0errors; frozen source/class/cases same.
30 backend Lock-wait races и15 role snapshot→SQL mutations проверены. Scope20:
3 main/11 old+3 new tests/task/S0/changelog. Старые assertions не ослаблены;
fixture admin отдельный, recipient roles не меняются. R228529 не rerun,
их source/class/CURRENT/READ/OPERATE прежние. V2 interrupted after direct-only
role coverage gap; partial XML/log retained,13 sandbox files restored before
180 extra role cases/v3 RED; final v4 GREEN. Verified1371Java/2226class cache,
changed sources recompiled/nativePG16.15/test schemas/ignored supplier guards;
Docker не запускался, owned server остановлен. Schema83/UI/deps/flags unchanged.
Source/test машина C:\Users\sushi\Git_H\tg_ref_b24_sup; рабочий экземпляр на другой
машине/сессии C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Последний документированный
runtimeR223: webR222/workerR198/foundationOFF/readiness200UP/health503DOWN из-за
прежнего inboundDLQ1. Полные R184/R198/R204/R207/R216/R223 runtime/rollback/V83
blocks сохранены. Working DB/deploy/restart/flags/provider/queue не затрагивались;
V83 не повторять. R224–R226 и R228–R229 требуют будущего panel-web image;
R227 tests/docs отдельного rollout не требует. Развёртывание R229 не заявляется.
Остаток: child/media/policy/AI/fleet/flags/S6/DLQ; Task🟡/S2B снятR190.

Source R228 (не развёрнут): GENERAL work/write/SSE принимает исходный
Authentication и immutable numeric ID/exact raw-name snapshot. 9 services/29 actor
entries,3 controllers; String fallback отсутствует. Owning SQL связывает account
по ID+raw name и сохраняет CURRENT/READ/OPERATE/fold-uniqueness policy. Durable
dispatch использует сохранённую пару; SSE delivery/heartbeat удерживают actor key,
не следуют за повторно использованным именем. Redis/public payload, URL, schema,
UI/flags/admin policy прежние. Boundary tests мутируют fixture перед SQL, не
доказывают внешний revoke после lock. 1287 tests /13 suites GREEN,
529 frozen new cases; unchanged product RED 232 failures/0 errors,
controls297. 9 old fixture files адаптированы к Authentication без
ослабления result assertions; GENERAL real-login/HTTP/SQL/realtime/Redis повторены.
Setup v1 529 identity-insert errors, v2 smoke19 JDBC-array snapshot failures и
v4 full32 NOT-NULL setup errors сохранены; fixture исправлен до product edits,
nullable compatibility probe снимает/восстанавливает constraint только test users.
v3 smoke32 GREEN/v5 full RED0 errors.
Verified R227 compile cache1368Java/2221classes, changed sources recompiled;
nativePG16.15/test schemas/ignored supplier guards, Docker не запускался, owned
server остановлен. Scope27; migration83/UI/deps/flags unchanged.
Source/test машина C:\Users\sushi\Git_H\tg_ref_b24_sup; рабочий экземпляр на другой
машине/сессии C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Последний документированный
runtimeR223: webR222/workerR198/foundationOFF/readiness200UP/health503DOWN из-за
прежнего inboundDLQ1. Полные R184/R198/R204/R207/R216/R223 runtime/rollback/V83
blocks сохранены. Working DB/deploy/restart/flags/provider/queue не затрагивались;
V83 не повторять. R224–R226 и R228 требуют будущего panel-web image; R227 tests/docs
отдельного rollout не требует. Никакого подтверждения развёртывания R228 здесь нет.
Остаток: admin grant atomicity, child/media/policy/AI/fleet/flags/S6/DLQ gate;
S2B снятR190/Task🟡. Отдельный Git publisher до конца29 октября после GREEN.

Source R227 (tests/docs): восстановлены четыре GENERAL suites из долга R226.
Work HTTP actor fixture использует bound ID/exact raw-name principal и реальные
DB authorities для R189; filters не mock/skip, прежние HTTP assertions сохранены.
Шесть новых cases: unbound, page revoke, disabled/blocked/rename/delete.
Три upgrades V69/V70/V71→V83 сравнивают все прежние values/history/grants,
exact old+6 column set и все новые defaults/null provenance; без скрытия drift.
Fresh baseline 343/89 failures/0 errors; после fixes 586 tests /11 suites
GREEN, failures/errors/skips0: 343+6 и237 unchanged entry/read/media cases.
148 identity cases R226 повторены; main/unmodified test class hashes прежние.
R226779/14 остаётся historical non-GREEN, полного779 rerun не заявлено.
Full compile731/637,1368 Java/83 migrations;4 tests recompiled. V1 setup176 errors
(Maven перезаписал native helper/Testcontainers без Docker) сохранены, provider
mtime/class восстановлены; v2 baseline/v3 GREEN nativePG16.15/fresh schemas,
ignored supplier SHA/version/exact-directory guards. Docker/containers не
запускались, owned server остановлен. Product/schema/UI/flags прежние, JS не rerun.
Source/test C:\Users\sushi\Git_H\tg_ref_b24_sup; рабочая машина/сессия другая:
C:\Users\SinicinVV\git_h\tg_ref_b24_sup. RuntimeR223/webR222/workerR198/
foundationOFF/readiness200UP/health503DLQ1, rollback/V83 blocks сохранены.
R224–R226 требуют нового web image; R227 tests/docs не требует rollout.
Working DB/restart/migrate/flags/provider/queue не затрагивались. Далее GENERAL
work/write/SSE ID-bound package, post-entry/child/media/policy/AI/fleet/S6;
Task🟡/S2B снятR190. Отдельный Git publisher до конца29 октября после GREEN.

Source R226 (не развёрнут): GENERAL read/access/page entry получает исходную
Authentication вместо повторного String lookup. GeneralTicketAccessService
проверяет bound PanelUserPrincipal/positive ID/exact raw name/principal status;
один SQL повторяет собственные ID/name/enabled, прежний COALESCE(blocked,FALSE)
и SQL-fold uniqueness. Без String overload/trim/Java fold/name fallback; login
не менялся. Все12 входов (5 REST reads/queue page/5 admin APIs/admin page)
проверяют bound current account. У accounts/READ grant/revoke/admin page guard
добавлен; PAGE_DIALOGS/ROLE_ADMIN+PAGE_SETTINGS/flags/CSRF прежние. Entry guard
не требует GENERAL READ, admin без READ управляет чужими grants. GENERAL
READ/OPERATE SQL, nullable blocked policy и отсутствие data bypass прежние.
237 targeted tests/7 suites GREEN:148 frozen SQL34/real-login HTTP114 на baseline
60 assertion failures/errors0, те же3 sources/5 class hashes/cases;89 прежних
cases повторены. Три fixtures меняют6 inputs+2 imports, inverse/assertions exact.
1368 Java/83 SQL/1847 inputs проверены;4 generated CSS EOL-only. Расширенный
прогон779/14 не GREEN:89 failures в4 старых наборах. Все89 повторены на исходном
baseline (135 cases):86 unbound session fixture/R189 login redirect и3 старых
SELECT * migration snapshots против добавленных колонок. Их assertions/файлы
не менялись; это отдельный тестовый долг, не скрытый GREEN общего набора.
Гарантия — account SQL на entry, не response/transaction lock или атомарная
проверка полномочий при поздней записи grant. GENERAL work/write/SSE String
callers, post-entry/child/media/provenance/policy/AI/fleet/flags/S6 остаются.
Scope13/source-only: runtimeR223/webR222/workerR198/foundationOFF/readinessUP,
health503DLQ1, без rebuild/restart/migrate/backfill/flags/queue/provider.
Rollback сохранён, задача🟡; подробные проверки и остаток — task01-278 R226.

Source R225 (исторический пакет до R226, не развёрнут): AttachmentService требует LegacyAttachmentReadAccess
для direct/path/key download families и всех4 legacy HTTP routes, после прежних
page/metadata/owner guards, до local/S3 open. Только bound Authentication с
positive ID/exact raw name/status; один SQL повторяет current own account/status/
SQL-fold uniqueness, non-GENERAL/unique raw root и общий current business READ.
SQL fold происходит от того же ID/raw-name row, без Java fold/name fallback.
Active membership/ALL_BUSINESSES поддерживают все4 READ роли; глобальный admin
не заменяет grant. UNRESOLVED только strictOFF/current account, GENERAL всегда
закрыт, foundationOFF не bypass. Отказ/DB error — пустой404 без binary calls.
Старого конструктора без guard нет; uploads/deletes/descriptors/knowledge/avatar
не менялись. 546 selected tests/16 suites GREEN, frozen252(SQL180/HTTP72) на
baseline155 failures/errors0, те же3 source/8 class hashes/cases. Прежние294 cases
повторены;13 fixtures лишь добавляют19 dependency args, inverse/assertions прежние.
1365 Java/83 SQL/1844 inputs проверены,4 CSS EOL-only; schema/flags/UI/JS прежние.
Это current READ перед open, не транзакционный metadata/READ lock, не полная
child business/generation attribution и не прекращение уже начатых streams
при revoke. GENERAL String lookup/child/media/policies/AI/fleet/flags/S6 открыты.
Runtime остаётся R223/webR222/workerR198/foundationOFF; не rebuild/deploy/migrate/
backfill/queue/provider, rollback сохранён. Полные runtime gates ниже, Task🟡.

Текущий runtime R223 — 29 октября,11:05+03, Windows/Docker SinicinVV:
только panel-web обновлён до clean R222 aa69238ca88000e99b7503d4132485627713397f,
image sha256:0558a3411df9aad28c98e7679d479feeb17f056535cd8c3d5c01f513aa60b8b9,
web1 healthy/restart0. WorkerR198 и остальные17 containers прежние; ENV/mounts,
Compose/env/shared inputs сохранены (exact mutable backup heartbeat отдельно),
HostConfig отличается только доказанной Binds permutation. JAR/classes proof,
1139 selected tests/11 suites GREEN,25 live read/access/readiness checks прошли.
Readiness200UP/logs0WARN0ERROR; общий health503DOWN остаётся из-за прежнего DLQ1.
Flyway/settings/accounts/grants fingerprints прежние,83 migrations/V83 уже была;
миграции/backfill/flags/queue/provider не затрагивались, foundationOFF.
Rollback iguana-panel:rollback-r223-web-20261029-b94104870164 сохранён, реальный
откат не запускался. Полный checkpoint/evidence — task01-278 R223. Не закрытие S6.

Source-уточнение R224 (пока не в image): canonical BUSINESS entry
BusinessScopedTicketReadService.requireAuthenticatedUserId принимает Authentication.
Требуются bound PanelUserPrincipal, positive ID/exact raw name/valid status;
один SQL проверяет собственные ID/name/enabled/not blocked и прежнюю SQL-fold
uniqueness. Нет name relookup/trim/Java fold/unbound fallback. Все12 canonical
read callers и manual creation controller передают исходную Authentication.
Page/flags/READ/OPERATE/owner/media proofs и downstream numeric-ID API прежние.
99 tests/6 suites GREEN: frozen64(SQL41/real-loginHTTP23) на baseline25 failures,
errors0; те же source/3 class hashes/cases, прежние35 cases и result assertions.
4 fixtures адаптируют inputs/status schema, inverse baseline exact;1360 Java/
83 migration inputs совпали,4 CSS EOL-only. Schema/UI/JS/grants/principal прежние.
Гарантия — только entry account SQL, не post-entry/response/transaction lock или
атомарная авторизация всех child reads/manual writes. Legacy AttachmentService
downloads всё ещё требуют отдельного business READ до local/S3 open; GENERAL
String lookup, child/media/feedback/realtime/policy/AI/fleet/flags/S6 остаются.
Source apply отдельно от rollout R223; R224 не deploy, commit/push не выполнялись.

Source-уточнение R222 (BUSINESS projection readers, до rollout R223):
AssignmentRead/CurrentAssignmentRead/ParticipantRead/WorkRead требуют собственные
ID/exact raw name/enabled/not blocked/lower(reader.username)=n.user_identity
в authorized_owner. Четыре SQL guards охватывают 5 API: assignment history,
current assignment, roster, participant history, work history. Java/PG Unicode
fold mismatch закрывает чтение вместо заимствования READ у другого ID.
READ/ALL_BUSINESSES (включая VIEWER), full owner/public uniqueness, MANUAL/SLA
provenance, current diagnostics, roster bound/eligibility, cursors и 503 прежние;
statement-time, без response-wide lock. Login normalization/API/UI/schema прежние.
347 tests /10 suites GREEN одним selected run, failures/errors/skips0:
150 frozen SQL (baseline15 failures/0 errors, controls135), те же source/3 class
hashes/case names; прежние SQL29/38/35/32, MVC14/9/14/14, real-login identity12.
20 SQL-boundary status/grant cases, BIGINT/private snapshots/read-only denial;
старые tests/fixtures/assertions прежние. 1357 baseline Java inputs проверены,
новый test/4 main compiled; native PG16.15/fresh schemas/ignored supplier,
owned connection/server закрыты. Docker/Testcontainers не запускались, 83 migration
sources прежние/SQL migrations только tests; JS не rerun.
Source/test машина sushi; рабочий экземпляр на другой машине/сессии
C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Последний runtime R216/webR215/workerR198/
foundationOFF/readinessUP/health503DLQ1; полные rollback/V83 blocks сохранены.
R217–R222 требуют нового web image; новых migrations нет, V83 не повторять.
Рабочие runtime/flags/queue/provider здесь не затрагивались. Четыре readers из
плана R221 закрыты; сквозные child/media/edit/feedback/GENERAL/notification/
realtime, policy/AI/fleet/flags/S6 gates открыты, Task🟡/S2B снятR190.
Commit/push разрешены до конца29 октября, отдельный publisher после GREEN.

Source-уточнение R221 (BUSINESS workbench Unicode barrier, не развёрнуто):
Assignment/Participant/Work используют общий ACTOR_ACCOUNT: own enabled/not
blocked/lower(actor.username)=n.user_identity рядом с ID/exact raw-name JOIN.
Он применяется к readers/authenticated writers и прямо в финальном work UPDATE;
все 3 main скомпилированы вместе, оба import callers получили новый ACTOR.
12 actor API/16 веток, READ/OPERATE/ALL, owner tuple/collision, target eligibility,
revision/advisory/row/grant locks, atomic audit/outbox/legacy boundaries прежние.
774 tests /6 suites GREEN: 275 frozen SQL (baseline44 failures/errors0, controls231),
те же source/3 class hashes/case names; actor36/66/97, Reader75, HTTP225 повторены.
Сводно: v2 полный774/0 failures/1 SSL-connect fixture timeout; 708 cases5 suites
GREEN v2 + отдельно Participant66 GREEN v3. XML/log v2 и source/classes/provider
сохранены, retry без изменений code/tests; полного clean rerun774 не заявлено.
SQL-boundary status/rollback проверены; writer callbacks внутри writer transaction,
без post-lock/global revocation обещания. Старые tests/fixtures/assertions прежние.
1356 cached Java inputs проверены, новый test и 3 main compiled, SHA/fresh reports.
Native PG16.15/fresh schemas/ignored supplier, own connection/server закрыты;
Docker/Testcontainers не запускались, 83 migrations прежние/Flyway только tests.
UI/JS/schema/dependencies/flags прежние, JS не rerun. Source/test машина sushi,
рабочий экземпляр на другой машине/сессии C:\Users\SinicinVV\git_h\tg_ref_b24_sup.
Последний runtime R216/webR215/workerR198/foundationOFF, readinessUP/health503DLQ1;
full rollback/V83 blocks сохранены. R217–R221 требуют нового web image; новых
migrations нет, V83 не повторять. Runtime/flags/queue здесь не менялись.
Далее четыре отдельных projection READ services; остальные child/GENERAL/policy/
AI/fleet/flags/S6 gates открыты, Task🟡/S2B снятR190. Отдельный publisher использует
разрешение commit/push до конца29 октября после GREEN; runtime не включён.

Source-уточнение R220 (workbench/persistence Unicode barrier, не развёрнуто):
В 9 actor API двух сервисов READ_ACTOR требует ID/exact raw name/own enabled/
not blocked/lower(actor.username)=n.user_identity. Обе CAS-ветки также используют
этот JOIN. Java/PG fold mismatch отказывает вместо заимствования READ/status у
соседа. READ/OPERATE/ADMIN CONFIGURE, owner tuple/collision, CAS/audit/evidence
и trusted worker record SQL прежние; statement-time, без response-wide lock.
327 tests /4 suites GREEN: 187 frozen SQL (baseline49/187 failures/errors0,
138 controls), те же source/3 class hashes/case names; Workbench47/Persistence58/
real-login DialogHTTP35. Старые tests/assertions/fixtures не менялись.
1355 cached Java inputs проверены; новый test и 2 main скомпилированы, SHA/fresh
reports проверены. Native PG16.15/fresh test schemas/ignored R200 supplier;
own connection/server закрыты. Docker/Testcontainers не запускались, 83 migrations
прежние, Flyway только tests; UI/JS/dependencies/flags прежние, JS не rerun.
Source/test машина sushi; рабочая машина/сессия другая:
C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Последний документированный runtime R216:
webR215/workerR198/foundationOFF; readinessUP, health503/inboundDLQ=1 остаётся gate.
R217–R220 не в image, нужен новый web image; migrations/rollout/working flags нет.
Full rollback/V83 blocks сохранены. Далее assignment/participant/work bound-ID/
name-based READ audit; остальные child/GENERAL/policy/AI/fleet/flags/S6 gates
открыты. Task🟡, historical S2B снятR190. Commit/push разрешены до конца29 октября,
отдельный publisher после GREEN; runtime этим разрешением не выполняется.

Source-уточнение R219 (retrieval/handoff readers, не развёрнуто):
Source.retrieve/isCurrentCandidate и handoff.candidates принимают bound Auth;
SQL требует ID/exact raw name/own status/совпадение Java-PG fold, с прежними READ,
unique account/business/owner tuple/collision и source approval/payload/origin.
Wrapper/controller сохраняют Authentication, String-only identity overloads закрыты.
Все11 actor API Source теперь bound; latest handoff event/privacy/limits прежние.
Trusted record и legacy retrieval SQL не менялись; autoReplyAllowed=false.
Statement-time, без response-wide отзыва уже разрешённых control/messages.
370/8 selected suites GREEN:155 new frozen cases RED61/155/errors0→те же sources/
case names GREEN; прежние Source82/Workbench47/Principal19/Dialog35/Fold27/Legacy5.
55 old identity arguments/3 fixtures, assertions и чужие candidate API сохранены;
receiver-specific replay/inverse проверены. Compile-v2/v3 и prep NOTICE EOL false
RED сохранены; только adapter repair, frozen assertions/product при нём не менялись.
1353 Java inputs/cache1340/compile13+new2; main/test compile, SHA/fresh reports.
Native PG16.15/fresh schemas/ignored R200 supplier, server/own connection закрыты;
Docker/Testcontainers не запускались,83 migrations прежние, Flyway только tests.
UI/SCSS/JS/policy/flags/dependencies прежние, JS не rerun. Source/test машина sushi;
рабочая машина/сессия другая: C:\Users\SinicinVV\git_h\tg_ref_b24_sup.
Runtime R216/webR215/workerR198/foundationOFF и full rollback/V83 blocks сохранены;
health503/inboundDLQ=1 остаётся отдельным gate, readinessUP; очередь не трогали.
R217–R219 не в image, нужен новый web image; rollout/working migrations/flags нет.
Далее соседние bound-ID/fold paths и child/GENERAL/policies/privacy/delivery/fleet/
flags/S6; Task🟡, S2B снятR190. Commit/push разрешены29 октября до конца суток;
отдельный publisher после GREEN, без rollout/working migrations/flags.

Source-уточнение R218 (library read/configure и общий actor guard, не развёрнуто):
Пять source APIs availableBusinesses/canConfigure/catalog/find/history получают
Authentication с positive session ID/exact raw name; String-only overloads удалены,
оба контроллера передают Authentication. SQL сохраняет READ vs ADMIN CONFIGURE,
unique active account/business, MEMORY origin/collision и pre-limit scope filtering.
Общий BOUND_ACTOR для5 readers и4 writers требует own ID/raw username/enabled/
not blocked и lower(actor.username)=n.user_identity. При Java/PG fold mismatch
нет подмены соседом, способ нормализации оставлен прежним с fail-closed отказом.
U+0130/i+U+0307 gap подтверждён PostgreSQL16: intermediate783/12 GREEN, затем
supplement27/22 assertion failures/0errors. Первый frozen RED188/87/0 на R217.
Всего215 новых cases (163SQL+25 real-login HTTP+27 supplement), прежние595 cases.
Финальный полный повтор810/13 GREEN, failures/errors/skips0. Те же3 frozen source
files/8 class hashes/case names, freshness13 XML проверена до main apply.
61 fixture identity argument/4 never matchers/1 exact session spy, inverse/assertions
proof. Maven/JDK17/PostgreSQL16 Testcontainers/fresh schemas,1353 Java/83 migrations/
1832 inputs проверены,4 generated CSS EOL-only. JS/UI/schema/policy не менялись.
Statement-time, не response-wide lock: ранее разрешённый body не отзывается при
позднем history denial. CAS/audit/прежняя read failure semantics сохранены.
Baseline18a00b3dc+known R21712 paths; staging/commit/push/runtime mutations нет.
RuntimeR216/webR215/workerR198/foundationOFF, R217/R218 не в image. Retrieval/
handoff и прочие child/GENERAL/policy/privacy/delivery/fleet/S6 gates открыты,
Task🟡, S2B снятR190. Полный текущий source/runtime checkpoint в01-278.

Source-уточнение R217 (revise identity, ещё не развёрнуто):
ScopedAiSourceService.revise/reviseExpected принимают bound Authentication вместо
String identity; UPDATE требует session ID/exact raw name, current unique active
account, business READ/ADMIN CONFIGURE и прежний MEMORY origin/collision proof.
Source/revise-event сохраняют ID/raw name; controller передаёт Authentication.
CAS/review/status/content/opaque key/audit rollback сохранены, late HTTP409/no-store.
Общий create/revise principal guard, без String-only review overloads; statement-time,
не global post-snapshot revocation. UI/schema/policy/retrieval/handoff не менялись.
226 frozen cases (200SQL+26 real-login/CSRF): baseline RED78/226/errors0, те же
source bytes/3 class hashes/case names GREEN. Всего570/9 selected tests GREEN,
включая344/7 прежних. 34 fixture arguments/4 files — только initial-login helper,
inverse baseline/assertions proof. Maven/JDK17, PostgreSQL16 Testcontainers/fresh
schemas,1350 Java/83 migrations inputs проверены.1829 inputs:4 generated CSS
отличаются только EOL после Sass, другие raw hashes совпали. JS не rerun.
Compile import defect исправлен до freeze-v2; PS verifier metadata/array defects
исправлены вv3 без изменения Java/tests, evidence сохранён. Same patch/replay/
path/hash guards, без staging/commit/push. Runtime ниже R216 остаётся webR215,
workerR198/foundationOFF; R217 не в image, нужен отдельный web build. Миграции/
backfill/flags/working data не менялись. Read/configure/retrieval/handoff identity,
child/GENERAL/policies/privacy/delivery/fleet/S6 gates открыты, Task🟡/S2B снятR190.

Текущий runtime checkpoint R216 (2026-10-28, 23:04 +03): на рабочей машине обновлён
только panel-web до clean R215 `18a00b3dc38ba7b2624cca40deda9be941a28c77`.
Image b94104870164/container3449295889d1, одна реплика healthy/restart=0;
JAR SHA059ec09a3ba0d831 совпал package/image/live, 1414 classes — tested bytecode.
Compose/env и число реплик сохранены, build/dry-run/up только web с --no-deps.
Rollback tag rollback-r216-web-20261028-0e8a5171215f сохраняет R206; старые R184/
R198/R204/R207 rollback/backup сохранены. Остальные17 containers полностью
неизменны: workerR198/bot-runner прежний, Docker/WSL не перезапускались.
Web ENV/mounts совпали, HostConfig отличается только порядком4 Binds. Shared inputs
не менялись (кроме явно исключённого backup heartbeat); Flyway83/settings/users/
grants/roles fingerprints совпали. Web Flyway skip, 0 WARN/0 ERROR; migration/
backfill/flags/domain writes не выполнялись. Все83 migration sources прежние.
Свежий локальный повтор 344/7 existing tests GREEN, PostgreSQL16 Testcontainers,
схемы изолированы от стенда. 25 live access/readiness checks GREEN, реальный login/
logout и ROLE_ADMIN+PAGE_CHANNELS/PAGE_SETTINGS; foundation OFF сохранён.
Общий health503/DOWN **не принят**: iguanaProduction degraded из-за inboundDLQ=1.
Prometheus worker sample на19:55Z подтверждает этот backlog до web StartedAt19:59:41Z;
db/redis/rabbit connection/storage/readiness UP. DLQ не consume/ack/purge/requeue;
это отдельный operational gate. Полной runtime/provider приёмки не заявляется.
Полный checkpoint/evidence в01-278/.tmp/01-278-r216-rollout. R208–R215 развёрнуты
только в web; identity/child/GENERAL/policy/privacy/delivery/fleet/flags/S6 gates
открыты, Task🟡, historical S2B снят R190. R207 ниже — исторический checkpoint.

Предыдущий runtime checkpoint R207 (2026-10-28, 12:38 +03): на этой машине обновлён
только panel-web до clean R206 commit c2ee116f3147297a28b20165a8f1ac0a2a19aa86.
Одна реплика, healthy/restart=0, health/readiness 200/UP. Image 0e8a5171215f,
container d262f92f80d5, JAR SHA 9d7ce559a89ffb9f; полный checkpoint в 01-278.
Compose build → web-only up с --no-deps/--no-build/pull never. Старый R204 сохранён
tag rollback-r207-web-20261028-c1634138193d; откат не делали. У остальных 17
containers не изменились IDs/images/start/restarts/config: worker R198, bot-runner
прежний. Docker/WSL не перезапускались. Web ENV/mounts совпали; полный HostConfig
hash восстанавливается одной перестановкой четырёх Binds, других отличий нет.
Compose/env/settings/locations/org_structure/backup policy не менялись; mutable
backup daemon heartbeat явно исключён из утверждения равенства shared files.
Flyway83 history/users/grants/roles/settings fingerprints совпали, web Flyway
skip явный; migration/backfill/flags/domain writes не запускались.
Сборка из pinned clean worktree с heap384/helper896 MiB, без параллельных tests:
937/14 + 15 JS R206 повторно не запускались, source/reports проверены; 1333 Java
inputs функционально совпали (620 EOL-only различий), восемь main class SHA
точно совпали с tested. Image сохраняет R204 runtime/base/bot artifacts,
копируется только новый JAR, его SHA совпал package/image/live.
Финальные 26 live read checks GREEN, реальный login и logout; foundation на
стенде выключен, положительный live BUSINESS workflow не заявляется. V1 probe
ошибочно склеил URL и вызвал один ERROR; исправлен только скрипт, V2 повторён.
0 WARN/0 иных ERROR; этот один ERROR сохранён в evidence, не скрыт.
Старые R184/R198/R204 runtime/rollback/V83 backup сохранены. R205/R206 web rollout
завершён, но другие identity/child/GENERAL/policy/AI/fleet/flags/S6 gates остаются;
Task 🟡, historical S2B исключён R190. Этот checkpoint приоритетнее source-only
состояния среды R206 ниже.

Source-уточнение R215 (create identity, без rollout):
ScopedAiSourceService.createKnowledge/createMemory принимают bound Authentication;
SQL INSERT требует session ID/exact raw name, current unique active account,
active business/READ/ADMIN CONFIGURE, MEMORY origin tuple/collision denial.
Source/create-event сохраняют numeric ID/raw name. String create overloads удалены;
controller передаёт Authentication. DRAFT/revision1/content/key/idempotency/atomic
audit rollback сохранены. Statement-time, без post-snapshot revocation guarantees.
118 frozen new SQL/real-login cases: RED40/118, errors0; те же bytes GREEN.
Итог344/7 selected suites GREEN, включая5 прежних fixtures. Setup v1 и его
ошибка display_name сохранены; v2 чинит только test schema/lifecycle, assertions
не менялись. 1346 cache inputs проверены; main/test compile, native PG16.15/fresh
schemas/ignored R200 supplier. Test server остановлен; Docker/Testcontainers не
запускались,83 migrations неизменны, Flyway только test schemas, JS не rerun.
Source/test C:\Users\sushi\Git_H\tg_ref_b24_sup; рабочая машина/сессия другая:
C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Runtime R207/webR206/workerR198/foundation off,
полные rollback/V83 blocks сохранены; deploy/restart/working migrations/flags нет.
Нужен новый panel-web image, новых migrations нет. Source revise/read/configure/
retrieval/handoff identity и child/GENERAL/policy/privacy/delivery/fleet/flags/S6
gates открыты; Task🟡, historical S2B снят R190. UI/schema/policy неизменны.

Source-уточнение R214 (test-only source HTTP fixture, без rollout):
Source HTTP использует bound ID/exact raw name/DB page rights без request-time repair.
30 old case names сохранены; missing/disabled/blocked→302/login по R189, прежние
business403/late SQL409/CSRF/CAS/privacy/opaque-key/escaping/origin checks сохранены.
13 new cases:6 prepared account revoke/reuse,4 invalid principals,2 page revoke,
1 actual JDBC SESSION/CSRF create/review/reload/revoke/logout. 75/2 suites GREEN
(Source43+Session32); baseline30/30/0 после только datasource isolation, old auth/
test methods/assertions неизменны тогда. Product/main/UI/schema/policy неизменны.
Fixture имеет fresh schema/current-schema guard; прежние base mocks/properties
сохранены. Tracked helper/base неизменны; native PG16.15/ignored R200 supplier,
server остановлен. Docker/Testcontainers не запускались;83 migrations неизменны,
Flyway только test schemas. JS не rerun, старые R213 evidence не считаются новым run.
Source/test C:\Users\sushi\Git_H\tg_ref_b24_sup; рабочая машина/сессия другая:
C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Последний runtime R207/webR206/workerR198/
foundation off и полные rollback/V83 blocks сохранены; deploy/restart/working
migrations/flags не выполнялись. R214 не требует нового restart/build; прежние
product changes этим test-only шагом не развёрнуты. Source SQL identity и прочие
child/GENERAL/policy/privacy/delivery/fleet/flags/S6 gates открыты; Task🟡, S2B снят R190.

Source-уточнение R213 (внутренний non-CAS control, без rollout):
setControl получает bound Authentication; positive ID/raw name/status и SQL
actor.id/exact username защищают INSERT и conflict UPDATE. Audit columns сохраняют
ID/raw name; String-only overload удалён. READ/OPERATE/current account/uniqueness/
business/grants, полный owner tuple/SAME_EXCLUDED_OWNER/collision denial и reason
trim/null/cap500 сохранены; при SQL failure нет частичной записи. Primitive не
создаёт event. Production callers отсутствуют; controller использует прежний CAS.
Все остальные persistence методы/helpers, workbench/MEMORY/source/handoff, UI/schema
неизменны; statement-time, не post-snapshot global revocation. 230/5 selected SQL
suites +15 JS GREEN; frozen99 RED99/30/0, те же bytes GREEN. 22 old calls/4 fixtures
адаптированы, assertions прежние. Source proof subset6+1; HTTP не rerun, route/CAS
не менялись. Native PG16.15/fresh schemas/ignored R200 supplier, server остановлен.
Docker/Testcontainers не запускались;83 migrations неизменны, V62..64 только test
schemas. Source/test C:\Users\sushi\Git_H\tg_ref_b24_sup; рабочая машина/сессия другая:
C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Последний runtime R207/webR206/workerR198/
foundation off и полные rollback/V83 blocks сохранены; deploy/restart/working
migrations/flags не выполнялись. Нужен новый panel-web image, migrations нет.
Прямой source MEMORY primitive, source/handoff и остальные policy/privacy/delivery/
fleet/flags/S6 gates открыты; Task🟡, S2B снят R190.

Source-уточнение R212 (MEMORY writer, без rollout):
Workbench createMemory получает bound Authentication; positive ID/raw name/status
и SQL actor.id/exact username проверяются вместе с READ/CONFIGURE/current account/
owner/text evidence. Source и audit сохраняют raw имя, String-only overload удалён.
ADMIN/ALL_BUSINESSES, PAGE_DIALOGS+KB/CSRF/flags/reviewed/DRAFT, content validation,
idempotency и atomic source/audit/evidence rollback сохранены. Поздний отказ409/
no-store без частичной записи; statement-time, не global post-snapshot revocation.
Reads/helpers/persistence/source/handoff/UI/schema неизменны. 228/7 suites +15 JS
GREEN; frozen51 SQL+25 real-login HTTP RED76/20/0, те же bytes GREEN.
Setup v1:76 FK seed errors; v2 исправляет isolated seed TRUNCATE/ANALYZE;
в v3 убраны только собственные rollback-test CHECK между scenarios после control
failure в v2. Product constraints/triggers и SQL/HTTP assertions прежние; evidence
сохранены. 7 old fixture calls
адаптированы, assertions прежние. Native PG16.15/fresh schemas/ignored R200 supplier,
server остановлен. Docker/Testcontainers не запускались;83 migrations неизменны.
Source/test C:\Users\sushi\Git_H\tg_ref_b24_sup; рабочая машина/сессия другая:
C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Последний runtime R207/webR206/workerR198/
foundation off и полные rollback/V83 blocks сохранены; deploy/restart/working
migrations/flags не выполнялись. Нужен новый panel-web image, migrations нет.
Прямой source MEMORY primitive, non-CAS controls/source/handoff и остальные
policy/privacy/delivery/fleet/flags/S6 gates открыты; Task🟡, S2B снят R190.

Source-уточнение R211 (четыре persistence read API, без rollout):
findControl/canOperate/findState/findEvents получают bound Authentication, positive
ID/exact raw name/status; SQL actor связан с обоими полями. String read overloads
удалены; READ/OPERATE/current account/uniqueness/business/grants, full owner tuple,
control/state matching, collision denial и event order/cap сохранены. Authorized
absent-control defaults отличаются от denial Optional.empty. Поздний отказ одного
чтения не отзывает уже прочитанные поля; atomic whole-page revocation не заявляется.
Три writer bodies и workbench service неизменны; non-CAS/MEMORY/source/handoff
String boundaries открыты. 314/9 suites +15 JS GREEN; frozen56 SQL+31 real-login
HTTP RED87/30/0, те же bytes GREEN. V1 compile missing import, tests0; исправлен только
import; три SQL owner fixtures исправлены в v3 без изменения triggers. SQL RED v3
56/18/0 + HTTP v2 31/12/0, прежние evidence сохранены. 48 old calls адаптированы,
assertions прежние.
Native PG16.15/fresh schemas/ignored R200 supplier, JIT off only own process; server
остановлен. Docker/Testcontainers не запускались;83 migrations неизменны. Source/test
C:\Users\sushi\Git_H\tg_ref_b24_sup; рабочая машина/сессия другая:
C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Runtime R207/webR206/workerR198/foundation off,
runtime/rollback/V83 blocks сохранены. Remote/build/restart/deploy/working migrations/
flags не выполнялись. Нужен новый panel-web image, migrations нет. Task🟡, S2B снят
R190; остальные AI/GENERAL/policy/privacy/delivery/fleet/flags/S6 gates открыты.

Source-уточнение R210 (две AI read API, без rollout):
Workbench resolve/messages получают bound Authentication, positive ID/exact raw
username/status; SQL actor JOIN связан с обоими полями. String-only read overloads
удалены; current READ/ambiguity/account/business/grants, canonical locator/full
messages owner tuple, collision denial, text filters/preview/fingerprint/order/
limit50 и PAGE_DIALOGS/KB/flags/private404 сохранены. Statement-time, не atomic
whole-page revocation. Persistence reads/non-CAS/MEMORY/source/handoff остаются
отдельными String boundaries; MEMORY writer и persistence service не менялись.
249/7 suites +15 JS GREEN; frozen80 SQL+19 real-login HTTP RED99/32/0,
те же bytes GREEN. Четыре прежних fixture assertions сохранены; R20935/R20817/
R18932/workbench47/ScopedPrincipal19 включены. Native PG16.15, fresh test schemas,
ignored R200 supplier, JIT off only own process; server остановлен. Docker/
Testcontainers не запускались;83 migrations неизменны. Source/test здесь:
C:\Users\sushi\Git_H\tg_ref_b24_sup; рабочая машина/сессия другая:
C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Runtime R207/webR206/workerR198/foundation
off, checkpoints/rollback/V83 backups сохранены. Remote/build/restart/deploy/working
migrations/flags не выполнялись. Нужна новая panel-web сборка; migrations нет.
Task🟡, historical S2B снят R190; прочие identity/GENERAL/policy/fleet/S6 gates открыты.

Source-уточнение R209 (test-only, без нового rollout):
Долг прежней AI HTTP fixture закрыт:35 cases (22 прежних+13 новых),17 R208 HTTP,
32 R189 session — всего84/3 suites GREEN. Baseline22/22 failures/0 errors;
все прежние case names сохранены. Bound ID/exact raw account, DB page rights,
без repair helper; blocked case теперь требует302/login до controller по R189,
never(resolve)/zero writes. Прочие прежние privacy/404/CSRF/CAS assertions сохранены.
Проверены revoke/reuse/mismatch и настоящий JDBC SESSION/CSRF login-memory-control-
page-revoke-logout. Production/main/UI/schema/dependencies не менялись;83 migrations
неизменны. Native PG16.15, fresh schemas, ignored R200 supplier; Docker/Testcontainers
не запускались, test server остановлен. R208234/6+15JS — прежние evidence, не rerun.
Source/test здесь C:\Users\sushi\Git_H\tg_ref_b24_sup; рабочий экземпляр на другой
машине/сессии C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Runtime R207/webR206/workerR198,
foundation off и полные runtime/rollback/V83 blocks сохранены; deploy/restart/working
migrations/flags не выполнялись. Для R209 перезапуск не нужен; product R208 требует
отдельной panel-web сборки. Остальные AI identity/child/GENERAL/policy/fleet/S6
gates открыты, task🟡; historical S2B снят R190.

Source-уточнение R208 (source/test на другой машине, без нового rollout):
UI AI control setControlExpected получает Authentication с bound session ID/exact
raw username/status. INSERT и timestamp-CAS UPDATE связывают того же actor по
обоим полям, updated_by сохраняет raw username. Reused login/case change после
R189 refresh/owner lookup не меняют автора. READ/OPERATE/current account/uniqueness,
owner tuple, stale form refusal, atomic control/event rollback, PAGE_DIALOGS/CSRF/
flags сохранены. String-only overload этой UI API удалён; non-CAS setControl и
остальные AI String reads/writers остаются отдельными открытыми границами.
234 tests/6 suites + 15 BUSINESS JS GREEN; frozen61 SQL+17 real-login HTTP:
RED78/35/0, те же финальные bytes GREEN. Новая login/CSRF fixture исправлена и
повторно проверена на baseline; старые logs/reports сохранены. Отдельный прежний
AI HTTP probe1/1/0 на исходном HEAD выявил unbound user(String) fixture vs R189;
её assertion не менялся, весь старый HTTP suite не объявляется GREEN.
Native PostgreSQL16.15, JIT off только в own test process, fresh schemas, explicit
ignored R200 supplier; Testcontainers/Docker Desktop не запускались. Test server
остановлен; 83 migrations неизменны. Current working runtime по-прежнему R207,
webR206/workerR198, на C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Source/test здесь:
C:\Users\sushi\Git_H\tg_ref_b24_sup; remote/build/restart/deploy/working DB не
выполнялись. Нужна новая panel-web сборка, не migration. Runtime/rollback/V83
backups R184/R198/R204/R207 сохранены. Task🟡, historical S2B исключён R190.

Source-уточнение R206 (до rollout R207): все девять BUSINESS workbench reads

Assignment.find/candidates/recent, Participant.revision/members/candidates/recent,
Work.find/recent принимают тот же Authentication из controller. Требуются
authenticated non-anonymous bound principal, positive session ID, exact raw name
и principal status. Read JOIN связывает actor ID/raw name, прежний READ в том же
SQL проверяет current enabled/non-blocked account/ambiguity/active business/grants.
Нет String-only read APIs или rebind по reused name после refresh. VIEWER/OPERATE,
candidate rules, full owner/legacy collision refusal, caps/revisions/snapshots,
PAGE_DIALOGS/CSRF/flags сохранены. Тела writers не менялись; новых policy/grants/
schema/UI нет. Гарантия statement-time: позднее чтение при revoke возвращает
empty/null, уже законно прочитанные поля model не отзываются. Атомарность всей
страницы или revoke после начала SQL snapshot не заявляется.
Frozen 75 SQL + 56 real-login HTTP: baseline 72 failures/0 errors; те же bytes
GREEN. Final 937 tests/14 suites + 15 BUSINESS JS scripts GREEN. Четыре прежние
fixtures точно восстановлены inverse transforms; все 1333 Java inputs совпали
с native, 11 code/test paths совпали sandbox/replay/native/main. Полная compile
730 main/603 test. Testcontainers PG16.15, только тестовые схемы, 83 migrations
без изменений; helper удалён. Source-only на этой машине от f94389ea4 (R205),
parent aeff55ee6. Working web R204/image c1634138193d, worker R198; 18 containers
не перезапускались и сохранили ID/images/config, одна web-реплика, health/readiness
200/UP. Настройки не менялись; отдельно изменился hash штатного heartbeat-файла
backup-policy-runner.status. Build/deploy/working migration/backfill/flags/grants
не выполнялись. R205/R206 требуют отдельного web rollout. Все старые runtime/
rollback/V83 backup checkpoint сохранены. Другие read/AI consumers, child/media/
feedback/GENERAL, policy/AI/fleet и S6 остаются; эти девять readers закрыты.
Task 🟡, historical S2B исключён R190.

Историческое source-уточнение R205 (2026-10-28): три BUSINESS readers —
assignment history, participant roster/history — получают Authentication.
Требуются authenticated non-anonymous bound principal, positive session ID,
exact raw username/status. Один SQL связывает reader ID/name/enabled/non-blocked
с прежним BUSINESS READ/ambiguity/active business/full owner; reused name/case
change после refresh не подменяют читателя. String-only APIs удалены.
Full-owner audit, manual/SLA snapshots/tie refusal, member eligibility, roster
200/413, pagination/BIGINT/private headers, PAGE_DIALOGS/foundation сохранены.
Нет schema/grants/UI/writer/policy changes. Atomic in-flight revocation после
начала SQL snapshot не заявляется. Workbench find/recent/candidates и прочие
read/AI consumers остаются отдельными границами, не закрыты этим reader scope.
Frozen 93 SQL + 27 real-login HTTP cases: baseline 48 failures/0 errors;
те же bytes GREEN. Итог 509 tests/10 suites + 15 BUSINESS JS scripts GREEN.
Четыре прежние fixture sources восстановлены inverse transformations без
ослабления assertions/case names; 10 code/test SHA совпали в candidate/replay/
native/main. Штатный Testcontainers PG16.15, migration только test schemas.
Source-only на этой машине, baseline aeff55ee6 (R204), parent 53e858df0.
Рабочий web остаётся R204/image c1634138193d, worker R198. Все 18 Compose
containers неизменны за R205; Docker/build/deploy/working migration/flags/grants
не менялись. R205 rollout ещё нужен отдельно; R184/R198/R204 runtime evidence,
rollback/V83 backup и исторический memory recovery R204 сохранены. Task 🟡.

Подтверждённое source/runtime уточнение R204 (2026-10-28): BUSINESS work history reader
получает Authentication, bound positive session ID и exact raw username/status.
Один SQL проверяет текущий account + BUSINESS READ/ambiguity/active business.
Rename/delete + reused name и case change после session refresh не подменяют
читателя. String-only history API нет; owner tuple, immutable audit/categories,
pagination/BIGINT/private headers и 404/400/503 сохранены. Другие name-based
reads/find/recent/AI остаются отдельными границами; новых policy/grants/schema нет.
40 frozen новых cases: baseline 16 failures/0 errors; после патча GREEN.
Итог 601 tests/12 suites + 15 BUSINESS Node scripts GREEN; 349 завершённых cases
из прерванного run + 252 повторённых cases с неизменными native classes.
Шесть source/test files совпали в sandbox/replay/main; два main bytecode class
совпали с образом. Штатный Testcontainers PostgreSQL 16; migrations только test.

Рабочий экземпляр подтверждён на этой машине C:\Users\SinicinVV\git_h\tg_ref_b24_sup.
Panel-web обновлён до base 53e858df0 + локальный R204 (uncommitted=true), одна
реплика, healthy/readiness 200; live login/channel smoke 13 checks GREEN.
Image c1634138193d, JAR bfd68529a9d719ca; полный checkpoint/rollback hashes в 01-278.
Worker остаётся R198, bot/infrastructure images не обновлены. Однако за сессию
был Docker/WSL restart для восстановления после memory exhaustion; running
сервисы перезапускались, bot-runner один раз автоматически, затем healthy.
Web-only rollout после восстановления не менял остальные 17 containers.
83 migration sources, working Flyway/users/authorities/roles/settings/flags
не менялись. Final web ENV/mounts/HostConfig совпали с исходными; промежуточный
post-restart HostConfig hash отличался, не замаскирован как Binds-only.
Старые R184/R198 backup/rollback/evidence сохранены. Полная S6 приёмка не заявляется.

Историческое source-уточнение R203 (2026-10-28): manual BUSINESS close/reopen/categories
writer принимает Authentication с bound PanelUserPrincipal, positive session ID,
exact raw username/status. Locking root SELECT, conditional UPDATE и confirmation
после audit/outbox используют того же actor по ID/raw name. String-only API нет;
rename/delete/reused name после controller read/advisory wait и case change между
SQL boundaries не меняют автора. Отказ final UPDATE/confirmation откатывает root,
categories/counters/revision/audit/outbox. READ/OPERATE/ambiguity/status, owner
tuple/CAS/locks, category validation, reopen categories и PAGE_DIALOGS/CSRF/R189
сохранены. Assignment/participant writers R201/R202 не менялись; name-based reads/
AI и прочие consumers остаются следующими шагами. Atomic in-flight revocation
за пределами проверенных SQL snapshots не заявляется; новых policy/grants нет.
952 tests / 11 suites + 15 unchanged BUSINESS Node scripts GREEN.
Frozen 97 SQL + 12 HTTP новых cases: 52 failures / 57 controls / 0 errors;
с 213 прежними HTTP controls baseline 322/52/0. Final HTTP 225; три прежних fixture
sources/assertions восстановлены точно через inverse transforms. Native PostgreSQL
16.15 + explicit ignored R200 JDBC supplier, fresh schemas; Testcontainers/Docker
Desktop не запускались. Test server остановлен, 83 migration sources неизменны.
Source/test здесь; working project C:\Users\SinicinVV\git_h\tg_ref_b24_sup — другая
машина/сессия. Runtime остаётся R198; R199–R203 rollout не подтверждён. Нужна новая
сборка panel-web, новых migrations нет. Deploy/restart/working DB не выполнялись.
Полные R184/R198 runtime и исторический R200 Docker incident сохранены. Task 🟡,
historical S2B исключён R190.

Историческое source-уточнение R202 (2026-10-28): BUSINESS participant add/remove writer
принимает Authentication с bound PanelUserPrincipal; во всех writer actor joins
требуется positive session user ID + exact raw username. Нет String-only writer
или повторной привязки автора по lower(name) после controller read/advisory wait.
READ/OPERATE, uniqueness/status, full owner tuple, revision/CAS/locks, roster 200,
rollback/outbox сохранены; удаление неактивного/удалённого member остаётся доступным
по stored ID/snapshot. Assignment writer R201 не менялся; name-based participant/
assignment reads, work/AI и прочие consumers требуют следующих отдельных шагов.
Полная atomic revocation in-flight SQL snapshots не заявляется.
619 tests / 14 suites + 15 unchanged BUSINESS Node scripts GREEN.
66 SQL + 8 HTTP новых frozen cases: baseline 33 failures / 41 controls / 0 errors;
с 205 прежними HTTP controls baseline 279/33/0. Final HTTP 213, ScopedPrincipal 19;
3 старых fixture bodies/assertions восстановлены точно при inverse transform.
Owned native PostgreSQL 16.15 + explicit ignored R200 supplier, fresh schemas;
Testcontainers/Docker Desktop не запускались. Startup/status/PID/data/port/listen
проверены, test server остановлен после проверки. 83 migrations не менялись.
Source/test выполняется здесь, working project — на другой машине/сессии
C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Runtime checkpoint остаётся R198;
R199–R202 rollout не подтверждён. R202 требует новой сборки panel-web, не новой
migration; deploy/working DB/restart не выполнялись. R184/R198 runtime и R200
исторический Docker incident сохранены. Task 01-278 остаётся 🟡, S2B исключён R190.

Историческое source-уточнение R201 (2026-10-28): manual BUSINESS take/assign writer
получает Authentication, positive numeric PanelUserPrincipal ID и exact raw name.
Locking root SELECT и final conditional write связывают actor по обоим полям;
String-only change overload удалён. Rename/reuse после refresh/read/lock wait
не меняют автора; take/audit сохраняют того же bound actor. PAGE_DIALOGS/CSRF,
READ/OPERATE/ambiguity, tuple/revision/CAS/locks/rollback/outbox сохранены.
394 tests / 9 suites и 15 BUSINESS Node scripts GREEN. Frozen 40 новых cases:
23 RED failures + 17 новых controls, ошибки 0; те же bytes/block GREEN.
201 прежних HTTP и 19 ScopedPrincipal cases/assertions сохранены. Real SQL и
advisory-lock wait — owned native PostgreSQL 16.15, fresh schemas; явный ignored
R200 JDBC supplier adapter, без изменения tracked helper и без Docker.
Это не Testcontainers/working/fleet acceptance. Новых migrations нет, 83 SQL
sources неизменны. Readers/participant/work/AI String paths остаются отдельным
scope, полной атомарной revocation всех in-flight requests не заявлено.
Текущая source/test машина C:\Users\sushi\Git_H\tg_ref_b24_sup; рабочая
C:\Users\SinicinVV\git_h\tg_ref_b24_sup — другая машина/сессия.
Remote R198/V83 checkpoints, backup/rollback и R200 historical incident сохранены.
R201 требует новой сборки panel-web, не новой migration; здесь deploy не выполнялся.
S3–S6/policy/AI/bots/fleet открыты, historical S2B снят R190, статус 🟡.

Историческое source-уточнение R200 (2026-10-28): BusinessTicketAssignmentHttpPostgresTest
переведён на numeric-ID PanelUserPrincipal и DB authorities. Все прежние 190
cases/assertions сохранены, literal padded accounts не подменяются соседями.
Добавлены 11 account/page revoke/forged principal/reuse cases и настоящий
form-login/JDBC SESSION/CSRF/current/history путь. 201 HTTP cases GREEN;
всего 400 tests / 12 suites и 15 неизменённых BUSINESS Node scripts GREEN.
Baseline 190 cases воспроизвёл прежние 122 failures/1 error. v2 ошибка только
нового MockHttpSession fixture и v3 empty-cookie array исправлены cookie jar;
отдельный real-login v4 и общий final v5 GREEN.
Исполнение SQL — disposable native PostgreSQL 16.15 на loopback с временным
ignored JDBC supplier bytecode adapter; tracked Testcontainers helper не изменён.
Это не штатный Testcontainers runner или working/fleet acceptance. Production,
UI, 83 migrations, permissions и dependencies R200 не меняет. Runtime R198/V83
на другой машине, backup/rollback ниже сохранены; R199 source-only по checkpoint.
Локальный Docker был ошибочно запущен и автоматически поднял 14 старых контейнеров,
затем остановлен; их автоматические background writes не аудированы. Native
tests используют отдельную временную БД. Remote deploy/restart не выполнялись.
Текущая source/test машина C:\Users\sushi\Git_H\tg_ref_b24_sup, рабочая
C:\Users\SinicinVV\git_h\tg_ref_b24_sup — другая машина/сессия.
S3–S6/policy/AI/bots/fleet остаются открытыми, historical S2B снят R190, статус 🟡.

Историческое source-уточнение R199 (2026-10-27): текущий BUSINESS responsible доступен через
отдельный private READ endpoint и main card. Требуются bound reader ID/exact
username/status, PAGE_DIALOGS, foundation и текущий BUSINESS READ.
ASSIGNED требует live row + единственный raw root + одно MANUAL/SLA событие
ровно текущей responsibility_revision с full-owner tuple и exact snapshot;
history/raw row отдельно недостаточны. UNASSIGNED — только чистый revision=0,
остальное UNVERIFIED без raw disclosure. Manual ID frozen, SLA ID не выводится.
UI сохраняет string BIGINT, owner/revision checks, stale-response/XSS guards.
134 tests / 6 suites и 15 Node scripts GREEN. Старый широкий HTTP fixture
падает одинаково на R198 и R199: 122 failures/1 error из 190, требует отдельного
ID-bound session обновления; assertions/filter не ослаблены.
R199 source-only, новых migrations/flags/deploy нет. Работающие web/worker
остаются на R198; checkpoint/backup/rollback ниже не менялись. Статус — 🟡.
Актуальный runtime checkpoint R198 (2026-10-27, 22:53 +03): на машине
C:\Users\SinicinVV\git_h\tg_ref_b24_sup обновлены panel-web и ops-worker до
c3fa2777914095e91b783344072db01e10015682, по одной реплике, healthy/restarts=0.
Worker обновлён также для фонового ID-bound automation credential registry.
Одноразовый migrator применил V83 (82 → 83); штатный db-migrate не пересоздан.
Прежняя migration history, users/roles/grants/settings сохранились; остальные
16 Compose-контейнеров, ENV/mounts/HostConfig, flags/shared config не менялись.
666 tests / 15 suites GREEN включают реальные PostgreSQL/JPA/Flyway R197 и
window/Boolean R198 cases. Native Java inputs/deployed JAR сверены.
Health/readiness, real admin login, channels/settings/preferences, employee
options и automation history прошли HTTP-проверки; сессия закрыта.
Web logs без WARN/ERROR; worker без ERROR, но с предупреждениями внешнего
SSL-монитора, не блокирующими readiness. Мониторинг не отключался.
Оба rollback images, прежний R184 rollback и проверенный восстановлением pg_dump
сохранены. Детали — актуальный checkpoint в task-details/01-278.md и ignored
.tmp/01-278-r198-rollout-20261027/. Старые name-keyed настройки/ключи не
импортируются. Это не полная browser/provider/fleet/S6 приёмка. Статус — 🟡.

Историческое source-уточнение R198 (2026-10-27): общий legacy/global employee catalog
использует минимальную JDBC-проекцию вместо полного users SELECT; disabled,
blocked и case-fold ambiguous accounts не предлагаются. Window count включает
даже disabled/blocked соседей. Raw username остаётся точной строкой, label/order,
value/label response и прежние page gates сохранены. Основной PanelUser RowMapper
корректно читает JDBC Boolean и прежние numeric/string flags. Это не расширение
scoped candidates или assignment grants. Null status defaults совпадают с login.
38 suites / 974 tests GREEN; frozen 46 cases: baseline v2 8 failures / 38 controls /
0 errors, те же bytes GREEN. Новый PanelUserOptionsPostgresqlTest (8 cases)
только скомпилирован, реальная SQL/Boolean/window acceptance не выполнялась.
83 migrations неизменны, V83 здесь НЕ запускалась. Изолированные PostgreSQL
R195–R198 suites нужны до отдельно разрешённых migration/rollout.
Текущая source/test машина C:\Users\sushi\Git_H\tg_ref_b24_sup;
рабочий экземпляр/Docker C:\Users\SinicinVV\git_h\tg_ref_b24_sup на другой
машине/в другой сессии. R185–R198 не развёртывались отсюда; рабочая БД/данные,
grants/roles/config/providers и checkpoint/rollback R184 сохранены. S3–S6,
policy/AI/bots/fleet gates открыты; historical S2B исключён R190, статус 🟡.

Историческое уточнение R197 (2026-10-27): automation run history использует
Authentication и positive numeric actor_user_id. Этот ID сохраняется до первого
audit write/discovery; actor — только raw display snapshot. List/detail repository
queries ограничены automation key/owner ID (+ run ID для detail); child records
читаются после собственного parent. Нет actor.trim/IgnoreCase fallback. Top 20
упорядочены started_at DESC/id DESC. PAGE_DIALOGS/session/CSRF и UI contract сохранены.
Source-only V83 добавляет nullable positive BIGINT + composite index; legacy rows
остаются NULL и скрыты, без исторического backfill. V1–V82/старый index не менялись.
V83 здесь НЕ выполнялась; PostgreSQL/JPA acceptance и отдельная migration перед
согласованным rollout обязательны. Код без колонки не имеет schema/name fallback.
34 suites / 909 tests GREEN; frozen 43 cases: baseline v2 32 failures / 11 controls /
0 errors, те же bytes GREEN. Real security + stateful repository/JDBC и Spring Data
PartTree проверены; новый PostgreSQL/JPA/Flyway suite (8 cases) только скомпилирован.
Текущая машина C:\Users\sushi\Git_H\tg_ref_b24_sup — source/test.
Рабочий экземпляр/Docker C:\Users\SinicinVV\git_h\tg_ref_b24_sup — другая
машина/сессия. R185–R197 не развёртывались отсюда, рабочая БД/данные/schema,
roles/grants/flags/config/providers и checkpoint/rollback R184 не менялись.
Следующий runtime gate — isolated PostgreSQL suites R195–R197, затем отдельное
разрешение на migration/rollout. Остальные S3–S6/policy/AI/bots/fleet gates открыты;
historical S2B исключён R190, общий статус 🟡.

Историческое уточнение R196 (2026-10-27): личные automation credentials
Bitrix24/iiko используют bound Authentication и canonical numeric account ID в
employee_discount_automation_credentials.account.v2; нет trim/lower(name),
v1 fallback или импорта тестовых данных. Account guard/row locks и REQUIRES_NEW
охватывают read/merge/write; CAS/affected count защищают update, duplicate/corrupt
rows дают 409, SQL/transaction failures — 503. Все credential callers передают
Authentication; generic settings CRUD/list/model закрывает namespace даже admin.
Registry keys используют account ID + SHA-256 URL-key, не lossy name/profile slug.
31 suites / 862 tests GREEN; 56 frozen cases: baseline v6 53 failures / 3 controls /
0 errors, те же bytes GREEN. Stateful JDBC/transaction и loopback HTTP adapters
не доказывают PostgreSQL locking/rollback или live/fleet acceptance.
Текущая машина — C:\Users\sushi\Git_H\tg_ref_b24_sup, source/test checkout.
Стенд/Docker — на другой машине C:\Users\SinicinVV\git_h\tg_ref_b24_sup;
здесь daemon недоступен. R195 runtime probe — исторический факт другой сессии.
R185–R196 не развёртывались отсюда, рабочие данные/БД/роли/config/providers,
checkpoint/rollback R184 не менялись. Следующие конкретные проверки — отдельный
PostgreSQL для credentials и ID-bound automation history (ещё ActorIgnoreCase).
Остальные S3–S6/policy/AI/bots/fleet gates открыты; historical S2B не возвращается.
Статус — 🟡.

Историческое уточнение R195 (2026-10-27): personal UI/triage preferences
получают Authentication и хранятся по positive numeric ID в
ui_preferences.account.v2, без trim/lower(name) и legacy auto-import.
Account ID/exact raw username/status проверяются под row lock; транзакционные
read/merge/write сериализуют UI/triage saves, сохраняют частичный merge и UTC.
Duplicate/corrupt rows дают 409, ошибки SQL-чтения/записи — 503/rollback, не success.
Общий settings API/model исключает весь private namespace ui_preferences.*;
generic CRUD не обходит personal ownership даже для admin. Unbound optional
bootstrap/workflow даёт пустые defaults, не доступ к чужим настройкам.
Старые v1/shared-config записи не удалены; после будущего rollout defaults
без автоматического переноса соответствуют R190. Новых migrations нет.
30 suites / 704 tests GREEN; frozen 32 MVC cases: baseline v2 26 failures /
6 controls / 0 errors, те же bytes GREEN; 14 PostgreSQL cases включают реальные
concurrent saves и rollback. Workspace integration проверен одним методом.
Это не вся workspace identity, не новая global/business policy и не live
browser/fleet/JDBC session-store приёмка.

Сессия здесь на машине тестового стенда. Read-only probe: web 9fb4a33fd8a4,
R184 8310d1f7f0001314abdc60e52516e563168bfee1, healthy/restart 0.
R185–R195 не развёрнуты; данные/схема БД стенда, grants/flags/config/providers,
R184 checkpoint и rollback не менялись. Изолированные Maven/Testcontainers
не являются rollout; существующая Flyway chain запускалась только в тестовой БД.
Нет migrations/backfill/reset на стенде, staging/commit/push.
Следующий identity-кандидат — automation credentials и их rotation registry;
остаются scoped data/picker/bot/AI paths, manual/global/system policy и сквозная
приёмка/первый production S6. Historical S2B снят R190. Статус — 🟡.

Историческое уточнение R194 (2026-10-27): self workforce check-in использует
только ID-bound Authentication и exact stored username. Конфигурация смены
читается по numeric ID с row/status guards; conditional INSERT проверяет
identity/account/workforce status ещё раз в statement. Нет переноса смены
или уведомления к trim/case-fold соседу, обязательный check-in не пропускается
при ошибке account/workforce read. Opt-in, расписание и идемпотентность сохранены.
16 suites / 598 tests GREEN: 36 frozen MVC/security cases (baseline v6:
23 failures / 13 controls / 0 errors), 20 PostgreSQL workforce cases и
login/session/password/admin/activity/channel regressions. Девять SQL cases новые.
UI source contract обновляет только точную ожидаемую R187 asset version.
Schema/grants/flags/serialized principal не менялись. Это не новая общая
recipient/global/business policy и не сериализация всех account/config mutations.

Здесь текущая source/test сессия C:\Users\SinicinVV\git_h\tg_ref_b24_sup на
машине тестового стенда. Read-only probe подтвердил прежний R184 panel-web
9fb4a33fd8a4, healthy; R185–R194 не развёртывались. Изолированные Docker Maven/
PostgreSQL tests не меняют данные/схему БД стенда и не являются rollout.
Migration/backfill/reset/flags/config/provider sends, staging/commit/push не
выполнялись. Следующие gates: personal/triage preferences по ID, остальные
S3–S5 paths/policies, fleet/сквозная приёмка и первый production S6.
Historical S2B снят R190; runtime/rollback R184 сохраняются. Статус — 🟡.

Историческое уточнение R193 (2026-10-27): last_portal_activity_at обновляется
только по positive numeric ID из authenticated PanelUserPrincipal и exact raw
username. Нет case-fold fan-out, trim или unbound username fallback. Session
refresh/revoke R189 остаётся перед activity; timestamp UTC и best-effort/cache/
retry semantics сохранены, новых grants/schema/flags нет.
17 Maven suites / 727 tests GREEN; frozen 29 cases на baseline v1 —
10 failures / 19 controls / 0 errors, те же bytes GREEN. Real login/security/
session/remember-me/activity filters с mocked JDBC: collisions, rename/delete
races, revoke/read errors, activity retry/cache, raw keys и long IDs проверены.
Здесь source/test checkout C:\Users\sushi\Git_H\tg_ref_b24_sup; рабочие
проект/Docker тестового стенда на другой машине/в другой сессии
C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Baseline R192 23da82a09 сверён по
parent/subject/exact 7 files/content. R184 checkpoint сохранён точно;
R185–R193 здесь не развёрнуты. Runtime/БД/данные/flags не менялись;
Browser/Node/Docker/live DB/HTTP/providers не запускались. Grant commit/push
до 2026-10-27 21:00 UTC; apply и publisher разделены. Historical S2B снят R190;
остаются ID consumers (в том числе workforce/preferences), S3–S5 и первый
production запуск S6. Это не общая изоляция online/notification recipients. Статус — 🟡.

Историческое уточнение R192 (2026-10-27): AuthManagementApiController берёт
self/current user ID из authenticated PanelUserPrincipal, а field permissions
non-superuser читает по этому ID с exact username/status/row-count guards.
Нет переноса self/edit прав к case-fold соседу; self delete/block guards
сохраняют собственный ID. Avatar file cleanup требует разрешённого photo UPDATE.
Существующие page/global admin/portal/field policies сохранены; полная business
изоляция и атомарность всех admin CRUD этим шагом не объявляются закрытыми.
16 Maven suites / 698 tests GREEN; frozen 63 cases на baseline v4 —
49 failures / 14 controls / 0 errors, те же bytes GREEN. Real login/security/
method security/CSRF с mocked JDBC/config/file adapters, без working DB или browser.
Здесь source/test checkout C:\Users\sushi\Git_H\tg_ref_b24_sup; рабочие
проект/Docker тестового стенда на другой машине/в другой сессии
C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Baseline R191 8946949ea сверён по
parent/subject/exact 9 files/content. R184 checkpoint сохранён точно;
R185–R192 здесь не развёрнуты. Runtime/БД/flags/данные не менялись;
Browser/Node/Docker/live DB/HTTP/providers не запускались. Grant commit/push
до 2026-10-27 21:00 UTC; apply и publisher разделены. Historical S2B снят R190;
остаются новые scoped data flows/S3–S5 и первый production запуск S6. Статус — 🟡.

Историческое уточнение R191 (2026-10-27): /profile/password использует только
numeric ID из authenticated PanelUserPrincipal, с exact stored username и
current enabled/blocked checks. Одна null-safe CAS запись сравнивает исходные
password/password_hash и доступные status columns; race возвращает 409,
соседние case-fold аккаунты не изменяются. Legacy/unbound identity не получает
username fallback. Существующие password validation/current-password/CSRF guards
сохранены, UI preferences и общая revocation policy вне этого scope.
15 Maven suites / 635 tests GREEN; frozen 35 cases на baseline v3 —
18 failures / 17 controls / 0 errors, те же bytes GREEN. Stateful mocked JDBC
с real security/encoder — не PostgreSQL concurrency или browser acceptance.
Здесь source/test checkout C:\Users\sushi\Git_H\tg_ref_b24_sup; рабочие
проект/Docker тестового стенда на другой машине/в другой сессии
C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Baseline R190 96878bd4b сверён по
parent/subject/exact 10 files/content. R184 checkpoint сохранён точно;
R185–R191 здесь не развёрнуты. Runtime/БД/flags не менялись, Browser/Node/Docker/
live DB/HTTP/providers не запускались. Исторический S2B снят R190; остаются
новые scoped data flows и первый production запуск. Grant commit/push до
2026-10-27 21:00 UTC; source apply отделён от publisher. Статус 01-278 — 🟡.

Историческое уточнение R190 (2026-10-27): здесь source/test checkout
C:\Users\sushi\Git_H\tg_ref_b24_sup; рабочие проект/Docker тестового стенда
на другой машине/в другой сессии C:\Users\SinicinVV\git_h\tg_ref_b24_sup.
R189 a4d263eb3 сверён по parent/subject/exact 10 files/content. R190: первичный
login/remember-me принимает только одну case-insensitive account row, без
trim и выбора по регистру/порядку. Коллизия отклоняется до чтения прав;
открытая ID-bound сессия R189 не переходит на соседний аккаунт.
14 Maven suites / 600 tests GREEN; frozen 18 cases на baseline —
9 failures / 9 controls / 0 errors, те же bytes GREEN. JDBC mocked,
Browser/Node/Docker/live DB/HTTP/providers здесь не запускались. Checkpoint R184
сохранён точно как evidence тестового стенда; runtime/БД не изменены.
R185–R190 здесь не развёрнуты. Grant commit/push до 2026-10-27 21:00 UTC;
source apply и expiry-guarded publisher разделены. Исторический S2B снят,
остаются новые data flows/S3–S5, будущие product policies и первый запуск S6.
Правило входа/сессий: [08-panel-account-credentials](08-panel-account-credentials.md).

Историческое уточнение R189 (2026-10-27): рабочие проект и Docker остаются на
другой машине/в другой сессии C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Здесь
source/test checkout C:\Users\sushi\Git_H\tg_ref_b24_sup: Maven MockMvc с
настоящими login/security/method security и MockHttpSession, только JDBC mocked.
R188 1ccdfc4c5 сверён по parent/subject/exact 7 files/content. 13 suites / 582
tests GREEN; новые frozen 32 cases на baseline — 24 failures / 8 controls / 0 errors.
Проверены refresh/revoke прав в той же сессии, numeric identity, статус/rename/
username reuse, строгий отказ при read failure, сериализация, public/CSRF и прежние
login/channel guards. Browser/Node/Docker/live DB/HTTP/providers не запускались.
Checkpoint R184 и пользовательский start/stop одного бота сохранены точно;
новых runtime probes/restart/deploy/migration/backfill/config/flags/данных нет.
R185–R189 здесь не развёрнуты. Grant commit/push до 2026-10-27 21:00 UTC
сохраняется; source apply и expiry-guarded publisher разделены. Статус 01-278 — 🟡.

R189 привязывает DB-backed principal к numeric account ID. Каждый защищённый
запрос проверяет ID + точный stored username, active status и текущие direct/role
authorities до controller/service. Права обновляются в том же запросе и явно
сохраняются в session context; старый/переименованный/удалённый аккаунт не получает
права нового пользователя с тем же логином. Legacy principal без ID требует
повторного входа. Read failure — 503 без старых прав, public routes независимы.
Подробнее и rollout/rollback compatibility нового serialized principal:
[08-panel-account-credentials](08-panel-account-credentials.md).
Это next-request guard, не cluster-wide deletion или полная S6 acceptance;
неоднозначный первичный login lookup и остальные legacy/S2B/S3–S6 gates открыты.

Историческое уточнение R188 (2026-10-27): рабочие проект и Docker остаются на
другой машине/в другой сессии C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Здесь
source/test checkout C:\Users\sushi\Git_H\tg_ref_b24_sup: Maven MockMvc с
настоящими security/DAO/encoder/session/remember-me и Thymeleaf, JDBC mocked.
R187 d8a68ca80 сверён по parent/subject/exact 20 files/content. 12 suites / 550
tests GREEN; новые frozen 37 cases на baseline — 16 failures / 21 controls /
0 errors. Проверены точный логин и собственные authorities при похожих аккаунтах
с одним паролем, отказы, настоящая CSRF cookie/form token, session ID, logout,
remember-me restore/disable/tamper и прежние channel/credential/permission guards.
Browser/Node/Docker/live DB/HTTP/providers в R188 не запускались. Checkpoint R184
и пользовательский start/stop одного бота сохранены точно; новых runtime probes
или рабочего restart/deploy/migration/backfill/config/flags нет. R185–R188 здесь
не развёрнуты. Commit/push grant до 2026-10-27 21:00 UTC сохраняется; source
apply и expiry-guarded publisher разделены. Общий статус 01-278 — 🟡.

R188 сохраняет exact username/password уже в POST /login, до DAO: стандартный
filter больше не создаёт trim alias. Политика R187 нормализует только новые
и явно изменяемые логины, не вход или существующие account keys. Прежние
case-insensitive lookup, authority loading и framework session/remember-me
остаются; новое чтение authority/revoke уже открытых сессий не добавляется.
Подробнее: [08-panel-account-credentials](08-panel-account-credentials.md).
Другие legacy consumers, S2B и worker/fleet/S6 gates остаются открыты.

Историческое уточнение R187 (2026-10-27): рабочие проект и Docker остаются на
другой машине/в другой сессии C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Здесь
source/test checkout C:\Users\sushi\Git_H\tg_ref_b24_sup: изолированные Maven
MVC/mock/security/Thymeleaf и browser/Node fixtures. R186 9b7bd6f6b проверен по
parent/subject/exact 9 files/content. 255 Java + 76 real DOM browser cases и
110 scoped Node checks GREEN. Полный Node run содержит один прежний R53 notes
fixture failure, отдельно повторённый на неизменённых baseline inputs.
Docker/live DB/HTTP/providers не запускались. Checkpoint R184 и пользовательский
start/stop одного бота сохранены точно, без новых runtime probes. Рабочие
restart/deploy/migration/backfill/config/flags и массовые изменения логинов
не выполнялись. R185–R187 здесь не развёрнуты. Grant commit/push до
2026-10-27 21:00 UTC сохраняется; source apply и publisher разделены.

Политика R187: новые/явно изменяемые логины — ASCII латиница, цифры и `. _ - @`,
обычные крайние пробелы удаляются; внутренние/другие символы отклоняются.
Старые account keys и grants не нормализуются при сохранении других полей.
`lower(trim(username))` допустим только для консервативного отказа в коллизии
новой записи/переименования, не для выбора identity или authority. Пароли
передаются точно, включая пробелы; общая write policy и подсказки описаны в
[08-panel-account-credentials](08-panel-account-credentials.md).
UserLastActivityFilter больше не trim'ит principal; прежние SQL case folding,
auth/blank guards и best-effort поведение сохранены. Это не миграция старых
логинов и не разрешение ослаблять exact-key guards других consumers.
Worker/fleet/S6 acceptance по-прежнему открыта, общий статус 01-278 — 🟡.

Историческое уточнение R186 (2026-10-27): рабочие проект и Docker остаются на
другой машине/в другой сессии C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Здесь
source/test checkout C:\Users\sushi\Git_H\tg_ref_b24_sup: только изолированные
browser/Node fixtures детальной карточки инцидента. R185 3e5ceba37 проверен по
parent/subject/exact files/content. 203 browser cases + 110 scoped Node checks
GREEN; один прежний R53 notes fixture failure в полном Node run отдельно
повторён на неизменённых baseline inputs. Java/Docker/DB/live HTTP/providers
не запускались. Checkpoint R184 и сообщение пользователя о start/stop одного
бота сохранены из документации, без новых runtime probes. R185/R186 не
развёрнуты здесь; нет рабочего restart/deploy/migration/backfill/config/flags.
Grant commit/push до 2026-10-27 21:00 UTC сохраняется; source apply и publisher
разделены. Общая worker/fleet/S6 acceptance не выводится из UI fixtures.

Историческое уточнение R185 (2026-10-27): source/test checkout находится на
машине C:\Users\sushi\Git_H\tg_ref_b24_sup; рабочий проект и Docker — на другой
машине/в другой сессии C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Из репозитория
прочитаны checkpoint panel-web R184 (8310d1f7f) и сообщение пользователя об
успешном start/stop одного бота. Это сохранённые runtime evidence другой сессии,
не новые probes здесь и не полная приёмка S6. R185 — только source и isolated
browser/Node fixtures создания инцидента: 117 browser cases + 110 scoped Node
checks GREEN; один прежний R53 notes fixture failure в полном Node run отдельно
повторён на неизменённых baseline inputs. Java/Docker/DB не запускались. Рабочие
revision/config/flags/worker/bot fleet не менялись, R185 не развёрнут. Grant
commit/push до 2026-10-27 21:00 UTC сохраняется; source apply/publisher разделены.

Историческое уточнение R184 (2026-10-27): рабочие проект и Docker остаются на
другой машине/в другой сессии. Здесь source и изолированные MVC/security/Thymeleaf
tests; Docker/PostgreSQL/browser/Node не запускались. R183 5aa3d5e46 проверен
по parent/subject/files/content. Grant commit/push до 27 октября 21:00 UTC
сохраняется; source apply и expiry-guarded publisher разделены. Рабочие
restart/deploy/migration/backfill/config/flags/боты не затронуты.

Историческое уточнение R183 (2026-10-27): рабочие проект и Docker находятся на
другой машине/в другой сессии. Здесь source и изолированные Maven service/MVC
fixtures. Read-only local docker version probe: daemon недоступен; Docker и
PostgreSQL tests не выполнялись. R182 ae325d000 проверен по parent/subject/files/
content. Grant commit/push до 27 октября 21:00 UTC сохраняется; source apply и
expiry-guarded publisher разделены. Рабочие restart/deploy/migration/backfill/
config/flags не выполнялись, рабочая acceptance неизвестна.

Историческое уточнение R182 (2026-10-27): рабочие проект и Docker находятся на
другой машине/в другой сессии. Здесь source и изолированные Maven unit/MVC fixtures;
Docker/browser/Node не запускались. R181 ad2b0af6b проверен по parent/subject/files/
content. Новый grant commit/push действует до 27 октября 21:00 UTC; source apply
и expiry-guarded publisher разделены. Рабочие restart/deploy/migration/backfill/
config/flags не выполнялись; рабочая acceptance неизвестна.

Историческое уточнение R181 (2026-10-26): рабочие проект и Docker остаются на
другой машине/в другой сессии. Здесь source и изолированные Maven MVC/service
fixtures; Docker/browser/Node в R181 не запускались. R180 f8a8367f3 сверён по
parent/subject/files/content. Пользователь явно разрешил commit/push до конца
26 октября Moscow (21:00 UTC); source apply и expiry-guarded publisher разделены.
Рабочие restart/deploy/migration/backfill/config/flags не выполнялись.

Историческое уточнение R180 (2026-10-26): рабочие проект и Docker остаются на
другой машине/в другой сессии. Здесь только source и изолированные browser/Node
fixtures; Java/Docker в R180 не запускались. R179 b92c1aa26 сверён по parent,
subject/files/content. Grant commit/push — до 26 октября 21:00 UTC; source apply
и publisher разделены. Рабочие restart/deploy/migration/backfill/config/flags
не выполнялись; рабочая acceptance неизвестна.

Историческое уточнение R179 (2026-10-26): рабочие проект и Docker остаются на
другой машине/в другой сессии. Здесь только source и изолированные Maven
unit/service fixtures; Docker/browser/Node в R179 не запускались. Новый baseline
a687d6b73 содержит независимые incident/pagination commits после проверенного
R178. Grant commit/push действует до 26 октября 21:00 UTC; source apply и Git
publisher разделены. Рабочие restart/deploy/migration/backfill/config/flags
не выполнялись, рабочая acceptance неизвестна.

Историческое уточнение R178 (2026-10-26): рабочие проект и Docker остаются на
другой машине/в другой сессии. Здесь source и изолированные browser/Node fixtures;
Docker/Java tests в R178 не запускались. R177 dd41dea3a опубликован и проверен
по parent/files/content. Grant commit/push на 26 октября действует до
2026-10-26 21:00 UTC; source apply и Git publisher разделены. Рабочие
restart/deploy/migration/backfill/config/flags не выполнялись.

Историческое уточнение R177 (2026-10-26): рабочие проект и Docker находятся на
другой машине/в другой сессии; здесь source/test и изолированные Testcontainers.
R176 e10cd9706 уже опубликован пользователем и сверён по parent/files/content.
Пользователь продлил commit/push на 2026-10-26 Moscow, expiry 2026-10-26 21:00 UTC.
Source apply и отдельный Git publisher имеют exact baseline/content/file guards;
publisher дополнительно проверяет срок и actual remote. Рабочие restart/deploy/
migration/backfill/config/flags не разрешались и не выполнялись.

Историческое уточнение R176 (2026-10-26): рабочий экземпляр и Docker остаются на
другой машине/в другой сессии; здесь source/test checkout и изолированные
Testcontainers. Git grant 23 октября истёк; нового разрешения на commit/push нет.
R176 заканчивается только source apply после GREEN, без staging/commit/push
и без рабочего restart/deploy/migration/backfill/config/flags.

Историческое уточнение R175 (2026-10-23): рабочий экземпляр и Docker остаются на
другой машине/в другой сессии; здесь source/test checkout и только изолированные
Testcontainers. Пользователь явно продлил commit/push на 2026-10-23 Moscow,
expiry 2026-10-24 00:00 +03 (2026-10-23 21:00 UTC). Source apply и Git publisher
разделены с exact baseline/content/files/remote и expiry guards. Это разрешение
не включает рабочие restart/deploy/migration/backfill/config/flags.


Историческое уточнение R168–R171 (2026-10-22): рабочий экземпляр и Docker находятся
на другой машине и в другой сессии относительно этой source/test-сессии.
Здесь разрешён только изолированный Testcontainers; рабочие stack/revision/
DB migrations/flags не проверялись и не менялись. Одинаковый путь checkout
не идентифицирует машину. Новый явный grant текущего пользователя разрешает
commit/push только 2026-10-22 Europe/Moscow, до 2026-10-22 21:00 UTC;
не включает рабочие restart/deploy/migration/backfill/config/flags.
Source apply и Git publisher разделены, с exact content/file и expiry guards.

Историческое уточнение runtime-сессии R163–R167: рабочий проект и Docker находятся
на машине текущей сессии; пользователь разрешил restart Docker/контейнеров.
В R167 заново проверены Docker Server 29.7.2 и healthy web/bot/worker/direct/
PostgreSQL/RabbitMQ/Redis. Это health, не функциональная приёмка и не проверка
рабочих revision/DB migrations/flags. Restart/rebuild/deploy/migration/backfill/
config/flags не выполнялись. Restart не разрешает migration при startup.
Git grant R160 из другой сессии не наследуется: staging/commit/push отсутствуют.

Историческое уточнение R160 (2026-10-22): рабочие Docker и проект находятся на
другой локальной машине, в другой сессии агента; здесь source/test checkout.
Наблюдения R143/R155–R159 о машине/health относятся к их runtime-сессии и не
переносятся сюда. Рабочие revision/DB migrations/flags/acceptance здесь неизвестны.
Рабочий stack не инспектируется и не изменяется; Docker допустим только для
изолированных Testcontainers. Одинаковый путь checkout не идентифицирует машину.

Пользователь R160 явно разрешил commit/push без повторного подтверждения только
2026-10-22 Europe/Moscow: expiry 2026-10-23 00:00 +03 (2026-10-22 21:00 UTC).
Новый grant текущей сессии не наследуется из прошлых записей. Source apply и Git
publisher разделены; publisher проверяет срок и exact file/content set. Разрешение
не включает рабочий deploy/restart/migration/backfill/config/flags.

Историческое уточнение другой сессии:

Уточнение R137 (2026-10-19): по уточнению пользователя рабочий Docker
и весь запущенный проект находятся на другой локальной машине, в другой сессии
агента относительно source/test-сессии R137. Docker/image observations R135/R136
относятся к той runtime-сессии; слова «на этой машине» из неё не переносятся в
текущую. Runtime revision/DB/flags/rollback отсюда не проверены. Местный Docker
допустим для изолированных Testcontainers и не доказывает состояние рабочего
экземпляра. Одинаковый путь checkout не идентифицирует машину или сессию.

## Зафиксированные продуктовые решения

- Бизнес — самостоятельная доменная сущность, а не iiko-объект, юридическое
  лицо, канал или текстовое поле локации.
- `Суши` и `Блины` создаются как обычные предустановленные записи с устойчивыми
  `code`; новое направление не наследует их обращения, вопросы и шаблоны.
- У одного сотрудника одна учётная запись `users.id`; доступ может быть к
  одному, нескольким или всем бизнесам.
- Режим «Все доступные» объединяет только уже разрешённые business IDs и не
  расширяет полномочия.
- Изоляция проверяется на сервере и в SQL read/write paths. UI filter,
  выбранный browser-local context либо название бизнеса не являются authority.
- PostgreSQL остаётся единственным relational source of truth, а Flyway и
  canonical business schema принадлежат `spring-panel`.
- Для ручного бизнес-обращения без бота/интеграции создаётся локальный клиент с новым
  ID, принадлежащий ровно одному выбранному бизнесу. Это не аккаунт `users.id`
  и не клиент внешнего канала. Ручной путь использует отрицательный `user_id`,
  выделенный отдельной последовательностью; ноль недопустим. Новый `ticket`
  получает явный `business_id` до записи корня, без вывода из legacy текста.
- В общем канале человек явно выбирает бизнес **или вариант «Без бизнеса» до первого вопроса**.
  `business_id` не выводится из названия локации, текста, последнего выбора
  оператора или факта привязки канала. Пока выбора нет, вопросник не начинается;
  бизнес-ответ сверяется с активными привязками канала, а выбранный scope
  сохраняется с сессией/обращением и переносится через transport/replay.

## Свежий baseline и проверенные факты

Аудит выполнен на `main`/`origin/main`
`08c1bd0add49cd39d9d23dd6240d27f985607f23`; рабочее дерево до S0 было чистым.
Предыдущий commit `da969404009276263c9b51bc362ce233ea6a7cf0` не содержит
мультибизнесовой реализации.

Подтверждено чтением текущего source и PostgreSQL migration chain V1–V50:

- `tickets` имеет ключ `(user_id, ticket_id)`, но `messages.ticket_id` пока
  уникален, а большинство дочерних read/write paths принимает один `ticket_id`.
  В частности, так работают `DialogLookupReadService`, bot runtime read/write,
  `UiEventOutboxWatcher` и часть task links. Новая модель не вправе считать
  строковый `ticket_id` вечным глобальным ключом.
- `ConversationTicketCreationIngestionService` в одной транзакции создаёт
  root `messages`, `tickets`, историю, attributes, active/span records; в
  payload есть только legacy `business` string. Поэтому scope обязан быть
  определён до создания root ticket/message и сохранён в event envelope.
- `DialogLocationBusinessApiController` меняет `messages.business` по одному
  `ticket_id`, а `DialogAuthorizationService` проверяет `PAGE_DIALOGS` и
  глобальные action permissions. Ни один из них пока не проверяет business
  scope.
- Аутентификация использует `users.id`; `user_authorities` и `roles` дают
  глобальные page authorities. `panel_users` не является отдельным
  каноническим account store. Business-role нельзя выводить из `ROLE_ADMIN` или
  из права на страницу.
- `channels.question_template_id` и `channels.questions_cfg` существуют,
  однако runtime берёт общую canonical `bot_settings` конфигурацию. В
  `SettingsParameterService` и `IikoDepartmentLocationCatalogService` всё ещё
  есть текстовые aliases и специальные имена двух legacy-бизнесов. Это
  compatibility input, но не будущий business identity.
- `ticket_attributes` хранит answers по `(ticket_id, question_id)`;
  `tasks`/`projects`/boards и `notifications` не имеют business scope;
  `ui_event_outbox` несёт `ticket_id` и `channel_id` без business ID.
- Ранее зафиксированный read-only data audit остаётся входным evidence: 47
  tickets и root messages, 619 history, 108 attachment metadata, 44 непустых
  legacy business labels, 3 пустых labels, 2 mixed channels из 3 и 3 history
  channel mismatch. Значения aliases не переносятся в этот открытый документ.

## Каноническая модель данных

### Бизнес, участник и полномочия

```text
users 1 ──< business_memberships >── 1 businesses
users 1 ──< business_access_grants
businesses 1 ──< business_channel_bindings >── 1 channels
businesses 1 ──< business_locations
businesses 1 ──< tickets
```

### Предлагаемые новые сущности

| Сущность | Обязательные поля и ограничения | Назначение |
| --- | --- | --- |
| `businesses` | identity PK, immutable unique `code`, display name, `status` (`active`/`archived`), timestamps, optional JSON settings | Единственный canonical business identity. Предустановленные записи получают codes `sushi` и `bliny`; display names не используются как ключи. |
| `business_memberships` | `user_id` FK `users`, `business_id` FK, `role_code`, `permission_overrides`, active state, unique `(user_id, business_id)` | Явный доступ и функциональная роль пользователя в одном бизнесе. |
| `business_access_grants` | `user_id` FK, `scope=ALL_BUSINESSES`, `role_code`, active state, один active grant данного scope на пользователя | Динамический доступ ко всем текущим и будущим businesses. Это не `ROLE_ADMIN` и не доступ к global/system data. |
| `business_channel_bindings` | `channel_id` FK, `business_id` FK, `resolution_mode`, active state, unique `(channel_id, business_id)` | Разрешает одному каналу обслуживать несколько бизнесов без неявного default. |
| `business_locations` | identity PK, `business_id` FK, display fields/status, unique key только внутри business | Позволяет одноимённым локациям существовать в разных бизнесах. Маппинг iiko хранится отдельно. |
| `external_location_mappings` | `business_location_id` FK, source type, external ID, unique `(source_type, external_id)` | Привязка iiko/CRM и иных источников к существующей локации, а не создание business identity из внешнего значения. |

`role_code` определяет только функциональные capability в business context
(`VIEW`, `OPERATE`, `MANAGE`, `CONFIGURE`); его точный vocabulary и relation с
глобальными page permissions должны быть утверждены до S1. Effective permission
всегда равен пересечению page authority, membership/all-business grant и
business capability. `ALL_BUSINESSES` добавляет data scope и базовую
business-role, но не system-admin capability.

### Tickets и дочерние записи

- Бизнес-обращение имеет canonical owner `tickets.business_id`. `NULL` в этом
  поле сам по себе означает только legacy/unresolved, но не подтверждённый
  отказ от бизнес-привязки. Намеренно общее обращение имеет отдельный признак
  `tickets.is_general=TRUE`, `business_id=NULL` и сохранённый источник
  `USER_SELECTION` (общий канал) либо `GENERAL_CHANNEL` (явно настроенный
  канал без бизнесов). Неопределённый scope нового ticket — отказ/ручная очередь,
  а не запись с guessed default.
- `messages.business` остаётся legacy display/compatibility metadata и не
  участвует в авторизации. Нельзя удалять или переписывать это поле в S1.
- S2A фиксирует новый public route key как отдельный immutable
  `ticket_public_id`: 32 lowercase hexadecimal symbols, уникальный только для
  non-null значений. Legacy routes не расширяются и остаются в подтверждённом
  compatibility window; internal callers без public ID обязаны нести полный
  `(user_id, ticket_id)` tuple. Bare `ticket_id` не считается глобальным ключом.
- S2A также предоставляет только read-only preview legacy assignment: он
  сравнивает `messages.business` с `business_legacy_aliases.alias` побайтно,
  без trim/lowercase или иных догадок. `UNRESOLVED`, duplicate mapping и
  conflict остаются отдельными состояниями; сервис не меняет `tickets` и не
  является backfill.
- Child records получают business scope через canonical ticket. Там, где
  таблица хранит только `ticket_id`, этап S2 обязан добавить/вывести достаточный
  tuple или public reference до включения strict filtering. `chat_history`
  принадлежит ticket, а не своему историческому `channel_id`.
- Изменение business у существующего ticket — отдельная audit-only privileged
  операция после проверки зависимостей. Смена UI filter не меняет owner data.

### Каналы и вопросники

`business_channel_bindings.resolution_mode` допускает только:

- `SINGLE_BUSINESS` — канал связан с одним бизнесом;
- `EXPLICIT_SELECTION` — пользователь сначала выбирает бизнес;
- `VERIFIED_EXTERNAL_MAPPING` — business получен из подтверждённого external
  mapping;
- `VERIFIED_LOCATION_MAPPING` — business получен из однозначного approved
  mapping локации.

При нескольких кандидатах, отсутствии кандидата или conflict runtime должен
создать управляемое `UNRESOLVED` состояние/ручное назначение и не начинать
question flow. Business, template version и selection source сохраняются с
ticket до конца retries, edits, feedback и replay.

Вариант `GENERAL` — самостоятельный сценарий без бизнеса, а не разновидность
`UNRESOLVED` или `GLOBAL_SYSTEM`. В общем канале он выбирается явно; в
предварительно настроенном канале только для общих обращений выбор не нужен.
Для него используется отдельный от
business-scoped шаблон из canonical `bot_settings.question_templates`:
произвольный набор вопросов, типов и порядок редактируются в редакторе
вопросников; бизнес-локации и бизнес-presets не подмешиваются. В общем канале
вариант «Без бизнеса» должен выбирать этот шаблон до первого вопроса.
Персоналу такие обращения нужны в отдельной явно разрешённой очереди; grant
`ALL_BUSINESSES` сам по себе не разрешает их читать или изменять.

Read-only очередь GENERAL использует отдельный явный grant
`general_ticket_read_grants` на канонический `users.id`, без автоматических
назначений. Доступ требует одновременно `PAGE_DIALOGS`, единственную canonical
учётную запись с enabled=TRUE/is_blocked=FALSE и активный READ grant. R134
проверяет уникальность case-insensitive username также при чтении, не выбирая
произвольную enabled запись из нескольких. `ROLE_ADMIN`, business membership и
`ALL_BUSINESSES` не заменяют этот grant. Установка/отзыв доступны только
системному администратору с `PAGE_SETTINGS` и CSRF. READ не разрешает отвечать,
менять статус либо выполнять другие write operations.

R135 вводит отдельный opt-in general_ticket_operate_grants: OPERATE действует
только вместе с current READ, не наследуется из ROLE_ADMIN/ALL_BUSINESSES и
никому не назначается миграцией. Системный admin с PAGE_SETTINGS/CSRF может
выдать его текущему READ account; отзыв READ атомарно отзывает OPERATE, возврат
READ сам по себе работу не восстанавливает. Новый write scope этого этапа —
только GENERAL triage pending↔open. Это не resolve/reopen/close, не назначение
ответственного и не отправка клиенту; legacy lifecycle/feedback не вызываются.

Страница /general-dialog-work/{publicId} требует PAGE_DIALOGS и current READ;
POST также OPERATE и настоящий CSRF. Public locator сервером разрешается в
expected GENERAL tuple/generation; raw owner не экспортируется в model.
V65 добавляет general_work_revision, автоматически увеличиваемую PostgreSQL
при смене GENERAL status/owner/scope даже другим writer, и запрещает её ручную
правку. CAS проверяет revision/status, exact owner, unique raw ID, current
account/READ/OPERATE в финальном UPDATE. В одном SQL атомарны status, immutable
metadata audit и V61 GENERAL outbox ticket_status_changed; ошибка любого INSERT
откатывает всё. Новых grants, классификации/backfill старых owner или runtime
flags миграция не создаёт. Readiness требует V65 schema до объявления READY.
На этапе R135 GENERAL reply/edit/delete/resolve/reopen/assignment оставались
PENDING; добавленный в R136 текстовый reply path описан ниже.

R136 добавляет отдельный текстовый reply path для GENERAL pending/open.
PAGE_DIALOGS, current READ + OPERATE и CSRF обязательны. Уникальный request key
привязан к frozen tuple/public ID, actor, revision, тексту и SHA-256 destination
snapshot; его повтор не создаёт вторую отправку.
Форма несёт fingerprint исходного destination; перенастройка канала до
POST требует перечитать форму, а не принять новый destination молча.
QUEUED→SENDING фиксируется до сети. Отправку инициирует только этот POST;
фонового sender/автоматического replay очереди в R136 нет.
После claim повторно проверяются root message/channel, account/grants,
revision и destination; блокировки этих rows удерживаются до результата.
Непосредственно перед HTTP send транспорт снова вызывает guard. Неизвестная
платформа не превращается в Telegram; credentials в outbox не сохраняются.

SENT и canonical history/public generation, metadata audit и GENERAL outbox
фиксируются одной транзакцией. При timeout/неясном результате — UNKNOWN;
crash/ошибка сохранения оставляют SENDING/UNKNOWN для ручной сверки, без
автоматического retry и без обещания exactly-once у внешнего провайдера.
Повторный POST того же request key не отправляет SENDING/SENT/UNKNOWN заново.
Очередь/состояния видны только через текущий GENERAL READ и exact public owner.
V66 additive, без backfill/grants. Новая история несёт ticket_public_id;
NULL старых rows не классифицируется автоматически. Reply media/edit/delete,
resolve/feedback, activity/span attribution, reconciliation/retention policy и provider E2E остаются gates.

R137 открывает ручные GENERAL pending/open→closed и closed→pending. Прежний
pending↔open сохранён. Во final SQL действуют те же owner/account/READ/OPERATE,
expected status/revision и новый V67 general_ticket_transition_allowed gate.
Close фиксирует resolved_at/resolved_by/closed_count; reopen очищает resolution,
повышает reopen_count/last_reopen_at и возвращает в pending. V65 увеличивает
revision; старую форму нельзя применить после close/reopen цикла. Status,
metadata/counters, immutable audit и GENERAL outbox атомарны; partial failure
откатывает всё. V67 меняет только allowed transition CHECK, сохраняет старые
records/grants и immutable trigger; readiness требует новую function schema.
Закрытый ticket не принимает новый reply, старый queued reply теряет revision;
reopen не включает retry. Close сериализуется с уже guarded network attempt
через существующие reply row locks. Legacy lifecycle/feedback/assignment,
GENERAL activity/span attribution и reconciliation остаются отдельными scope.

R138 добавляет наблюдаемые интервалы работы GENERAL, без attribution старых
ticket_spans/ticket_active/work_time_total_sec. Только новый manual pending→open
создаёт typed interval полного user/raw/public owner с revision/actor/time
snapshot. При open→pending/closed учитывается время только при совпадении
opened revision с прежним root revision и неубывающих часах. Внешний status/
owner/scope ABA и clock regression исключают интервал из итога (UNCONFIRMED
при чтении, INVALIDATED при следующем переходе того же полного owner).
Разные owner/public generation не наследуют интервалы. Старый open root без
observed start не получает выдуманный duration. Это частичный учёт.

V68 additive: unique OPEN для full tuple, immutable start/identity/terminal
snapshot, sequential close-before-insert helper внутри authorized root CAS.
Root status/metadata/counters/revision, interval, audit и GENERAL outbox атомарны;
failure любого шага откатывает всё. Новых grants/backfill/flags нет; readiness
компилирует table и helper probe WHERE 1=0 без выполнения writer.
Чтение summary/последних 10 повторно требует current READ/account/flags и exact
explicit GENERAL/NULL business unique owner до aggregate/order/limit. Только
COMPLETED входят в FLOOR суммы точных seconds; незавершённый интервал показан
отдельно. UI явно сообщает partial tracking, raw IDs не выдаёт.
Legacy attribution, session/activity heartbeat, assignment, reconciliation/
retention и runtime acceptance на другой машине/в другой сессии остаются gates.

R139 добавляет отдельное ручное GENERAL assignment pending/open: назначение,
смена и снятие ответственного через PAGE_DIALOGS + current READ/OPERATE + CSRF.
Target обязан быть canonical unique enabled/unblocked account с GENERAL
READ+OPERATE. Candidate pool закрыт для READ-only; page authority target по-прежнему
нужна для его входа в «Диалоги», назначение само не выдаёт доступ/grants.
V69 — append-only scoped assignment events с exact user/raw/public tuple,
actor/assignee snapshot и отдельной assignment revision. Новых grants/backfill
и переноса ticket_responsibles нет. Current/history требуют READ и exact owner
до latest/limit; owner/generation не наследуют старое назначение. Утративший
права target показан недоступным, автоматического переназначения нет.

Root FOR UPDATE сериализует assignment CAS с status/owner и другим assignment;
account/grants actor и target удерживаются FOR SHARE до commit. Проверяются
expected work revision, assignment revision, explicit GENERAL и unique raw ID.
Event и GENERAL assignment_changed outbox атомарны; stale/ABA/no-op отказ не
создаёт event. Root status/work revision не меняются, interval/reply не прерываются.
Closed assignment — read-only. Readiness требует V69; missing schema не открывает
legacy fallback. Metadata-only invalidation идёт через отдельный GENERAL
SSE/Redis/current READ path без legacy callbacks. Автоназначение/балансировка,
историческая attribution, bell/служебная доставка и retention остаются gates.

R140 открывает отдельное ручное GENERAL creation без бизнес-привязки и канала.
Нужны PAGE_DIALOGS, current GENERAL READ+OPERATE/account и одновременно default-off
foundation/general-ticket-queue/manual-ticket-creation. Форма не принимает
business/client ID: новый local client из manual_general_clients получает
отрицательный ID общей non-cycling V54 sequence с проверкой занятых namespaces.
Business-local registry остаётся отдельным. Ticket создаётся pending с revision 0,
explicit GENERAL/NULL business и MANUAL source, без legacy spans/active records.
V70 source allowlist сохраняет USER_SELECTION/GENERAL_CHANNEL, допускает MANUAL
только в GENERAL и сохраняет non-general source=NULL. Старые rows не меняются,
новых grants/backfill нет; bot transport allowlist не расширен.

Request key привязан к actor ID/input SHA-256 и immutable exact tuple/public ID.
Same-key запросы сериализуются advisory lock; account/grants удерживаются FOR SHARE
до commit и проверяются в client INSERT. Повтор возвращает прежний public ID
только при current exact GENERAL MANUAL owner/root/registry и том же actor/input;
при owner/generation/source changes, revocation, missing root или collision
нет дубля/нового owner capture. Client/root message/ticket/operator_note history/
request audit/GENERAL ticket_created outbox атомарны. Строковые SQL inputs digest
явно CAST text и проверены в default/unspecified JDBC mode. No provider/AI/legacy
callbacks; interval возникает только при последующем manual переводе в работу.
UI предупреждает о локальном клиенте и отсутствии канала отправки; redirect
содержит только public ID. Readiness требует V70 tables/function. Retention/
reconciliation keys, media/feedback, auto-assignment и live acceptance остаются gates.

R141 добавляет отдельные Telegram EDIT/DELETE только для подтверждённых R136
GENERAL text replies. Нужны PAGE_DIALOGS/current READ+OPERATE/account, CSRF,
foundation/general-ticket-queue и pending/open exact owner с unique raw ID.
Canonical history имеет exact tuple/public generation, sender operator,
operator_message, без attachment; старые/NULL-generation/client/note rows
не классифицируются. Source reply/destination/provider ID и text/timestamps
фиксируются в форме/immutable command V71. Смена actor/input/snapshot/owner/
work revision/channel/config закрывает запрос без recapture. Reply/history
update locks и root/ticket/channel/account/grant share locks сохраняются через
provider call; transport повторяет guard прямо перед HTTP и использует frozen
channel. Старые transport entrypoints остаются совместимыми.

Single QUEUED→SENDING claim не допускает повторного HTTP для key. Подтверждение
provider атомарно меняет canonical text/edited_at либо deleted_at, APPLIED/audit
и metadata GENERAL outbox; исходная отправка сохраняется как audit. Любой
неясный внешний результат — UNKNOWN либо durable SENDING при ошибке closeout;
partial unique index блокирует последующие команды этого source reply. Нет
auto-retry или автоматической reconciliation. Safe guard denial — CANCELLED.
Устаревший content fingerprint не перезаписывает более новое сообщение.
Media, VK/MAX edit/delete, feedback и retention/reconciliation — gates.
V71 не переписывает старые data/grants и не включает flags.

R142 добавляет отдельную multipart/CSRF отправку Telegram GENERAL documents.
PAGE_DIALOGS/current READ+OPERATE/account, foundation/general-ticket-queue,
pending/open, exact tuple/public generation, revision и unique raw ID/root/channel
обязательны; только USER_SELECTION/GENERAL_CHANNEL, не MANUAL local client.
Один файл 1..10 МиБ, caption<=1024 UTF-16 units, безопасное имя<=200 units,
forced application/octet-stream, без inline preview. Raw ID<=255 из-за старого
metadata field; storage key основан на public ID. Actual bounded bytes и SHA-256
сохраняются в immutable V72 intent. Key привязан к actor/input/destination/work
snapshot; единственный QUEUED→SENDING claim, guarded HTTP и frozen payload/channel.
Share locks удерживаются через provider call и closeout. Только positive provider
ID и проверенные key/size/SHA object storage допускают atomic SQL history/
metadata/SENT/audit и metadata-only GENERAL outbox. Неясный результат — UNKNOWN
либо durable SENDING, auto-retry нет; новый key означает новый intent.

Document download требует SENT registry + exact canonical history/generation/
owner/provider и available unique metadata key/name/hash/size. Legacy fallback
исключает document marker ИЛИ registry-linked history даже при порче marker.
Bounded read проверяет actual bytes/size/SHA, затем повторяет current READ/metadata
proof после IO; attachment/octet-stream/nosniff/no-store, mismatch/revocation=404.
Object storage не участвует в SQL rollback, возможен orphan после внешнего успеха.
Immutable SQL payload и external objects требуют отдельной retention/quotas/
reconciliation policy; no auto-cleanup. V72 не меняет старые rows/grants/flags.
Inline media, VK/MAX uploads и media edit/delete остаются gates; live acceptance
на рабочей машине не выполнена.

`/api/general-tickets` и `/general-dialogs` закрыты foundation и отдельным
default-off `multibusiness.general-ticket-queue`. Каждый SQL read повторно
проверяет grant и выбирает только `is_general=TRUE`, `business_id IS NULL` и
канонический public ID. Legacy/unresolved NULL и бизнес-обращения не входят
в эту очередь. История использует composite owner; attributes и attachment
namespace дополнительно требуют однозначности legacy ticket ID. Grant этого
read-only контура не доказывает готовность legacy API, write paths,
глобального поиска/уведомлений и фоновых заданий; их изоляция остаётся gate до rollout.

R134 добавляет отдельный GET /api/general-tickets/events/stream: PAGE_DIALOGS
проверяется на handshake, foundation/general-ticket-queue и live GENERAL READ
проверяются сервисом. Каждый emitter перед отправкой получает отдельный SQL
proof текущего account/grant и frozen tuple/public generation с is_general=TRUE,
business_id=NULL и unique raw ID. Регистр username сопоставляется canonical
case-insensitive, пробелы principal не отбрасываются и не дают чужой grant.
GENERAL не становится допустимым UiEventTicketScope.isValid для legacy callbacks:
отдельный GeneralTicketEventScope создаётся только из полного GENERAL V61 snapshot.

Watcher направляет известные GENERAL event types только в этот realtime, до
initial-message lookup и legacy AI/bell/assignment/support-chat. Cursor проходит
отклонённые/старые rows. Consumer не захватывает owner повторно; смена tuple,
business/kind/generation, удаление, collision либо missing schema закрывают delivery.
Redis использует отдельный topic <existing-ui-topic>:general:v1 и version=1
envelope без текста/media. Старый subscriber не получает этот topic; local mode
применяет те же per-emitter guards. Redis error не открывает local/legacy fallback.
Сами invalidations best-effort, не журнал доставки и не durable event replay.

Браузер получает только ticketPublicId, известный reason и emittedAt в событии
general_tickets_changed; raw tuple/IDs и содержимое диалога не выдаются. Отдельная
GENERAL очередь coalesce-ит обновления, перечитывает список/открытую карточку через
текущие READ API, а reconnect исправляет пропущенные invalidations новым чтением.
Heartbeat повторно проверяет grant/account/flags; при отказе посылается только
general_access_revoked без ticket data, emitter закрывается. UI очищает данные
при отзыве/ошибке stream, отменяет pending refresh и не применяет старые HTTP
ответы. Pagehide закрывает подписку. Сам этап R134 не добавлял migrations/flags.
GENERAL triage/OPERATE, text reply, close/reopen, intervals, assignment, creation, Telegram text edit/delete и documents описаны в R135–R142 выше;
остальные GENERAL write, отдельные bell/внешняя доставка и live Redis/proxy/provider acceptance PENDING.

Основные legacy dialog reads (список/summary, карточка, история, предыдущие
диалоги, участники и client context), ticket-bound `DialogAuthorizationService`
и HTTP downloads `AttachmentService` исключают GENERAL независимо от flags
очереди. `hasUniqueNonGeneralTicket` требует единственного raw-ID кандидата
во всей таблице и явного `is_general=FALSE`. Сначала отфильтровать GENERAL,
а затем считать уникальность нельзя: mixed-scope collision остаётся отказом.
`business_id=NULL,is_general=FALSE` сохраняет legacy/unresolved поведение;
этот guard не проверяет business membership и не заменяет strict scope.
Внутренний `hasUniqueTicket` сохраняет проверку identity для ботов/background.
READ grant GENERAL не открывает старые маршруты и write actions.

Legacy ticket-bound realtime использует тот же non-GENERAL guard: проверка
выполняется до dispatch из UI outbox, до уведомлений/назначений/AI в
`DialogRealtimeEventService`, до публикации и повторно перед доставкой SSE.
Адресный Redis fanout не обходит проверку; `dialogs_changed` и
`dialog_history_changed` требуют точного строкового ticket ID, а любой иной
envelope с `ticketId` тоже проверяется по canonical ticket. Неизвестный marker,
отсутствующая схема, GENERAL и коллизия закрывают legacy delivery. Курсор
outbox продвигается и по отклонённым событиям. Отдельный realtime GENERAL R134
использует описанный выше собственный контракт с действующим READ grant. Системные SSE без
ticket ID сохраняют поведение.

Legacy ticket-bound SSE дополнительно проверяет business READ каждого
получателя непосредственно перед каждой отправкой в emitter, включая
несколько соединений одного пользователя. Локальная доставка и адресный/
broadcast Redis fanout применяют один и тот же guard. Один SQL snapshot
требует точного unique raw-ID, явного non-GENERAL owner и текущего business
READ по общему с notifications predicate: единственный enabled/unblocked
canonical account, active business, membership или ALL_BUSINESSES с READ.
Доверять полям scope/business из envelope нельзя; ROLE_ADMIN не даёт grant.
Отзыв прав не требует переподключения; отказ пропускает ticket event, сохраняя
соединение для остальных разрешённых событий. Ошибка/отсутствие access schema
закрывает ticket delivery. UNRESOLVED с доказуемым public locator совместим
при default-off strict-read; при strict-read-scope=true скрывается.

Перед ticket-bound fanout фиксируется внутренний UiEventTicketScope: точный
canonical tuple, non-null immutable public ID, BUSINESS/UNRESOLVED и business ID.
Snapshot читается одним SQL statement с unique raw-ID/non-GENERAL proof и
передаётся отдельно от browser payload в Redis envelope либо local fallback.
Перед каждым emitter один SQL snapshot сверяет tuple/public ID/scope/business
и текущий READ получателя. Смена owner, пересоздание tuple с другим public ID,
смена business/scope, удаление или raw-ID collision запрещают старую delivery
даже получателю с ALL_BUSINESSES. Consumer не перезахватывает текущий owner
для старого payload. Необязательное поле ticketScope сохраняет чтение старых
system envelopes; ticket events без snapshot, с некорректным snapshot либо
другим payload.ticketId отклоняются. Наличие ticketScope само делает событие
ticket-bound и не позволяет переименованием события обойти guard.

Ticket с NULL/invalid public ID либо отсутствующая locator schema не позволяют
публиковать ticket SSE, включая legacy/UNRESOLVED при strict-read=false:
одного composite tuple недостаточно, чтобы отличить его пересоздание.
Автоматического присвоения public ID/backfill нет. Browser event names/payload
сохраняются; внутренний owner в них не добавляется. Сам transport-only этап
не добавляет migrations/flags.

Persistent DB outbox получает additive V61 snapshot при исходном INSERT:
точный существующий ticket_id, ticket_user_id, nullable ticket_public_id,
ticket_scope_kind и ticket_business_id. Panel и bot writers копируют owner
одним INSERT SELECT с проверкой уникальности raw ID; GENERAL сохраняется
отдельным scope, не превращается в UNRESOLVED. Исторические/старые producer
rows остаются без snapshot; автоматического backfill нет. CHECK запрещает
частичный owner, trigger — изменение snapshot, включая raw ID и присвоение
owner старой строке. FK намеренно отсутствует, snapshot переживает owner.
Readiness требует четыре новые columns; отсутствие V61 не позволяет объявить
runtime READY. Сам этот source-пакет не запускает migration.

Watcher до initial-message lookup и legacy callback сверяет frozen tuple,
public ID, kind/business и текущую unique non-GENERAL identity одним SQL.
GENERAL, старые unclassified rows, NULL locator и устаревший owner закрыты;
typed UNRESOLVED с locator совместим только до strict-read. Курсор проходит
отклонённые события. Initial-message suppression сравнивает полный snapshot,
поэтому события другой generation не подавляют bell нового обращения.
Проверка перед dispatch сама по себе не блокирует owner на время callback.

Bell/SSE ветви callback принимают именно snapshot V61: scoped entrypoints
DialogRealtimeEventService передают его через AlertQueueService до условного
notification INSERT и через UiEventStreamService до fanout/local delivery.
Scope не захватывается заново по raw ID. Notification SQL и recipient child
reads требуют совпадения tuple/public ID/kind/business, non-GENERAL, unique ID;
действующий business READ каждого получателя R122/R123 сохраняется. Пустой или
невалидный expected scope закрывает scoped API, а не включает legacy fallback.
Scoped UNRESOLVED разрешён только до strict-read. Поздний отказ queue может
попробовать fallback recipients лишь с тем же frozen scope и тем же SQL guard.
Scoped incoming callback передаёт исходный V61 owner через отдельные
processScopedIncomingClientMessage facade/flow и notifyScopedOperatorsEscalation
до NotificationService.notifyScopedAiDialogUsers. Условный INSERT проверяет
frozen owner и текущий READ каждого получателя R122/R126 в том же SQL snapshot;
поздняя смена owner/grants не открывает raw fallback, recapture или повторный send.

Это безопасная эскалация оператору; полноценный business-scoped AI ещё PENDING.
V62 добавляет отдельные ticket_ai_scoped_control/state/events с обязательным
immutable tuple/public ID/kind/business. Ключ control/state — public generation
и business (NULL имеет отдельный generated key); новая generation или другой
business не наследует прежние записи. Events immutable, legacy AI tables не
изменяются и не переносятся. Снимок создаётся только условным SQL из current
unique non-GENERAL owner, active business и исходного V61 expected scope.
UNRESOLVED automation совместим только до strict, actor business access из
него не возникает. Отсутствие schema/flags и конфликт snapshot закрывают путь.

Scoped incoming при глобальном ai_agent_enabled=true атомарно записывает
non-processing state escalate_only и event operator_handoff_requested без
текста клиента/media; это запрос handoff, не доказательство доставки. Затем
может уведомить оператора bounded preview исходного текста. ai_disabled из
scoped control проверяется и в state/event statement, и в финальном AI bell
INSERT. auto_reply_blocked сохраняется, не препятствуя human handoff. Отказ
после записи handoff не переименовывает event в доставленный и не повторяет send.
Actor reads state/control/events требуют live READ до ORDER/LIMIT; control
mutation требует live OPERATE (OPERATOR/MANAGER/ADMIN через membership либо
ALL_BUSINESSES) в том же SQL. VIEWER только читает, ROLE_ADMIN без business grant
не даёт доступа. Account enabled/unblocked, unique canonical identity и active
business обязательны; отзыв grants действует на следующую SQL-операцию.
R133 открывает эти primitives через отдельную BUSINESS-страницу обращения;
raw controllers и legacy controls по-прежнему не используют scoped records.

Legacy state/control rows не имеют canonical owner; raw retrieval использует
MAX(messages.business) и общие knowledge/memory rows. Scoped путь не вызывает
raw media normalization, state/control/event writes, retrieval, LLM, memory
learning или auto-reply; attachment не загружается и его storage key не включается
в уведомление.
Глобальное ai_agent_enabled=false оставляет эту AI-ветвь без эффектов.
auto_reply/assist_only/escalate_only не дают scoped пути доступ к raw pipeline.
Per-dialog legacy AI control также не читается/не изменяется этим путём.
UNRESOLVED сохраняет compatibility handoff/notification только до strict;
GENERAL, NULL/invalid scope закрыты. Legacy direct callers без expected scope
пока сохраняют свой контракт и требуют отдельного аудита.

V63 вводит отдельные ai_scoped_sources/events для явно созданных business-owned
KNOWLEDGE и MEMORY. Business обязателен и immutable; identity источника —
business/kind/exact key, origin MEMORY — immutable полный BUSINESS ticket snapshot.
KNOWLEDGE не зависит от наличия обращения. Legacy memory/knowledge/link rows,
aliases и global/unclassified sources не копируются и не используются scoped API.
Global-source policy этого этапа — DENY: даже ALL_BUSINESSES не расширяет retrieval
одного обращения до других бизнесов или общих источников. Разрешение sharing/
global sources требует отдельного контракта, а не пустого business или text alias.

ScopedAiSourceService создаёт только DRAFT; явный revise с expected revision
выполняет review/approval/archive и атомарно пишет metadata audit без title/body.
Любая mutation требует текущего CONFIGURE (business ADMIN через membership либо
ALL_BUSINESSES), unique enabled/unblocked account и active business внутри SQL.
MANAGER не имеет CONFIGURE по текущей матрице. READ библиотеки/history использует
тот же live business guard. MEMORY при создании, изменении и чтении требует
действующего origin tuple/public ID/business, unique raw ID/non-GENERAL; смена
origin скрывает запись, не переопределяя её владельца. Новый origin не наследует
source identity; для нового MEMORY в той же business scope нужен отдельный key.

AiRetrievalService.retrieveScoped — отдельный actor READ API без legacy intent/
context/scoring. Только APPROVED текущего BUSINESS попадают в SQL до ORDER/LIMIT;
GENERAL/UNRESOLVED/invalid scope закрыты независимо от strict compatibility
human handoff R130. Проверяется текущий request owner и scoped ai_disabled.
Источник возвращается отдельным immutable Source snapshot с revision/origin;
isCurrentCandidate повторно проверяет полный payload, version, request/origin,
READ/control перед будущим handoff. Это statement-time proof, не network lock.
Scoped result всегда autoReplyAllowed=false; raw Candidate/policy не используются.
R133 подключает к incoming отдельный trusted-worker поиск approved references
для human handoff, описанный ниже. Actor API retrieveScoped сохраняет отдельный
контракт. Автоматического learning/legacy sync нет.
R132 добавляет server-rendered каталог /business-ai-sources и editor/review:
foundation+ui-selector gates, явный один business без aggregate/default-all,
PAGE_KNOWLEDGE_BASE на каждом GET/POST и текущий READ внутри data SQL. Список
доступных business names также требует enabled/unblocked unique canonical account
и live READ. Catalog возвращает summaries без body/origin; literal q, kind/status
и current MEMORY origin фильтруются до id-keyset ORDER/LIMIT (25 строк на странице).
Private HTML получает Cache-Control: no-store; titles/body/keys экранируются
Thymeleaf, raw origin/user ID не передаются в editor model. Навигация использует
existing PermissionService и flags; авторитетным page gate остаётся server @PreAuthorize.

POST create создаёт только KNOWLEDGE DRAFT с новым opaque key формы; переданный
client status/kind/actor не определяет тип, одобрение или автора записи. POST revise
требует текущего CONFIGURE и CSRF стандартного SecurityFilterChain. В final UPDATE
reviseExpected совпадают immutable source ID, business/kind/exact key и revision;
старую форму нельзя применить к другому business или пересозданному key с той же
revision, даже при ALL_BUSINESSES. Отзыв account/grants после controller precheck
закрывает SQL mutation. Existing R131 revise API сохранён для внутренних callers.
Draft save — первая/default кнопка; approval/archive требуют отдельного действия.
Конфликт identity/version/доступа либо ошибка atomic audit дают 409 без overwrite.

R133 добавляет /business-dialog-ai/{publicId}, доступную из карточки BUSINESS
обращения. Foundation/uiSelector, PAGE_DIALOGS и current business READ обязательны;
GENERAL/UNRESOLVED/коллизии не открывают страницу. Public locator разрешается
сервером, raw ticket/user IDs не передаются в model. Private HTML no-store,
Thymeleaf экранирует текст. Управление требует live OPERATE и стандартного CSRF.
Старая форма доказывает business и expected updated_at в final mutation;
отсутствующая запись создаётся только ON CONFLICT DO NOTHING. Existing control
меняется только CAS с полным current owner и monotonic updated_at; audit atomic.
Конфликт владельца, прав, версии либо ошибка audit дают 409 без overwrite.

Создание MEMORY требует дополнительно PAGE_KNOWLEDGE_BASE и live CONFIGURE.
Оператор выбирает текстовый вопрос клиента и последующий ответ человека из
existing composite-owned history; NULL-owner/unlinked, AI, deleted и attachment
rows исключены. Показаны последние 50 сообщений, preview до 1000 символов.
Пара ID и SHA-256 полного текста, current root tuple/public generation/business,
аккаунт и grants повторно доказываются в одном INSERT snapshot. Подготовленный
вручную текст и явное подтверждение удаления PII/secrets обязательны; автоматической
PII-проверки нет. Создаётся только MEMORY DRAFT, затем требуется отдельный review
в каталоге R132. Новые metadata evidence хранят ID пары и fingerprints, не raw
диалог. Source, metadata audit и evidence атомарны; повтор key не создаёт дубль.
Это не backfill и не доказательство исторической business/generation принадлежности
всех chat_history rows: используется существующая привязка user_id/ticket_id,
а полная child attribution и перенос старых данных остаются отдельным gate.
Правка history после создания не переписывает curated source или его provenance.

При BUSINESS incoming глобальный ai_agent_enabled и scoped ai_disabled сохраняются.
Trusted worker использует frozen V61 scope и current owner/control в едином SQL:
literal case-insensitive substring до 500 символов, только APPROVED того же business
с current MEMORY origin, LIMIT 3 после фильтров. Query/body/title не копируются
в event/evidence. State escalate_only, event и ссылки source ID/revision записываются
атомарно; отсутствие совпадений не отменяет handoff. UI показывает summaries
последнего handoff только при PAGE_KNOWLEDGE_BASE и live READ; повторно проверяются
request owner/control, точная source revision/approval/origin. Отзыв прав, archive,
новая revision или смена owner скрывают ссылку без raw/global fallback. Evidence
не означает доставку оператору и не разрешает автоответ. Для UNRESOLVED остаётся
прежний compatibility handoff до strict, без поиска источников.

Полноценный scoped AI требует scope до media/LLM handoff и client delivery,
privacy/retention, более точного ranking и policy источников для автоответа. Они
PENDING вместе с producer context до INSERT и global page/system policy. V62–V64 —
source migrations, проверяемые в Testcontainers; V64 добавляет только отдельные
immutable-update evidence tables, без классификации старых rows. Рабочая DB/backfill
не выполняются; новых flags/rollout нет.

Auto-assignment из scoped ticket_created callback передаёт исходный V61
snapshot в DialogResponsibilityService.assignScopedResponsibleIfMissing.
Условный INSERT и последующее чтение existing/new responsible каждый
требуют полного expected tuple/public ID/kind/business, unique raw ID и
non-GENERAL marker в своём SQL snapshot. NULL/invalid scope либо отсутствие
flags/access schema закрывают scoped путь без raw fallback. BUSINESS INSERT
также требует текущего OPERATE у предложенного ответственного: единственный
enabled/unblocked canonical account, active business и active membership
либо ALL_BUSINESSES с OPERATOR/MANAGER/ADMIN, согласно initial capability
matrix BusinessAccessService. VIEWER, чужой business и ROLE_ADMIN этого не дают.
Existing responsible не переназначается; прежние trim/actor fallback/ai_agent
ограничения сохранены. Typed UNRESOLVED с locator совместим только до strict.
Отказ назначения не подменяет независимый recipient READ для bell/SSE.
Это statement-time proof, не блокировка owner на всё исполнение callback и
не attribution/backfill старых child rows. Раннее business-scoped построение
candidate pool/load balancing, остальные raw assignment writers и миграция
child identity ещё PENDING; отказ не запускает выбор другого кандидата.

R157 закрывает ранний BUSINESS candidate pool для scoped ticket_created callback,
не все assignment writers. ScopedAssignmentCandidateService одним SQL snapshot
сверяет frozen user/raw/public/business/kind, unique raw ID, non-GENERAL, canonical
channel и его active state. BUSINESS требует foundation flag; schema/owner mismatch
не открывают global fallback. Текущие enabled/unblocked/однозначные canonical
accounts с OPERATOR/MANAGER/ADMIN membership либо ALL_BUSINESSES отбираются до
single/pool/department strategy. VIEWER, чужой business, ROLE_ADMIN без business
OPERATE и ai_agent не входят. Это тот же internal OPERATE contract R125; назначение
не выдаёт page authority, membership или recipient READ.

Для least_loaded читается число открытых assigned roots только текущего business,
с unique raw ID и non-GENERAL; closed/resolved строки исключаются по прежней status
семантике, NULL status считается open. Глобальный loadDialogs не вызывается.
Это текущая canonical привязка ticket_responsibles, не backfill/доказательство
исторической business/generation attribution старых child rows.
Round-robin namespace разделяет canonical business/channel и route/pool; scoped
ветка использует полный long cursor. Глобальные queue/notification routing calls
не продвигают этот business counter.
AlertQueue использует resolveRecipients: тот же список получателей без расчёта
assignee, global workload reads и любых assignment cursor side effects.

Метаданные directory запрашиваются только для допущенных usernames; пустой набор
никогда не означает всех пользователей. Scoped directory читает только canonical
migrated users/roles, без optional-column metadata других schemas и без legacy
users.role fallback. Hash/least_loaded/round_robin выбирают
только отфильтрованный pool. Итоговый условный INSERT R125 по-прежнему проверяет
current owner/OPERATE; это не lock на всё время callback. Поздний отказ не запускает
автоматический подбор следующего кандидата. Existing responsible не заменяется.
Notification READ остаётся независимым: READ-only получатель не исключается из
bell/SSE только из-за отсутствия OPERATE. UI/global directory catalog не менялся.
Typed UNRESOLVED делегирует прежней стратегии только после exact root/channel
proof до strict; GENERAL и invalid scope не попадают в BUSINESS routing.
SLA/escalation/manual/raw assignment writers, child attribution, внешние chat
destinations и live Redis/fleet acceptance остаются отдельными gates. Новых SQL
migrations/flags/рабочих изменений или rollout R157 не выполняет.

R158: background SLA использует отдельный typed source. Frozen canonical
user/raw/public/business/kind/channel, responsibility_revision и точный stored
responsible читаются тем же SQL, что и SLA-атрибуты. Нет позднего разрешения owner
по raw ID; message user/channel mismatch и ambiguous root исключаются.
BUSINESS candidate pool использует R157 current OPERATE перед стратегией;
skills/queues/max-open проверяются по business pool и workload. Round-robin
разделён по canonical business/channel/route/pool и использует полный long.
Старые routing preview API остаются read-only, не authority для назначения.

Финальный ScopedSlaAssignmentService открывает отдельную транзакцию, берёт
legacy-ID advisory guard и root/channel locks, повторяет frozen owner/generation,
active channel, open/non-GENERAL/unique root, current OPERATE и CAS по exact
previous responsible + revision. V77 trigger увеличивает root revision на
assignment insert/change/delete, включая legacy writers; last_read_at сам по
себе её не меняет. Поэтому A→B→A и delete/insert не оживляют устаревшее решение.
Назначение и новый append-only scoped_sla_assignment_events с frozen provenance
commit вместе; отказ и rollback не создают raw success audit. Повтор того же
снимка не назначает повторно. Upgrade V76→V77 оставляет прежние root/assignment
поля без изменений, новая revision=0; это не historical attribution/backfill.

GENERAL не допускается; typed UNRESOLVED — только после current exact proof до
strict. Поздний отказ не запускает global/raw fallback или подбор следующего.
Current grant проверяется SQL на момент записи, не блокируется на всё время
внешнего workflow; max-open остаётся snapshot policy, не резервированием лимита.
Raw/manual writers в целом не переведены на scoped API; порядок их locks,
старые child rows, внешний SLA webhook destination/recipient READ policy,
scoped UI/read и retention нового audit, live/fleet acceptance остаются gates. Frozen контекст
не сериализуется во внешний decision payload. V77 — source-only migration с
readiness probe, без рабочего migration/flags/rollout.

R159: отдельный BUSINESS manual assignment workbench по public ID, ссылка из
scoped карточки. Нужны PAGE_DIALOGS + current canonical READ/OPERATE + CSRF.
Полный expected user/raw/public/business и responsibility_revision формы
проверяются с текущим корнем и повторно SQL перед записью; raw ID непрозрачен
и не trim'ится. Инициатор и target — unique enabled/unblocked accounts с business
OPERATOR/MANAGER/ADMIN membership либо ALL_BUSINESSES OPERATE; ROLE_ADMIN сам
по себе не обходит эту проверку. Candidate pool доступен только OPERATE;
READ-only видит проекцию и последние 10 новых событий, но не список кандидатов.
Назначение само не выдаёт page authority, membership или READ.

Take всегда назначает аутентифицированного actor, не submitted target. Assign
использует canonical numeric user ID; foreign/VIEWER/disabled/blocked/duplicate/
ai_agent не допускаются. Root lock + advisory legacy-ID guard + общая V77
revision/CAS не дают stale/ABA или SLA/manual решению перезаписать другое.
Accounts/business и qualifying memberships/ALL grants удерживаются FOR SHARE;
после блокировки grants финальный SQL заново проверяет права. Закрытый root,
GENERAL/UNRESOLVED, collision, owner/generation mismatch, no-op дают отказ.
Канал может отсутствовать: это локальная операция, не доставка клиенту.

Projection ticket_responsibles, immutable frozen audit V78 с actor/assignee IDs
и снимками имён, а также business_assignment_changed outbox commit атомарно.
Failure любого шага откатывает всё. Новый callback лишь инвалидирует metadata
по сохранённому BUSINESS owner через existing current READ SSE/Redis path:
без позднего raw capture, bell, очереди, AI, участников и support-chat/provider.
Смена owner/generation не открывает старую историю; new audit читается с exact
owner + current READ до limit. Старый responsible показан как legacy-проекция,
не доказательство historical attribution. V77→V78 сохраняет все старые root/
assignment поля и не создаёт прошлых событий.

Новый экран и ссылка скрываются при недоступном scope, старый URL очищается
при смене карточки/ошибке. Страница имеет no-store, POST требует CSRF и проверяет
full owner/revision формы. Старые raw take/reassign/reply/bot APIs не переводятся
на новый workflow этим пакетом. Участники/AI control, снятие ответственного,
историческая attribution, retention, старые writers и external recipient policy
остаются отдельными gates. V78/readiness — source-only, без рабочего rollout.

R160: bare-ID take/reassign и implicit assignment допускаются только для одного
текущего UNRESOLVED/non-GENERAL root до strictReadScope. BUSINESS использует
scoped R159, GENERAL — собственный workflow. Capability не является authority:
writer повторяет owner/public/channel/revision/namespace/open proof под общим
advisory и root lock; zero-row/SQL failure не запускает success cleanup/AI/
уведомления. Callback выполняется под root lock и общим panel transaction manager,
совместимым с JPA-уведомлениями. Прежний V61 scoped callback берёт тот же advisory
lock перед root/child, сохраняя final owner/current OPERATE SQL. Raw take/reassign
audit не пишется в BUSINESS/GENERAL или при strict. Reply/edit/delete/reopen/bot
relay больше не назначают scoped root через общий raw writer; сами legacy
delivery/lifecycle/participant/read APIs и historical attribution этим не закрыты.
Закрытый legacy root может вернуть существующего ответственного без новой записи;
redirect запрещён. Прежний bot relay contract сохранён.
Новая SQL migration не добавлена; source minimum V78.

В R160 compatibility IDs/actors и legacy redirect quirks сохранены; read marks не
меняются. Успешный workflow callback и назначение откатываются вместе при ошибке;
уведомления/SSE legacy остаются best-effort, без новой scoped outbox гарантии.
Assignment boundary не приписывает прошлым child rows business/generation и не
заменяет авторизацию остальных legacy операций. Все 956 Java / 20 XML GREEN
суммарно: 13 классов broad v3 и 7 классов / 320 tests corrected v4.
Новые миграции/рабочий rollout отсутствуют.

R161: узкий пакет одного legacy writer — markDialogAsRead. Bare-ID read mark
пишется только в UNRESOLVED/non-GENERAL до strictReadScope, для точного stored
responsible. При strict ранний lookup не выполняется; UPDATE повторяет namespace,
unique root и получает актуальное strict значение при формировании аргументов.
BUSINESS/GENERAL, collision, missing root/schema не меняют last_read_at. Расчёт
MAX(chat_history.timestamp) / CURRENT_TIMESTAMP, точные ID, actor matching,
assignment fields и прежняя обработка закрытых legacy roots сохранены.
Это не новый scoped BUSINESS/GENERAL read-mark API, не исправление всей legacy
read authorization и не historical business/generation attribution child rows.
Source minimum остаётся V78; SQL migration и рабочие изменения отсутствуют.

Проверки R161: 169 Java / 3 XML GREEN (24 новых PostgreSQL boundary,
139 прежних responsibility snapshots и 6 service tests), без failures/errors/skips.
Это один legacy write path; остальные raw read/write gates сохраняются.

R162: узкий пакет одного audit writer. Raw participants_add/participants_remove
получили ту же UNRESOLVED/non-GENERAL/unique-root границу INSERT, что take/reassign.
Для всех четырёх action codes strict проверяется до lookup и повторно после него.
Поздний business/GENERAL/collision/missing proof не добавляет новый raw audit,
включая result=success/error/not_found. Исторические строки остаются без изменений;
opaque ticket ID, case/trim/default actor/result/detail и timestamp сохранены.
Другие audit actions, successful-action lookup и telemetry сохраняют прежний
контракт. Это не current READ для raw audit и не business/generation attribution.
Операции с участниками, их final writer/recipient policy этим не закрыты;
strict resampling не сериализует in-memory flag после второй проверки.
Минимальная source-схема V78; новых migrations и рабочих изменений нет.

R162 проверен изолированно: 176 Java / 3 XML GREEN, без failures/errors/skips;
50 новых boundary cases, 117 прежних audit snapshots и 9 service tests.
Scoped participant audit/workflow, historical attribution и live acceptance
остаются отдельными gates; рабочие Docker/проект находятся на другой машине.

R163 закрывает обе bare-ID participant mutation SQL: addParticipant и
removeParticipant допускают только один текущий UNRESOLVED/non-GENERAL root
до strictReadScope. INSERT и DELETE повторяют business_id IS NULL,
is_general=FALSE и count всех raw-ID roots=1. Strict проверяется до guard/JDBC
и повторно при формировании SQL arguments; BUSINESS/GENERAL запрещены независимо
от foundation flag. NULL marker/неизвестная namespace schema/child schema дают
отказ без legacy SQL fallback. Успешный ранний unique proof не заменяет final SQL.

Сохранены opaque IDs без trim, username/added_by normalization, timestamps,
duplicate/no-op/catch semantics и отсутствие directory требования для legacy
mutation. Старые SQL rows и соседний trim-ID не переписываются. Новый scoped
BUSINESS/GENERAL participant UI/API не добавлен; recipient/current READ policy,
generation freezing/attribution и остальные legacy paths остаются gates.
In-memory strict после формирования arguments не блокируется до commit; это
тот же ограниченный sampling contract R161/R162, не serializable flag lock.

Baseline R163 отдельно воспроизводит stale R118 GENERAL-test: он ожидал bare-ID
назначение GENERAL, уже запрещённое R160. В новой проверке актуальные canonical
root fields заполнены, обе participant mutations и assignment отвергаются,
старые child rows сверены без изменений. UNRESOLVED golden assertions сохранены.
Source minimum R163 — V78; новой migration/backfill в R163 нет.
Текущее размещение runtime и разрешение restart приведены в актуальной сводке,
не в истории R160–R162.

### Новый BUSINESS roster участников — R164

V79 создаёт business_ticket_participants и append-only
business_ticket_participant_events. Только новые явные действия; никаких
INSERT SELECT из legacy participants, классификации или перепривязки старых
данных. Каждая связь/история содержит ticket_user_id, непреобразованный ticket_id,
ticket_public_id, business_id, member_user_id и actor_user_id со снимками имён.
Числовой users.id — идентичность участника; смена имени не переносит участие
новому аккаунту. UPDATE roster identity запрещён; event UPDATE/DELETE запрещены.
Удаление root не каскадирует аудит на другую генерацию.

GET/POST на странице business-dialog-assignment используют public locator,
PAGE_DIALOGS, no-store, CSRF и сверку full owner. Actor — текущий OPERATE
активного бизнеса и открытого обращения; member — текущий уникальный enabled/
unblocked READ этого бизнеса, включая VIEWER и ALL_BUSINESSES VIEWER. AI_AGENT
не допускается. Это не grant и не system-admin bypass. Удалять из roster можно
и участника с отозванным доступом, отключённым или удалённым аккаунтом.
READ history/list не требует OPERATE; candidate/forms недоступны read-only actor.

Независимая ревизия roster = max event revision данного полного owner (начало 0).
Она не меняет responsibility_revision. Shared advisory/raw-ID lock → root
FOR UPDATE → account/business/grants locks → повторная проверка current rights/
revision → roster и immutable event → frozen metadata outbox, всё в одной JDBC
транзакции. Предел 200 active members. Конкурентные/старые/ABA формы и no-op
отвергаются, ошибки event/outbox откатывают все записи; legacy callbacks,
assignment/AI и отправка клиенту не вызываются. Flags проверяются до входа и
после lock; это не serializable lock in-memory конфигурации.

Scoped BUSINESS recipient discovery использует full-owner roster и numeric
account join с current READ; raw ticket_participants и empty→all fallback для
этого entrypoint отсутствуют. Финальный notification INSERT повторяет full owner,
current READ и существование numeric roster/member username, чтобы удаление
участника/rename/username reuse до INSERT не наследовали старую доставку.
Ранее созданное уведомление удалением из roster не отменяется. Responsible/active
и explicit/channel recipient workflows сохраняют отдельную политику; их historical
attribution и внешние destinations остаются gates. UNRESOLVED legacy policy
не меняется; GENERAL participant workflow этим пакетом не добавлен.

business_participants_changed использует frozen outbox owner и current READ
для metadata-only dialogs_changed; нет raw recapture, bell, queue, AI или provider
callbacks при mutation. Source minimum V79, readiness проверяет обе таблицы.
Рабочая migration/flags/rollout не выполнены и требуют отдельного этапа.

Карточка маркирует старые workspace participants как legacy-проекцию, не новый
roster, и не утверждает historical attribution по одному unique raw ID.
Ссылка управления ответственным/участниками ведёт на отдельный scoped экран.
R164 проверен 567 Java tests / 15 XML (302 regression + 265 final) и 18 Node
tests; это sandbox evidence, не рабочая приёмка. Предыдущий статус S2B не меняется.

### Отдельный GENERAL roster участников — R165

V80 создаёт general_ticket_participants и immutable append-only
general_ticket_participant_events. Только новые явные add/remove; нет
классификации/копирования старых participants, BUSINESS roster или выдачи grants.
Идентичность GENERAL — exact ticket_user_id/ticket_id/ticket_public_id при
текущих is_general=TRUE/business_id IS NULL и unique raw ID. Участник и actor
фиксируются по numeric users.id со снимками имён; UPDATE roster identity и
UPDATE/DELETE event запрещены. Root/account deletion не каскадирует историю.

Страница general-dialog-work/{publicId} требует PAGE_DIALOGS/current GENERAL
READ, mutation также GENERAL OPERATE и pending/open. MEMBER достаточно READ,
включая read-only сотрудника; уникальная enabled/unblocked non-AI учётная запись.
ROLE_ADMIN и ALL_BUSINESSES не заменяют GENERAL grants, участие не выдаёт прав.
Формы передают CSRF/work revision/list revision/member ID, но не raw owner.
Сервер разрешает полный owner из public locator; V65 work revision отвергает
старую форму после смены статуса или владельца. Principal не trim-ится:
строка с добавленными пробелами не заимствует grant другой учётной записи.

Ревизия списка — max immutable event revision полного owner (начало 0), отдельно
от assignment/work revision. Shared advisory/raw-ID lock → root FOR UPDATE →
actor account/READ/OPERATE locks и target account/READ locks для ADD → final
INSERT SELECT/DELETE с повтором owner/unique namespace/current rights/обеих
revisions. ADD проверяет target READ и 200-bound; REMOVE не требует member READ,
чтобы отозванного/отключённого/удалённого участника можно было убрать.
Roster/event/frozen outbox коммитятся одной JDBC transaction; stale/ABA/no-op/
конкурентная форма отвергается без success, ошибка audit/outbox откатывает всё.
Flags foundation/generalTicketQueue проверяются до SQL и после lock, не являются
serializable lock конфигурации. Status/work intervals/assignment/AI не меняются.

participants_changed разрешён только как metadata reason отдельного GENERAL
realtime: frozen GENERAL outbox не проходит legacy callbacks, bell, provider,
AI или BUSINESS fanout. На browser — только public ID/reason/time. Сохранена
прежняя аудитория всех current GENERAL READ connections, а не roster-only;
членство не ограничивает чтение и не определяет новую bell/client policy.
Redis uses existing separate GENERAL topic; consumer/emitter повторяет current
owner и current READ. Старые consumers, не знающие reason, требуют совместимого
выпуска до включения потока. Source minimum V80, readiness проверяет обе таблицы.
Рабочие migration/backfill/flags/deploy не выполнялись; S2B остаётся PENDING.

R165: 526 Java / 8 XML и 2 GENERAL Node tests GREEN изолированно. Проверены
реальные grant/root lock waits и повторная current proof, final SQL injections,
CSRF/CAS/rollback/200-bound и сохранность legacy/BUSINESS/grant rows V79→V80.

### Граница bare-ID lifecycle и категорий — R166

DialogTicketLifecycleService не предоставляет authority для BUSINESS/GENERAL.
Resolve/reopen/categories и activity/feedback followups допускаются только для
текущего unique UNRESOLVED: business_id IS NULL, is_general=FALSE, strict выключен.
NULL marker/недоступная namespace schema дают отказ, ID не trim-ится.
Предварительная уникальность не заменяет namespace: все 13 существующих SQL
повторяют unique raw root и UNRESOLVED predicate; strict заново читается при
binding каждого statement. До входа и после eligibility probe strict также
проверяется. Reopen assignment callback требует повторной eligibility;
сам assignment writer сохраняет собственный R160 full-owner lock/proof.
Root status UPDATE проверяет namespace непосредственно в обновляемой строке,
не только во вложенном SELECT: после ожидания concurrent root lock PostgreSQL
повторно проверяет committed BUSINESS/GENERAL. Это покрывается четырьмя
реальными lock-wait cases, но не делает дочернюю цепочку транзакционной.

Quick actions после lifecycle повторяют допуск до success AI/notification/SSE/
provider callbacks; категории — до и после void writer, spam — до глобального
blockClient и после категорий. Ранний namespace/strict отказ не вызывает
блокировку или success callbacks. Это не транзакция с blacklist: если namespace
меняется после уже разрешённой блокировки, она не откатывается. Глобальные
client blacklist и внешняя recipient policy требуют отдельного решения.

Bare-ID audit quick_close/resolve/reopen/categories/mark_spam включён в набор
защищённых mutation actions R160/R162: strict до/после unique lookup,
UNRESOLVED/unique predicate в INSERT. Это sampled strict, не SQL/config lock.
Unrelated action/telemetry и read lookup audit не получают нового namespace
контракта. Не объявляется полная business/generation attribution старого audit.

Legacy нормализация status/categories/actor, timestamps/counters и best-effort
семантика followups сохраняются; новый общий transaction/rollback не вводится.
Перестановка/пересоздание UNRESOLVED root (ABA), частичный lifecycle и смена
флага после binding остаются gates. Проверки не замораживают user/public/channel
generation на всю цепочку и не сериализуют in-memory flags.

GENERAL manual lifecycle R137 остаётся отдельным scoped workflow. Для BUSINESS
нужны новые close/reopen/categories с полным owner/current OPERATE/CAS и atomic
audit/outbox: raw endpoint не должен временно заменять их даже для ROLE_ADMIN.
R166 source-only, source minimum V80 без новой migration; рабочие DB/flags/
restart/deploy не менялись. Общий статус 01-278 — 🟡.
Golden R139 V68→V69 сохранён; новые nullable/default поля V79 не смешиваются
с утратой старых значений при сравнении SELECT *. Это не live acceptance.

### Ручной BUSINESS lifecycle и категории — R167

V81 добавляет в canonical tickets business_work_revision и business_categories
(JSON array, default []). Это новые категории текущего полного owner, не импорт
и не fallback из raw ticket_categories. Старые поля/категории/назначения,
GENERAL и UNRESOLVED не классифицируются. Новый immutable
business_ticket_work_events хранит full user/raw/public/business, revision,
action, from/to status, previous/current categories и numeric actor со snapshot.
История не следует за другим business/tuple/generation; нет каскадного удаления.

Локальный BUSINESS workflow на существующем business-dialog-assignment требует
PAGE_DIALOGS, current READ и OPERATOR/MANAGER/ADMIN membership либо ALL_BUSINESSES
OPERATE активного бизнеса. ROLE_ADMIN, VIEWER и GENERAL grants не заменяют OPERATE.
Principal не trim-ится к чужому имени; account должен быть unique case-insensitive,
enabled/unblocked/non-AI. Полный posted owner, exact status и work revision
сверяются с current public lookup и повторяются в final UPDATE. GET не смешивает
work/assignment views разных owners между последовательными lookups.
POST с CSRF; чужой scope — 404, READ-only — 403, stale/no-op/invalid state — 409.
HTML CSRF denial сохраняет общий redirect /error/403; page/OPERATE denial — 403.
Это прежняя security policy, не изменение общих обработчиков.

Из pending/open закрытие переводит в resolved и требует 1–50 новых категорий;
из closed/resolved повторное открытие переводит в pending, сохраняя категории.
Сохранение/очистка категорий — только pending/open, no-op отвергается.
Категории trim/dedup, case-sensitive, до 160 Unicode code points каждая;
null/control/oversize и более 50 входных строк не принимаются.
Формы используют строки textarea, категории/history экранируются Thymeleaf.
Root counters/resolved_at/by/last_reopen_at изменяются только соответствующей
командой. Новое открытие не назначает ответственного автоматически.

Общий raw-ID advisory → root FOR UPDATE → actor/business FOR SHARE → qualifying
membership/ALL-grant FOR SHARE → final current rights/full owner/unique raw ID/
revision/status/categories/channel UPDATE. Root trigger увеличивает work revision
при статусе/категориях/owner/channel, включая внешние SQL writers; прямой revision
override запрещён. Статус/category ABA не оживляют старую форму. Assignment и
roster revisions не меняются. При смене full owner новая category projection
сбрасывается в [], не наследуется другим scope; значения, записанные этим
workflow, сохраняются в immutable истории исходного owner. Произвольный direct
SQL сам обязан обеспечивать свой audit; trigger не создаёт историю за него.

Root/categories, immutable event и frozen business_work_changed outbox атомарны
в отдельной JDBC transaction. Ошибка любого шага, недоступный revision trigger,
поздний owner/grant отказ либо неподтверждённый final state откатывают изменения.
Metadata-only watcher/current READ SSE не вызывает legacy lifecycle/feedback,
activity/spans, assignment/participants, AI, bell или client/support provider.
Flags foundation/uiSelector проверяются до/внутри/после mutation; in-memory
конфигурация не блокируется до commit. Это не полный внешний delivery workflow.

Source minimum V81; readiness проверяет новые root поля и event schema.
Рабочая migration/backfill/flags/restart/deploy не выполнены. Отдельными gates
остаются policy смены business и reconciliation/retention, оставшиеся global/legacy
read/analytics/export projections (новые BUSINESS пути закрыты R168), scoped feedback/providers/AI,
global blacklist, historical attribution legacy children и live acceptance.
Общий статус 01-278 остаётся 🟡.

### Canonical BUSINESS категории в чтении, поиске, экспорте и сводке — R168

R168: canonical категории V81 проведены через BUSINESS list/page/public card,
вложенные ticket snapshots history/answers/workspace/spans и JSON export.
Workspace больше не читает raw ticket_categories: categories всегда относятся
к текущему canonical root, даже при коллизии raw ID; relationVerified продолжает
защищать только оставшиеся legacy responsible/participants. Старые rows не
переписываются. Фильтр category — точное case-sensitive значение до 160 Unicode
code points, без trim/LIKE/JSON эвристик; blank/control/oversize дают 400.
List/page/export используют один SQL и одинаковые status/ticket/q/category
фильтры; q дополнительно ищет literal case-insensitive substring в canonical
категориях. Полные массивы категорий входят в JSON, порядок V81 сохраняется.
Новый private /api/business-scope/tickets/categories даёт отдельные counts
по business ID/category, один ticket на category, без объединения одинаковых
business labels. Более 1000 групп — 413, не частичная аналитика. Все новые
чтения повторяют active business/current membership либо ALL_BUSINESSES grant
в SQL и не открывают GENERAL/UNRESOLVED. UI показывает категории в списке и
карточке, общий фильтр экспорта и сводку; textContent и generation guards
защищают markup и поздние ответы после переключения контекста.
Source minimum остаётся V81; новой migration/flags нет. Scoped feedback/client
delivery, blacklist, остальные writers/legacy projections, S2B и live acceptance
не закрываются этим read-пакетом.

PAGE_DIALOGS/foundation сохраняются. Чтение категорий не требует OPERATE и
не выдаёт дополнительных прав. Optional category одинаков для list/page/export;
пустое либо некорректное значение не превращается в отсутствие фильтра.
Сводка имеет собственную границу 1000 business/category групп; не зависит от
фильтров списка и не является global analytics. UI JSON export сохраняет
полные category arrays и прежнюю границу 1000 tickets; category summary может
вернуть 413 независимо от размера ticket export. Историческая принадлежность
прочих legacy children и live provider delivery этим не подтверждаются.

### BUSINESS roster и immutable history READ — R169

R169: отдельный full-owner BUSINESS roster READ и paged immutable history
через /api/business-scope/tickets/{publicId}/participants[/history]. Один SQL
связывает user/raw/public/business текущего root со строками V79; canonical
данные читаются при raw collision и closed/resolved без OPERATE. PAGE_DIALOGS,
foundation и current unique enabled/unblocked READ активного бизнеса обязательны;
GENERAL/UNRESOLVED, чужой scope и неоднозначный public locator закрыты.
Principal lower-case без trim; бизнес не берётся из browser context или raw ID.
200 members либо явный 413; история newest-first, по 1–100 событий, strict
before-revision cursor и limit+1/hasMore. Default cursor допускает Long.MAX_VALUE.
Owner IDs, member/actor IDs, revisions/cursors передаются JSON strings без
потери BIGINT в браузере. Reader и member eligibility проверяются внутри SQL
по прежнему current READ predicate; VIEWER допустим, участие не выдаёт доступ.
Numeric member ID сохраняет snapshot при rename/delete/reuse имени; недоступный
member остаётся видимым с eligible=false, история не меняет account/grants.
Недоступная V79 schema даёт 503, а не пустой список или legacy fallback.
Main card показывает новый roster/history с pagination и textContent, проверкой
полного owner и равенства revision ответов; stale owner/revision/generation,
смена бизнеса и отказ доступа очищают панель. Cursor остаётся строкой.
Workspace больше не читает raw ticket_participants; compatibility participants
возвращается [] и не выдаётся за новый список. Старые rows не импортируются и
не меняются; remaining relationVerified/legacy projection относится только к
ответственному. Write/notification/provider workflows не менялись.
Source minimum остаётся V81, без новой migration/feature flags. Историческая
attribution legacy responsible/active/spans и external recipient policy,
S2B, scoped feedback/delivery и рабочая приёмка остаются gates.

Новые reads не вызывают write service и не строят owner через предварительный
raw lookup. Roster и current revision, history page и current revision получены
каждые одним statement snapshot; UI не смешивает ответы разных revisions.
Удаление участника не отзывает его business membership; READ определяется
только действующими account/grants. Наличие roster не меняет ответственного,
уведомления, status/work revision и права. Snapshot имён — audit, не authority.
Legacy compatibility workspace participants=[]; raw таблица не переписывается.
GENERAL roster API и остальные legacy read paths этим не объявляются закрытыми.

### BUSINESS manual/SLA assignment history READ — R170

R170: единый read-only BUSINESS audit ручных V78 и SLA V77 назначений через
/api/business-scope/tickets/{publicId}/assignments/history. Один SQL повторяет
current unique enabled/unblocked READ активного бизнеса (membership либо
ALL_BUSINESSES с допустимой ролью), foundation и PAGE_DIALOGS на HTTP-границе.
Principal lower-case без trim. GENERAL/UNRESOLVED и ambiguous public locator
не допускаются. Каждое событие связано с текущим полным user/raw/public/business;
raw collision и closed/resolved не мешают чтению подтверждённого audit.
События newest-first, по 1–100 на страницу; strict before-revision cursor,
limit+1/hasMore, default cursor включает Long.MAX_VALUE. Duplicate revision
между источниками в странице/дополнительной строке даёт 503: tied event не
пропускается курсором. Нет fallback на raw audit или молчаливой пустоты при
недоступной canonical schema. Success JSON private no-store/nosniff.
Owner IDs, numeric manual assignee/actor IDs, revisions и cursors — JSON strings.
SLA имена остаются историческими snapshots, numeric IDs для них не выводятся
по текущему username. SLA source/route и previous_responsible не выбираются
и не публикуются. История не устанавливает текущего responsible и не выдаёт
участникам либо назначенному новые grants.
Main card: отдельная раскрываемая история ручных/SLA назначений, textContent,
постраничная загрузка без повторного клика. Full owner и две версии — текущая
responsibility_revision и latestEventRevision scoped audit — защищают смешение
страниц; stale owner/version/generation, смена карточки/бизнеса и отказ доступа
очищают панель. Neutral Ops UI r9 и native disclosure сохранены.
Source minimum остаётся V81: новой migration/flags нет. Writer/notification/
provider paths не менялись. Legacy responsible/active/spans attribution,
retention/reconciliation, external SLA destination policy, S2B, scoped feedback/
client delivery и рабочая приёмка остаются gates.

Один statement snapshot возвращает owner, root revision, последнюю scoped audit
revision и страницу обоих источников. Audit не предоставляет authority текущего
назначения; raw legacy snapshot отдельно и не импортируется. Retention не менялась.

### BUSINESS lifecycle/category history READ — R171

R171: paged immutable BUSINESS lifecycle/category history через новый
/api/business-scope/tickets/{publicId}/work/history и отдельную панель main card.
Читаются только V81 business_ticket_work_events текущего полного user/raw/public/
business. Отдельный READ service не вызывает writer и не использует raw child
tables; raw collision, closed/resolved допустимы. PAGE_DIALOGS/foundation на
HTTP-границе, current unique enabled/unblocked READ активного бизнеса внутри
одного SQL: membership либо ALL_BUSINESSES с допустимой ролью, без OPERATE.
Principal lower-case без trim; GENERAL/UNRESOLVED/foreign и ambiguous public
locator закрыты. История не предоставляет authority текущего статуса/автора
и не восстанавливает чужие права.
События close/reopen/categories содержат frozen actor ID/name, from/to status,
previous/current ordered category arrays и timestamp. Категории не подменяются
текущим root или raw ticket_categories; clear сохраняет пустой after-snapshot.
JSON snapshots читаются как массивы строк без scalar/number/null coercion.
Повреждённый snapshot либо недоступная canonical schema дают 503, не пустую
историю и не legacy fallback. Success JSON private no-store/nosniff.
Newest-first, 1–100 entries/page, strict before-revision cursor, limit+1/hasMore;
default cursor включает Long.MAX_VALUE. Owner/actor IDs и revisions/cursors —
JSON strings. Один statement snapshot возвращает owner, root work revision,
latest scoped event revision и страницу; gaps после owner/channel/status writes
не объявляются потерянными событиями. UI проверяет обе версии и полный owner,
очищает stale/denied panel и игнорирует late responses после смены контекста.
TextContent защищает frozen actor/categories; повторный click во время загрузки
не создаёт второй запрос. Native disclosure и Neutral Ops r9 сохранены.
Source minimum остаётся V81, новой migration/flags нет. Write/feedback/provider/
assignment/participant workflows не менялись. Историческая attribution legacy
responsible/active/spans, retention/reconciliation, external recipient policy,
scoped feedback/client delivery, S2B и live acceptance остаются gates.

V81 snapshots не переинтерпретируются при изменении текущих категорий или имени
автора. Пагинация не заменяет current scope proof; каждая страница повторяет READ.
Retention, immutable-table maintenance и рабочая приёмка остаются отдельными gates.

### Authenticated BUSINESS principal без trim aliases — R172 (исторический checkpoint)

R172: authenticated BUSINESS principal сохраняется при привязке к SQL без
trim: допускается прежний lower-case Locale.ROOT, пробелы/табуляция не удаляются.
Исправлены BusinessTicketAssignmentService, BusinessTicketParticipantService,
ScopedDialogAiWorkbenchService, ScopedDialogAiPersistenceService,
ScopedAiSourceService и ScopedAiHandoffKnowledgeService — семь bindings в шести
сервисах. Authenticated account key не является display name/recipient config.
Прежний trim позволял principal другого/отсутствующего account стать именем
действующего ADMIN/OPERATOR и пройти current READ/OPERATE/CONFIGURE чужого бизнеса.
Теперь find/candidates/recent, assignment take/assign, participant add/remove,
AI owner/messages/state/events/control/CAS, source catalog/body/history/create/
revise/approval/retrieval/current-candidate proof, curated MEMORY и handoff
evidence проверяют именно переданный principal с сохранением whitespace.
Literal spaced account не запрещён: его собственные numeric ID и текущие grants
проверяются отдельно от похожего имени без пробелов. Upper/mixed case прежнего
canonical account допустимы; current uniqueness/enabled/unblocked, business
active, membership/ALL_BUSINESSES и разница READ/OPERATE/CONFIGURE сохранены.
В присвоенных записях/audit остаются ID и имя фактического account, не его соседа.
Тексты, reasons, raw/public IDs, source keys, candidate/ai_agent правила, lock/CAS,
scope proof, retention и grant policy не менялись. Source minimum V81; новой SQL
migration/flags нет. Это исправление конкретных BUSINESS actor/reader paths,
не полный аудит остальных authentication/notification/realtime identity paths.
SSE/bell identity normalization, global/system/PermissionService policy,
external recipients, legacy child attribution, S2B и live acceptance ещё gates.

Case folding не заменяет проверку numeric account/grants. Для principal нельзя
использовать normalization свободного текста, alias display names или списка
получателей. Новый scope не меняет authentication/login/account creation policy;
остальные legacy/SSE/bell/global identity boundaries проверяются отдельно.

### Exact SSE и notification recipient identity — R173 (исторический checkpoint)

R173: точная identity получателя SSE и уведомлений сохраняет whitespace при
прежнем lower-case Locale.ROOT. Исправлены четыре production service:
UiEventStreamService (connect/map key/targeted delivery/unregister),
UiEventTicketAccessService (current recipient READ SQL), UiEventFanoutPublisher
(Redis targetUser, Locale.ROOT) и NotificationService (authenticated list/page/
cursor/count/read/read-all, canonical recipient sets/exclusions/DB usernames).
Отдельный account с пробелами получает только свои права и уведомления; имена
с leading/trailing/both spaces, tab/newline не становятся соседним account.
Canonical roster использует numeric member ID/current username и final roster
proof; exact recipient не подменяется похожим именем после SQL lookup.
Current unique enabled/unblocked account, active business, membership либо
ALL_BUSINESSES READ, frozen full owner, raw uniqueness, late revoke, strict
UNRESOLVED и GENERAL exclusion сохранены. Новых grant rules, migration, flags,
UI/JS/templates нет; source minimum V81. Системные broadcast/heartbeat и прежние
blank fallbacks не менялись. Свободные legacy recipient lists по-прежнему
разбираются отдельным splitIdentities; их responsible/active/spans attribution
и NotificationRoutingService (base/config/DB audience), global/system/
PermissionService policy остаются следующими gates.

Проверки R173: 422 Java tests / 20 свежих XML GREEN,
failures/errors/skips=0, финальный Maven exit 0. Evidence: 15 сохранённых GREEN
suites v2 (.tmp/r173-panel-v2.log) + 5 полных rerun suites v4
(.tmp/r173-panel-v4.log), с exact source/classes/XML proof.
До исправления production-файлы были byte/blob-equivalent baseline: новый v1
дал 10/10 RED cases (5 recipient READ и 5 bell reads), failures=10/errors=0/
skips=0. XML/log/source SHA сохранены в ignored .tmp/r173-baseline-red.json и
r173-baseline-*.xml; отрицательные assertions не ослаблены.
Новый SSE suite использует реальные connect/emitter buffers, local fallback,
queued broadcast/target, publisher JSON и реальный subscriber с mock Redis,
две похожие реальные учётные записи разных бизнесов и late revoke/disable/
block/archive/duplicate. Новый notification HTTP suite использует полный
Spring/JPA/Flyway и действующие Security/CSRF filters; читает list/page/count,
проверяет read/read-all и неизменность полных notification rows при отказе,
canonical roster/exclusion и положительный собственный spaced account.
Шесть старых fixture suites явно обновлены под новый exact-name contract:
положительные owner/grant проверки используют uppercase canonical identity без
добавленных пробелов; targeted fanout assertion сохраняет literal spaces;
добавлен явный whitespace denial для четырёх READ-ролей. Прежние frozen-owner,
GENERAL/UNRESOLVED, current grants, pagination, outbox/roster assertions сохранены.
В v2 были 2 failures: старое ожидание /dialogs/<id> вместо уже канонического
/dialogs?ticketId=<id> (URL builder не менялся), и прежнее склеивание spaced
recipients в UNRESOLVED fixture. URL assertion уточнён, case dedup сохранён и
добавлена проверка разных notification IDs для plain/spaced recipients;
UNRESOLVED fixture теперь проверяет три точных имени вместо двух aliases.
Кроме того, новый HTTP fixture оставлял свои root rows без messages, и следующий
shared Spring context корректно отказался READY (16+8 errors). Добавлена очистка
собственных notification/roster/root rows после assertions. Production readiness
не отключалась, READ/owner predicates не менялись. Все пять затронутых suites
повторены целиком; оставшиеся 15 GREEN suites и production classes неизменны.
Вспомогательный v3 повтор остановлен до test execution из-за EOL guard
fixture operator: raw SHA сравнивался после LF normalization. Operator v4
проверяет raw bytes до normalization; основной checkout не менялся.
Source/class/XML freshness и exact generated CSS baseline blobs сверены.
Node/browser checks=0 (JS/templates неизменны). Live Redis/proxy/provider и
рабочая functional acceptance не выполнялись; задача 01-278 остаётся 🟡.

### Exact notification-routing audience identity — R174

R174: NotificationRoutingService сохраняет точные account keys без trim,
с прежним lower-case Locale.ROOT. Base recipients, DB usernames, typed JSON
employeeUsernames/excludeUsernames и сопоставление portal activity больше не
сливают plain/spaced accounts. Это важно для employees_only, department_all,
department_except, base/base_plus_route и online_only_fallback_all: состав и
активность конкретного account не переносятся похожему имени. Enabled/blocked,
department lookup, case dedup, disabled route, пустое сообщение и fallback при
отсутствии online recipients сохраняют прежний контракт.
Array element — точное имя account; отделённый legacy scalar text со списком
по comma/semicolon/newline сохраняет прежний явный trim отдельных tokens.
Blank elements исключаются. Department/event/scope/route labels и content не
становятся account keys и сохраняют свою нормализацию. Save/load, wrapped/direct
payload, top-level settings apply и HTTP settings сохраняют typed array names.
Изменён только этот production service; NotificationService/SSE predicates,
grant rules, default audiences, source minimum V81 и migration chain неизменны.
UI/JS/templates и рабочие настройки не менялись. Global/system/task policy,
AlertQueueService, ChannelAssignmentRoutingService, ChannelTransportService,
IncidentRouteDeliveryService user/freeform targets, legacy responsible/active/
spans recipient attribution и PermissionService остаются отдельными gates.
Этот шаг не вводит новую business-авторизацию системных уведомлений.

Проверки R174: 117 Java tests / 11 свежих XML GREEN,
failures/errors/skips=0, Maven exit 0, .tmp/r174-panel-v2.log. До исправления
production source был exact baseline blob: отдельный v1 дал 5/5 RED cases с
неверным plain recipient вместо leading/trailing/both spaces/tab/newline.
Original XML/log/source/test SHA — в ignored .tmp/r174-baseline-report.xml,
r174-panel-v1.log и r174-baseline-red.json. Тот же diagnostic source SHA
неизменен в итоговом GREEN; assertions не ослаблялись.
Новый PostgreSQL suite проверяет canonical DB directories двух qualifiers,
base sets, typed selectors/exclusions, чужой department, online activity,
fallback/case dedup, save/load/apply, legacy scalar text, disabled/blocked/
deleted/moved accounts и missing schema. Новый full Spring/JPA/Flyway HTTP suite
проверяет settings POST/GET с PAGE_SETTINGS и CSRF, перечитывание сохранённой
конфигурации, dispatch в реальную notifications row, точный SSE refresh target,
чтение/read-all только адресатом и повторный current directory lookup.
Существующие fixtures и production readiness/grants не изменялись. Source/class/
XML freshness и exact generated CSS baseline blobs сверены; Node/browser=0,
так как JS/templates неизменны. Live Redis/proxy/providers, рабочая functional
acceptance и rollout не выполнялись. Общий статус 01-278 остаётся 🟡.

### Exact channel notification audience identity — R175

R175: AlertQueueService сохраняет точные account keys без trim при прежнем
lower-case Locale.ROOT в typed employeeUsernames/excludeUsernames, DB usernames,
online activity и передаче уже разрешённых assignment recipients. Канальный
селектор больше не подменяет spaced account похожим plain account другого
department/business и не переносит его online activity или исключение.
ChannelTransportService использует отдельный exact-key normalizer только для
notification arrays: panelNotifications.routing, direct panelNotifications и
legacy alertQueue сохраняются через API без обрезки account names, с case dedup
и исключением blank entries. Department labels, preview и времена сохраняют свою
нормализацию; event defaults, online fallback и disabled/blocked filters прежние.
Current READ/full frozen owner/raw uniqueness/GENERAL denial по-прежнему проверяет
NotificationService перед INSERT; новых grant rules или channel business policy нет.
Production scope — только AlertQueueService и notification arrays в
ChannelTransportService; source minimum V81, migration chain и UI/JS неизменны.

Не закрыты: assignment resolver/config/directory/final writer identity
(ChannelAssignmentRoutingService, assignment settings ChannelTransportService,
DialogParticipantService и DialogResponsibilityService), UI CSV parsing/round-trip,
IncidentRouteDeliveryService user/freeform targets, legacy responsible/active/spans
attribution и global/system/task/PermissionService policy. Проверка source также
подтвердила отдельный gate: ChannelApiController использует общий authenticated
доступ/CSRF, без PAGE_SETTINGS на CRUD methods; R175 не меняет эту policy.
Положительный exact recipient handoff не доказывает корректность upstream assignment.

Проверки R175: 200 Java tests / 10 свежих XML GREEN,
failures/errors/skips=0: 9 GREEN suites v3 (.tmp/r175-panel-v3.log) и полный
повтор HTTP suite v4 (.tmp/r175-panel-v4.log), итоговый Maven v4 exit 0.
Сохранённые source/class/XML hashes девяти suites совпадают с финальными.
До изменения оба
production source совпадали с baseline blobs: v1 воспроизвёл 5/5 failures для
leading/trailing/both spaces/tab/newline (errors/skips=0). Original XML и test SHA
сохранены в ignored .tmp/r175-baseline-report.xml и r175-baseline-red.json;
diagnostic source SHA неизменен в итоговом GREEN, assertions не ослаблялись.
Новый PostgreSQL suite проверяет DB department keys, typed selector/exclusion,
online activity и fallback, exact assignment handoff, три config shapes,
save/reload, current disabled/blocked/moved/deleted и frozen scope передачи.
Новый full Spring/JPA/Flyway HTTP suite проверяет channel PATCH, реальную
persisted config и dispatch до notifications row, точный mocked SSE target,
bell GET/read-all, foreign READ denial и late revoke/disable/block/archive/
duplicate/public-ID change/raw collision/GENERAL. Authentication и CSRF действуют.
Одна старая положительная AlertQueue fixture явно заменяет padded Sasha на
uppercase canonical SASHA; прежние assertions case dedup/получатели/preview
сохранены, а literal whitespace denial проверяет новый suite.
V2 остановился до тестов на двух лишних закрывающих скобках нового HTTP test;
v3 исправляет только эти скобки и повторяет все 10 suites. V2 log/exit/input hashes
сохранены. В v3 новый HTTP suite получил 3 fixture errors: immutable public ID,
unique ticket ID/channel и GENERAL provenance check. V4 меняет только подготовку
этих трёх состояний: valid DELETE/INSERT с новым public ID, raw collision с
другим/null channel и GENERAL с general_selection_source=MANUAL. Добавлены явные
проверки состояния БД; исходные delivery denial assertions сохранены. Production
triggers/constraints не отключались; v3 XML/source сохранены в ignored
r175-http-v3-report.xml и r175-http-v3.java. Все 18 HTTP tests повторены целиком.
Production classes скомпилированы в v2 с теми же source SHA и pinned class SHA;
классы девяти v3 GREEN suites совпадают побайтно с финальными, их XML неизменны.
Новые HTTP classes/XML свежие относительно v4.
Остальные выбранные suites неизменны. Source/class/XML freshness и exact generated
CSS baseline blobs сверены; Node/browser=0, поскольку JS/templates не менялись.
Live Redis/proxy/providers, рабочая functional acceptance и rollout не проверялись.
Общий статус 01-278 остаётся 🟡.

### Global channel administration: утверждённая политика — R184

Baseline R184 (2026-10-27): clean main, HEAD/origin/main/actual remote
5aa3d5e465ab0fb21b08b9459383fb793d872415. R183 проверен по parent/subject/exact
8 paths/normalized content SHA. Detached sandbox .tmp/01-278-r184. Рабочие
проект и Docker остаются на другой машине/в другой сессии. Здесь source и
изолированные Maven MVC/security/Thymeleaf fixtures; Docker, PostgreSQL, browser
и Node в R184 не запускались. Рабочие revision/DB/config/flags/providers не
проверялись и не менялись; нет restart/deploy/migration/backfill или запуска
рабочих ботов. Grant commit/push от 27 октября сохраняется до 2026-10-27
21:00 UTC (28 октября 00:00 +03); source apply и Git publisher разделены.

Принятое решение владельца: «Только системный администратор с правом страницы».
Канал, его token/credentials и общая конфигурация управляются как глобальный
ресурс: нужен явный ROLE_ADMIN вместе с PAGE_CHANNELS или PAGE_SETTINGS в
Authentication. Business ADMIN, ALL_BUSINESSES, ROLE_PORTAL_ADMIN без ROLE_ADMIN,
одна page authority и исторический username admin сами по себе не подходят.
Новые grants/roles не назначаются; делегирование бизнес-администраторам всех
привязанных бизнесов не реализуется. Общий/чужой business binding не расширяет
право управлять каналом. Этот ранее открытый policy gate закрыт source-only
реализацией, без подтверждения рабочего rollout.

ChannelManagementAccess содержит общий PreAuthorize expression и raw-authority
predicate для навигации. Защищены 14 маршрутов R181 channel CRUD/aliases,
credentials, test-message/bot-info/channel-notifications и дополнительно четыре
BotProcessApiController: start, stop, status, runtime-contract. Все 18 route/verb
combinations требуют роль и одно page right до transport/repository/lifecycle
вызова; write CSRF остаётся обязательным. /channels использует ту же политику.
ROLE_ADMIN + PAGE_CHANNELS без PAGE_SETTINGS остаётся на legacy channels/index;
ROLE_ADMIN + PAGE_SETTINGS открывает /channels как alias в /settings?open=channels.
Redirect больше не использует широкий PermissionService admin bypass вместо
явного PAGE_SETTINGS. Navbar учитывает canManageChannelsInSettings, поэтому
channel-only администратор сохраняет ссылку на доступную страницу. Settings tile,
channelsModal, addChannelModal и channelEditorModal отсутствуют в серверном HTML
для остальных пользователей; сама /settings и другие её разделы сохраняют
прежние page permissions. Внешний th:if wrapper используется до th:replace.

SQL/schema, ChannelTransportService, bot lifecycle orchestration/runner internals,
internal token/HMAC APIs, settings save API и JS не менялись. Не добавляется
публичный read-only channel catalog/status; sidebar без нужных прав сохраняет
catch/hide на 403. Остальные global settings/proxy/template permissions, live
login/session authority refresh/revoke, incident UI/upstream identity, legacy
attribution/global/system policy, S2B и рабочие S3–S6 gates остаются отдельными.
Проверяется authority snapshot текущей Authentication, не новый DB membership
resolver. Общий статус 01-278 — 🟡.

Validation R184: 393 Java tests / 10 свежих XML GREEN, Maven v3 exit 0,
failures/errors/skips=0. ChannelManagementAuthorityWebMvcTest расширен с 170 до
268 cases: 162 отказа для 9 actor/authority combinations × 18 routes,
36 разрешений по ROLE_ADMIN + одному из двух page rights, 52 missing/invalid
CSRF отказа для 13 writes и 18 anonymous redirects. Проверены exact delegation,
response и long ID >2^53; отказы не вызывают transport, repository, runtime role,
runner, process service или SSE publisher. Настоящие SecurityConfig/filters/
method security/CSRF; реальные процессы, provider calls и runner — mocks.
Новый ChannelManagementPageSecurityWebMvcTest — 13 cases с реальными navigation,
PermissionService и Thymeleaf: 5 отказов /channels, 5 settings pages без channel
controls, обе разрешённые entry combinations и отказ роли без page rights.
281 policy cases суммарно; прирост к прежнему suite — 111 cases. До production
edits те же два test source SHA на 9 baseline blobs дали в v2 281 tests /
146 failures / 135 passing controls / 0 errors/skips. V1 сохранил неверное ожидание
redirect вместо прежнего HTTP 403 для page denial; исправлен только новый test
на фактический error contract, без production edit. После v2 оба policy test
source не менялись. RED XML/log/test SHA и baseline blobs сохранены в ignored
r184-baseline-red.json. ManagementController regression сохраняет все assertions;
5 requests получили явно разрешённые policy actors/page authorities.
Остальные финальные suites: ChannelApiController 50, BotProcessApiController 27,
ManagementController 16, SettingsApi/Bridge 5+5, HomeController 2 и UI source
contracts 6+1. Старые functional MVC suites с mocks/без filters не подменяют
отдельную security проверку. Java cache R183 разрешён после normalized SHA
comparison всех Java sources; 8 production classes реально перекомпилированы.
Source/class/XML/log SHA и actual rendered classpath templates сверены. Maven
сохраняет resource timestamps: v2 verifier проверяет exact new-source bytes и
отличие от baseline, вместо неверного требования mtime после начала test run.
Остальные cache inputs, generated CSS baseline и diff --check проверены.
Browser/live DB/provider/runner/rolling-upgrade acceptance отсутствует; прежний
R53 Node debt в R184 не запускался.

### Incident backend exact identity — R183

Baseline R183 (2026-10-27): clean main, HEAD/origin/main/actual remote
ae325d000f43d367f2e0c6ad344e1e30c34986b7. R182 проверен по parent/subject/exact
6 paths/normalized content SHA. Detached sandbox .tmp/01-278-r183. Рабочие
проект и Docker находятся на другой машине/в другой сессии. Здесь source и
изолированные Maven service/MVC fixtures. Read-only docker version probe показал
недоступный local daemon; Docker не запускался, PostgreSQL/Testcontainers здесь
не выполнялись. Рабочие revision/DB/config/flags/providers неизвестны и не менялись;
restart/deploy/migration/backfill отсутствуют. Grant пользователя на commit/push
от 27 октября остаётся до 2026-10-27 21:00 UTC (28 октября 00:00 +03).
Source apply и отдельный expiry-guarded Git publisher разделены.

R183 сохраняет точные непустые account keys в backend инцидентов. IncidentService
не обрезает owner, watcher_identity, created_by/added_by/event actor; прежние
case folding, порядок/dedup и empty-input validation сохранены. Загруженные owner
и watchers передаются в NotificationRoutingService без trim. Add/remove watcher
различают literal " lead " и отдельный "lead". Исторические строки не переписываются.
Singular route_type=user сохраняет scalar target в inline routes при создании,
add/update route, JDBC binding outbox, JSON envelope, row mapping и вызове
NotificationService.notifyUser. В outbox также сохраняются exact owner и
requested_by; прежний регистр этих metadata не меняется. Нормализация остальных
route types и строковая users CSV-грамматика с split/trim/case folding/dedup
сохранены; department и all_operators используют прежние API. Webhook targets,
display text, route retries/claim/lease/monitoring policy и SQL predicates не менялись.
Это исправление identity strings, не новый business scope, recipient eligibility,
правила глобальных incidents или выдача grants. UI/JS по-прежнему имеют отдельный
trim/CSV debt: полный browser→API round-trip R183 не закрывает. Старая уже обрезанная
outbox/history metadata не восстанавливается по догадке. Foreign/shared channel
CRUD policy, остальные upstream/global picker/attribution, S2B и рабочие S3–S6
rollout/acceptance остаются открытыми. Общий статус задачи — 🟡.

Проверки R183: 88 Java tests / 7 свежих XML GREEN, Maven v3 exit 0,
failures/errors/skips=0. Новые IncidentIdentityBoundaryTest — 50 cases и
IncidentRouteIdentityBoundaryTest — 19. Проверены create/update/reload projection,
owner/watchers/audit/notification delegation, add/remove literal watcher,
inline/add/update singular user route, enqueue bindings + JSON → реальный outbox
row mapper/dispatch → реальный delivery service → captured NotificationService,
long string >2^53, whitespace/CRLF/tab, padded null, delimiter/markup keys,
blank/case/order/dedup и CSV/department/operator controls. Repository/notification
зависимости — mocks; CapturingJdbc сохраняет параметры и возвращает их реальному
row mapper, но не исполняет SQL. DB locking/transactions/claims, live authentication,
сохранение SQL/фактическая доставка или browser/provider acceptance не заявляются.
До production edit те же test source SHA на трёх exact baseline blobs дали
69 tests / 48 failures / 21 passing controls / 0 errors/skips в v2. Первый v1
выявил missing findByIdForUpdate stub нового fixture; добавлен только lock lookup,
assertions не ослаблялись. V1 log сохранён; final test source заморожен после v2.
RED XML/log/test SHA и baseline blobs записаны в ignored r183-baseline-red.json.
Финально повторены существующие IncidentService (3), IncidentApiController (3),
route delivery diagnostics (1), monitoring policy (8), ops escalation (4).
Java cache R182 использован только после normalized SHA comparison всех Java
sources; новые tests скомпилированы в baseline runs, все три production class
реально перекомпилированы в v3. XML свежие; source/class/report/log SHA, unchanged
cache inputs, generated CSS baseline и diff --check проверены. Node/browser не
запускались; прежний R53 Node debt и PostgreSQL suites здесь не проверялись.
Рабочей приёмки нет.

### Exact principal в global PermissionService — R182

Baseline R182 (2026-10-27): clean main, HEAD/origin/main/actual remote
ad2b0af6bba77487a563e999732860e09b2b79e4. R181 проверен по parent/subject/exact
8 paths/normalized content SHA. Detached sandbox .tmp/01-278-r182. Рабочие
проект и Docker находятся на другой машине/в другой сессии; здесь только source
и изолированные Maven unit/MVC fixtures. Docker/browser/Node не запускались;
рабочие revision/DB/config/flags/providers неизвестны и не менялись. Нет рабочих
restart/deploy/migration/backfill. Явный grant commit/push действует до конца
27 октября Europe/Moscow: 2026-10-27 21:00 UTC (28 октября 00:00 +03).
Source apply и отдельный expiry-guarded Git publisher разделены.

R182 устраняет подмену account key в PermissionService.isSuperUser: username
сравнивается с прежним admin без trim. Отдельные аккаунты " admin", "admin ",
" admin ", tab/CR/LF variants больше не получают global superuser bypass
через похожий логин. Это распространяется на isSuperUser, hasAuthority и
hasAnyRole. Прежняя case-insensitive совместимость точного unpadded admin,
явные ROLE_ADMIN/ROLE_PORTAL_ADMIN, собственные page authorities, нормализация
role vocabulary и пустой allow-list сохранены. Бизнес-роли/ALL_BUSINESSES tokens
не заменяют системную административную роль. Пользователи/роли/grants в БД,
authentication provider, business membership/SQL, API payloads, UI и flags не
менялись. Удаление самого исторического admin-name bypass и переработка global
role policy не входят в R182. Foreign/shared channel CRUD policy остаётся
отдельным открытым решением; R181 page gate сохраняется. Incident user targets,
остальные legacy/global picker/attribution, S2B и rollout/acceptance открыты.
Общий статус задачи — 🟡.

Проверки: 307 Java tests / 8 свежих XML GREEN, Maven v2 exit 0,
failures/errors/skips=0. Новые 55 cases: PermissionIdentityBoundaryTest (27) и
AdminStorageInventoryIdentityWebMvcTest (28). Второй использует настоящие
SecurityConfig, PermissionService, method security, фильтры и CSRF; inventory
service — mock, скрипт инвентаризации не выполняется. Без admin privileges
12 literal accounts с PAGE_SETTINGS и valid CSRF получают 403 без вызова service;
сохранены 9 allowed combinations (unpadded admin case variants и две роли),
4 отказа без PAGE_SETTINGS, 2 CSRF отказа и anonymous redirect. Unit tests проверяют
все три permission API, собственные права literal account, role/empty-list и
unauthenticated controls. До production edit те же test source SHA на exact
baseline PermissionService blob дали 55 tests / 21 failures / 34 passing controls /
0 errors/skips: 13 unit и 8 HTTP failures, где другой аккаунт допускался к inventory.
Исходные XML/log/test SHA сохранены в ignored r182-baseline-red.json и копиях
report; тесты после RED не менялись. Финально повторены прежние PermissionService
(2), DialogAuthorization (6), SettingsMacroTemplate (1), ManagementController (16),
ChannelManagementAuthority (170) и AuthManagementApiController (57). Некоторые
прежние suites используют permission mocks/отключённые фильтры; новая MVC проверка
отдельно доказывает реальную permission boundary, не live login или DB acceptance.
Java cache R181 использован только после normalized SHA comparison всех Java
sources; оба новых test class скомпилированы в v1, изменённый PermissionService
реально перекомпилирован в v2. XML свежие; source/class/report/log hashes,
неизменность остальных cache inputs, generated CSS baseline и diff --check
проверены. Прежний R53 Node debt не запускался. Рабочей приёмки нет.

### Channel management page authority — R181

R181 вводит явную page authority для ChannelApiController,
ChannelBotCredentialApiController, ChannelTelegramDiagnosticsApiController и
ChannelNotificationApiController: hasAnyAuthority('PAGE_CHANNELS','PAGE_SETTINGS').
Это существующие права двух управляющих страниц: ManagementController /channels
требует PAGE_CHANNELS, settings API/редактор — PAGE_SETTINGS. Наличие одного
из этих прав сохраняет прежние controller→transport вызовы и ответы. Роль
ROLE_ADMIN, права других страниц и business/ALL_BUSINESSES tokens сами по себе
не заменяют page authority. Клиентский business_id тоже не даёт её.
Барьер закрывает GET/POST /api/channels, PATCH/PUT/POST/DELETE /api/channels/{id},
оба public-id/regenerate alias, bot credentials GET/POST/DELETE, test-message,
bot-info и channel-notifications — 14 route/verb combinations. Отказ происходит
до вызова транспорта, в том числе до загрузки credentials и внешней диагностики.
Настоящий CSRF для всех write aliases остаётся обязательным; GET не требует token.
Полный channel catalog больше не доступен любому authenticated пользователю;
sidebar без этих page rights получает прежний catch/hide при 403. Отдельного
расширения READ scope или нового публичного status endpoint здесь нет.
ChannelTransportService, DB/SQL, payload normalization, IDs, grants/roles, UI/JS,
schema/flags и internal bot APIs не менялись. Это global administration page
boundary, не business scoping каналов: foreign/shared channel CRUD policy,
membership/owner predicates и правила глобальных администраторов — следующий
отдельный gate. Остальные legacy/global picker/attribution, incident targets,
S2B и рабочий rollout/acceptance также открыты. Статус 01-278 — 🟡.

Проверки R181: 246 Java tests / 5 свежих XML GREEN, Maven v2 exit 0,
failures/errors/skips=0. Новый ChannelManagementAuthorityWebMvcTest — 170 cases
с реальной SecurityConfig, фильтрами, method security и CSRF. Для 14 routes
проверены 84 запрета при valid CSRF без нужной page authority, 28 разрешений
по PAGE_CHANNELS или PAGE_SETTINGS с exact delegation/response и long ID >2^53,
44 missing/invalid CSRF отказа и 14 anonymous redirects. Отказы не вызывают
mock transport. Session principal задаётся Spring Security test; live auth/DB/
provider acceptance не заявляется. Существующие 50 ChannelApiControllerWebMvcTest
с отключёнными фильтрами остаются функциональной регрессией; новый suite отдельно
доказывает security boundary. ManagementController (16), SettingsApiController (5)
и SettingsBridgeController (5) повторены без изменений.
До production edits тот же test source SHA на exact baseline blobs дал
170 tests / 84 failures / 86 passing controls / 0 errors/skips: чужие права
получали HTTP 200 вместо 403. XML/log/test SHA и controller blobs сохранены
в ignored r181-baseline-report.xml, r181-panel-v1.log, r181-baseline-red.json.
Тесты после RED не менялись. Кэш Java R179 использован только после сравнения
нормализованных SHA всех Java sources с текущим baseline; новый test и четыре
controller class реально перекомпилированы, XML свежие. Source/class/XML/log SHA,
generated CSS baseline и diff --check проверены. Node/browser=0: UI/JS не менялись;
прежний R53 debt из R180 здесь не запускался. Рабочей приёмки нет.

### Exact typed channel notification UI — R180

R180 сохраняет точные typed notification usernames от parseQuestionsCfg через
picker до JSON POST и reload. Только channelEditorPanelNotifEmployees и
channelEditorPanelNotifExclude opt-in в data-employee-value-format="username-json".
Их textarea type/id остаются прежними; .value содержит JSON-массив строк,
а backend payload по-прежнему содержит массивы employeeUsernames/excludeUsernames.
Typed values сохраняются без trim при прежнем case folding; case dedup сохраняет
первое вхождение. Старая строковая CSV-грамматика channel parser и прочих полей
picker не менялась. Справочник /api/panel-users/options остаётся прежним global/
legacy source, без нового доступа или расширения native/scoped options.
Явно пустое поле больше не восстанавливает старый список через value || existing;
fallback применяется только при отсутствующем control. defaultValue восстанавливает
загруженный выбор при native reset; ошибка справочника не стирает существующий
список, отсутствующий literal key не подменяется похожим plain account.
Повреждённый JSON/non-string entry блокирует сохранение до POST с сообщением UI.
Непустые строковые ключи, включая >2^53, остаются строками; markup — textContent.
Исправлен и общий observer loop: added node, удалённый до доставки MutationRecord,
больше не считается внешним добавлением и не запускает бесконечный scan/refresh.
SQL/backend predicates, grants, schema/flags, CSV legacy fields и assignment
native selectors не менялись. Это исправление channel UI strings и picker
refresh, без нового authorization scope. Остальные legacy/global picker identity,
ChannelApiController CRUD authority/business policy, incident targets, legacy
attribution, S2B и рабочий rollout/acceptance остаются gates. Статус 01-278 — 🟡.

Проверки R180: 88/88 headless Edge DOM scenarios GREEN (52 notification,
36 assignment regression), 110/110 Node checks из 40 scripts GREEN, 5 node --check.
Реальный channel-editor.html и config/editor/shell/persistence/controls/picker
JS используются с fixture API; GET справочника и JSON POST перехватываются,
внешние URL блокируются. Проверены whitespace variants, comma/semicolon,
string ID >2^53, markup, оба списка, missing literal, directory failure, clear,
reset, disabled/absent control, case folding, malformed typed value без POST,
compatibility shapes, неизменённый legacy CSV, keyboard/Escape/Enter/focus.
Исходный baseline зависал после remote picker open: CDP Runtime.evaluate timeout
180s, Debugger в refresh/scan. После единственного observer fix тот же QA source
SHA дал 52 tests / 47 failures / 5 passing controls / 0 errors в v2. Остальные
четыре production inputs тогда оставались baseline blobs. Final notification
v4 и assignment v1 GREEN; diagnostic test после RED не менялся. Исходные
blobs/test/report SHA и timeout observation сохранены в ignored
r180-baseline-observer.json, r180-baseline-red.json и r180-browser-v2.json.
Полный Node run: 41 scripts / 111 checks, 1 старый R53 notes fixture failure.
Он отдельно воспроизведён на неизменённом main b92c1aa26; все 5 dependencies
совпадают в root/sandbox с baseline. R53 и код заметок не менялись; scoped Node
run повторил остальные 40 scripts без skip/cancel. Весь Node набор не GREEN.
Evidence: r180-node-v1.json/.log, r180-node-baseline.json/.log, r180-node-v2.json/.log.
Source/report/log SHA и diff --check проверены. Java=0, Docker=0; это не MVC/DB/
working-instance acceptance, backend R175/R177 здесь повторно не проверялся.

### Exact SLA preview and audit identities — R179

R179: SLA snapshot, escalation candidates и governance rule parser сохраняют
точные непустые account keys. Scalar assign_to, typed assign_to_pool, current/
previous responsible и upstream audit actor больше не обрезаются. Общий
package-local scalar helper exactAccountKey сохраняет исходный регистр, blank
и unpadded null sentinel обрабатываются как прежде; padded " null " остаётся
literal identity в соответствии с resolver R177. Fallback presence diagnostic
использует тот же контракт. Типизированный pool сохраняет прежние порядок и
case-sensitive dedup; строковая comma/newline CSV-грамматика не менялась.
Snapshot/candidate используют rawResponsible(), а не presentation accessor
responsible(): display name или прошлый resolvedBy не создают текущего назначения.
Snapshot больше не принимает literal ответственного за похожий plain account
и не скрывает корректное reassign-решение. Governance assignee_target совпадает
с буквальной целью фактического resolver. Notifier передаёт exact actor вместе
с frozen context в scoped writer; прежний sla_orchestrator fallback сохранён.
Rule owner/reviewer metadata, labels/matchers, UTC, приоритеты/конфликты, режимы
monitor/assist/autopilot, includeAssigned gates и settings schema не менялись.
Scoped eligibility, final writer/locks/CAS/atomic event, UI/JS, SQL/migrations/
grants/flags не менялись. Это исправление identity strings, без нового
authorization scope для preview и без подтверждения live DB/worker поведения.
Общий статус 01-278 остаётся 🟡.

Проверки R179: 201 Java tests / 56 свежих XML GREEN, Maven v3 exit 0,
failures/errors/skips=0. Новый SlaRoutingIdentityBoundaryTest: 50 cases —
single/typed-pool live resolver → snapshot/governance audit, exact current
responsible и reassign, escalation candidate payload/includeAssigned, notifier
→ captured scoped writer (actor и frozen owner), mode/writer gates, отсутствие
legacy writer/raw audit/webhook calls, display name/resolvedBy isolation, case/order/delimiter typed pool, legacy
CSV/labels и missing/blank/null fallback. Шесть literal variants включают
leading/trailing/both spaces, tab, newline и padded null. Endpoint/DB acceptance
этим набором не заявляется: writer и DB reader в notifier fixture — mocks.
Первый baseline v1 дал 46 tests / 32 failures / 0 errors. Затем добавлены
четыре raw-vs-presentation regressions без изменения прежних 46 cases.
До production edits окончательный test SHA на exact baseline blobs в v2 воспроизвёл
36 failures / 14 passing controls, errors/skips=0. Исходные XML/log/test SHA
сохранены в ignored r179-baseline-report.xml, r179-panel-v2.log и
r179-baseline-red.json; diagnostic test не изменялся после RED. Финальный v3
повторяет все Sla*Test и четыре service consumer suites, существующие тесты
не менялись. Source/class/XML/log SHA, generated CSS baseline и diff --check
проверены. Java-only scope: прежний R53 Node fixture debt из R178 не изменён
и в R179 не запускался; полной Node/browser/live acceptance нет.

### Exact channel assignment UI keys — R178

R178: typed assignment selectors в редакторе каналов сохраняют точные
непустые username без trim, с прежним case folding. Исправлены parseQuestionsCfg,
assignment catalog, single/pool rendering и сборка JSON payload. Typed arrays
отделены от прежней строковой CSV-грамматики; case dedup сохраняет первое
typed вхождение. Присутствующий пустой single select больше не подставляет
старый configured username; обязательность single/pool по-прежнему проверяет
существующий persistence runtime, без POST для отсутствующего выбора.
Options строятся DOM properties value/textContent вместо HTML-атрибутов:
CR/LF account key не нормализуется HTML parser, labels не исполняются как markup.
defaultSelected сохраняет native reset. Не добавляются отсутствующие в каталоге
accounts; похожее обрезанное имя не выбирается автоматически. Порядок selected
pool остаётся прежним порядком DOM options; нового управления порядком нет.
Общий employee-picker.js, id/name полей, runtime API, CSS/HTML, Java/backend,
SQL/migrations/grants и strategy/cursor semantics не менялись. Native picker
использует только свои options, без расширения глобальным справочником.

Граница R178 — channel assignment native selects. Notification CSV/typed-array
UI round-trip, прочие legacy/global pickers, SLA preview/audit parser и actor
label, ChannelApiController CRUD authority/business policy, incident user
targets и legacy attribution остаются отдельными gates. Refresh unsaved state
и управление порядком пула не переработаны. Общий статус 01-278 остаётся 🟡.

Проверки R178: 36/36 headless Chromium/Edge DOM scenarios GREEN,
95/95 Node checks из 38 regression scripts GREEN, 4 node --check GREEN.
Browser fixture использует актуальный channel-editor.html и реальные config/
shell/editor/persistence/controls/employee-picker runtimes; внешние зависимости
страницы заменены fixture, POST только перехватывается, внешние URL блокируются.
Пять whitespace variants проверены для single/pool restore → picker → captured
JSON POST → parse/reload и для отсутствующего target при наличии plain account.
Также проверены comma/semicolon keys, string ID >2^53, markup label, case dedup,
clear, disabled mode, legacy CSV, keyboard/Enter/Escape/focus и native form reset.
До production edits тот же QA source SHA дал 22 failures / 14 positive passes,
errors=0 в v4; baseline blobs/report/test SHA сохранены в ignored
r178-baseline-red.json и r178-browser-v4.json. Final browser report — v5;
проверки не ослаблялись после baseline, test SHA совпадает.

Полный Node run: 39 scripts/96 checks, 1 failure старого R53 notes fixture
(ожидает «Заметок нет.», получает error state). Сбой воспроизведён отдельно на
неизменённом main dd41dea3a; все 5 dependencies совпадают с baseline/root/sandbox.
Старый DOM stub не реализует replaceChildren текущего renderer. R53 и код
заметок не менялись. Точечный scoped run повторил остальные 38 scripts, без
skip/cancel; весь Node набор не объявляется GREEN. Evidence: r178-node-v1.log,
r178-node-baseline.json/.log и r178-node-v2.json/.log. Этот прежний test debt
нужен отдельным шагом. Source/report SHA, exact scope и diff --check проверены.
Java=0: серверные изменения R177 не менялись и здесь повторно не проверялись.
Это изолированные source-проверки, не приёмка рабочего экземпляра.

### Exact active assignment routing identity — R177

R177 закрывает обрезку account key в активном выборе ответственного:
ChannelTransportService сохраняет scalar/list assignment selectors без trim,
с Locale.ROOT case folding и case dedup отдельно от названий отделов.
Notification arrays используют тот же exact-key normalizer, их контракт R175
сохранён. ChannelAssignmentRoutingService читает single/pool keys и каталоги
без trim; scoped и общий assignable catalog DialogParticipantService возвращают
реальные username. DialogOperatorOption сохраняет этот ключ в compact constructor;
displayName/avatar/department/role по-прежнему нормализуются как labels. Legacy participant CRUD,
profile lookup и их прежний normalizeIdentity остаются отдельными путями.
Assignable catalog introspection привязана через to_regclass к реально используемой
users relation; одноимённая users.role другой схемы больше не ломает каталог.
ScopedAssignmentCandidateService больше не исключает реальный literal username
только за внешние пробелы: blank/ai_agent, unique enabled/unblocked account,
current OPERATE membership/ALL_BUSINESSES, active business/channel, frozen owner
и exact workload predicates сохраняются. Похожий plain account не даёт прав.
SlaEscalationAutoAssignService сохраняет single/fallback/typed pool identity,
operator skills/queues map keys и previous responsible без trim; identity map
lookup использует Locale.ROOT. Прежние blank и unpadded null sentinel сохранены;
labels, rule matching, typed-list grammar и порядок pool не менялись.
Финальные writers R176, locks/CAS/atomic event, namespaces и long cursor semantics
не менялись; новых migrations, grants, flags, JS/UI и внешних вызовов нет.
SettingsDialogSlaAiConfigService уже копирует typed значения без нормализации,
его исходник не менялся; round-trip проверен через SharedConfigService.

R177 не закрывает UI CSV/picker round-trip, SLA policy preview/audit parser,
upstream SLA actor label, ChannelApiController CRUD authority/business policy,
incident user targets, legacy responsible/active/spans attribution,
global/system/task/PermissionService и S2B/рабочий rollout. Общий статус 🟡.

Проверки R177: 494 Java tests / 15 свежих XML GREEN,
failures/errors/skips=0, Maven v4 exit 0. Новый PostgreSQL suite:
71 tests, полная Flyway V81-схема, два похожих реальных account с разными
business grants; отрицательные selectors и успешные сохранение → каталог →
resolver → финальный writer, real DB channel questions_cfg save/reload и
typed SLA settings file save/reload. Проверены пять whitespace variants,
single/pool/fallback, три стратегии и department queue, exact workload/capacity,
skills/queues isolation, exact previous identity и SLA reassignment/CAS event,
current revoke/disabled/blocked/duplicate/VIEWER/archive/foreign/channel/owner.
На baseline production blobs новый diagnostic method дал 25/25 failures,
errors/skips=0 (пять путей × пять вариантов). XML/log/test SHA и production
blobs сохранены в ignored .tmp/r177-baseline-red.json,
r177-baseline-report.xml и r177-panel-v1.log. Исходный test source SHA сохранён вместе с XML; diagnostic method/aliases
и account/grant/root fixtures неизменны в финальном GREEN.
Первый полный v2 (455 tests) обнаружил 27 failures только нового suite:
дополнительный trim в DialogOperatorOption после SQL-чтения. Исходный XML и
диагностика сохранены в r177-routing-v2-report.xml/r177-fix-v3.json; baseline
DTO blob проверен. V3 исправляет DTO, повторяет все 13 suites и добавляет
DialogQuickActionServiceTest и DialogWorkspaceWorkflowSnapshotServiceTest как
потребителей DTO. V3: 494 tests, 6 failures/1 error только нового suite. Пять
failures выявили metadata union чужих schemas (u.role), что исправлено внутри
assignable catalog; явный foreign-schema decoy теперь есть в каждом catalog test.
Две новые fixture assumptions исправлены: immutable public ID меняется только
через delete/recreate root, а create writer без channel key получает результат
resolver при disabled channel. SLA active-channel и остальные прямые writer
negatives сохранены; triggers/constraints не отключались. Исходные source/XML
и три точечных test transformations сохранены в r177-routing-before-v4.java,
r177-routing-v3-report.xml и r177-fix-v4.json. Diagnostic method/aliases и
account/grant/root setup побайтово прежние; в v4 все 15 suites повторены целиком.
Две прежние scoped fixtures обновлены по контракту: single mixed-case ALICE
без добавленного padding и previousResponsible с исходными внешними пробелами;
grant/root/frozen assertions сохранены, новые negatives не ослаблены.
Выбранные regression suites: ScopedAssignmentWriterIdentityPostgresTest (55), ScopedAssignmentCandidatePostgresTest (43), ScopedAssignmentRoutingServiceTest (23), ScopedSlaRoutingServiceTest (19), ChannelAssignmentRoutingServiceTest (3), SlaEscalationAutoAssignServiceTest (4), DialogParticipantSnapshotPostgresqlTest (73), DialogParticipantLegacyTicketGuardTest (3), LegacyParticipantWriteBoundaryPostgresTest (51), ScopedSlaAssignmentPostgresTest (26), ChannelApiControllerWebMvcTest (50), ChannelNotificationIdentityPostgresTest (34), DialogQuickActionServiceTest (35), DialogWorkspaceWorkflowSnapshotServiceTest (4).
Проверены source/class/XML SHA/freshness, exact file set, diff --check и
generated CSS baseline blobs. Node/browser=0: JS/UI не менялись. Это source
проверки, не функциональная приёмка рабочего экземпляра на другой машине.

### Exact final auto-assignment writer identity — R176

R176: два финальных auto-assignment writers сохраняют точный account key
и непустой actor без trim, при прежнем case-insensitive SQL lookup:
DialogResponsibilityService.assignScopedResponsibleIfMissing и
ScopedSlaAssignmentService.assign. Имя с leading/trailing/both spaces, tab или
newline не может воспользоваться OPERATE похожего обрезанного account. Реальный
literal account с собственным current OPERATE сохраняется без подмены в
ticket_responsibles и SLA audit; loadScopedResponsible возвращает точное
сохранённое имя существующего/concurrent ответственного. Blank target/actor
по-прежнему обрабатывается прежними fallback правилами; canonical ai_agent
исключён, прежние length limits SLA применяются к полному значению.
Не изменены SQL grant/owner predicates, enabled/blocked/unique identity, active
business, membership/ALL_BUSINESSES роли, advisory/root locking, CAS revision,
атомарный SLA event и typed pre-strict UNRESOLVED compatibility. Legacy bare-ID
assignment/loadResponsible/mark-as-read используют прежние отдельные пути.
Source minimum V81; новых migrations, flags, UI/JS или grants нет.

R176 закрывает именно конечные writers, а не всю цепочку назначения. Upstream
ChannelTransportService assignment config, ChannelAssignmentRoutingService,
DialogParticipantService directory, ScopedAssignmentCandidateService eligibility
(в том числе username=btrim(username)) и SlaEscalationAutoAssignService config/
skills/queues/previous identity ещё требуют согласованного exact-key прохода.
Уже обрезанное upstream имя final writer восстановить не может. Сохранены gates
UI CSV/picker round-trip, ChannelApiController CRUD authority/business policy,
incident user targets, legacy attribution, global/system/task/PermissionService,
S2B и рабочий rollout/acceptance; общий статус 01-278 остаётся 🟡.

Проверки R176: 451 Java tests / 9 свежих XML GREEN,
failures/errors/skips=0: 8 GREEN suites v2 (.tmp/r176-panel-v2.log) и полный
повтор ScopedSlaAssignmentPostgresTest v3 (.tmp/r176-panel-v3.log), финальный
Maven v3 exit 0. Source/class/XML сохранённых suites совпадают с финальными.
До исправления оба
production source были exact baseline blobs: новый v1 воспроизвёл 10/10 failures
(два writers × пять whitespace variants), errors/skips=0. Original XML/log/test
SHA и production blobs сохранены в ignored .tmp/r176-baseline-report.xml,
r176-panel-v1.log и r176-baseline-red.json. Diagnostic source SHA неизменен
в финальном GREEN; отрицательные assertions не ослаблены.
Новый suite использует полную Flyway V81 PostgreSQL-схему, два похожих реальных
account с разными business grants, положительную собственную literal identity,
три OPERATE роли через membership/ALL_BUSINESSES, late disable/block/duplicate/
VIEWER/revoke/archive/system-only/foreign, frozen owner replacement/tuple/GENERAL/
raw collision, exact stored/concurrent read, actor fallback и SLA redirect/CAS.
Отказ сверяет полные root/assignment/audit snapshots; успех — exact target/actor,
previous responsible, revision и event. Existing SLA suite дополнительно проверяет
реальный frozen reader, rollback audit failure, revision ABA и гонку workers.
В старом DialogScopedResponsibilityPostgresTest положительные grants/fallback
используют canonical mixed-case target без добавленного padding; stored и
concurrent return assertions теперь сохраняют уже существовавший literal padding.
Их DB/grant/root assertions сохранены; новые tests отдельно проверяют отсутствие
trim alias. V2 выявил один прежний upgrade comparator: V76→latest после V77
учитывал только responsibility_revision, но V81 уже добавляет два root поля.
Исходный test blob и неизменность migration chain подтверждены; failure/XML/source
сохранены в ignored r176-sla-upgrade-v2-report.xml, r176-sla-upgrade-v2.java и
r176-fix-v3.json. V3 добавляет явные assertions business_work_revision=0 и
business_categories=[], сохраняя exact comparison всех прежних root/assignment
fields, revision tracking и отсутствие SLA audit при legacy writes. Миграции
не менялись, assertions не удалялись, triggers/constraints не отключались.
Весь SLA suite (26 tests) повторён целиком. Остальные выбранные suites неизменны. Source/class/XML freshness,
exact changed-file set и generated CSS baseline blobs проверены. Node/browser=0
(UI/JS неизменны). Проверки не являются live provider/Redis/proxy/fleet или
функциональной приёмкой рабочего экземпляра на другой машине.

### Scoped auto-close и внешняя доставка

Scoped auto-close callback передаёт исходный V61 scope и channel ID события
в DialogNotificationService.notifyScopedSupportChat. Один SQL capture требует
полного expected owner, unique raw ID/non-GENERAL, совпадения canonical
ticket.channel_id с событием, активного канала и, для BUSINESS, active business.
Он читает immutable target из canonical channels, не из JPA-объекта callback:
token, support_chat_id, platform, platform_config, delivery_settings,
credential_id. После сборки HTTP request и client/network setup один повторный
SQL непосредственно перед HttpClient.send сверяет тот же owner/channel и все
поля target, включая NULL. Telegram/VK/MAX используют этот scoped guard;
VK/MAX scoped client учитывает IntegrationNetworkService, прежние raw paths
сохраняют свой transport contract. Detached channel copies не меняют frozen
target; ошибки конфигурации/отправки не открывают raw fallback и в scoped
логах не раскрывают токен через exception text. Target.toString не выводит секреты.
UNRESOLVED с locator допускается только до strict-read; GENERAL, invalid scope,
missing schema, неактивный либо неизвестный destination закрывают отправку.
Это proof последнего SQL перед handoff, не транзакционная блокировка HTTP,
не version registry настроек канала на момент исходного события и не проверка
прав участников внешнего чата. Inherited/global network profiles и credential
contents отдельно не версионированы. Действующие grants внешнему destination,
остальные raw support-chat producers и полноценные scoped AI effects ещё PENDING.
Уже корректно записанный bell не откатывается при позднем отказе support-chat.

Mixed-version consumers без нового guard
не доказывают эту изоляцию; отдельный rollout/preflight остаётся обязательным.

Ticket-bound bell delivery сохраняет точный `ticketId` через
`NotificationService.notifyDialogUsers` до условного SQL INSERT: snapshot
требует единственного owner и явного `is_general=FALSE`. Возвращаются только
реально записанные получатели; отказ не считается успешной рассылкой.
Recipient child reads повторяют тот же predicate, а поздний отказ не запускает
fallback на всех операторов. Legacy queue, realtime, AI escalation и
`OperatorNotificationWatcher` используют этот путь. GENERAL history/SLA rows
не обрабатываются этим watcher. Общие system notification methods сохраняют
свой контракт; их URL не является authority для ticket scope.

Additive V60 сохраняет у новых ticket-bound уведомлений immutable snapshot:
точный `(ticket_user_id, ticket_id)`, nullable `ticket_public_id`,
`ticket_scope_kind` и `ticket_business_id`. Snapshot получается из owner
в том же условном INSERT, а не из URL/текста. BUSINESS требует business ID,
UNRESOLVED и GENERAL — его отсутствия; частичный tuple запрещён CHECK.
Trigger запрещает изменение всех пяти полей, включая назначение owner старой
строке с NULL. Исторические и generic/system/task уведомления сохраняют NULL
без автоматической классификации и backfill. FK на изменяемое/удаляемое
обращение намеренно отсутствует: snapshot остаётся историческим фактом.

Legacy bell list/page/count/read/read-all применяют один SQL predicate:
получатель совпадает, canonical tuple/public ID и business соответствуют
snapshot, текущий owner имеет `is_general=FALSE`. Исчезновение owner, смена
scope/business или другого non-null public ID закрывают чтение и read update.
GENERAL исключён даже при отдельном READ grant; его bell ещё не реализован,
а realtime R134 изолирован отдельными endpoint/topic. Фильтрация предшествует cursor pagination/aggregation, read
updates условны в том же SQL и сигнализируют только фактическое изменение.
Коллизия bare ID после сохранения не подменяет owner с полным tuple; legacy
dialog URL при этом по-прежнему закрывает свой ambiguous route.

При default-off `strict-read-scope` сохраняется совместимость unclassified
NULL и подтверждённого non-GENERAL UNRESOLVED. В strict-режиме оба варианта
скрыты, включая generic/system/task rows, пока для них не введена отдельная
явная global/system policy. Для типизированных BUSINESS-уведомлений действующий
business READ проверяется одним SQL predicate при INSERT, list/page/count и
условных read/read-all. Он требует единственного case-insensitive canonical
аккаунта получателя, `enabled=TRUE`, `is_blocked=FALSE`, активного бизнеса и
активной membership либо `ALL_BUSINESSES` с одной из текущих READ-ролей
`VIEWER/OPERATOR/MANAGER/ADMIN`. Системный `ROLE_ADMIN` и отдельный GENERAL READ
не подменяют business grant. Архивация бизнеса, блокировка аккаунта и отзыв
всех независимых grants закрывают следующую SQL-операцию, включая старую сессию.
Фильтр действует до pagination/aggregation; read updates не меняют скрытые rows.
Новый future business сразу доступен по действующему `ALL_BUSINESSES`.

Предикат business READ разделяется JDBC writer, JPA native repository и SSE;
проверка ticket identity/owner остаётся обязательной. Наличие read capability
у всех четырёх текущих ролей соответствует `BusinessAccessService`; разрешения
не выводятся из текста/URL, чужой users DB либо browser-selected business.
UNRESOLVED/unclassified сохраняют описанную выше совместимость; этот фильтр
не классифицирует исторические строки. Глобальная page authority, явная
global/system/task policy, полноценный scoped AI pipeline, внешняя support-chat recipient policy, остальные assignment writers и прочие producers ещё
требуют отдельного контракта/аудита. Это не разрешение включать strict flag
или выполнять rollout.

Для канала только с общими обращениями `GENERAL_CHANNEL` включается отдельной
записью `general_channel_question_template_bindings`: активный канал должен
явно выбрать тот же ID вопросника, не иметь активных business bindings, а
сохранённый SHA-256 должен совпадать с текущим шаблоном. Изменение состава или
порядка вопросов требует повторного подтверждения привязки. На текущем этапе
general-only runtime отклоняет preset-вопросы, пока для них не определён
безопасный, не связанный с бизнесами каталог. Отсутствие бизнес-привязки само
по себе никогда не включает `GENERAL`.

Основной internal channel runtime endpoint при активной general-only привязке
выдаёт тот же проверенный вопросник и явные `scopeKind=GENERAL`,
`scopeSelectionSource=GENERAL_CHANNEL`, ID/SHA-256 шаблона. Устаревшая привязка
или выключенный флаг дают конфликт, а не legacy fallback. Bot session обязана
сохранять выбранный scope до создания обращения; старые ответы другого scope
не подставляются в general-only опрос. ID/SHA-256 передаются из сессии через
command/event, outbox и inbox в nullable immutable поля ticket. Новое обращение
`GENERAL_CHANNEL` принимается только при точном совпадении версии с проверенной
текущей привязкой; устаревший или неполный snapshot отклоняется до записи корня.
После создания replay сверяет сохранённую версию ticket, не текущую привязку.
Смена/удаление ID либо SHA-256 при повторном event запрещена. Старые обращения
не получают версию из сегодняшней конфигурации. Завершение старых незаконченных
сессий после rebind пока требует отдельного version registry/UX; без него
устаревший опрос нужно начать заново. Очередь и права GENERAL, shared-channel
selection и сквозная provider-приёмка всё ещё обязательны до rollout.

На S4 вводится business-scoped registry template bindings. Existing
`question_template_id`, `rating_template_id`, `auto_action_template_id` и
`questions_cfg` сохраняются как legacy transport/config fields. Новый template
для business создаётся clone с новым stable template ID в canonical
`bot_settings`; редактирование template B не может менять template A. Пока
JSON-owner и precedence не утверждены, S1 не меняет эти поля.

### R143: read-only handshake выбора BUSINESS в общем канале

GET /internal/api/bot/channels/{channelId}/questionnaire/choices требует
существующий internal token/HMAC guard и foundation + shared-channel-resolution.
Меню версии 1 содержит только ID/code/display name бизнесов канала и ID/SHA-256
их шаблонов, без вопросов/глобальных settings. Все активные привязки должны
быть EXPLICIT_SELECTION, business/channel/template — active, шаблон — distinct,
не legacy/general-only, без unsupported presets и duplicate question IDs.
Неполный/stale/mixed набор закрывает весь новый каталог. До 100 вариантов;
более крупный каталог требует отдельного pagination/UX-контракта.

GET .../questionnaire/selection принимает businessId и catalogSha256 исходного
меню. Любое изменение видимого состава/имени/template version требует нового
выбора; шаблон не захватывается молча из свежей привязки. Конфигурация выбранного
BUSINESS/USER_SELECTION получается из того же read snapshot, только с одним
вопросником и пустыми locationTree/presetDefinitions. Оба ответа no-store.
Старый business runtime endpoint сохраняет R112 semantics.

Основной unscoped runtime endpoint больше не выдаёт legacy settings каналу
с активными business bindings, даже при выключенных flags: нужен явный выбор.
Общий PanelRuntimeConfigClient не кэширует legacy/general runtime между
lookup и не возвращает local fallback при ошибке настроенного API. Новый SDK
choices/select проверяет channel/business/version/catalog hash, membership
выбора, точную форму settings и SHA-256 фактических bytes шаблона. Неизвестные
scope/версии, подмена payload или глобальные presets/settings закрыты.
Совместимость legacy runtime остаётся только для unbound channels; отключённый
Panel API/другие transport modes ещё требуют отдельного аудита.

R143 сам по себе — read-only handshake, не право создать ticket. Контракт
BUSINESS producer/INSERT/replay добавлен в R144 ниже; MAX selection UX — в R145,
VK — в R146, Telegram — в R147. GENERAL option общего канала, scoped locations и сквозная
приёмка resume/UX после rebind остаются PENDING. Новый shared-channel flow нельзя включать до
подключения совместимых consumers; существующий general-only flow сохранён.

### R144: создание BUSINESS по сохранённому выбору вопросника

QuestionnaireScope для BUSINESS содержит положительный businessId,
USER_SELECTION, stable template ID, SHA-256 шаблона и catalogSha256. Неполный
BUSINESS snapshot не десериализуется как legacy/GENERAL. Старый GENERAL snapshot
совместим с nullable новыми полями. Command и оба event DTO передают catalogSha256
без повторного lookup; Telegram/VK/MAX finalizers используют businessId и
отпечатки из scope сессии. Это подготовка finalizers, не подключение меню выбора.

Новый BUSINESS/USER_SELECTION INSERT требует foundation, strict ticket writes
и shared-channel-resolution. До записи корня backend проверяет platform канала,
полный актуальный каталог R143, membership выбора и точные template ID/SHA.
Изменение любого варианта меню, rebind или stale template требует начать выбор
заново; нельзя молча получить новую версию или вывести owner из display business.
Канал с активным EXPLICIT_SELECTION не принимает новый legacy/unresolved или
непроверенный GENERAL envelope, в том числе при выключенном strict flag.
SINGLE_BUSINESS сохраняет отдельную прежнюю проверку однозначной привязки.

V73 добавляет nullable immutable business_selection_source и
questionnaire_catalog_sha256 в tickets. CHECK допускает только полный
BUSINESS/USER_SELECTION с owner и версией шаблона; GENERAL поля не переиспользуются.
Исторические строки не получают сегодняшнюю конфигурацию. SQL trigger запрещает
смену/удаление новых полей; readiness требует новую схему до READY.

Outbox и inbox сверяют catalogSha256 наряду с owner/scope/template identity
до пропуска duplicate или reclaim. Replay существующего canonical tuple
сверяет frozen owner/source/template/catalog/channel, а не нынешние bindings;
новый event ID не позволяет потерять selection provenance. Отключение strict
flag также не превращает уже выбранный BUSINESS root в legacy replay.
При ошибке дочерней записи root/children откатываются, failed inbox допускает
точный повтор. Сами transport event IDs и display поля не выдают права доступа.

Подтверждены typed JSON/CAS scope и MAX state round-trip. Подключение MAX
pending choice/selection UX описано в R145, VK — в R146, Telegram — в R147.
GENERAL option общего канала требует отдельной явной настройки, а не ослабления
general-only binding. Нет новых scoped presets/locations, version registry для
старых незавершённых опросов или разрешения на flags/migration/rollout.

### R145: MAX — выбор до первого вопроса и восстановление сессии

MAX при отказе primary runtime может запросить только проверенный BUSINESS
каталог, не локальные/global settings. Для choices/selection с forStart=true
обязательны foundation + shared-channel-resolution + strict writes, internal
token/HMAC, no-store и X-Iguana-Questionnaire-Writer-Version: 1. SDK требует
явный ответ этой версии на обоих этапах: старый backend, игнорирующий query,
не считается готовым к созданию обращений. Read-only R143 API совместим.
Новый selection path работает только с RabbitMQ + настроенным Panel API и
активным channel; audit старых non-Rabbit/disabled-API modes остаётся PENDING.

До первого вопроса сохраняются каталог, его SHA, случайный nonce меню и
исходный provider event key. Нативные MAX message buttons содержат business
name/code и ограниченный по длине код выбора. Код принадлежит этому nonce,
user/channel/chat и одному из business IDs сохранённого каталога. Чужая или
старая кнопка после /start не выбирает новый бизнес и не становится ответом
на первый вопрос. CAS не позволяет старому обработчику заменить чужой выбор.

Проверенный выбранный payload преобразуется в DTO без повторной нормализации,
смены order или channel/local overrides. Нет обязательных business/problem
вопросов и подстановки прошлых бизнес-ответов. Bootstrap, flow, ответы,
scope/ID/SHA/catalog и nonce сохраняются в существующем session store.
Старый legacy flow после смены режима канала требует новый /start.

До первой постановки в очередь выбор проверяется ещё раз; stale/unavailable
даёт явный /start без захвата новой версии. После подготовки публикации
сохранены последний ответ и фиксированный submission event key. Retry
публикует тот же command без свежих bindings. После принятия в очередь
сохраняется маркер с ticket ID: потеря delivery dedup cache не создаёт другой
опрос/обращение. Это транспортное состояние, не canonical ticket source of truth.
Новый follow-up ожидает именно этот canonical root, не другой active ticket;
pending/retry и terminal state не удаляются перед публикацией.

Создание и follow-up этого selected MAX flow не отправляют raw summary в
непроверенный support chat. Это локальная защита нового пути, не завершённая
recipient policy всех legacy/direct/active путей. Общие правила destinations,
TTL/retention сессий, одновременные обращения разных бизнесов, live Redis/MAX/
proxy и rolling-upgrade acceptance ещё обязательны до rollout.
Проверки используют реальный handler/JSON/CAS с изолированными doubles для
доставки и отдельные PostgreSQL suites; это не живой provider E2E.

После VK R146 и Telegram R147 GENERAL option общего канала, scoped locations/presets и release
остаются отдельными этапами. Новых migrations нет; minimum schema — V73.
Необходимость совместимого backend/fleet не разрешает менять рабочие flags,
запускать migration/backfill, rebuild или deployment без отдельного scope.

### R146: VK — выбор, сохранённый опрос и Callback/Long Poll retry

VK использует тот же writer-gated choices/selection и выбранный loader R145.
Новый BUSINESS опрос не использует channel/global overrides, legacy defaults,
прошлые business answers либо обязательный problem. GENERAL-only и unbound
legacy остаются отдельными совместимыми ветками, не вариантами общего меню.

До отправки меню сохраняются каталог/его SHA, nonce, стартовый conversation
message ID и страница. До первого вопроса сохранены scope/template ID/SHA,
flow/settings, bootstrap и ответы. Нативные text buttons несут JSON payload
с nonce и business ID; видимый label не является authority. До 8 бизнесов на
странице, 2 кнопки в ряду и отдельная навигация: не более 5 рядов/10 кнопок.
Усечённые имена сохраняют ID suffix; текст страницы и ручной код позволяют
различать варианты. Страницы не меняют каталог, /start создаёт новую генерацию.
Старые, чужие и malformed payload не превращаются в ответы вопросника.

В существующей конфигурации vk-bot.channel-id берётся из VK_OPERATOR_CHAT_ID:
это внешний служебный peer, не channels.id. Новые selected sessions используют
отдельный namespace vk-questionnaire-v1 + канонический channels.id, разрешённый
через token. Внутренний message guard тоже привязан к этому ID; общий служебный
чат не смешивает опросы двух каналов одного клиента. Старый vk/legacy namespace
совместим отдельно и не выдаётся за selected state. Рабочие ключи не переносятся
и не удаляются миграцией. Legacy ingress/job lease namespaces требуют отдельного
аудита; новая схема сессий не доказывает готовность всей fleet coordination.

Callback и Long Poll используют общий guard по peer/conversation_message_id,
а не только event_id envelope. В новой сессии сохранён список принятых answer
events (до 4096, превышение — отказ, не усечение). Он сохраняет дедупликацию
ответов при потере transport cache. Сообщение с conversation message ID меньше
стартового относится к предыдущему опросу и не заполняет новый бизнес.
Long Poll продвигает ts только после успешной обработки пачки; повтор пропускает
ранее завершённые сообщения. CAS conflict после трёх попыток остаётся retryable,
не подтверждается как обработанный. BUSINESS требует положительный stable
conversation_message_id; дата, текст и random ID его не заменяют.

До первой публикации проверяются актуальный выбор и отсутствие другого active
root. Последний ответ, submission key и accepted-answer ledger сохраняются до
очереди. Retry после ошибки очереди использует тот же command, даже при смене
bindings. После queue acceptance сохранён ticket ID marker; ошибка записи самого
marker допускает повтор только того же детерминированного command, не новый root.
Следующее сообщение при восстановлении pending публикации остаётся retryable,
не теряется под подтверждением завершения опроса. Follow-up ждёт именно свой
canonical root и проверяет user/active ID; другой active ticket его не перехватывает.

Новый selected VK путь text-only: вложения явно отклоняются до storage/ответа,
без unscoped upload. Raw support-chat summaries/follow-up и прежний rating prompt
для этого пути не отправляются; shared-channel active shortcut без сохранённого
контекста также не публикует raw сообщение. Это не завершённая общая destination,
media/feedback, follow-up event retention или permission policy остальных путей.

Selected sessions не удаляются legacy first-response scheduler: сохраняются до
явного /start, /cancel либо TTL store. TTL/retention, dedup TTL, non-Rabbit/API-off,
rolling upgrades, несколько active бизнесов и live Redis/VK/Long Poll/proxy
проверяются отдельно. Session store остаётся transport state, не canonical DB.
Новых migrations/flags нет; минимум V73. После Telegram R147 shared GENERAL option,
scoped locations/presets и release остаются PENDING; rollout этим не разрешён.

### R147: Telegram — отдельный сохраняемый BUSINESS опрос

Новый TelegramQuestionnaireHandler обрабатывает только private user/chat tuple
активного Telegram channel. Он вызывается основным SupportBot до legacy edits,
feedback, active shortcut и media. GENERAL-only/unbound runtime остаются в
прежней ветке; отказ настроенного scoped API не открывает local/global fallback.
BUSINESS старт/selection использует writer-version ACK и selected loader R145.

Pending меню хранит catalog/SHA, nonce, страницу, начальный provider message ID
и identity клиента. Нативные reply buttons посылают generation-bound текст;
до 100 бизнесов, 8 на страницу. Кнопки выбора/навигации после выбора не становятся
ответами. Выбранный payload, исходный order, ответы, history/provider IDs,
посещения вопросов и accepted-answer ledger сохраняются в JSON/CAS namespace
telegram-questionnaire-v1 + canonical channels.id. ID служебного чата не
используется как channel/session authority; delivery guard также canonical-scoped.

Вопросник не получает обязательных business/problem или прошлых business answers.
Сохраняются select по номеру/label, forward routes по option ID, возврат назад с
очисткой следующих ответов и optional skip. Bootstrap относится только к новому
началу: оставшийся legacy/GENERAL in-memory ответ при rebind не переносится в
новый BUSINESS history. Меню сохраняется до отправки; выбор и каждый ответ —
до следующего prompt. Selected marker не находится в legacy scheduler map.

До первой публикации проверяются current catalog/selection и отсутствие другого
active root. Последний ответ и fixed submission message ID сохраняются до queue.
После ошибки публикации/marker persistence повторяется тот же command, не новая
версия/owner/ответ. Сохранённый ticket ID marker и accepted ledger переживают
потерю update dedup cache. Сообщения до нового /start и запоздалые более ранние
ответы не продвигают новый опрос. CAS conflicts после трёх попыток не подтверждаются.

Follow-up ждёт свой canonical root/user/active ID; другой active ticket не
выбирается. Текст нового сообщения, пришедшего во время восстановления публикации,
не подменяет последний ответ: клиент получает явную просьбу повторить сообщение,
обработка завершается retryable error. Это не durable очередь follow-up. Живая
семантика Telegram Long Poll offset/re-delivery после exception ещё не доказана;
настоящие recovery/доставка требуют отдельной приёмки, а не только unit replay.

Новый путь text-only: media/unsupported payload и edits явно отклоняются до
unscoped storage/writer. Selected commands не вызывают legacy ticket-list/reply
маршруты; raw summary/support-chat relay/rating prompt не отправляются. Blacklist
сохраняется. Saved state проверяет user/chat/channel/version, template membership,
progress, только свои text history и известные answer keys. Это локальная защита,
не завершённый общий recipient/child attribution или media/feedback policy.

TTL/retention/4096-event budget, non-Rabbit/API-off modes, fleet/rolling upgrade, Long Poll/proxy/Redis,
GENERAL/legacy session persistence и несколько active бизнесов остаются PENDING.
Session state не является canonical relational source of truth.
При direct coordination BotSessionStoreService хранит state в памяти процесса;
durability полного process restart требует Redis mode и отдельной приёмки.
Тесты восстанавливают handler/бота с общим store, а не доказывают live Redis restart.
GENERAL option общего канала не добавлена: она требует собственного явного binding и writer,
а не ослабления BUSINESS или general-only API. Scoped locations/presets и release
также отдельные этапы. Новых migrations/flags нет; минимум V73, rollout не разрешён.

### R148: отдельная настройка и read-only v2 preview варианта «Без бизнеса»

V74 создаёт пустой `shared_channel_general_question_template_bindings` с FK канала,
unique template ID и SHA-256. Это не расширение GENERAL-only V57: для новой
привязки нужен active shared channel с полностью валидным BUSINESS/EXPLICIT_SELECTION
каталогом и отдельный canonical шаблон. Legacy/channel/global-active, business
и GENERAL-only шаблоны не переиспользуются. Шаблон без business/problem вопросов
допустим; его исходные question IDs/order сохраняются. Presets, пустые flow,
duplicate template/question IDs запрещены. Deactivate не освобождает template ID
для другого scope; повторное bind обновляет явную привязку и frozen SHA.

GET/PUT/DELETE `/api/business-admin/shared-channels/{channelId}/general-question-template`
требуют foundation и одновременно PAGE_SETTINGS + системный ROLE_ADMIN;
мутации — CSRF. Business ADMIN/ALL_BUSINESSES не заменяют эти полномочия.
Успешные bind/deactivate оставляют actor/channel/template audit log, но не создают
grants. Три template-binding writer используют общий PostgreSQL transaction-scoped
advisory lock по template ID; GENERAL-only сохраняет запрет active business bindings.
Удаление всех business bindings не открывает legacy runtime/INSERT: active shared
GENERAL registry сам требует explicit selection даже при выключенных flags.
GENERAL-only на том же канале также требует сначала явного deactivate shared GENERAL.
Внешние обходные SQL/config edits не узакониваются: runtime повторно проверяет
reserved references и fingerprints и закрывается при конфликте.

Подписанные GET `/internal/api/bot/channels/{channelId}/questionnaire/v2/choices`
и `/selection` — **только preview**. Требуют foundation + shared-channel-resolution +
general-channel-resolution, token/HMAC с query и no-store. Каталог версии 2 содержит
BUSINESS choices и только явно включённый GENERAL с `businessId=null`, не 0 и не
UNRESOLVED. Выбор задаётся `scopeKind`, nullable `businessId`, `catalogSha256`.
GENERAL имеет USER_SELECTION, собственный template ID/SHA и общий catalog SHA.
Payload содержит ровно выбранный вопросник; global settings/presets/location tree
не выдаются. В каталоге не более 100 вариантов **с учётом GENERAL**. Повреждённая
active GENERAL настройка закрывает весь v2 каталог; отключение/перепривязка GENERAL
и изменение любого видимого BUSINESS варианта делают прежний выбор устаревшим.
DB snapshot использует REPEATABLE_READ; настройки из файла проверяются по frozen SHA,
но не становятся частью PostgreSQL-транзакции. Preview меню не загружает runtime
payload всех бизнесов повторно; selection выдаёт только выбранный BUSINESS payload.

V1 API/SDK/боты и v1 writer ACK сохраняются отдельно. V2 `forStart=true` всегда 404
и **никогда не возвращает writer-version ACK**, даже при всех включённых flags.
V73 writer пока отклоняет GENERAL/USER_SELECTION с catalog SHA; preview не является
разрешением записи или запуска опроса. Следующий связный этап — v2 INSERT/replay и
immutable GENERAL provenance, затем SDK/session/menu consumers всех трёх ботов.
Readiness проверяет V74; рабочая migration/flags/restart/rollout не выполнялись
и не разрешены этим source-only шагом. Scoped locations/presets и общая приёмка
остаются открытыми.

### R155: Telegram selected followup media и frozen root version

V76 добавляет nullable immutable tickets.questionnaire_version. Новый selected
INSERT записывает 1–4 из проверенного backend-каталога; GENERAL-only/legacy/manual
без такого каталога остаются NULL. Existing rows не backfill-ятся и version NULL
не превращается в 4 после retry/rebind. SQL CHECK и trigger запрещают подмену и
удаление версии. Старое scoped чтение draft attachments не требует новой версии.

GET /internal/api/bot/channels/{channelId}/questionnaire-media?userId=...&ticketId=...
является token/HMAC-signed no-store admission только для Telegram root version=4.
Он проверяет canonical tuple, active channel/root, USER_SELECTION, frozen scope/
template/catalog и remainingFiles. BUSINESS/GENERAL различаются nullable business ID;
GENERAL дополнительно требует своего flag. Foundation/strict/shared flags обязательны.
Нет lookup текущих bindings/resources и нет local/legacy fallback. Capability version=1
в ответе — версия admission протокола, не версия каталога. SDK проверяет все поля
против сохранённого выбора и не кэширует admission.

Telegram допускает те же 8 типов и 1 байт–10 МиБ/файл после регистрации своего root.
Клиентский бюджет 10 файлов включает draft и followup; операторские/legacy attachment
writers этим пакетом не переводятся на общую quota policy. Неподтверждённый/чужой/
закрытый root, старые catalog sessions и исторический root без version=4 не открывают download.
Полный pending media command snapshot сохраняется CAS до enqueue. При первом таком
событии session становится v2; новый consumer читает старый v1 без followup fields,
а старый consumer отвергает v2 вместо потери prepared события. Rolling upgrade/fencing
требует живой приёмки. Нельзя объявлять её выполненной по unit JSON restore.

Inbound writer для selected Telegram media и новых scoped namespaces повторяет
проверку frozen canonical root и provider uniqueness до INSERT. Ticket/channel/active
locks вместе с существующим legacy-ID guard сериализуют close/media/quota paths.
Проверяются реальные bytes, key/type/name/hash/size; history/metadata/profile/activity
записываются одной транзакцией. Failed event допускает точный retry, processed inbox
duplicate не открывает файл и не зависит от нынешних flags/bindings. Creation history
и answers не дополняются followup payload; raw operator relay/AI/feedback не вызываются.
MAX legacy attachment admission сохраняется отдельно, не объявляется безопасным
новым протоколом. VK selected followup пока не открыт.

Namespace questionnaires/telegram и прежний scoped download contract сохраняются:
frozen root/provider proof, current READ и bytes SHA/size. Legacy URLs не открываются.
Остались old-root admission policy, VK followup, edit/delete/feedback, retention/orphan
reconciliation и live Redis/provider/S3/proxy/fleet acceptance. Source minimum V76;
рабочая migration/flags/restart/deploy этим этапом не разрешаются.

### R156: VK selected followup batch, точный replay и атомарный inbox

Дата: 2026-10-21. Продолжение R155; его запись о закрытом VK followup — история.
Новые v4 BUSINESS/GENERAL roots с frozen questionnaire_version=4 принимают фото,
документы и audio_message после регистрации. Исторические NULL/v1–v3 roots не
получают эту возможность автоматически. SQL minimum остаётся V76, новой migration нет.

- Перед файловым I/O отдельный token/HMAC GET questionnaire-media/vk-batch возвращает
  capability version=2. Telegram endpoint/version=1 не подменяет VK-допуск. SDK
  сверяет user/channel/ticket, nullable business/scope, frozen template/catalog и
  remainingFiles. Текущие bindings/resources не перечитываются для owner admission.
- 1 байт–10 МиБ/файл и общий бюджет 10 с учётом draft/принятых followup. Бюджет
  относится к этому client ingress, не ко всем операторским/legacy writers.
  До скачивания проверяются весь batch, свой active root и доступное число мест.
  VK CDN/redirect/size правила и owner/provider/slot/content-addressed keys R154
  сохраняются. Creation history/answers не меняются; raw relay/feedback/AI нет.
- Один conversation_message_id порождает одно versioned событие
  client_message.selected_media_batch.v1, не N одиночных событий с конфликтующим ID.
  UUID привязан к kind/channel/user/length-prefixed opaque ticket/provider.
  Слот — точный последовательный индекс 1..N; весь batch и профиль/время исходного
  сообщения сохраняются CAS до enqueue. Pending replay не скачивает файл, не
  перечитывает вопросник и не заменяет подготовленный payload изменённым retry.
- Ledger после submitted marker сохраняет новые provider keys, не меняя сам marker.
  Старый consumer R155 отвергает такой snapshot прежней проверкой «submission — last»;
  новый проверяет pending owner/slots/provider/time/budget и читает старые snapshots
  с отсутствующими pending/counter. Legacy sessions без selection сохраняют прежнюю
  attachment policy. Это source fencing, не доказанная rolling-upgrade приёмка.
- Backend под legacy-ID guard и root/channel/active locks проверяет frozen root,
  стабильный event ID, platform/type/name, уникальность provider, quota и реальные
  bytes/size/SHA всех slots до первого child INSERT. Затем history/metadata/profile/
  activity и processed inbox marker нового batch коммитятся одной транзакцией.
  Deferred commit failure не оставляет processed inbox без данных; после такой
  ошибки processing reclaim использует существующий timeout 15 минут.
- Outbox/inbox сохраняют полный batch payload; совпадение event ID с другим
  содержимым — отказ, включая failed/processed replay. Сравниваются JSON trees
  после одинакового serialize/parse, без различия внутренних IntNode/LongNode.
  Exact processed replay не читает storage/current flags/каталог. Одиночный selected
  VK media writer не является обходом нового batch verifier. MAX protocol не менялся.
- BUSINESS/GENERAL download использует прежние frozen root/provider/slot proof,
  metadata hash/size, bytes и current READ. Отзыв grant закрывает файл; legacy URLs
  не открываются. Enqueue не подтверждает backend commit, operator/client delivery.
- Для выпуска нужны совместимые panel web/worker, core outbox и VK consumer.
  Старый core outbox не публикует незнакомый event kind; R155 writer отвергает
  scoped VK вместо частичной записи, но это не разрешает mixed fleet со всеми
  историческими versions. Admission от нового web не доказывает обновление worker.
  Shared storage, Redis/Rabbit/VK/proxy, TTL/retention/orphans/DLQ/reconciliation,
  historical-root policy, scoped edit/feedback и прочие S2B/S3–S6 gates остаются.
  Рабочие DB/flags/контейнеры, deployment и rollback этим source-пакетом не меняются.

## Scope guard и API contract

Один сервис `BusinessAccessService` должен предоставлять:

```text
resolveAccessibleBusinessIds(authenticated users.id)
resolveEffectiveBusinessCapabilities(users.id, business_id)
requireReadableTicket(ticket locator, authenticated user)
requireWritableTicket(ticket locator, action, authenticated user)
requireSelectedBusiness(requested context, authenticated user)
```

Контракт:

- Доступ к сущности сначала резолвится по её canonical owner, затем проверяется
  against accessible IDs. Нельзя сначала загрузить entity по public ID и
  передать её в UI/notification до scope check.
- `business=all` — допустимый presentation context только для union resolved
  `accessibleBusinessIds`; конкретная write operation всегда имеет один
  business owner.
- Неавторизованный direct entity lookup возвращает нераскрывающий ответ (для
  data endpoint — `404`), а invalid/forbidden selected context — `403` с
  безопасным сообщением. Counts, pagination, search, export, attachments,
  autocomplete, SSE и AI retrieval используют тот же guard.
- Internal transport callbacks не получают operator grant. Они несут trusted
  resolved business context, который сверяется с ticket/channel binding и
  сохраняется до outbox. Unknown context останавливает обработку.
- У event/outbox/notification есть явный `scope_kind`:
  `BUSINESS`, `GENERAL`, `GLOBAL_SYSTEM` или `UNRESOLVED`. `GENERAL` — намеренно
  выбранное обращение без бизнеса, `GLOBAL_SYSTEM` — системные данные. Оба
  требуют отдельного права; `ALL_BUSINESSES` сам по себе его не предоставляет.

## Общие задачи, проекты и Kanban — решения R301/R303

Пользователь выбрал общие межбизнесовые работы с отдельными правами и участниками
(R301), затем уточнил полный доступ системного администратора (R303). Текущий
ROLE_ADMIN создаёт и администрирует любые задачи, проекты, доски и стадии,
читает/изменяет/удаляет объекты, управляет допусками/участниками и владельцами.
PAGE_TASKS/PAGE_SETTINGS, shared grants и членство ему не нужны. Текущий canonical
users.id/raw username, enabled/blocked и роль повторно проверяются в SQL;
сохранённая отозванная роль, username admin и похожее имя привилегий не дают.
CSRF и структурная целостность данных обязательны.

Для остальных нужны текущий PAGE_TASKS, shared can_read и собственное участие:
VIEWER читает, EDITOR меняет, OWNER также управляет составом. Создание требует
can_create_task либо can_create_project; trusted writer записывает numeric owner
и OWNER с root атомарно. Business ADMIN, ALL_BUSINESSES и ROLE_PORTAL_ADMIN
этого допуска автоматически не дают. PAGE_TASKS сам по себе не заменяет grants.
Только OWNER или системный администратор передаёт владение; recovery старой
unowned записи доступен администратору, без ownership по текстовым creator/lead.

Связь с обращением не заменяет его независимый READ. Членство в проекте не даёт
READ его задач. Требуемая граница распространяется на списки, counts, поиск,
экспорт, уведомления и realtime. В R303 подключены основные CRUD/list/count/
pagination/project links/Kanban/server model/task analytics; скрытые связи и
placements сохраняются при посторонних изменениях. R304 подключил UI общих
допусков/участников/владельца. R305 добавил append-only аудит с before/after,
numeric actor/target и raw actor snapshot, UI history и50-row cursor paging;
изменения grants/members/owner/create и их аудит атомарны. История не имеет FK
на удаляемый account/root. UPDATE/DELETE запрещены trigger; защита от
администратора БД или TRUNCATE этим не обещается.

Typed уведомления замораживают kind/objectId/numeric recipient; текущие account,
PAGE_TASKS/module/member READ проверяются до записи, до list/count/pagination/
read mutation и непосредственно при SSE emit под READ locks. Recipient expansion
происходит до фильтрации. Legacy unowned task/project уведомления не открывают
обход. После revoke/username reuse старый recipient не сохраняет допуск.
Публичный SSE содержит только reason/emittedAt, без внутренних scope metadata.

Trusted auto-close не подменяет SecurityContext: отдельно проверяет текущего
eligible numeric owner, canonical source ticket READ и project READ/WRITE.
Root/owner/OWNER/sequence/source link/system audit регистрируются атомарно;
unresolved/GENERAL legacy fallback без подходящего scope не создаёт задачу.
Manager report, dialog task history, incident task picker фильтруют доступ
до counts/LIMIT; incident summaries в task DTO разрешены системному admin.

V88/V89 применены в рабочем R305; начальных grants, автоматического ownership
и backfill нет. Старые строки доступны системному admin; numeric владелец
восстанавливается явно. 454 Java +15 Node и22 real-browser проверки GREEN,
включая stale session revoke и настоящие уведомление/SSE.
Правила новых incidents/system notifications, historical incident snapshots
и оставшихся global/system полномочий — отдельный пункт. Business ADMIN
не становится системным администратором.

## Legacy compatibility и decision table

| Область | Безопасное временное поведение | Решение, необходимое до реализации |
| --- | --- | --- |
| Три непустых legacy aliases | Dataset тестовый, не переносится | **СНЯТО R190:** alias map и historical assignment не требуются |
| Три пустых `messages.business` | Dataset тестовый, не переносится | **СНЯТО R190:** решения по этим строкам не требуются; новые unresolved остаются fail-closed |
| 3 history channel mismatch | Dataset тестовый, не переносится | **СНЯТО R190:** классификация старой истории не требуется; новый child scope определяется canonical ticket |
| Mixed channels | Явный выбор бизнеса или «Без бизнеса» до первого вопроса; без выбора flow не запускается | **УТОЧНЕНО 2026-10-16:** general — отдельный scope, не unresolved. **PENDING:** UX-представление и precedence для иных verified sources |
| Ручные задачи / global Kanban | Старые строки не классифицируются автоматически | **РЕШЕНО R301/R303:** полный system ROLE_ADMIN; основные create/CRUD/query/links/Kanban подключены к R302. UI/audit/late READ уведомлений и SSE/trusted auto-close/task projections активированы R305/V89;22 real-browser checks GREEN; ticket READ независим |
| Новые incidents / system notifications | 105 старых инцидентов не классифицируются и не переносятся | **PENDING:** модель business/shared/global для новых событий, получатели и отдельные permissions |
| Existing ticket route | Legacy route доступен только в подтверждённом unique window | **РЕШЕНО S2A:** opaque immutable `ticket_public_id`; internal fallback — обязательный composite locator |
| Roles | Global page permissions не выдаются автоматически; channel management требует системного ROLE_ADMIN + права страницы (R184) | **PENDING:** оставшаяся global/system и business capability policy |
| Projects/boards | Не показывают inaccessible tasks | **РЕШЕНО R301/R303:** current system ROLE_ADMIN имеет полный доступ; остальные — отдельные grants/numeric participants. Основные reads/writers/links/boards подключены; UI, аудит, task projections и typed delivery/SSE активированы R305/V89; полный incident/system контур отдельно |

## Поэтапное применение

1. **S1, additive foundation.** Свободная на дату реализации Flyway version
   выше V50; новые tables/nullable FKs/indexes, `BusinessAccessService`,
   default-deny contract и PostgreSQL integration tests. Feature flags off;
   никаких existing-row mutation, UI switch или strict filtering.
2. **S2, canonical identity.** S2A public/composite locator и security guards
   сохраняются для новых данных. Historical S2B alias assignment/dry-run/
   backfill снят R190. Проверяются чистая схема, canonical owner и child tuple
   integrity новых записей; перенос старого dataset не является gate.
3. **S3, server scope.** Read/write API, dialogs, attachments, tasks,
   projects/boards, analytics, notification and realtime/outbox paths; negative
   cross-business tests before strict flag.
4. **S4, channel runtime.** Shared-channel resolver, per-business template
   registry and frozen ticket context across Telegram/VK/MAX replay.
5. **S5, UI.** Accessible-business selector, business catalog/memberships,
   empty states and manual acceptance. Browser state can select scope but cannot
   grant it.
6. **S6, первый production запуск.** Чистая схема, immutable revision/image,
   совместимые consumers, отдельно согласованный runtime preflight/запуск,
   rollback plan и независимая приёмка новых data flows. Historical backfill
   не требуется; source implementation не выполняет runtime операции.

### S1 source checkpoint — 2026-10-10

- Добавлена V51, не запускавшаяся вне изолированной PostgreSQL Testcontainers
  schema. Она создаёт `businesses`, memberships, `ALL_BUSINESSES` grants,
  пустой approved-alias registry, channel/location mapping foundations и
  nullable `tickets.business_id` с FK/index; legacy rows не присваиваются.
- В V51 seed-ятся только предустановленные records `sushi` и `bliny`;
  реальных legacy aliases в migration нет.
- `BusinessAccessService` резолвит direct membership и `ALL_BUSINESSES` grant,
  применяет initial role matrix `VIEWER`/`OPERATOR`/`MANAGER`/`ADMIN` и по
  умолчанию отказывает при отсутствии active grant. Business `ADMIN` — не
  системный `ROLE_ADMIN`.
- `requireSelectedBusiness` принимает типизированный single/all context:
  конкретный business разрешён только из accessible IDs, а all-view возвращает
  их union (включая пустой). Это presentation scope, не grant и не write scope.
- `requireReadableTicket` пропускает только `TicketReference` с non-null
  canonical owner и effective `READ`; unassigned legacy ticket и чужой owner
  получают fail-closed отказ. Метод не подключён к endpoint до S3.
- Targeted PostgreSQL integration test подтверждает legacy ticket без
  assignment, denied-by-default, изоляцию selected membership, fail-closed
  selected/ticket contexts и включение future business в `ALL_BUSINESSES` без
  capability `CONFIGURE`.
- Existing API/UI/read paths намеренно не подключены к сервису до S3, поэтому
  V51 source foundation не меняет поведение existing users.

## Feature flags и rollout boundary

Предлагаемые flags выключены по умолчанию:

```text
multibusiness.foundation.enabled=false
multibusiness.ticket-write-requires-business=false
multibusiness.strict-read-scope=false
multibusiness.shared-channel-resolution=false
multibusiness.general-channel-resolution=false
multibusiness.ui-selector=false
multibusiness.manual-ticket-creation=false
```

`MultibusinessFeatureFlags` binds these exact keys and defaults all of them to
`false`. At the S2A source checkpoint no runtime path reads this bean: adding
the configuration does not enable filtering, assignment or a UI switch.

Ручное создание дополнительно требует `foundation.enabled=true`,
`ui-selector=true` для страницы и отдельный `manual-ticket-creation=true` для
POST. Исходное значение флага — `false`; наличие source/migration не включает
операторскую запись в рабочей среде.

Первый запуск с чистой базой не требует historical assignment. Каждая новая
показываемая запись должна иметь canonical business/general/global scope и
соответствующий допуск; unresolved и противоречивые rows не дают доступ по
догадке. Остатки тестового dataset исключаются либо удаляются при отдельно
подготовленном пересоздании стенда, без silent fallback. Каждый flag требует
своей приёмки и rollback в false; source merge не является rollout.

## Обязательная test matrix

- PostgreSQL: FK/check constraints, unique business codes, membership and
  `ALL_BUSINESSES` effective scopes, no cross-business duplicate location
  collision.
- Security: user A не читает/не меняет B через list, direct ID, attachment,
  search, export, count, task, notification, SSE и AI context; `ALL_BUSINESSES`
  не получает global-system data.
- Ticket identity: новые записи имеют устойчивую public/composite identity;
  ambiguous bare ticket_id отклоняется, child lookups подтверждают canonical
  owner/generation и FK integrity. Сохранение ID/history старого dataset
  не является требованием (R190).
- Transport: single and shared channel, explicit selection, verified mapping,
  conflict/unresolved, idempotent replay/retry/edit/feedback.
- Templates: clone B does not mutate A, template version stays frozen on an
  existing ticket, new business works without iiko.
- UI/manual: one/many/all grants, forbidden context switch, all-view labels and
  counts, reload/realtime, empty business and business catalog membership save.

## Gates после S1

Historical S2B не блокирует S3: approved alias map и решения по старым строкам
больше не нужны (R190). Релевантные будущие product policies из decision table
остаются PENDING; их нельзя вывести из отказа от тестовых данных. Перед
соответствующим новым path нужны явный contract, changed-file/test plan от
свежего main и negative scope tests. Подготовка чистой схемы/стенда и первый
production запуск — отдельный согласованный runtime scope.

Ниже сохранены исторические source checkpoints; старые требования переноса
dataset не переопределяют решение R190.

### R149: v2 GENERAL producer, nullable SDK и выбранные sessions во всех ботах

- V75 расширяет только CHECK нового questionnaire catalog: GENERAL/USER_SELECTION
  имеет business_id=NULL и business_selection_source=NULL, свой ID/SHA и общий
  catalog SHA. V73/V58 fingerprints остаются immutable; новый trigger также
  фиксирует выбранные owner/kind/source/channel/composite ID. Старые rows не
  переписываются; GENERAL-only и nullable legacy representation сохраняются.
- Writer заново проверяет flags, platform/active channel, полный current catalog,
  membership и selected ID/SHA до root/children. BUSINESS V1 поддерживается без
  active shared GENERAL reservation; с ней требуется v2 catalog, а не каталог,
  в котором GENERAL отсутствует. USER_SELECTION без version proof не превращает
  shared channel в legacy/GENERAL-only. Inbox, root/composite locator и child
  writes атомарны; inbox проверяет повторный payload и сохраняет статус ошибки.
  Exact replay хранит frozen version и не захватывает новый owner.
- V2 choices/selection forStart выдаёт ACK=2 только при strict writer; preview
  не выдаёт ACK. GENERAL требует general resolution. BUSINESS-only v2 start не
  требует включать GENERAL при отсутствии active GENERAL binding. HMAC покрывает
  path/query/forStart. SDK проверяет обе версии, NULL versus fake owner, duplicate
  scope/template, selected bytes SHA и отсутствие global locations/presets;
  новые starts не откатываются на v1 при ошибке/отсутствии ACK=2.
- MAX/VK/Telegram имеют отдельный GENERAL command/button namespace и выбор до
  первого вопроса, JSON/CAS persistence и exact template flow. ScopeKind + nullable
  business ID сопоставляются с сохранённым меню; version/channel/catalog обязаны
  совпасть. Stale nonce не является ответом. Сессия/ответы переживают rebuild,
  final publication key фиксируется до queue; неизвестный итог повторяет ту же
  frozen command, а не перечитывает текущие bindings. Shared GENERAL использует
  выбранную сохраняемую ветку, не legacy GENERAL-only path. Raw support-chat,
  local/global settings, legacy feedback/media fallback не открываются.

Новые starts требуют совместимых backend V75 и SDK/consumers v2; отсутствие ACK=2
закрывает запуск. Старые V1 BUSINESS sessions поддерживаются явно, не получают
GENERAL через fallback. Рабочая migration/flags и provider/Redis/proxy приёмка не
выполнены. Scoped locations/presets/media, feedback/edit, rolling-upgrade и
retention остаются gates; общий статус задачи 🟡.

### R150: изолированные ресурсы выбранного бизнес-вопросника

- Активные business_locations читаются по точному business_id. Global/iiko/
  legacy location tree и dialog_config.preset_definitions не участвуют.
  Business presets хранятся в businesses.settings.questionnaire_presets как
  group → field → список строк. Names/labels не определяют owner. GENERAL
  возвращает только свой вопросник и два пустых объекта ресурсов.
- System-admin GET/PUT /api/business-admin/businesses/{businessId}/questionnaire-presets
  требует PAGE_SETTINGS + ROLE_ADMIN, foundation и CSRF для PUT. READ/ADMIN
  бизнеса или ALL_BUSINESSES не заменяют системные полномочия. expectedSha256
  и row lock защищают от потери параллельного изменения. Другие settings
  сохраняются; отсутствующий/архивный бизнес даёт 404, stale version — 409.
- V3 /internal/api/bot/channels/{channelId}/questionnaire/v3/choices и selection
  используют подпись path/query/forStart и ACK=3 для starts. Каждый choice
  содержит resourcesSha256. Хешируется UTF-8 JSON [locationTree,presetDefinitions]
  с рекурсивной сортировкой object keys и сохранением порядка массивов; общий
  catalog SHA фиксирует все choices и resource versions. ID/SHA самого шаблона
  не подменяется материализованным flow. V1/V2 не содержат нового nullable
  digest в backend JSON и по-прежнему не выдают presets; SDK поддерживает их
  сохранённые сессии, новые starts общего каталога запрашивает только через V3.
- SDK проверяет resource SHA по сохранённому choice, точный owner, shape,
  положительные/уникальные location IDs, допустимые поля, группы и значения.
  Для location_type/city/location_name разрешена одна reference на dimension;
  порядок вопросов произвольный, уже выбранные dimensions взаимно ограничивают
  варианты. Остальные business presets — независимые списки строк. Exclusions
  сохраняются, пустой список не становится свободным вводом и не открывает
  global fallback. Лимиты: 1000 активных локаций, 20 групп × 20 полей, до 100
  строк на поле и до 1000 строк суммарно. Каталог пока до 100 choices.
- MAX/VK сохраняют весь выбранный resource snapshot в Selection; Telegram —
  в уже существующем SelectedQuestionnaire. Restore проверяет menu/owner/hash.
  В V3 answer key — явный binding_key либо ID вопроса; прежняя legacy/preset
  эвристика MAX/VK для старых сессий сохранена. Повтор после неизвестного
  результата queue использует ту же команду/ответы/publication key. GENERAL V3
  переживает JSON/rebuild без синтетического business ID и без чужих ресурсов.
- До нового root/child INSERT writer повторно проверяет полный current catalog,
  шаблон и resource version. V3 preset attributes проверяются по question ID,
  binding key, input type, text/label, допустимым значениям и согласованным
  dimensions; duplicate/unknown attributes и несовпадающие непустые legacy
  projections отклоняются. Exact replay существующего root сохраняет frozen
  proof и не перечитывает новый справочник. Полная server-side проверка всего
  route/required question path этим шагом не объявляется готовой.

Source-схема остаётся V75; runtime rollout/flags/migrations не выполнялись.
UI presets/reference editor, полный route/required validator и все прочие gates
текущей сводки 01-278 остаются открытыми. Статус 🟡.

### R151: бизнес-presets UI и финальный v3 required/forward path

Пункты R150 об отсутствующем UI/validator выше — исторический checkpoint.
В R151 эти source-блоки реализованы; рабочая приёмка остаётся обязательной.

- На /businesses/manage добавлен редактор собственных готовых полей: явный
  активный бизнес, группа/поле, строки вариантов, добавление/удаление и очистка.
  PUT использует SHA из GET; 409 сохраняет черновик, обновление версии выполняется
  явно. Во время записи владелец и поля заблокированы. Задержанный GET другого
  бизнеса не подменяет текущий экран. Значения выводятся через textContent/value.
- В существующем редакторе шаблона доступен явный справочник для просмотра:
  прежний общий, конкретный бизнес или «Без бизнеса». GET .../questionnaire-presets/resources
  требует тех же foundation/PAGE_SETTINGS/ROLE_ADMIN; business ADMIN/ALL_BUSINESSES
  не заменяют эти права. В бизнес-контексте нет global fallback; GENERAL не
  получает бизнес-ресурсы. Выбор здесь — предпросмотр, привязка остаётся отдельной
  операцией в каналах. Ссылки, exclusions и маршруты остальных шаблонов сохраняются
  при общей сериализации; проверка их forward targets использует собственный flow.
- Только v3 catalog/selection применяет QuestionnaireFlowContract: до 200 вопросов,
  уникальные ID и effective keys (binding_key либо ID), стабильная сортировка order,
  boolean required/dashboard, select IDs/labels и однозначные переходы вперёд на
  существующие вопросы. Ключ problem допускается для свободного ответа. Общая
  business-привязка и v1/v2 catalogs сохраняют прежний admission contract; отдельный тест
  доказывает, что несовместимый flow доступен старому каталогу и закрыт для v3.
- До первого нового root/child INSERT проверяется весь финальный v3 путь:
  обязательные ответы на посещённых шагах, отсутствие ответов из пропущенной
  ветки, ID/key/type/text/dashboard metadata, select value ID/label/text, preset
  membership/exclusions/согласованная локация и problem projection. Необязательный
  свободный ответ можно опустить; обязательный вопрос вне выбранной ветки не
  требуется. Порядок transport attributes не определяет маршрут. Exact replay
  существующего root сохраняет прежний frozen proof и не перевалидирует новый flow.
- Telegram теперь использует строку preset-ответа как route value, как MAX/VK;
  JSON restore и отсутствие промежуточного ответа проверены. MAX production code
  не менялся: отдельный сценарий двух возвратов и смены ветки подтвердил очистку
  старых ответов. История provider updates не становится серверным answer ledger.
- Validation: 468 Java tests / 19 XML и 11 Node checks,
  failures/errors/skips=0. Backend 262/12; SDK + Telegram/MAX 206/7.
  PostgreSQL/Spring transaction/inbox/rollback, v1/v2 compatibility, HTTP access/CSRF,
  templates/settings, session restore/retry, быстрые переключения и stale CAS проверены.
  Новые v3 writer fixtures передают canonical question text/dashboard из шаблона.
- Первый серверный прогон: 247 tests, errors=1 — flow guard ошибочно стоял в общем
  bind path и ломал прежний negative fixture с duplicate question. Guard перенесён
  на v3 capture; старые assertions сохранены и усилены v3 assertion. Повторены
  зависимые серверные suites и добавлены четыре compatibility cases. Исходные логи
  сохранены в .tmp/r151-*. Sass outputs исключены из source-пакета.
- Source-схема остаётся V75; новые starts требуют ACK=3, но один ACK не доказывает
  совместимость произвольных старых образов с новым route guard. Рабочий экземпляр
  проекта находится на другой машине и в другой сессии агента. Здесь source и
  isolated tests; runtime revision/DB/flags неизвестны. Deploy/restart/working
  migration/backfill/flags/provider sends и browser/live приёмка не выполнялись.
- Остались >100 choices, scoped media/edit/feedback, live Redis/provider/proxy,
  rolling-upgrade/retention, прочие S3–S5, S2B и S6 gates. Общий статус 🟡.


### R152: v4 каталог до 1000 и pagination сохранённого меню

Предыдущие предел 100/ACK=3 относятся к v1–v3; новые starts используют v4.

- Добавлены signed v4 choices/selection и строгий writer ACK=4. Новые starts
  общего каталога идут только через v4, без downgrade; прежние v1/v2/v3 endpoints
  и сохранённые сессии обслуживаются отдельно, в v4 автоматически не переводятся.
  Подпись включает path/query/forStart; Cache-Control=no-store сохранён.
- V4 допускает до 1000 choices на канал: 1000 BUSINESS либо до 999 BUSINESS и
  отдельный GENERAL. Переполнение закрывает весь каталог, без усечения/скрытия
  GENERAL. Старые версии сохраняют предел 100. Общее число бизнесов в системе
  этим лимитом меню не ограничивается. GENERAL bind учитывает новый v4 budget.
- Каталог фиксируется целиком в сессии и разбит на локальные страницы по 8
  вариантов, до 2 navigation buttons. Переходы не запрашивают новый каталог;
  membership/ID/template/resource hashes проверяются при выборе и перед INSERT.
  Переименование, изменение шаблона либо ресурсов любого, в том числе далёкого
  пункта, меняет общий catalog SHA и требует нового выбора. Labels не являются
  owner identity; GENERAL сохраняет nullable business_id и пустые ресурсы.
- V4 registry делает один общий reservation lookup вместо чтения глобальных
  settings на каждый бизнес. Ресурсы всех участников проверяются/хешируются,
  но runtime payload и ресурсы удерживаются только для выбранного бизнеса;
  копии вопросников/локаций всех участников не создаются. Shared v4 selection
  использует один capture бизнес-каталога и выбранного runtime в своей транзакции.
  Цена capture по-прежнему линейна по числу бизнесов/ресурсов; server paging,
  нагрузочная и live Redis/retention приёмка не объявляются выполненными.
- SDK проверяет v4 capacity, exact ACK и byte/resource proof. MAX/VK/TG сохраняют
  frozen каталог/выбор и собственные ресурсы, включая выбор на последней странице.
  MAX получил страницу в Selection, guarded native navigation и сохранение до
  provider send. Page=0 не добавляется в JSON прежних сессий; отсутствующее поле
  восстанавливается как 0. Telegram допускает page token до страницы 124.
  VK использует прежнюю native pagination с новым валидатором каталога.
- Ingestion узнаёт v4 по проверенному catalog SHA через selectExpanded. До
  первого нового root/child INSERT применяет тот же required/forward path и
  canonical attributes validator, что v3. Frozen replay не перечитывает текущий
  каталог, не переносит владельца и не создаёт новые children. Новая migration
  не нужна: минимальная source-схема остаётся V75.
- Validation: 837 Java tests / 75 XML, failures/errors/skips=0;
  backend 259/7 и полный fleet 578/68. PostgreSQL границы 101/999+
  GENERAL/1000/1001, отсутствие усечения, offscreen resource/name invalidation,
  signed HTTP/ACK, создание/replay/rollback после первой сотни во всех трёх
  платформах, все native buttons до 1000, JSON restore и MAX send retry проверены.
  Node suites отдельно не запускались: JavaScript source не менялся.
- Реализация/валидация — detached sandbox .tmp/01-278-r152, затем guarded source
  apply с exact 27 files/content hashes. Generated Sass CSS исключён. Baseline
  51e89ff2e19df855f397448cc9569fe7a37a27c7, parent
  8cf6d3fb181b2c10616065e2d5e75ce6bfd378ac; R151 actual 21 files проверен.
- Рабочий Docker и проект находятся на другой машине, в другой сессии агента.
  Здесь только source и isolated tests. Рабочие revision/DB/flags неизвестны;
  deploy/restart/рабочие migration/backfill/flags/provider sends не выполнялись.
  Нужны совместимые backend/SDK/боты с ACK=4 и отдельная приёмка на рабочей машине.
- Общий статус задачи 🟡. Далее scoped media/edit/feedback выбранных потоков,
  provider/Redis/proxy, rolling-upgrade/retention, прочие S3–S5, S2B и S6 gates.


### R153: Telegram v4 draft media и server-side bytes admission

- Telegram v4 выбранных BUSINESS/GENERAL вопросников принимает document, photo,
  video, voice, audio, animation, sticker и video_note как свободный ответ до
  регистрации. Caption становится ответом, при его отсутствии используется
  явное описание вложения. Preset/select требуют текстового выбора; media не
  подменяет вариант. До выбора владельца вложение не скачивается/не сохраняется:
  клиенту предлагается отправить его повторно после выбора. Старые v1–v3 остаются
  text-only; их сессии не переводятся в v4.
- До скачивания повторно проверяются текущие selection/catalog и поддержка media
  writer. V4 forStart требует прежний exact ACK=4 и отдельный exact header
  X-Iguana-Questionnaire-Media-Version=1; choices и selection выставляют его только
  после authorization/feature gates. Отсутствие/другая версия закрывает start или
  revalidation без downgrade. Поэтому новые SDK/боты требуют backend R153+;
  одной совместимости с v4 R152 недостаточно. Старые v4 consumers игнорируют
  дополнительный header; payload/catalog schema не менялись.
- Бинарные данные: 1 байт–10 МиБ на файл, до 10 файлов в истории опроса.
  Фактический stream ограничен независимо от Telegram file_size. Ключ
  questionnaires/telegram/{channel}/{business-ID|general}/{user}/{catalogSHA}/
  {providerMessageId}/{bytesSHA}.{extension} связывает файл с canonical owner,
  клиентом и снимком; filename не определяет storage path. Original name очищается
  от пути/control chars и ограничен 255 символами. Выбирается одна rendition photo.
  Повторы/CAS используют тот же content-addressed key; локальный файл публикуется
  после полной записи, S3 использует общий attachments domain/prefix.
- Media history, answers, accepted IDs и frozen publication key сохраняются до
  prompt/provider send. JSON restore проверяет owner/channel/catalog/provider key
  и предел файлов. Duplicate update, CAS retry, prompt/send failure и queue retry
  не меняют выбранного владельца и не добавляют второй history item. Pending
  publication повторяет точный сохранённый command без нового скачивания и
  recapture; /start остаётся явным новым выбором.
- На panel перед первым новым root/child INSERT проверяются v4 version, Telegram,
  canonical channel/business-or-GENERAL/user/catalog/provider identity, допустимый
  тип/имя, уникальность и лимит файлов. Читаются реальные shared storage bytes;
  missing/empty/oversize, чужой path/URL/traversal, изменённый SHA или повтор
  отклоняются. В одной ingestion-транзакции сохраняются chat_history с точным
  ticket_owner_user_id/channel и metadata size/content_hash/storage_key.
  Прежний metadata SQL/opaque ticket-ID contract сохранён. Existing-root frozen
  replay не перечитывает текущий каталог или файлы и не создаёт children повторно.
- BUSINESS/GENERAL scoped attachment APIs теперь читают новый namespace по
  canonical public ticket/history locator, текущему READ и frozen root proof.
  Сверяются channel/user/business-or-GENERAL/catalog/provider и metadata hash/size;
  текущие bindings/каталог не определяют владельца старого обращения. При download
  байты повторно сверяются с SHA/size, затем повторно проверяются READ/reference.
  Старые direct/key/path endpoints закрыты для нового namespace, включая Windows
  slash/case aliases. HTML/SVG и остальные документы выдаются как attachment с
  application/octet-stream, no-store и nosniff через scoped APIs.
- Дополнительные PostgreSQL/read/controller checks покрывают отзыв READ,
  повреждение provider/channel/hash/size metadata, неоднозначные metadata,
  изменённые bytes и попытку обхода через legacy URLs. Старые ticket-directory
  readers и opaque ticket IDs сохранены и проверены отдельно.
- Validation: 845 Java tests / 84 XML, failures/errors/skips=0;
  backend 233/15, полный fleet 612/69. Проверены все 8 типов,
  BUSINESS/GENERAL, restore, duplicate, CAS, failed prompt/download/storage/queue,
  actual-byte limit, signed capability, PostgreSQL owner/metadata/rollback/replay.
  Дополнительно сохранён контракт legacy exact ticket-ID metadata и HTTP READ.
  Начальные 278 backend checks (135 writer cases) прошли; финальный batch
  повторяет 33 новых media write/read cases, остальные 116 writer cases не
  повторялись. Начальный XML сохранён отдельно, в итоговый счётчик не добавляется. Тесты используют
  isolated PostgreSQL, local_fs и direct JSON/CAS; provider downloads/sends mocked.
  Live S3/Redis/provider/browser/proxy acceptance не выполнялась. JavaScript source
  не менялся, Node suites отдельно не запускались.
- Detached sandbox .tmp/01-278-r153; exact 26 files/content guards перед source
  apply. Baseline 554ea0288fbe65fd2e7e8cf9cdf004699c4b4acf, parent
  51e89ff2e19df855f397448cc9569fe7a37a27c7; фактические 27 файлов R152 проверены.
  Generated Sass CSS исключён, source-схема остаётся V75, новой migration нет.
- Read operator v1 остановлен JS parser до transformation; v2 остановлен exact
  anchor guard до изменения reader files. Артефакты сохранены; v3 использует
  проверенные semantic anchors. Это не RED тестов и не source apply в main;
  emitted Java/read SQL проходят полный compile и targeted checks перед apply.
- Первый read batch: 79 checks, одна ошибка прежнего BUSINESS read test с
  минимальной схемой без ticket.channel_id. Новый proof SELECT оказался связан
  с legacy projection. V4 исправления отделяет proof-only SQL, сохраняет общий
  BUSINESS policy predicate и прежний projection для legacy; GENERAL proof
  вынесен в отдельный guarded lookup. Недоступная proof schema не разрешает
  новый namespace. Существующие тесты не ослаблялись; повторяются read batch,
  33 media write/read cases и прежний HTTP READ suite. RED log сохранён отдельно.
- Рабочий Docker/проект находятся на другой машине и в другой сессии агента.
  Рабочие revision/DB/flags неизвестны; deploy/restart, рабочие migrations/backfill,
  flags/config/provider sends здесь не выполнялись. Перед выпуском нужны совместимые
  backend/SDK/боты и один shared storage domain/bucket/prefix либо общий volume.
- Общая задача 🟡. Дальше: VK selected media, Telegram followup media, scoped
  edit/feedback, media deletion/retention/reconciliation (включая orphan objects
  отменённых/конфликтных черновиков), live fleet/provider/Redis/S3/proxy acceptance,
  прочие S3–S5/S2B/S6 gates. Одновременные бизнес-обращения не добавлялись.

### R154: VK media выбранного вопросника, batch identity и scoped bytes

Дата: 2026-10-21. Промт: «продолжай». Связный пакет 20 файлов; общий статус 🟡.

- VK v4 выбранного BUSINESS/GENERAL вопросника принимает photo, document и
  audio_message в свободном ответе до регистрации. Одно сообщение может содержать
  несколько файлов; caption становится ответом, без caption — «Вложений: N».
  Pending choice, preset/select, v1–v3 и media после регистрации закрыты до
  скачивания. Вложения до выбора предлагается отправить заново. GENERAL сохраняет
  business_id=NULL; raw operator relay и feedback не вызываются этим путём.
- Полный batch описывается и проверяется до файлового I/O: неподдерживаемый элемент,
  недоступный URL, declared oversize или превышение общего бюджета не принимают
  часть ответа. 1 байт–10 МиБ на файл, до 10 файлов на всю историю опроса.
  Фактические bytes ограничены независимо от размера в SDK/HTTP headers.
  Поддерживаются только HTTPS URL доменов userapi.com, vkuserphoto.ru,
  vkuserphoto.net, vkuseraudio.net, vkuser.net, vk.com, vk.ru и их subdomains;
  userinfo, fragments, нестандартные порты и redirects не допускаются. CDN/proxy
  совместимость требует live приёмки; новые домены требуют явного расширения.
- Ключ questionnaires/vk/{channel}/{business-ID|general}/{user}/{catalogSHA}/
  {conversationMessageId}/{slot}/{bytesSHA}.{extension}: private peer совпадает
  с user, slot — стабильный 1-based индекс 1–10. Одинаковые bytes в разных slots
  остаются разными вложениями, повтор одного slot не создаёт второй object.
  Сохраняются реальный тип, очищенное original name, provider conversation ID
  и время исходного VK message. URL/токен скачивания не попадают в session/history.
  Старые text history из четырёх полей читаются с nullable дополнительными полями.
- До скачивания RuntimeConfigService повторно проверяет выбранный scope/catalog.
  Только VK v4 дополнительно требует exact
  X-Iguana-Questionnaire-VK-Media-Version=1 на signed choices/selection forStart.
  Backend R154+ обязателен для нового VK consumer; ACK=4 и прежний media-version=1
  сохранены. Telegram/другие consumers не требуют нового VK header. Catalog/payload
  version, лимит 1000 и source-схема V75 не менялись; новой SQL migration нет.
- Весь принятый batch, ответ и accepted-event ledger сохраняются CAS до prompt
  либо публикации. Ошибка загрузки/storage не продвигает опрос; временная ошибка
  освобождает delivery claim для retry. Ошибка размера предлагает новый ответ.
  JSON restore проверяет owner/catalog/provider/slot/budget и accepted event.
  Prompt failure, duplicate, CAS conflict и queue retry не добавляют повторную
  историю. Frozen publication повторяет точный command без recapture/download.
- Panel до первого root/child INSERT проверяет VK v4, canonical owner/channel/
  user/catalog/provider/slot, тип/имя, бюджет, уникальность slot и реальные
  shared-storage size/SHA. Telegram proof и прежний MAX admission сохранены.
  Metadata сохраняет size/content_hash; existing-root replay не читает заново
  каталог/файлы и не создаёт повторные children.
- BUSINESS/GENERAL scoped reads сверяют frozen root proof, provider/slot и
  metadata size/hash; download сверяет реальные bytes и повторно current READ.
  Старые direct/key/path routes закрыты и для questionnaires/vk, включая Windows
  case/slash/absolute aliases. Общая политика legacy ticket-directory readers
  не расширялась. Активные документы выдаются attachment/octet-stream/no-store/
  nosniff; текущие bindings не определяют владельца старого обращения.
- Validation: 788 Java tests / 77 XML, failures/errors/skips=0;
  backend 128/7, полный fleet 660/70. Core 308 и Telegram 117
  взяты из GREEN modules прогона v2; VK/MAX проверены повтором v3 без повторения
  прошедших SDK/Telegram suites. Backend writer class запущен
  только по 8 media write/read methods, включая 33 прежних Telegram cases;
  остальные исторические writer cases в этом пакете не повторялись. Проверены
  BUSINESS/GENERAL batch/replay/rollback/current READ, повреждённые metadata и
  legacy обходы; реальные isolated PostgreSQL/local_fs, direct JSON/CAS;
  provider downloads/sends — doubles. Полный main/test compile backend и fleet.
- После первого backend GREEN 128 cases дополнительно усилены VK negatives:
  чужие channel/business/general/user/catalog/slot keys существуют в storage с
  корректными bytes/SHA; collision одного slot использует два разных корректных
  content hashes. V3 negative также имеет доступный объект. Это доказывает отказ
  по identity/version/slot, а не только из-за отсутствующего/повреждённого файла.
  Повторены все 70 media writer/read cases; исходный GREEN XML архивирован и
  не добавляется повторно в счётчик. Остальные 58 backend cases не повторялись.
- Первый fleet RED: 4 новых SDK cases повторно расширяли один fixture-каталог,
  создавая дубли. Исправление v2 разделило BUSINESS/GENERAL на независимые cases;
  production catalog validation и существующие assertions не ослаблялись.
  Второй fleet RED: 13 новых VK assertions учитывали profile HTTP в счётчике
  файлов либо ожидали exception вместо существующего stale-catalog UX.
  V3 fixture отделяет profile lookup от скачивания и проверяет restart prompt,
  no-download/no-answer и повторный selection call. Assertions защиты не ослаблены.
  RED logs сохранены; финальные fleet/backend GREEN проверены по XML.
- Detached sandbox .tmp/01-278-r154; baseline HEAD/origin/main/actual remote main
  7ca6a046d123c740cc04cef5d3183b13b447b031, parent
  554ea0288fbe65fd2e7e8cf9cdf004699c4b4acf. Проверены subject и реальные 26 файлов
  R153. Exact file/content guards, report hashes и freshness классов предшествуют
  source apply. Staging/commit/push выполняются отдельным operator по разрешению
  только на 2026-10-21 Europe/Moscow, с проверкой remote и без force.
- Рабочий Docker/проект — на другой машине и в другой сессии агента. Рабочие
  revision/DB/flags здесь неизвестны; deploy/restart, рабочие migrations/backfill,
  config/flags/provider sends не выполнялись. Локальный Docker использовался
  только для isolated Testcontainers. Live S3/Redis/VK/CDN/proxy/browser и rolling
  upgrade acceptance не выполнялись; JavaScript не менялся, Node suites не запускались.
- Далее: Telegram/VK selected followup media, scoped edit/feedback, retention/
  reconciliation, в том числе orphan objects частично загруженных, отменённых
  или конфликтных batches; live fleet/provider/Redis/S3/proxy приёмка и остальные
  S3–S5/S2B/S6 gates. Одновременные бизнес-обращения не добавлялись. Перед выпуском
  нужны совместимые backend/SDK/боты и один shared storage volume либо S3
  domain/bucket/prefix; rollout этим source-пакетом не разрешён.
