# 2026-05-05 18:05:00 - iikoserver departments source

## Пользовательский промпт

```text
смотри, изначально я тебе давал документацию iiko servr api, а ты пошёл читать iiko транспорт.
мониторинг транспортных запросов оставь - всё ок, но что касается департаментов, то их нужно получать и парсить через server api, используя такой запрос: https://host:port/resto/api/corporation/departments/. документация живёт тут: https://ru.iiko.help/articles/#!api-documentations/iikoserver-api
```

## Что сделано

- `IikoDepartmentLocationCatalogService` отвязан от `iiko_api_monitors` и transport endpoint'ов `/api/1/access_token` + `/api/1/organizations`.
- Live-структура локаций переведена на `iikoServer API`:
  - токен запрашивается через `/api/0/auth/access_token`;
  - департаменты читаются через `GET /resto/api/corporation/departments/?key=...`;
  - XML-ответ парсится с фильтрацией только `DEPARTMENT`, с отсевом неактивных сущностей и имён с `CLOSED`.
- Для глобального sync добавлен сбор активных `iikoServer`-профилей из сохранённых credential-записей автоматизации скидок: `EmployeeDiscountAutomationCredentialService.loadActiveIikoProfilesForAllUsers()`.
- Парсинг бизнес-логики сохранён: `ФР_` -> `Партнёры-франчайзи`, без `ФР_` -> `Корпоративная сеть`, коды `ББ`/`СВ`, разбор `город -> локация`.
- На странице `Мониторинг iiko API` убрана UI-связка `Источник структуры локаций`; добавлена явная подсказка, что экран относится только к transport API, а локации синхронизируются через `iikoServer`-профиль.
- В `ai-context/tasks` добавлена и зафиксирована задача `01-071`.

## Проверка

- `spring-panel\mvnw.cmd -q "-Dnet.bytebuddy.experimental=true" "-Dtest=IikoDepartmentLocationCatalogServiceTest,IikoDepartmentLocationsSyncSchedulerTest,IikoApiMonitoringServiceTest" test`
- `spring-panel\mvnw.cmd -q -DskipTests compile`
- `spring-panel\mvnw.cmd -q "-Dnet.bytebuddy.experimental=true" "-Dtest=PublicFormLocationIntegrationTest,ManagementControllerWebMvcTest" test`

## Итог

- Департаменты для `Структуры локаций` и каскадных клиентских вопросов теперь берутся из `iikoServer API`, а transport-monitoring остаётся самостоятельным read-only мониторингом.
- Настройка источника для этого sync теперь завязана на активный `iiko URL`-профиль из настроек автоматизации скидок, а не на transport monitor-записи.
