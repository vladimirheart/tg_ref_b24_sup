# 2026-04-15 22:14:20 - settings dialog config subdomain split

## Что сделано

- `SettingsDialogConfigUpdateService` переписан как coordinator/router слой для
  `dialog_config`, а не как giant монолитный update-method;
- добавлен `SettingsDialogSlaAiConfigService` для SLA/AI и базовых dialog
  runtime-настроек;
- добавлен `SettingsDialogWorkspaceConfigService` для workspace rollout,
  external KPI, client context и related workspace-настроек;
- добавлен `SettingsDialogPublicFormConfigService` для public form и
  `summary_badges`;
- giant guard по сотням `payload.containsKey(...)` заменён на доменный router по
  ключам и префиксам.

## Проверка

- `spring-panel\mvnw.cmd -q -DskipTests compile`

## Эффект

- split домена `settings` продолжился уже внутри `dialog_config`, а не только на
  уровне controller/service boundary;
- дальнейший рефакторинг `workspace` и `public form` теперь можно делать
  отдельными подэтапами без переписывания всего update-потока сразу.
