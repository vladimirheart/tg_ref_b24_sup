# Support systems monitoring roadmap

Date: 2026-08-25
Task: 01-197

User prompt:
- "как считаешь, что осталось по проекту чтобы смело его запустить в прод, включая настройки развёртки, безопасности, миграции, общение с внешними сервисами, например по API как приём так и отправку;
да, средние значения по нагрузкам: ежедневно около 3000 обращений и порядка 30 одновременных операторов.

если брать развитие проекта, то какие твои рекомендации?

в целом чего ещё не хватает для мониторинга состояния систем, с которыми классически работает саппорт?"
- "хорошо. создай соответствующие задачи и приступай к их выполнению"
- "делай"
- "дальше"

Changes:
- added a dedicated roadmap for monitoring classic support systems, delivery channels, external SaaS/API dependencies and corporate infra dependencies around Iguana;
- fixed the target coverage model, rollout waves, priority map, alert routing model and implementation backlog for missing signal sources such as backups, DNS/TLS, SMTP, token expiry and operator access path;
- linked the roadmap from the main documentation entrypoints and observability baseline, then marked task `01-197` as completed by AI and ready for manual review.

Files and areas:
- `docs/support-systems-monitoring-roadmap.md`
- `docs/observability-baseline.md`
- `docs/CURRENT_PROJECT_DOCUMENTATION.md`
- `README.md`
- `ai-context/tasks/task-list.md`

Verification:
- `git diff --check`
