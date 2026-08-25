# Production security hardening and prod tasks

Date: 2026-08-25
Task: 01-193

User prompt:
- "как считаешь, что осталось по проекту чтобы смело его запустить в прод, включая настройки развёртки, безопасности, миграции, общение с внешними сервисами, например по API как приём так и отправку;
да, средние значения по нагрузкам: ежедневно около 3000 обращений и порядка 30 одновременных операторов.

если брать развитие проекта, то какие твои рекомендации?

в целом чего ещё не хватает для мониторинга состояния систем, с которыми классически работает саппорт?"
- "хорошо. создай соответствующие задачи и приступай к их выполнению"

Changes:
- created task backlog `01-192`..`01-197` for production readiness, security, observability, integrations, migration rehearsal and support monitoring;
- hardened `spring-panel` production security contract with explicit remember-me secret, runtime guard for non-default internal API token and fail-fast bootstrap admin rules for external DB mode;
- preserved SQLite/dev bootstrap fallback while requiring explicit bootstrap admin credentials for external production-like contour when no `ROLE_ADMIN` user exists;
- documented new security env vars and added targeted tests for runtime guard and bootstrap behavior.

Verification:
- `spring-panel\mvnw.cmd "-Dtest=PanelSecurityRuntimeGuardTest,SecurityBootstrapTest" test`
