# 01-278 — точные SLA preview/audit identities, R179

Дата: 2026-10-26 (Europe/Moscow).

Промт пользователя:

> продолжай

## Baseline и среда

Baseline R179 (2026-10-26): clean main, HEAD/origin/main/actual remote
a687d6b7312a4cc41879c664f103ebee2dda2288. После R178 появились три независимых
коммита: incident relations, multiline и server pagination диалогов. R178
3629b7220 проверен по parent/subject/exact 7 paths/content SHA; все его файлы
сохранены в новом baseline. Detached sandbox .tmp/01-278-r179.
Рабочие проект и Docker находятся на другой машине/в другой сессии. Здесь
source/test checkout и изолированные Maven unit/service tests; Docker/browser/
Node в R179 не запускались. Рабочие revision/DB/config/flags/providers неизвестны
и не менялись; restart/deploy/migration/backfill не выполнялись. Продлённый
grant commit/push действует до 2026-10-26 21:00 UTC (27 октября 00:00 +03).
Source apply и отдельный Git publisher разделены, с baseline/content/file/remote
и date guards; разрешение на публикацию не означает приёмку рабочего экземпляра.

## Изменение

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

## Проверки

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

## Передача

- ai-context/changelog/2026-10-26_01-278_sla_identity_preview_audit_r179.md
- ai-context/rules/03-multibusiness-s0-contract.md
- ai-context/tasks/task-details/01-278.md
- spring-panel/src/main/java/com/example/panel/service/SlaEscalationCandidateService.java
- spring-panel/src/main/java/com/example/panel/service/SlaEscalationWebhookNotifier.java
- spring-panel/src/main/java/com/example/panel/service/SlaRoutingPolicyDecisionPayloadService.java
- spring-panel/src/main/java/com/example/panel/service/SlaRoutingPolicySnapshotDialogStateService.java
- spring-panel/src/main/java/com/example/panel/service/SlaRoutingRuleDefinitionFactoryService.java
- spring-panel/src/main/java/com/example/panel/service/SlaRoutingRuleMatchNormalizerService.java
- spring-panel/src/main/java/com/example/panel/service/SlaRoutingRuleScalarParserService.java
- spring-panel/src/test/java/com/example/panel/service/SlaRoutingIdentityBoundaryTest.java

Guarded source apply переносит ровно 11 файлов после GREEN и завершается без
staging/commit/push. Отдельный publisher использует действующий суточный grant.
Фактические source_applied/committed/pushed фиксируются в ignored
.tmp/r179-result.json и итоговом ответе. Deployment=false; общая задача открыта.
