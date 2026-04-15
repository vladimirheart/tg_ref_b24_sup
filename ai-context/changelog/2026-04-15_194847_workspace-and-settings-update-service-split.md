# 2026-04-15 19:48:47 - workspace and settings update service split

## Что сделано

- оставшийся workspace endpoint диалогов вынесен в отдельные
  `DialogWorkspaceController` и `DialogWorkspaceService`;
- контроллер workspace теперь является thin-wrapper, а вся прежняя тяжёлая
  orchestration-логика живёт в service-слое;
- основной update-use-case `/settings` вынесен в `SettingsUpdateService`;
- `SettingsBridgeController` превращён в thin-wrapper, который только
  делегирует update-сценарий в service-слой.

## Проверка

- `spring-panel\mvnw.cmd -q -DskipTests compile`

## Эффект

- controller-level split домена `dialogs` доведён до точки, где remaining
  workspace logic больше не живёт в controller-монолите;
- `SettingsBridgeController` перестал быть местом, где одновременно находятся
  transport-layer и основной update-use-case настроек.
