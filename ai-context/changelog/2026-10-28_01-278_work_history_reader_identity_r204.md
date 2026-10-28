# 01-278 R204 — BUSINESS work history читает bound account

Дата: 2026-10-28, checkpoint 10:19 +03.
Baseline: 53e858df0ed8a6de61855fef0c56f2ee9decfdbd (R203); parent 0845e8d482d32a909cea90140ec303d95ed3a3c5.

Промт пользователя:

> пришли изменения в репо. нужна новая сборка panel web. но перед этим вополни следующую часть задачи, если сначала нужна пересборка, то пересобери

## Фактическое изменение

Два main files: BusinessTicketWorkReadService.history принимает Authentication,
controller передаёт тот же объект. Требуется authenticated non-anonymous
PanelUserPrincipal, positive ID, exact raw name и корректный principal status.
Один SQL связывает account ID/raw name/enabled/non-blocked с прежним BUSINESS
READ/ambiguity/active business. Нет выбора нового reader по reused name и нет
String-only API. Pagination, full-owner audit, immutable categories/actor, BIGINT
strings, headers, PAGE_DIALOGS/foundation и HTTP 400/404/503 сохранены.
Work writers/find/recent, assignment/participant readers и AI не менялись.
Нет schema/grants/dependencies/UI changes; atomic in-flight revocation за пределами
одного SQL snapshot не заявляется.

Два старых fixture files адаптированы к Authentication; assertions/case names
сохранены, VIEWER login fixture передаёт canonical stored viewer. Два новых tests:
BusinessWorkHistoryIdentityPostgresTest (31) и BusinessWorkHistoryIdentityWebMvcTest
(9), с настоящим login/session filter и SQL. Account rename/delete/reuse/case
инъекции в HTTP происходят после R189 refresh перед service SQL.

## Проверки и применение

40 frozen новых cases: baseline 16 failures / 0 errors/skips; те же bytes GREEN.
601 tests / 12 suites GREEN (349 полностью завершённых cases первого run плюс
252 отдельного ограниченного run); 15 BUSINESS Node scripts GREEN. Первый общий
run прерван и не объявляется успешным. Source/native compiled inputs оставались
теми же; второй run использовал surefire goal без перекомпиляции. Все шесть
code/test SHA совпали в detached candidate, replay, main и native builder.
Два main class image bytecode совпали с tested classes. Backend — штатный
Testcontainers PostgreSQL 16, без native adapter. Flyway только в тестовых схемах,
83 migration sources неизменны.

Сначала detached .tmp/01-278-r204-work-history, затем точный replay в
.tmp/01-278-r204-replay и тот же apply_patch в checkout. Source-applied=true;
staged/committed/pushed=false. Общий статус 🟡. Source scope: 2 main + 2 existing
fixtures + 2 new tests + task/S0/этот changelog. Runtime и memory recovery —
отдельная запись rollout R204. Следующие границы перечислены в task details.
