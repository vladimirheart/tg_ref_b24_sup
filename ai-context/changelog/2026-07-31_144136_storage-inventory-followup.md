# 2026-07-31 14:41:36 - storage-inventory-followup

## Затронутые области

- `scripts/report-iguana-storage.py`
- `docs/IGUANA_DATA_LIFECYCLE_AND_STORAGE_STRATEGY.md`
- `docs/IGUANA_PROJECT_GUIDE.md`
- `README.md`

## Пользовательский промпт

> приступай к выполнению задачи 01-160
>
> делай

## Что сделано

- Добавлен operational script `scripts/report-iguana-storage.py` для первого практического этапа из `01-160`: инвентаризации candidate attachment roots, размеров SQLite-файлов и attachment/path references из БД.
- Скрипт умеет проверять сразу `attachments/` и `java-bot/attachments/`, что важно для исторического path drift между panel-side и bot-side storage roots.
- Для `chat_history.attachment`, knowledge-base файлов, avatar/photo path-полей и legacy `knowledge_articles.attachments` добавлена сверка ссылок с фактическими файлами на диске.
- В резолвере путей добавлены heuristics для:
  - старых абсолютных путей;
  - перепривязки через `attachments/...`;
  - ticket-scoped filename-only ссылок из `chat_history`.
- Скрипт переведён на открытие SQLite через `mode=ro`, чтобы инвентаризация не меняла tracked DB-файлы.
- В `README.md`, `docs/IGUANA_PROJECT_GUIDE.md` и `docs/IGUANA_DATA_LIFECYCLE_AND_STORAGE_STRATEGY.md` добавлены явные ссылки на новый inventory helper как на operational шаг `Этапа 1`.

## Проверка

- `python -m py_compile scripts/report-iguana-storage.py`
- `python scripts/report-iguana-storage.py --top 3`

## Наблюдения по текущему состоянию

- На момент проверки `attachments/` и `java-bot/attachments/` в рабочем каталоге пусты.
- Отчёт сразу выявил missing attachment references в `chat_history.attachment`:
  - `panel_runtime.db` - 17 строк;
  - `java-bot/panel_runtime.db` - 17 строк;
  - `spring-panel/panel_runtime.db` - 41 строк.
- Среди missing-ссылок есть исторические абсолютные пути вида `...\\java-bot\\attachments\\...` и ticket-scoped filename-only записи, что подтверждает риск path drift и необходимость следующего implementation-шага по metadata/storage normalization.

## Остаточный риск

- Это ещё не storage abstraction и не cleanup policy; текущий шаг даёт безопасную инвентаризацию и фактическую диагностику, но не исправляет сами битые attachment references и не переносит binaries в metadata-first модель.
