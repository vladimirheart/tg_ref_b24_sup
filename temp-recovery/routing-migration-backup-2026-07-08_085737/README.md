# Routing Migration Backup

Этот каталог содержит архивный backup состояния на `2026-07-08` до cleanup mixed-schema настроек.

## Статус

- Архивный recovery snapshot.
- Не canonical fixture.
- Не источник истины для текущего shared settings schema.

## Почему здесь остаются legacy-поля

Файл `settings.json` в этом каталоге намеренно сохраняет старую форму данных, включая:

- top-level `auto_close_hours`
- root `unblock_request_cooldown_minutes`
- `bot_settings.rating_system`
- derived/root compatibility mirrors для question/rating flow

Это нужно для восстановления и аудита исторического состояния, а не для подтверждения текущего runtime/public contract.

## Что делать и чего не делать

- Можно использовать для ручного разбора старого инцидента или recovery-сценария.
- Нельзя использовать как пример того, что legacy-поля должны продолжать жить в актуальной схеме.
- Нельзя переписывать в canonical формат в рамках обычного cleanup shared settings.
