# Changelog

## 2026-08-31 18:45:00

- в `scripts/docker-production-credential-migration-apply.ps1` добавлены orchestration-флаги `-Components`, `-Component all`, `-Rehearsal` и `-BackupDirectory`;
- в `scripts/docker-production-credential-migration-apply.sh` добавлены orchestration-флаги `--components`, `--component all`, `--rehearsal` и `--backup-dir`;
- реализован bulk orchestration поверх существующих per-component flows с каноническим порядком `postgresql -> rabbitmq -> redis -> minio -> grafana`;
- добавлен pre-apply snapshot hook с файлами `env.before`, `component-order.txt` и `docker-ps.txt`;
- обновлены runbook и source-contract под новый supported contour;
- задачи `01-226` и `01-227` переведены в завершённое состояние.

## User prompt

> продолжай
