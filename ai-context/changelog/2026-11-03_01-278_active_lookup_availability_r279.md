# 01-278 / R279 — доступность поиска active ticket

Промт пользователя:

> продолжай по задаче

Дата:2026-11-03, Europe/Moscow +03.

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

Изменённые файлы:

- `ai-context/changelog/2026-11-03_01-278_active_lookup_availability_r279.md`
- `ai-context/rules/03-multibusiness-s0-contract.md`
- `ai-context/tasks/task-details/01-278.md`
- `docs/BOT_RUNTIME_CONTRACT.md`
- `java-bot/bot-core/src/main/java/com/example/supportbot/service/PanelTicketReadClient.java`
- `java-bot/bot-core/src/test/java/com/example/supportbot/service/PanelActiveTicketAvailabilityTest.java`
- `java-bot/bot-max/src/test/java/com/example/supportbot/max/MaxActiveTicketAvailabilityTest.java`
- `spring-panel/src/main/java/com/example/panel/controller/BotRuntimeReadApiController.java`
- `spring-panel/src/test/java/com/example/panel/controller/BotActiveTicketAvailabilityWebTest.java`
