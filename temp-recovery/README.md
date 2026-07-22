# Temp Recovery Archive

`temp-recovery/` хранит только исторические backup/snapshot-артефакты для ручного восстановления и форензики.

## Что важно

- Это не часть актуального schema-contract проекта.
- Эти файлы нельзя использовать как canonical fixtures для runtime, UI bootstrap или новых тестов.
- Legacy-ключи внутри snapshot'ов сохраняются намеренно и не должны "нормализоваться задним числом".
- Если нужно проверить текущий рабочий контракт, источником истины остаются:
  - `config/shared/settings.json`
  - актуальные runtime/service tests
  - текущие migration/normalization boundary в `spring-panel` и `java-bot`

## Как трактовать содержимое

- `*-backup-*` и `*-snapshot-*`:
  архивные слепки состояния перед миграцией, починкой или ручным восстановлением.
- `zip0`, `zip1`, `zip1root`:
  распакованные исторические доноры/снимки, а не рабочая часть репозитория.
- `routing-migration-backup-2026-07-08_085737/settings.json`:
  специальный snapshot старой mixed-schema модели, который намеренно сохраняет legacy-поля
  вроде `auto_close_hours`, root `unblock_request_cooldown_minutes`,
  `bot_settings.rating_system` и root `question_flow` mirrors.

## Правило для следующих cleanup-задач

- Не переписывать snapshot'ы в `temp-recovery`, если цель задачи не связана именно с архивной документацией.
- Не использовать snapshot из `temp-recovery` как аргумент, что legacy-поле всё ещё является canonical contract.
- Если legacy-поле нужно оставить только ради чтения старого snapshot, это считается migration-only compatibility, а не нормальным runtime API.
