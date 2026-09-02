# PostgreSQL auto-close participant ordering

## Пользовательский запрос

`так исправь чтобы всё работало`

## Фактические изменения

- Устранён несовместимый с PostgreSQL запрос `COALESCE(added_at, '')` при формировании follow-up задачи автозакрытого диалога.
- NULL-значения теперь сортируются переносимым выражением `CASE WHEN added_at IS NULL`, затем по timestamp и имени участника.
- Добавлен регрессионный тест, запрещающий возврат сравнения `timestamptz` с пустой строкой.

## Проверки

- `DialogAutoCloseFollowUpTaskServiceTest` и `DialogAutoCloseSchedulerServiceTest` проходят.

