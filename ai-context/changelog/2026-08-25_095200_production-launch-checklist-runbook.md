# Production launch checklist runbook

Date: 2026-08-25
Task: 01-192

User prompt:
- "как считаешь, что осталось по проекту чтобы смело его запустить в прод, включая настройки развёртки, безопасности, миграции, общение с внешними сервисами, например по API как приём так и отправку;
да, средние значения по нагрузкам: ежедневно около 3000 обращений и порядка 30 одновременных операторов.

если брать развитие проекта, то какие твои рекомендации?

в целом чего ещё не хватает для мониторинга состояния систем, с которыми классически работает саппорт?"
- "хорошо. создай соответствующие задачи и приступай к их выполнению"
- "забирай"

Changes:
- added a dedicated production launch runbook with go/no-go gates, preflight, deploy sequence, smoke, rollback and first-week follow-up;
- linked the new runbook from the main repository entrypoints so production launch guidance is reachable from README and current project documentation;
- marked task `01-192` as completed by AI and ready for manual review.

Verification:
- `git diff --check`
