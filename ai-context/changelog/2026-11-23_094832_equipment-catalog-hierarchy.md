# 01-305 — Фото и иерархия каталога оборудования

Время: 2026-11-23 09:48:32 Europe/Moscow.

## Промпт пользователя

```text
давай пройдёмся по каталогу оборудования.

1. если прикложено фото, то нужна возможность открыть его прямо из списка оборудования,  ане проваливаться в саму карточку.
2. нужна иерархия тип-производитель-модель. сейчас всё это является одним независимым от иерархии спискомю тут наверное можно покртить отдельно, но иерархию можно дёрнуть из загруженной информации из netbox
```

## Изменения

- Обложка с фото в карточке каталога стала кнопкой: она открывает существующий просмотрщик фотографий на обложке, с навигацией по галерее модели, без открытия формы редактирования.
- В панели фильтров добавлен фильтр модели. Производители ограничиваются выбранным типом, а модели — выбранными типом и производителем.
- Те же зависимые списки применены в форме добавления и редактирования модели, поэтому нельзя выбрать несовместимую комбинацию. Для записей без производителя предусмотрена отдельная ветка «Без производителя».
- Иерархия строится в браузере из строк `/api/settings/it-equipment`: синхронизация NetBox уже сохраняет тройки `device_role → device_type.manufacturer → device_type.model` в каталоге. Новых сетевых обращений к NetBox, миграций и backfill не добавлено.
- Пересобраны `settings.css`, обновлены cache-busting версии CSS и runtime, добавлены регрессионные проверки.

## Валидация

- `node --check spring-panel/src/main/resources/static/js/settings-it-equipment-runtime.js` — GREEN.
- `node --test spring-panel/src/test/js/settings-it-equipment-hierarchy.test.js` — 4/4 GREEN.
- `spring-panel\mvnw.cmd '-Dtest=SettingsItEquipmentPhotoUiSourceContractTest,ObjectPassportWorkspaceUiSourceContractTest,SettingsLocationsTreeHierarchySourceContractTest' test` — 9/9 GREEN; SCSS пересобран.
- `git diff --check` — GREEN.

## Состояние

`source_applied=true`; `staged=false`; `committed=false`; `pushed=false`; `deployment=false`.
База данных, миграции и внешние системы не изменялись.
