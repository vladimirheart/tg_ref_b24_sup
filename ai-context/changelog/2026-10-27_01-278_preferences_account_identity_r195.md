# 01-278 R195 — личные UI/triage preferences по ID аккаунта

Дата: 2026-10-27, 16:56 +03 (Europe/Moscow).
Baseline: cd0f175dfe591c302f65dfe790860be412e116fe.

Промт пользователя:

> давай дальше

## Контекст и scope

Свежие HEAD/origin/main/actual remote совпали; рабочий checkout clean.
Проверены R194, parent 13d1a6e7aec15203a1ea21394b2dad24f736f7ca
и точные 12 paths предыдущего commit. Продолжен подтверждённый в R194
personal/triage identity gap. Historical S2B исключён решением R190;
это не разрешение на reset или неутверждённые global/business policies.

- UiPreferenceService использует typed Authentication, bound positive numeric ID,
  exact raw username/principal status и users row guard. Storage namespace —
  ui_preferences.account.v2 с каноническим decimal ID в value. Нет trim/lower
  lookup, v1/name fallback или shared-config auto-import. Старые записи не удалены.
- Account FOR SHARE/FOR UPDATE и REQUIRES_NEW read/merge/write сериализуют
  cooperating UI/triage writers. UPDATE проверяет исходный JSON, type/key,
  active row и affected count. Corrupt/duplicate rows дают 409, ошибки SQL
  чтения/записи — 503; после simulated post-INSERT driver failure запись
  откатывается. Normalization, partial merge, unknown fields и UTC сохранены.
- Profile API, triage controller, global model bootstrap и workspace service/
  snapshot передают Authentication. Saves/profile storage строгие; unbound
  optional workflow/bootstrap даёт пустые defaults без storage access.
  Настоящий bound аккаунт с логином anonymousUser не блокируется по имени.
- Весь ui_preferences.* закрыт от generic settings parameters CRUD/list и
  /settings model даже для admin. Обычные параметры и существующие gates сохранены.
- Новых migrations, schema, grants, dependencies, JS/CSS/template или feature flags
  нет. После будущего rollout v2 начинается с defaults без переноса old name-keyed
  preferences; для тестовых данных это соответствует R190.
- Это не вся workspace participant/candidate identity и не новая policy
  recipient/manual/global/system/business доступа.

Scope: 9 production Java files, 7 адаптированных existing test files,
2 новых test files, task details, S0 contract, rule 08 и этот новый changelog —
22 tracked paths. Прежние assertions не удалены и не ослаблены.
Fixtures получают bound Authentication и v2 rows; старый workspace shared-config
fixture оставлен, но не импортируется. Исторические checkpoint не переписывались.

## Валидация

Meaningful baseline RED v2: 32 frozen MVC tests / 26 assertion failures /
6 controls / 0 errors/skips. Те же test bytes GREEN.
Frozen source SHA256:
8ba90fd1094d469868b584c6746067180d549815dd782abcd2777cb3004b3fba.
V1 нового harness дал 24 failures и 2 errors (нет ожидаемой новой строки в
queryForObject, dotted JSONPath); исправлены только новые assertions.
V1 не засчитан как meaningful RED. Production baseline для RED не менялся.

Реальные SecurityConfig/form login/DAO/session refresh/CSRF и PostgreSQL:
numeric ownership, raw/padded/Unicode/anonymousUser names, case collision после
входа, rename/reused name, ID >2^53/Long.MAX_VALUE, legacy non-import, base/triage
merge, corrupt/duplicate rows, invalid direct principals, bootstrap, private
namespace CRUD/list, revoke и missing CSRF. Working users/grants не менялись.

UiPreferenceIdentityPostgresqlTest: 14 cases на отдельном PostgreSQL,
включая 5 раундов concurrent first base+triage saves, 6 account/status guards,
read failure, post-INSERT rollback, soft delete и 4 noncanonical keys.

Первый focused GREEN: 68 tests / 7 suites. Итоговый Maven v2:
30 suites / 704 tests, failures/errors/skips=0, exit 0.
R184 channel policy, R188–R194 auth/session/password/admin/activity/workforce,
existing preference/controller/source contracts включены.
DialogWorkspaceIntegrationTest проверен только методом
workspaceApiProjectsOperatorWorkflowSnapshotWithCollaborationAndTriagePreferences,
не всем классом. В этом тесте 82 существующие Flyway migrations прошли только
в изолированной test PostgreSQL; рабочая БД/контур не подключались.

Native Maven workspace /r195 в точном временном контейнере iguana-r195-tests,
не Windows bind для source. Все 18 Java sources сверены по SHA256 с sandbox.
Reports: .tmp/r195-red-v2-reports, .tmp/r195-green-v1-reports,
.tmp/r195-green-v2-reports; логи с теми же prefixes.
Sandbox: .tmp/01-278-r195-preference-identity.
Final patch replay, normalized content equality, pinned blobs/exact paths и
git diff --check проверяются перед source apply; результат — .tmp/r195-result.json.
Это не browser/fleet/JDBC session-store или post-deploy acceptance R195.

## Среда, ограничения и остатки

Текущая source/test сессия — C:\Users\SinicinVV\git_h\tg_ref_b24_sup,
на машине тестового стенда. Свежий read-only probe подтвердил прежний R184 web:
container 9fb4a33fd8a45f037bcccb2880af86067e3eda1e7cdc3db799bc413cdfeb251a,
revision 8310d1f7f0001314abdc60e52516e563168bfee1,
image sha256:f2513264a171a8973125a243dbaaf3885fa9161194d5f962d6df7801991253bd,
StartedAt 2026-10-27T06:20:48.302422781Z, healthy, restart count 0.
Rollback tag iguana-panel:rollback-panel-web-r184-20261027-1a8f1c4f2ca6
по-прежнему указывает на sha256:1a8f1c4f2ca630fb15314fcba23123fadc00b2c79bf5cd04db58420679e16733.
R184 checkpoint/override сохранены. R185–R195 не развёртывались.
Нет Compose build/up/restart, рабочих migrations/backfill/reset, изменения
flags/config/roles/grants/providers или отправок клиентам. Временный Maven
helper и отдельные Testcontainers PostgreSQL/Ryuk не являются rollout стенда.
Staging/commit/push не выполнялись; команды ручной фиксации не выдаются.

Следующий подтверждённый identity-кандидат:
EmployeeDiscountAutomationCredentialService, username.trim/lower(value) LIMIT 1,
связанный CredentialRotationRegistryService. Они в R195 не менялись.
Обновлён текущий остаток в task details: scoped S3–S5 flows/pickers, каналы/
вопросники/retry/restore, no-business writes/realtime, дочерние записи и
получатели, task/project/incident/system policy, AI/media/LLM/delivery,
bot media/actions/feedback и сквозная/fleet приёмка/первый production S6.
Общий статус 01-278 остаётся 🟡; historical S2B не возвращается в план.
