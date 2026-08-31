# Changelog

## 2026-08-31 18:05:00

- расширен `scripts/docker-production-credential-migration-apply.ps1` на controlled apply/dry-run для `redis` и `minio`;
- добавлены live verify/recreate/rollback шаги для Redis и MinIO, включая ожидание `minio-init` и bucket access probe через `minio/mc`;
- apply workflow начал автоматически подключать observability overlay при уже поднятом monitoring-контуре;
- обновлены runbook и source-contract тест для нового охвата persisted credential rotation;
- задача `01-224` переведена в `🟡`, создан follow-up `01-225` для Bash parity, Grafana apply-path и bulk/rehearsal rotation.

## User prompt

> хорошо. давай дальше
