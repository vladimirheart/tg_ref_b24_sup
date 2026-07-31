# 2026-07-31 16:16:22 - attachment remediation validation

- Задача: `01-161`
- Области изменений:
  - runtime SQLite copies `panel_runtime.db`, `java-bot/panel_runtime.db`, `spring-panel/panel_runtime.db`
  - `scripts/report-iguana-storage.py`

## Пользовательский промпт

> давай

## Что сделано

- Выполнен one-off backfill `chat_attachment_metadata` в трёх найденных копиях `panel_runtime.db`, чтобы привести live repo-data к metadata-first состоянию до следующего штатного старта runtime.
- Подтверждено, что в canonical `spring-panel/panel_runtime.db` legacy rows без metadata больше не осталось.
- Повторно снят storage inventory после remediation-pass.
- Исправлена диагностика `report-iguana-storage.py`: `chat_attachment_metadata.storage_key` больше не интерпретируется как ticket-relative path поверх уже нормализованного storage key.
- Зафиксирован фактический остаток: normalized metadata rows указывают на отсутствующие binaries, а одна unresolved row остаётся внешним URL.
