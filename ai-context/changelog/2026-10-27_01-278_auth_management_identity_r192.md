# 01-278 R192 — numeric self identity и field permissions управления пользователями

Дата: 2026-10-27. Baseline: 8946949ea2e06f33977c6c52cde375c41e68f5de.

Промт пользователя:

> продолжай по задаче

Контекст: продолжить оставшиеся проверки доступа после R191; production ещё
не запускался, старые данные тестовые, historical S2B снят с обязательного плана.

## Изменение

- AuthManagementApiController.resolveCurrentUserId возвращает positive bound
  ID из authenticated PanelUserPrincipal. Удалён case-insensitive username lookup.
  Self profile/password/photo, current_user_id/is_self и self delete/block
  guards больше не выбирают другой аккаунт после появления case-fold collision.
- Role field permissions non-superuser читаются по тому же ID с exact stored
  username/status/row-count checks. Missing/changed/ambiguous rows и malformed
  permissions дают пустой набор; DB error не открывает username fallback.
- Avatar cleanup выполняется только если photo входит в разрешённый UPDATE.
  Разрешённое редактирование роли с запрещённым photo больше не удаляет файлы.
- Добавлен AuthManagementIdentityWebMvcTest. Два self principals старого fixture
  стали numeric/authenticated; старые assertions сохранены. Преобразования main/
  fixture воспроизводятся точно по baseline manifest. Обновлены task/S0/rule 08.
- Page gates, global admin/portal bypass и field matrix сохранены. R192 не
  вводит новых grants, schema или CAS для всех admin CRUD. R189 session checks
  сохраняются; общая revocation policy и полная business изоляция вне этого шага.

## Валидация

V1: 61 cases / 43 failures / 6 errors / 12 controls. Шесть direct helper calls
попали в method-security proxy без SecurityContext; для них используется AOP
target. HTTP cases по-прежнему проходят реальную method security. Добавлены
два avatar-cleanup cases в связанный scope. V1 harness/log/XML сохранены;
его errors не считаются meaningful security RED, production был baseline.
V2 сохранил 49 failures / 14 controls / 0 errors. В общем v3 единственный failure
нового теста был JSONPath list assertion при корректных current_user_id/is_self
в payload. После замены на четыре явных ID/flag assertions повторён baseline
v4 с теми же 49 failures / 14 controls / 0 errors; production guards неизменны.
Исходные v2 freeze и v3 XML/log/test bytes сохранены отдельно.

Meaningful baseline v4: frozen 63 cases / 49 failures / 14 controls / 0 errors;
те же test bytes после fix GREEN. Final Maven v5: 16 suites / 698 tests,
failures/errors/skips=0, прежние 635 cases сохранены. По 13 field actions
проверены отказ в чужих правах и допуск своих, плюс self state/profile/password,
own/foreign photo, self delete/block и legitimate foreign admin actions,
role read anomalies/failure, raw logins, unbound principals, page/global bypass,
native CSRF и anonymous. Real login/security/method security/session/encoder,
mocked JDBC/shared config/photo adapters; шесть direct AOP-target helper cases.

R191 attested по parent/subject/exact 9 files/content; все baseline Java sources
совпадают с кешем classes. Exact changed-file set, source/class/report hashes и
freshness, transform replay, protected R184 checkpoint и CSS no-op blobs проверены
в ignored .tmp/r192-* artifacts. Evidence: .tmp/r192-panel-v4.log (baseline RED),
.tmp/r192-panel-v5.log (GREEN), .tmp/r192-baseline-red-v4.json,
.tmp/r192-transform.json, .tmp/r192-tests-green.json. Browser/Node/Docker/live DB/
HTTP/providers не запускались; working PostgreSQL/JDBC sessions/files не проверены.

## Среда и границы

Source/test checkout: C:\Users\sushi\Git_H\tg_ref_b24_sup; detached sandbox
.tmp/01-278-r192. Рабочие проект/Docker тестового стенда — на другой машине/
в другой сессии C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Нет runtime probes/
restart/deploy, schema/data reset, migration/backfill, flags/config, provider
sends, session cleanup или рабочего avatar I/O. R184 checkpoint и ручной
start/stop одного бота сохранены точно; R185–R192 здесь не развёрнуты.
Старые changelog-файлы не изменены. Source apply отделён от publisher с grant
до 2026-10-27 21:00 UTC; факты commit/push — после действий в ignored
.tmp/r192-result.json. Общая задача остаётся 🟡; новые data flows/S3–S5,
future policies, fleet acceptance и первый запуск S6 с чистой базой ещё нужны.
