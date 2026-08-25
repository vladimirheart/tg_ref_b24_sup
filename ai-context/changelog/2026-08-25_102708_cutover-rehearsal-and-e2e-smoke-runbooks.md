# Cutover rehearsal and E2E smoke runbooks

Date: 2026-08-25
Task: 01-196

User prompt:
- "как считаешь, что осталось по проекту чтобы смело его запустить в прод, включая настройки развёртки, безопасности, миграции, общение с внешними сервисами, например по API как приём так и отправку;
да, средние значения по нагрузкам: ежедневно около 3000 обращений и порядка 30 одновременных операторов.

если брать развитие проекта, то какие твои рекомендации?

в целом чего ещё не хватает для мониторинга состояния систем, с которыми классически работает саппорт?"
- "хорошо. создай соответствующие задачи и приступай к их выполнению"
- "делай"
- "давай дальше"

Changes:
- added a dedicated PostgreSQL cutover rehearsal runbook with step-by-step migration rehearsal flow, reconciliation SQL, acceptance gates and rollback checkpoints;
- added a dedicated production E2E smoke runbook covering readiness, ingress, operator reply, attachment path, incident/transport surfaces and post-launch rollback checkpoints;
- linked both runbooks from the primary documentation entrypoints and marked task `01-196` as completed by AI and ready for manual review.

Files and areas:
- `docs/runbooks/postgresql-cutover-rehearsal.md`
- `docs/runbooks/production-e2e-smoke.md`
- `docs/runbooks/production-launch-checklist.md`
- `docs/CURRENT_PROJECT_DOCUMENTATION.md`
- `README.md`
- `ai-context/tasks/task-list.md`

Verification:
- `git diff --check`
