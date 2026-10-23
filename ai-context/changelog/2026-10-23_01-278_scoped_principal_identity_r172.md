# 01-278 — точная identity в BUSINESS и AI операциях, R172

Время: 2026-10-23 07:40:43 +03:00 (Europe/Moscow).

Промт пользователя:

> Продолжай. разрешение на пуш и коммит расширяю на сегодняшний день

Client date: 2026-10-23, Europe/Moscow. Предыдущий контекст рабочей среды:

> напоминаю, что рабочий экземпляр проекта на другой машине.

## Baseline и среда

Baseline R172: HEAD/origin/main/actual remote main
8fe01c7a4cf28e3ad80316b894732345c919fa63; parent
d13b428b65acc969f9be458302eec63820271ac3. R171 subject и фактические
12 файлов (742 additions/6 deletions) подтверждены; main/index исходно чистые.
Detached sandbox .tmp/01-278-r172. Рабочий экземпляр и Docker находятся на
другой машине и в другой сессии. Здесь source и изолированные PostgreSQL
Testcontainers; рабочие revision/DB/config/flags/providers не проверялись и
не менялись. Нет рабочего restart/rebuild/deploy/migration/backfill.

Новый явный grant пользователя 2026-10-23 Europe/Moscow продлевает commit/push
на эти сутки: [2026-10-22 21:00 UTC, 2026-10-23 21:00 UTC), expiry
2026-10-24 00:00 +03. Разрешение 22 октября истекло и не служит основанием.
Source apply и publisher разделены; Git проверяет expiry/exact baseline/files/
content/actual remote, без force. Рабочий rollout этим не разрешён.

## Исправлено

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

## Проверки

Проверки R172: 483 Java tests / 8 свежих XML GREEN,
failures/errors/skips=0, финальный Maven exit 0. Evidence: .tmp/r172-panel-v2.log (6 preserved GREEN suites) + .tmp/r172-panel-v3.log (2 corrected GREEN suites).
До исправления отдельный v1 воспроизвёл проблему на неизменных production
sources: 5/5 identity cases RED, failures=5/errors=0/skips=0, с видимым доступом
через все шесть сервисов. Original RED XML/log, baseline/source/test SHA сохранены
в ignored .tmp/r172-baseline-report.xml, r172-panel-v1.log и r172-baseline-red.json.
В финальном наборе те же assertions GREEN. Новый PostgreSQL suite: 19 cases —
чтение/отказ записи для leading/trailing/both spaces/tab/newline, настоящие
похожие accounts разных бизнесов, upper/mixed case, собственный literal spaced
account и его numeric audit, revoke/disable/block/ambiguity/archive/schema failure.
Перед/после denied operations сверяются полные rows accounts/grants/root/audit/
roster/outbox/AI state/control/sources/evidence, без появления успешных записей.
Добавлены 15 real HTTP cases с действующим CSRF/security: workbench/commands,
AI dialog/control/MEMORY и source read/create/approve; denied namespace и
положительные операции spaced account подтверждены на полной schema.
Две прежние role matrices использовали " OPERATOR " как положительный вход.
В v2 ровно 8 cases каждой матрицы стали RED на этом ожидании; errors/skips=0.
Для положительных ролей сохранён uppercase canonical "OPERATOR"; все прежние
READ/OPERATE/CONFIGURE/audit expectations сохранены, добавлены явные отказы
whitespace aliases и проверка сохранности control rows. Это не blanket deny
spaced accounts: отдельные реальные accounts проходят positive service/HTTP cases.
Итог — шесть сохранённых GREEN suites v2 и две полные исправленные suites v3;
exact sources/classes/XML шести suites и всех production classes неизменны после
rerun. Прежние security/CAS/owner/generation/snapshot assertions не ослаблены.
Source/class/XML freshness и exact generated CSS baseline blobs сверены.
JS/templates не менялись, отдельный Node/browser прогон не выполнялся.
Live Redis/proxy/provider и рабочая приёмка не выполнялись. Статус 01-278 — 🟡.

## Изменённые области и передача

- Семь principal SQL bindings в шести существующих BUSINESS/AI сервисах.
- Новый PostgreSQL identity suite; три real HTTP suites дополнены защитными и
  positive literal-account cases. Production UI и migration chain не менялись.
- Текущая сводка задачи и S0-контракт; старые changelog не переписывались.

Guarded source apply — только после итогового sandbox GREEN, exact baseline,
clean main/index, actual remote, source/test SHA и точного списка 15 файлов.
Git publisher отдельный и использует новый grant 23 октября. Фактические source/
commit/push/deployment состояния — в ignored operator result и финальном ответе.
Рабочий stack не затронут; задача остаётся 🟡. Следующий связанный аудит —
нормализация идентичности в SSE/bell/outbox и global/system paths; R172 не
объявляет эти пути безопасными и не разрешает их рабочую приёмку заочно.
