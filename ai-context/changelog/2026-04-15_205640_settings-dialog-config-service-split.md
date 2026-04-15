# 2026-04-15 20:56:40 - settings dialog config service split

## Что сделано

- добавлен и доведён до использования `SettingsDialogConfigUpdateService` как
  отдельный доменный слой для обновления `dialog_config`;
- giant `dialog_config` update flow убран из `SettingsUpdateService`;
- `SettingsUpdateService` переписан как thin orchestration service: он теперь
  координирует только top-level updates, dialog-config updates, сохранение
  `settings` и `locations` sync;
- общие timestamp/datamart validation helper'ы вынесены в
  `SettingsDialogConfigSupportService`, чтобы не держать их в giant
  `SettingsDialogConfigUpdateService`;
- внешний контракт `/settings` сохранён без изменения.

## Проверка

- `spring-panel\mvnw.cmd -q -DskipTests compile`

## Эффект

- `SettingsUpdateService` перестал быть очередной точкой концентрации giant
  доменного кода;
- `SettingsDialogConfigUpdateService` начал резаться не только по transport
  boundary, но и по внутренним support/responsibility слоям;
- split домена `settings` продолжился уже внутри update orchestration, а не
  только по endpoint-слою.
