# 2026-05-18 11:14:00 - passport effective location catalog

## Пользовательский промпт

```text
на странице паспорта объекта  непонятно откуда беурутся данные в "Департамент", "Страна", "Бизнес", " Юридическое лицо ", " Тип партнёра ", "Город". по правильному должны данные тянуться со страницы настроек. например города, типы партнёра и бизнес должны тянуться из iiko, по примеру как тянется в блок "Структура локаций"
```

## Что сделано

- `ManagementController.populatePassportEditor()` переведён на загрузку effective locations payload через `IikoDepartmentLocationCatalogService`.
- Для страницы паспорта добавлена backend-пересборка location-параметров:
  - `country`;
  - `partner_type`;
  - `business`;
  - `city`;
  - `department`.
- Эти поля теперь формируются напрямую из effective-структуры локаций и `city_meta/location_meta`, а не только из `settings_parameters`.
- Зависимости для каскадной фильтрации в `passports/new.html` сохраняются в payload, поэтому UI продолжает отфильтровывать значения по выбранным `страна -> тип партнёра -> бизнес -> город`.
- `legal_entity` оставлен на текущем settings-based источнике (`network_legal_entity_options` и `network_profiles`), чтобы не ломать сетевой блок паспорта.
- В `ManagementControllerWebMvcTest` добавлен regression test на то, что редактор паспорта видит live-значения `БлинБери / Корпоративная сеть / Смоленск / Ленина 1`.
- В `ai-context/tasks` добавлена задача `01-092` и переведена сразу в статус `🟣` как маленькая точечная доработка по прямому запросу пользователя.

## Проверка

- `spring-panel\mvnw.cmd -q "-Dnet.bytebuddy.experimental=true" "-Dtest=ManagementControllerWebMvcTest" test`
- `spring-panel\mvnw.cmd -q -DskipTests compile`

## Итог

- Паспорт объекта теперь использует тот же effective locations catalog, что и страница настроек.
- Live-локации из `iikoServer`-структуры становятся доступны в полях паспорта без необходимости полагаться на stale-данные из `settings_parameters`.
