# Workspace v1 rollback runbook (dry-run ready)

Цель: безопасно и быстро отключить `workspace_v1` без деплоя, сохранив triage-поток через legacy `dialogDetailsModal`.

## 1. Preconditions
- Есть доступ к настройкам `dialog_config` в панели (`settings bridge`) или через API конфигурации.
- Включён сбор telemetry `/api/dialogs/workspace-telemetry`.
- Дежурный инженер знает окно инцидента и целевые метрики: `workspace_open_ms`, `workspace_render_error`, `workspace_fallback_to_legacy`.

## 2. Fast rollback (target <= 5 минут)
1. Переключить feature flag:
   - `dialog_config.workspace_v1 = false`.
2. Сохранить конфигурацию и выполнить hard-refresh в панели диалогов.
3. Проверить, что открытие тикета больше не вызывает preflight `/api/dialogs/{ticketId}/workspace`.
4. Проверить, что открытие карточки идёт через legacy modal-flow (`dialogDetailsModal`).
5. Зафиксировать в журнале инцидента время отключения флага.

## 3. Validation checklist
- [ ] Новые открытия диалогов работают без редиректа в workspace-маршрут.
- [ ] Quick actions (take/snooze/close) продолжают работать в списке.
- [ ] Нет всплеска 4xx/5xx по `/api/dialogs/*` после rollback.
- [ ] Доля `workspace_fallback_to_legacy` падает к нулю после отключения флага.

## 4. Telemetry snapshot after rollback
Снять срез за окно `[T-30m, T+30m]`:
- p95 `workspace_open_ms`;
- error-rate `workspace_render_error`;
- fallback-rate `workspace_fallback_to_legacy`;
- количество операторов под флагом до/после.

## 5. Dry-run protocol (pre-production)
1. В тестовой среде включить `workspace_v1=true` для 1-2 операторов.
2. Эмулировать отказ workspace-контракта (например, `version_mismatch` или timeout).
3. Запустить шаги Fast rollback.
4. Подтвердить, что время переключения <= 5 минут.
5. Зафиксировать результат dry-run в changelog эксплуатации.

## 6. Post-incident follow-up
- Классификация причины: `contract / performance / permissions / data`.
- Action items:
  - исправление первопричины;
  - добавление guardrail/alert, который сработал слишком поздно;
  - дата повторного canary-rollout.
