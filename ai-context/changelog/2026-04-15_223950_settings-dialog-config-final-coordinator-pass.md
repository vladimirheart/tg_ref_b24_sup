# 2026-04-15 22:39:50 - settings dialog config final coordinator pass

## Что сделано

- добавлен `SettingsDialogTemplateConfigService` для template/macro governance и
  macro variable catalog settings;
- добавлен `SettingsDialogRuntimeConfigService` для базовых dialog runtime
  настроек (`time_metrics`, `default_view`, poll intervals, snooze/overdue`);
- `SettingsDialogConfigUpdateService` дожат до coordinator/router слоя и больше
  не держит внутри template/macro governance и runtime update-логику;
- `SettingsDialogWorkspaceConfigService` очищен от macro-catalog responsibility,
  чтобы остаться workspace-oriented service.

## Проверка

- `spring-panel\mvnw.cmd -q -DskipTests compile`

## Эффект

- `dialog_config` больше не обновляется через один giant service даже внутри
  внутреннего settings-domain;
- задача `01-024` доведена до логической точки завершения для AI: foundation,
  dialogs split, settings split и UI runtime governance уже лежат в коде.
