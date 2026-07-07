# 2026-07-07 10:41:34 — SQLite target topology

## Связанные задачи

- `01-142` — Спроектировать чистую целевую архитектуру SQLite-баз: логические имена, финальное число БД и доменные границы

## Пользовательский промпт

> возьми в работу задачу 01-142

## Затронутые файлы

- `ai-context/changelog/2026-07-07_104134_sqlite-target-topology.md`
- `ai-context/rules/backend/04-sqlite-topology.md`
- `ai-context/tasks/task-list.md`
- `docs/database-paths.md`
- `docs/db/sqlite-target-topology.md`
- `docs/sqlite_schema_snapshot.md`

## Что сделано

- Зафиксирована target-state архитектура SQLite в новом документе `docs/db/sqlite-target-topology.md`.
- Определено финальное разбиение на `5` logical contours: `panel-runtime`, `panel-identity`, `monitoring`, `panel-telemetry`, `bot-runtime`.
- Для каждого контура описаны канонические домены, причины отдельного существования, судьба текущих файлов и migration plan высокого уровня.
- Для основных типов данных задана retention/lifecycle policy с разделением raw business facts, raw monitoring history, raw telemetry/audit и long-term rollups.
- В `docs/database-paths.md` текущие physical env/files переописаны как transitional mapping к целевым logical contours.
- Правило `ai-context/rules/backend/04-sqlite-topology.md` обновлено так, чтобы новые задачи принимали решения по logical contour, а не по legacy filename.
- Задача `01-142` переведена в статус `🟣`.

## Примечания

- Изменения носят архитектурно-документационный характер; runtime-код и миграции в этом шаге не менялись.
- Отдельные implementation-задачи всё ещё нужны для фактического выноса monitoring history, telemetry-контура и удаления transitional secondary DBs.
