# 2026-07-31 17:02:13 - orphan attachment status sanitization

- Задача: `01-162`
- Области изменений:
  - `spring-panel`: availability/external classification for attachment metadata, dialog payload and UI fallback
  - `java-bot`: metadata write-path updated for external/local attachment status classification
  - `scripts`: inventory enriched with availability status breakdown
  - runtime SQLite copies: one-off classification of current attachment metadata rows

## Пользовательский промпт

> давай дальше по задаче 01-162

## Что сделано

- Добавлен новый schema/runtime слой `availability_status` для `chat_attachment_metadata` и migration `V40`.
- В canonical metadata contract теперь различаются:
  - `storage_provider=external_url` для внешних attachment URL;
  - `availability_status=available|missing|external|unresolved|unknown`.
- Panel-side runtime больше не пытается открывать отсутствующие local files как рабочие attachment links:
  - для `missing` показывается явный unavailable fallback;
  - для `external` используется внешний URL как canonical reference.
- Bot-side metadata persistence теперь сразу правильно помечает external URL и local attachment availability.
- `report-iguana-storage.py` научен показывать breakdown по `available/missing/external/unresolved` и не шумит external rows как missing path normalization problem.
- Выполнен one-off remediation/classification в repo-копиях `panel_runtime.db`, `java-bot/panel_runtime.db`, `spring-panel/panel_runtime.db`.
- По итогам на `2026-07-31` для canonical `spring-panel/panel_runtime.db`:
  - `0` legacy attachment rows без metadata;
  - `40` rows со статусом `missing`;
  - `1` row со статусом `external`;
  - `0` rows со статусом `unresolved`.
