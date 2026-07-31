# 2026-07-31 13:15:20 - iguana-data-lifecycle-strategy

## Затронутые области

- `docs/IGUANA_DATA_LIFECYCLE_AND_STORAGE_STRATEGY.md`
- `docs/IGUANA_PROJECT_GUIDE.md`
- `README.md`
- `ai-context/tasks/task-list.md`
- `ai-context/tasks/task-details/01-160.md`

## Пользовательский промпт

> гуд. вопрос: что делать с разростанием данных? например при большом количестве вложений и т.п.
>
> делай

## Что сделано

- Оформлена новая задача `01-160` по проектированию data lifecycle и стратегии масштабируемого хранения Iguana с привязкой к уже существующим работам по SQLite-топологии, retention monitoring и переносимым пакетам.
- Добавлен отдельный design-doc `docs/IGUANA_DATA_LIFECYCLE_AND_STORAGE_STRATEGY.md`, который фиксирует целевые классы данных Iguana, storage tiers, политику retention, подход к архивированию диалогов, режимы экспорта и phased rollout.
- В стратегии явно разведены `business facts`, `transport/runtime history`, `attachment metadata`, `attachment binaries`, `monitoring/telemetry` и `archive/backup` слой, чтобы рост данных больше не решался одной общей папкой и одной общей политикой хранения.
- Зафиксирован целевой переход для вложений к storage abstraction с локальным filesystem adapter как fallback и `S3`/`MinIO` как target-state для роста объёма и multi-host эксплуатации.
- Обновлены точки входа документации `README.md` и `docs/IGUANA_PROJECT_GUIDE.md`, чтобы новая стратегия была discoverable из главного контура документации.
- Переносимый пакет `C:\Intel\iguana` повторно пересобран через `scripts/export-iguana-portable.ps1`, и новый strategy-doc включён в актуальную копию проекта для запуска на другой машине.

## Остаточный риск

- Это пока проектная стратегия и source-of-truth для дальнейших implementation-задач; сами storage abstraction, archive jobs, legal-hold правила и export modes ещё нужно реализовать отдельными техническими этапами.
