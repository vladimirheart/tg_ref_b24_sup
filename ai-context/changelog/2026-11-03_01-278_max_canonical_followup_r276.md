# 01-278 / R276 — canonical MAX followup после потери session

Промт пользователя:

> проверь репо на изменения и продолжи по задаче

Время: 2026-11-03T07:11:02.979Z; Europe/Moscow +03.

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

Изменённые файлы:

- `ai-context/changelog/2026-11-03_01-278_max_canonical_followup_r276.md`
- `ai-context/rules/03-multibusiness-s0-contract.md`
- `ai-context/tasks/task-details/01-278.md`
- `docs/BOT_RUNTIME_CONTRACT.md`
- `java-bot/bot-core/src/main/java/com/example/supportbot/service/PanelTicketReadClient.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/service/TicketService.java`
- `java-bot/bot-core/src/test/java/com/example/supportbot/service/PanelTicketFollowupContextTest.java`
- `java-bot/bot-max/src/main/java/com/example/supportbot/max/MaxWebhookController.java`
- `java-bot/bot-max/src/test/java/com/example/supportbot/max/MaxCanonicalFollowupTest.java`
- `java-bot/bot-max/src/test/java/com/example/supportbot/max/MaxQuestionnaireSelectionTest.java`
- `java-bot/bot-max/src/test/java/com/example/supportbot/max/MaxWebhookControllerTest.java`
- `spring-panel/src/main/java/com/example/panel/service/BotRuntimeTicketReadService.java`
- `spring-panel/src/test/java/com/example/panel/service/BotRuntimeFollowupContextTest.java`
