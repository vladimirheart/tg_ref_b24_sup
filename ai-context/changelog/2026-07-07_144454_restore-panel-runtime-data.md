# 2026-07-07 14:44:54 - Restore panel runtime data

## Затронутые файлы

- `spring-panel/panel_runtime.db`
- `panel_runtime.db`
- `spring-panel/panel_identity.db`
- `temp-recovery/restore-backup-2026-07-07_144339/*`
- `temp-recovery/restore-snapshot-2026-07-07_144550/*`

## Пользовательский промт

Основной:

> давай - нужно восстановить данные

Значимые уточнения по этому инциденту:

> при выполнении задачи 01-143 видимо не все пути были перезаписаны, т.к. очень многого в проекте не вижу - ни диалогов ни аналитики ни клиентов - нет ни одной записи. самих ботов и их мониторинг тоже не наблюдаю

> всё без изменений. посмотри логи проекта, может что найдёшь

> а ты уверен что переименовывание БД обнулило эту БД? очень странное поведение

## Что сделано

- Восстановлены данные в `spring-panel/panel_runtime.db` из ближайшего живого донора `temp-recovery/zip1/tg_ref_b24_sup-main/spring-panel/bot_database.db` без отката текущей схемы Flyway `36`.
- Синхронизирован root-совместимый `panel_runtime.db` копией восстановленного runtime-файла, чтобы перекрыть оставшиеся path/fallback-сценарии.
- Восстановлен `spring-panel/panel_identity.db` копией из непустого `panel_identity.db`, чтобы убрать пустой users/auth runtime-файл.
- Сохранен дополнительный snapshot восстановленного состояния в `temp-recovery/restore-snapshot-2026-07-07_144550/` с раздельными root/`spring-panel` копиями.

## Проверка

- `spring-panel/panel_runtime.db`: `channels=2`, `tickets=20`, `messages=20`, `chat_history=246`, `feedbacks=15`, `notifications=680`
- `panel_runtime.db`: `channels=2`, `tickets=20`, `messages=20`, `chat_history=246`
- `spring-panel/panel_identity.db`: `users=4`, `roles=3`
- `PRAGMA integrity_check` для восстановленных БД: `ok`

## Ограничения

- Полностью июльское состояние не восстановлено: найденный живой донор датирован `2026-05-14`, поэтому гарантированно вернулись только данные на этот срез.
- В доноре есть только `2` канала. Следы третьего канала (`channel 3`, `Flisoff_bot`) найдены в логах и `settings.db`, но полного актуального runtime-снимка с его записью и токеном в рабочем виде локально не найдено.
