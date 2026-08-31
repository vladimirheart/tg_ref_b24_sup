# Changelog

## 2026-08-31 18:30:00

- расширены `scripts/docker-production-credential-migration-apply.ps1` и `.sh` на controlled apply/dry-run для `grafana`;
- добавлены live auth preflight, `grafana cli admin reset-admin-password`, recreate и best-effort rollback для Grafana;
- обновлён runbook persisted credential rotation с Grafana support и ручным rehearsal порядком для multi-component cutover;
- обновлён source-contract тест под новый supported contour;
- задача `01-226` переведена в `🟡`, создан follow-up `01-227` для bulk rotation и automated rehearsal flow.

## User prompt

> продолжай
