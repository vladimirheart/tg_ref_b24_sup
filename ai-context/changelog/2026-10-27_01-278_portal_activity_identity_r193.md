# 01-278 R193 — отметка активности только собственного numeric account

Дата: 2026-10-27. Baseline: 23da82a099132ce34131c9e658f624e2b91f68cd.

Промт пользователя:

> дальше

Контекст: продолжить ID/permission boundaries после R192; production ещё
не запускался, исторические тестовые данные не требуют сохранения/backfill.

## Изменение

- UserLastActivityFilter принимает только authenticated PanelUserPrincipal
  с positive numeric ID и непустым raw username. UPDATE ограничен ID + exact
  username; удалён case-insensitive fan-out и username fallback unbound principals.
- Case collision после входа не отмечает соседний аккаунт online; rename/delete
  между R189 refresh и touch не переносит запись на чужой ID. UTC timestamp,
  best-effort response/chain, retry и cache отсутствующей колонки сохранены.
- Добавлен PortalActivityIdentityWebMvcTest с real activity bean/security chain.
  Прежний UserLastActivityIdentityBoundaryTest использует bound numeric principal
  и проверяет ID + точный username; остальные assertions/error controls сохранены.
- Обновлены task/S0/rule 08. Зафиксированы следующие source-кандидаты workforce
  self check-in и personal/triage preferences, без их изменения в этом шаге.
  Это не новая общая recipient/business policy; grants/schema/principal не меняются.

## Валидация

Meaningful baseline v1: frozen 29 cases / 10 failures / 19 controls / 0 errors;
те же test bytes после fix GREEN. Final Maven v2: 17 suites / 727 tests,
failures/errors/skips=0; прежние 698 cases сохранены. Реальные login/security/
DAO/session/remember-me/activity bean и MockMvc servlet filters, mocked JDBC.
Проверены обе очередности collision, unique raw logins, rename/case rename/
delete race, session revoke, read failure vs activity best-effort/retry/cache,
exactly-one touch, UTC и large IDs (9007199254740993 / Long.MAX_VALUE).
Девять isolated filter cases проверяют legacy/invalid/anonymous principals.
Нет ошибок настройки нового harness и ослабления старых guard assertions.

R192 attested по parent/subject/exact 7 files/content; Java cache используется
после совпадения всех baseline Java sources. Transform replay, exact scope,
source/class/report freshness/hashes, protected R184 checkpoint и generated CSS
no-op blobs проверены в ignored .tmp/r193-* artifacts. Evidence:
.tmp/r193-panel-v1.log (RED), .tmp/r193-panel-v2.log (GREEN),
.tmp/r193-baseline-red.json, .tmp/r193-transform.json, .tmp/r193-tests-green.json.
Browser/Node/Docker/live DB/HTTP/providers не запускались; working PostgreSQL/
JDBC sessions и fleet acceptance не заявляются.

## Среда и границы

Source/test checkout: C:\Users\sushi\Git_H\tg_ref_b24_sup; detached sandbox
.tmp/01-278-r193. Рабочие проект/Docker тестового стенда — на другой машине/
в другой сессии C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Нет runtime probes/
restart/deploy, schema/data reset, migrations/backfill, flags/config/provider sends.
R184 checkpoint и пользовательский start/stop одного бота сохранены точно;
R185–R193 здесь не развёрнуты. Старые changelog-файлы не изменены. Source apply
отделён от publisher с grant до 2026-10-27 21:00 UTC; реальные commit/push
фиксируются после действий в ignored .tmp/r193-result.json. Общий статус — 🟡;
исторический S2B снят R190, новые data flows/S3–S5 и первый запуск S6 ещё нужны.
