# 01-278 R133: AI карточки, curated MEMORY и knowledge handoff

Checkpoint: 2026-10-19 11:55:34 MSK; sandbox validation GREEN.

Промты пользователя, в порядке поступления:

1. Инициирующий запрос:

```text
проверь изменения в репо и продолжай крупным пакетом.
**остаются семь крупных блоков:**\
**Общие каналы и вопросники.** Довести выбор бизнеса до первого вопроса в Telegram/VK/MAX, подключить нужные шаблоны и локации, сохранять выбранный бизнес и версию вопросника при повторах событий и восстановлении сессии.\
**Оставшиеся пути изоляции.** Передавать бизнес-контекст с момента создания событий, завершить подбор ответственных и привязку дочерних записей. Закрыть права получателей внешних служебных чатов.\
**Обращения «Без бизнеса».** Сейчас есть отдельная очередь чтения; операции записи и отдельный realtime ещё требуют реализации.\
**Задачи, проекты и системные данные.** Завершить правила доступа для общих задач, Kanban, проектов, инцидентов и системных уведомлений; уточнить границу полномочий бизнес- и системного администратора.\
**AI.** Добавить управление AI из карточки обращения, создание проверенного MEMORY из диалога, подключение поиска знаний к обработчику. Затем — изоляция медиа, LLM и доставки клиенту, правила хранения данных и допуска автоответов.\
**Старые данные — S2B.** Согласовать распределение старых обращений по бизнесам, спорные записи и расхождения истории. После этого подготовить проверяемый перенос данных. Часть решений требует владельца бизнеса.\
**Сквозная приёмка и выпуск на другой машине.** Проверить сценарии одного/нескольких/всех бизнесов, отзыв прав, сохранение настроек и работу ботов. Затем отдельно выполнить подготовку выпуска, миграции, включение флагов и проверку отката.
```

2. Продолжение текущего пакета: «продолжи».

Baseline HEAD/origin/main и actual remote main: 0312c10156eff2939299d86ee8119e15611fbab1,
parent ffba7dac1818ed34314c77ec1ced9eaecca3eda6. Commit message и фактические
11 файлов R132 проверены, исходный основной checkout чистый. Source и проверки
выполнены в detached worktree; продолжение учитывает R129–R132, не повторяет их.

Scope — ровно 15 project files:

- `ai-context/rules/03-multibusiness-s0-contract.md`
- `ai-context/tasks/task-details/01-278.md`
- `ai-context/changelog/2026-10-19_01-278_ai_dialog_workbench_r133.md`
- `spring-panel/src/main/java/com/example/panel/controller/ScopedDialogAiController.java`
- `spring-panel/src/main/java/com/example/panel/service/ScopedAiHandoffKnowledgeService.java`
- `spring-panel/src/main/java/com/example/panel/service/ScopedDialogAiWorkbenchService.java`
- `spring-panel/src/main/java/com/example/panel/service/ScopedDialogAiPersistenceService.java`
- `spring-panel/src/main/java/com/example/panel/service/ScopedAiSourceService.java`
- `spring-panel/src/main/java/com/example/panel/service/DialogAiAssistantMessageFlowService.java`
- `spring-panel/src/main/resources/db/migration/postgresql/V64__scoped_ai_handoff_evidence.sql`
- `spring-panel/src/main/resources/templates/businesses/dialog-ai.html`
- `spring-panel/src/main/resources/templates/businesses/dialogs.html`
- `spring-panel/src/main/resources/static/js/business-dialogs.js`
- `spring-panel/src/test/java/com/example/panel/controller/ScopedDialogAiHttpPostgresTest.java`
- `spring-panel/src/test/java/com/example/panel/service/ScopedDialogAiWorkbenchPostgresTest.java`

Из BUSINESS карточки открывается отдельная server-rendered AI-страница по public
locator, без raw owner/user ID в model. Foundation/uiSelector, PAGE_DIALOGS,
live READ, no-store и escaping обязательны. State и metadata events доступны
на чтение; control mutation требует OPERATE, CSRF и final owner/account/grant
proof. Expected business и timestamp CAS запрещают overwrite старой формой;
control и audit атомарны. GENERAL/UNRESOLVED/коллизии не открывают этот UI.

Создание MEMORY требует также PAGE_KNOWLEDGE_BASE и CONFIGURE. Выбирается
composite-owned пара client question / subsequent human operator answer;
NULL-owner/unlinked, AI, deleted и attachments исключены. Current root и
SHA-256 полного текста обеих строк повторно доказываются внутри INSERT.
Curator вводит очищенный текст вручную и подтверждает проверку PII/secrets;
автоматической PII-проверки нет. Создаётся только DRAFT, отдельный каталог
R132 выполняет review/approval. Source/audit/evidence atomic, повтор key закрыт.
V64 evidence хранит ID/fingerprints, не raw диалог. Историческая business/
generation атрибуция всех child rows НЕ решена: используется existing composite
history contract, без backfill/классификации старых данных. Позднее изменение
history не переписывает curated source; retention остаётся отдельным gate.

BUSINESS incoming использует frozen V61 scope для trusted-worker поиска
APPROVED того же business с current MEMORY origin, literal query <=500,
LIMIT 3 после фильтров. В одном SQL атомарно записываются state escalate_only,
handoff event и source ID/revision references. Query/title/body не копируются
в event/evidence; отсутствие кандидатов не отменяет human handoff. Global
ai_agent_enabled и scoped ai_disabled сохраняются. UNRESOLVED имеет только
прежний compatibility handoff до strict. Raw media/retrieval/state/LLM/outcome
и клиентский auto-reply не открываются. Existing actor retrieval API отдельный.
UI reproof live READ/owner/control и exact source revision/approval/origin
скрывает stale references; PAGE_KNOWLEDGE_BASE обязателен. ALL не расширяет
поиск до чужого бизнеса; ссылку нельзя считать фактом доставки или auto-reply
permission. V64 добавляет только две отдельные immutable-update evidence tables.

Валидация: итог 332/332 GREEN в 16 свежих Surefire XML, failures/errors/skips=0.
Новые SQL/flow/upgrade 47, новый real HTTP/Thymeleaf/security 17; source SQL 82,
каталог HTTP 25, persistence 58, ingress 53, migration 2, raw AI/exact IDs 41,
page/navigation contracts 7. Full Spring/Flyway V1–V64 и upgrade V63→V64,
main/test compile, node --check и diff check GREEN. XML freshness проверена
относительно final production Java/templates/SQL и собственной suite.
Проверены late revocation/owner/history mutations, SHA-256, role/grant matrix,
CSRF cookie roundtrip/denial, stale controls, atomic rollback, draft/review,
reference version/origin reproof и отсутствие raw/global/media/LLM fallback.

До GREEN были RED: 4 positive MEMORY failures во время перехода fingerprint
protocol на SHA-256/64 и перекомпиляции; после согласованного rebuild SQL 47/47.
Далее 1 HTTP failure и 2 fixture errors (parameter override, GENERAL CHECK и
delete FK). Исправлены fixtures, сохранение reason проверяется явно. Общий
332-case run дал 315 GREEN и 17 HTTP context errors: предыдущий HTTP fixture
оставлял tickets без messages в общей DB, production readiness корректно
отказал. Новый HTTP test получает отдельную schema с обычным JDBC String type
и полными migrations, без отключения readiness/security. Оба HTTP набора
повторены вместе 42/42 GREEN; остальные 290 reports уже свежие/GREEN и не
повторялись. Initial failure XML и shared-schema failed log сохранены в ignored
target. Production guards не ослаблялись.

Перенос допускается только после exact-set/SHA-256 проверки и replay того же
deterministic patch в ещё одном чистом worktree. Generated Sass CSS исключается.
Staging/commit/push/deployment, рабочие DB migrations/backfill/config/flags
не выполнялись. Testcontainers PG16, внешние providers/боты не вызываются;
browser/live-provider E2E и runtime на другой машине не проверялись/не менялись.

Scoped media/LLM/client delivery, privacy/retention, ranking и auto-reply policy,
producer context, assignment pool/child attribution, external chat recipients,
GENERAL write/realtime, global page/system/task policy, shared-channel UX/resume,
S2B owner decisions и сквозная приёмка/выпуск остаются PENDING. Статус задачи 🟡,
новых flags нет, strict scope/rollout не разрешены.
