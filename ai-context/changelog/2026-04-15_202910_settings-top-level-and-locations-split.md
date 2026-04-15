# 2026-04-15 20:29:10 - settings top-level and locations split

## Что сделано

- добавлен `SettingsTopLevelUpdateService` для базовых top-level settings:
  `auto_close`, `categories`, `client_statuses`, `client_status_colors`,
  `integration`, `reporting` и связанных ключей;
- добавлен `SettingsLocationsUpdateService` для обработки `locations` и
  синхронизации `settings_parameters` из location payload;
- `SettingsUpdateService` переведён на orchestration-модель: top-level settings
  и locations больше не обновляются прямо в нём;
- giant `dialog_config` block пока оставлен в `SettingsUpdateService`, но
  service уже разгружен по двум самостоятельным поддоменам.

## Проверка

- `spring-panel\mvnw.cmd -q -DskipTests compile`

## Эффект

- `SettingsUpdateService` стал менее перегруженным и ближе к роли orchestration
  слоя;
- settings split продолжается по безопасным поддоменам без изменения внешнего
  API `/settings`.
