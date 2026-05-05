# 2026-05-05 22:04:37 - locations iikoserver settings separation

## Промпты пользователя

```text
а где в UI настроить адреса серверов чейна для этого функционала?

не совсем корректное расположение. тут лежат креды для работы с iiko biz, но не должно быть iiko server. правильнее вынести на страницу настроек. и проверь - не слома-ли ты логику работы с iiko biz
```

## Что сделано

- Добавлен отдельный shared-settings сервис `LocationsIikoServerSourceSettingsService` для хранения источников `iikoServer API` под ключом `locations_iiko_server_sources`.
- `IikoDepartmentLocationCatalogService` переведён на чтение live-источников департаментов из shared settings, а не из `EmployeeDiscountAutomationCredentialService`.
- `EmployeeDiscountAutomationCredentialService` очищен от временной логики глобального сбора `iikoServer`-профилей, чтобы `AI Ops / iiko biz` снова отвечал только за свой контур автоматизации скидок.
- В `ManagementController` на страницу `Настройки` добавлена отдача `locationsIikoServerSources` для клиентского редактора.
- В `settings/index.html` в модалку `Структура локаций` добавлен отдельный UI-блок источников `iikoServer API`:
  - список чейнов/серверов;
  - поля `Название чейна`, `iikoServer URL`, `API login`, `API secret`;
  - переключатель участия в live-синхронизации;
  - сохранение без повторного показа ранее сохранённого секрета.
- `saveLocationsChanges()` и общее `saveSettings()` теперь отправляют `locations_iiko_server_sources` вместе с `locations`.
- Обновлены тесты и конструкторы сервисов под новый источник настроек.
- Добавлен отдельный тест `LocationsIikoServerSourceSettingsServiceTest` на сохранение секрета и client-view без раскрытия секрета.
- Задача `01-072` переведена в статус `🟣`.

## Проверка

- `spring-panel\mvnw.cmd -q "-Dnet.bytebuddy.experimental=true" "-Dtest=IikoDepartmentLocationCatalogServiceTest,LocationsIikoServerSourceSettingsServiceTest,SettingsTopLevelUpdateServiceTest,SettingsUpdateSharedConfigIntegrationTest,ManagementControllerWebMvcTest,PublicFormLocationIntegrationTest" test`
- `spring-panel\mvnw.cmd -q -DskipTests compile`

## Итог

- Настройка `iikoServer`-адресов для live-структуры локаций теперь живёт на странице `Настройки -> Структура локаций`, а не в user-scoped блоке `AI Ops`.
- Логика `iiko biz` отделена обратно: сборка и компиляция `spring-panel` проходят после удаления временной зависимости локаций от automation credentials.
