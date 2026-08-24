# v48 — dialog runtime PostgreSQL/media/rating stability

Дата: 2026-08-24

## Причина

Live smoke после v47 подтвердил несколько PostgreSQL-first regressions: оставшиеся String timestamp bindings ломали media metadata, edit UI events, pending feedback и system history. Параллельно обнаружены duplicate reply при повторном Send, silent media input на choice-question, `Тип бизнесаx` и рассинхрон server/AJAX status markup.

## Изменения

- JDBC TIMESTAMPTZ values передаются как OffsetDateTime в UI outbox, attachment metadata, feedback lifecycle, system history и outbox cleanup.
- Details reply защищён shared in-flight state до завершения transport request, включая media branch.
- Telegram questionnaire отклоняет media на choice-question с читаемой подсказкой выбрать допустимый вариант.
- Исправлен shared question text `Тип бизнесаx` -> `Тип бизнеса`.
- AJAX dialogs renderer восстановил `dialog-status-line`.
- Добавлены regression tests для PostgreSQL temporal binding и questionnaire guidance.

## Не входит

Полный append-only edit ledger и Telegram-подобный service-event timeline вынесены в 01-189.
