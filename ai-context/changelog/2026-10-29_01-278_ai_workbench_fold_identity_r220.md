# 01-278 R220 — Unicode-барьер AI-workbench и контролей

Время: 2026-10-29T04:56:25.739Z (UTC; Europe/Moscow +03:00).
Baseline 1afa4c574da45df26ceffa3ef6bd6c186c2f4da6, parent 4c1cdd6eba351d610fcc9b444d499088488b2b49.
HEAD/origin/main/actual remote совпали; новых commits после R219 не обнаружено.
Проверены subject и все 12 paths/blobs/content предыдущего commit.

Промт пользователя:

> репо обновлён. проверь и продолжай по задача

## Изменение

В ScopedDialogAiWorkbenchService и ScopedDialogAiPersistenceService ID/raw-name
JOIN не связывал Java-normalized identity с PostgreSQL lower самого actor.
U+0130/i+U+0307 neighbour мог предоставить READ/status другому ID. READ_ACTOR
теперь требует own enabled/not blocked и lower(actor.username)=n.user_identity;
при mismatch отказ вместо поиска прав другого аккаунта. CAS INSERT и UPDATE
переиспользуют тот же JOIN вместо отдельных копий.

Охвачены 9 actor API/11 SQL branches: resolve/messages/createMemory,
findControl/canOperate/findState/findEvents, обычный control INSERT/UPDATE,
CAS absent/present. Публичные API, нормализация логинов, READ/OPERATE/CONFIGURE,
owner tuple/collision, stamp/review/fingerprints и atomic audit/evidence/event
сохранены. Trusted recordHandoff/handoff record и legacy retrieval не менялись.
Это statement-time защита; response-wide/global lock не заявляется.

Scope 6 files: два main service, новый AiWorkbenchFoldIdentityPostgresTest,
task detail 01-278, S0 contract и этот changelog. UI/schema/dependencies/flags
и прежние tests/assertions/fixtures не менялись; fixture adapters не понадобились.

## Проверки

327 tests /4 suites GREEN, failures/errors/skips=0:
187 новых SQL + Workbench47 + Persistence58 + real-login/CSRF DialogHTTP35.
Baseline RED:187 tests /49 assertion failures /0 errors, 138 controls GREEN.
Те же frozen source bytes, 3 compiled class hashes и 187 case names GREEN.
Cases проверяют active/disabled/blocked/no-rights Unicode neighbour и отсутствие
соседа, exact raw names, READ/OPERATE/CONFIGURE и ALL_BUSINESSES, BIGINT IDs,
late SQL-boundary status, private payload, read-only denial и ID/raw audit.
Исторический R219370/8 этим шагом повторённым не считается.

1355 Java inputs совпали с проверенным R219 cache; новый test compiled v1,
2 main compiled v2, Maven wrapper/JDK17. Deterministic Git replay/inverse,
source/class/XML/log hashes/freshness, точный scope проверены до main apply.
Native PostgreSQL16.15/loopback54167/fresh schemas, explicit ignored R200 supplier
с SHA/version/data-directory guards; tracked helper/base прежние (Testcontainers).
Новый SQL class имеет свою autocommit connection/schema с полным row reset;
connection и owned server закрыты, artifacts сохранены. Docker/Testcontainers
не запускались. Все 83 migration sources прежние; Flyway только test schemas.
JS не rerun. Это selected validation, не весь repository/runtime/provider acceptance.

## Среда и остаток

Source/test C:\Users\sushi\Git_H\tg_ref_b24_sup; рабочий экземпляр на другой
машине/сессии C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Полные R184/R198/R204/
R207/R216 runtime/rollback/V83 blocks сохранены. Последний документированный
runtime R216: webR215/workerR198/foundationOFF; readinessUP, общий health503 из-за
прежнего inboundDLQ=1 остаётся operational gate. Очередь не consume/ack/purge/requeue.
Deploy/restart/working migrations/flags/provider calls здесь не выполнялись.
R217–R220 не в документированном рабочем image; нужен новый panel-web image,
новых migrations нет, V83 не повторять. Старые rollback/backup сохранены.

Далее — аудит BusinessTicketAssignmentService/ParticipantService/WorkService:
bound-ID/raw-name JOIN и name-based READ policy ещё требуют SQL regression;
пока эти paths только прочитаны, не объявляются проверенными или исправленными.
Открыты child/media/feedback/GENERAL, общие task/project/system policies, AI
privacy/context/retention/delivery, channel/fleet/flags/S6 acceptance. S2B снятR190,
Task🟡. Прежнее разрешение пользователя на commit/push до конца29 октября действует;
publisher отделён от source apply/runtime и проверяет expiry/path/content.
Git-факты отдельно в .tmp/r220-result.json и r220-final.json; evidence также
r220-baseline-red.json, r220-java-green.json, r220-validation.json и operators/logs.
