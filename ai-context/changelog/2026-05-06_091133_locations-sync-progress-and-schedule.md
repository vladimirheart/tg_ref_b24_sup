# 2026-05-06 09:11:33 - locations sync progress and schedule

## Промпты пользователя

```text
сделай ещё принудительную синхронизацию со статусбаром прогресса. настройки источников перенеси в отдельную вкладку. и нужна настройка расписания синхронизации
```

## Что сделано

- Добавлен отдельный runtime-сервис `IikoDepartmentLocationsSyncService` для ручного и планового запуска синхронизации департаментов с live-статусом, прогрессом, временем последнего успеха и расчётом следующего автозапуска.
- `IikoDepartmentLocationCatalogService` расширен поддержкой `forceRefresh`, чтобы ручная синхронизация могла обходить TTL-кэш и сразу тянуть свежие данные из `iikoServer API`.
- Старый scheduler переведён на минутный poll и теперь читает пользовательские настройки расписания через `LocationsIikoSyncSettingsService`, а не использует только жёстко зашитый интервал.
- Добавлен новый shared-settings ключ `locations_iiko_sync` с настройками:
  - включена ли автосинхронизация;
  - интервал в минутах.
- В `SettingsTopLevelUpdateService` и `ManagementController` добавлена отдача и сохранение новых sync-настроек вместе с остальными top-level settings страницы.
- Добавлен API-контроллер `SettingsLocationsSyncController`:
  - `GET /api/settings/locations-sync/status` для чтения текущего статуса;
  - `POST /api/settings/locations-sync/run` для ручного запуска синхронизации из UI.
- В модалке `Структура локаций` на странице настроек UI разделён на вкладки:
  - `Структура` для самого дерева локаций;
  - `Синхронизация iikoServer` для источников, расписания и ручного запуска.
- Источники `iikoServer API` вынесены из общего блока структуры в отдельную вкладку, чтобы редактирование дерева и live-источников не смешивалось в одном экране.
- Во вкладке синхронизации добавлены:
  - кнопка `Синхронизировать сейчас`;
  - status/progress bar;
  - текстовый статус;
  - метаданные по последнему и следующему запуску;
  - блок предупреждений.
- На клиенте добавлен polling статуса синхронизации во время выполнения, а также отдельное сохранение расписания без обязательного редактирования дерева локаций.
- Обновлены тесты и конструкторы сервисов под новый sync-service и новый settings-service.
- Задача `01-074` переведена в статус `🟣`.

## Проверка

- `spring-panel\mvnw.cmd -q "-Dnet.bytebuddy.experimental=true" "-Dtest=IikoDepartmentLocationCatalogServiceTest,IikoDepartmentLocationsSyncServiceTest,IikoDepartmentLocationsSyncSchedulerTest,LocationsIikoServerSourceSettingsServiceTest,LocationsIikoSyncSettingsServiceTest,SettingsTopLevelUpdateServiceTest,SettingsUpdateSharedConfigIntegrationTest,ManagementControllerWebMvcTest,PublicFormLocationIntegrationTest" test`
- `spring-panel\mvnw.cmd -q -DskipTests compile`

## Итог

- Пользователь может запускать принудительную синхронизацию департаментов из UI без рестарта панели и видеть её живой прогресс.
- Источники `iikoServer API` вынесены в отдельную вкладку внутри настроек локаций, а расписание автосинхронизации стало управляться из UI.
