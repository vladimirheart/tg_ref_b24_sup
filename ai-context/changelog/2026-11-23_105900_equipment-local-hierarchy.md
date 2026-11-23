# 01-306 — Локальная иерархия каталога оборудования

Время: 2026-11-23 10:59:00 Europe/Moscow.

## Промпт пользователя

```text
ты писал: "Изменения не закоммичены и не развёрнуты."

разворачивай



и ещё:

"Нужно решить, что является источником истины:

1. Только NetBox — иерархию меняют там, в панели лишь дополняют фото и комплектацию моделей.
2. NetBox + ручное управление в панели — импорт создаёт ветки, а администратор может создавать и переименовывать их локально."

2-й вариант
```

## Изменения

- Добавлена миграция с локальными ветками типа и производителя, а также с таблицей сопоставлений исходных значений NetBox и локальной целевой тройки.
- В каталоге оборудования появился редактор «Иерархия»: он позволяет создать тип или производителя, добавить модель, зафиксировать импортированную ветку как локальную, переименовать её или удалить пустую ветку.
- Ручные ветки включаются в каскадные списки типа и производителя ещё до создания первой модели.
- Переименование изменяет модели каталога, связанные настройки характеристик и профили комплектации. До сохранения проверяются повторы моделей, дочерних производителей и настроек.
- Синхронизация NetBox использует сохранённые сопоставления при добавлении новых моделей, поэтому исходные названия не создают вторую ветку после локального переименования.
- Добавлены backend, Web MVC и JavaScript регрессионные тесты, в том числе сценарий применения локального alias при синхронизации NetBox.

## Валидация

- `node --check spring-panel/src/main/resources/static/js/settings-it-equipment-runtime.js` — GREEN.
- `node --check spring-panel/src/main/resources/static/js/settings-parameters-shell-runtime.js` — GREEN.
- `node --test spring-panel/src/test/js/settings-it-equipment-hierarchy.test.js` — 5/5 GREEN.
- `spring-panel\mvnw.cmd '-Dtest=SettingsItEquipmentHierarchyServiceTest,SettingsItEquipmentControllerWebMvcTest,SettingsItEquipmentPhotoUiSourceContractTest,ObjectPassportWorkspaceUiSourceContractTest,NetBoxObjectPassportSyncServiceTest,SettingsLocationsTreeHierarchySourceContractTest' test` — 24/24 GREEN.
- `git diff --check` — GREEN.

## Состояние

`source_applied=true`; `staged=false`; `committed=false`; `pushed=false`; `deployment=false`.

Перед развёртыванием запущен штатный preflight:
`scripts/docker-production-up.ps1 -Observability -ValidateOnly`. Он остановился, потому что в окружении не задан обязательный `IGUANA_POSTGRES_PASSWORD`. Секрет не запрашивался и не извлекался из текущих контейнеров; обход launcher не выполнялся. Работающий Docker-контур не изменялся, миграция не применялась.
