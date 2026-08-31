# Changelog

## 2026-08-31 18:25:00

- добавлен `scripts/docker-production-credential-migration-apply.sh` с Bash parity для `postgresql`, `rabbitmq`, `redis` и `minio`;
- реализованы dry-run/apply, rollback checkpoint и coordinated recreate для Bash entrypoint;
- добавлена защита MinIO probe в Git Bash через `MSYS_NO_PATHCONV=1` и `MSYS2_ARG_CONV_EXCL='*'`;
- обновлены runbook и source-contract тест под два entrypoint: PowerShell и Bash;
- задача `01-225` переведена в `🟡`, создан follow-up `01-226` для Grafana apply-path и multi-component rehearsal/bulk rotation.

## User prompt

> хорошо. давай дальше
