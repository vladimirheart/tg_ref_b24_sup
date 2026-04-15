# 2026-04-15 15:30:57 — dialog read layer and settings parameters split

## Что сделано

- добавлены `DialogReadController` и `DialogReadService` для read-only
  endpoints:
  `/api/dialogs/public-form-metrics`,
  `/api/dialogs/{ticketId}`,
  `/api/dialogs/{ticketId}/history`,
  `/api/dialogs/{ticketId}/history/previous`;
- соответствующие read-сценарии удалены из `DialogApiController`, что ещё
  сильнее уменьшило его размер и зону ответственности;
- добавлены `SettingsParametersController` и `SettingsParameterService` для
  `/api/settings/parameters`;
- в новый service вынесены CRUD параметров, uniqueness-валидация,
  extra_json/dependencies, а также синхронизация `settings_parameters` и
  `locations`;
- `SettingsBridgeController` переведён на новый `SettingsParameterService` для
  sync при сохранении `locations`.

## Зачем

Этим проходом сделаны сразу несколько bounded-context шагов:

- `dialogs` получают уже не один, а несколько отдельных controller/service
  срезов для read-сценариев;
- `settings` впервые теряет самостоятельный API-блок и связанную с ним
  инфраструктурную логику;
- дальнейший split `workspace/history/settings` становится более механическим и
  безопасным.

## Проверка

- `spring-panel/mvnw.cmd -q -DskipTests compile`
