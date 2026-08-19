# PostgreSQL Full Production Gap Audit

Документ фиксирует фактический gap между текущим состоянием репозитория после `01-181`/`01-182` и целевым production contour, где система живёт на canonical `PostgreSQL` с backend-owned transport boundary.

Актуально на `2026-08-17`.

## 1. Что уже закрыто

- fresh `PostgreSQL-first` запуск проекта подготовлен;
- основные active runtime-path `Telegram` / `VK` / `MAX` уже смещены к backend-owned boundary;
- bot-side task/follow-up fallback больше не живёт как production-like bean-layer в `rabbitmq`-контуре;
- `java-bot` не должен владеть production schema в external PostgreSQL path;
- RabbitMQ уже начал использоваться как реальный transport boundary для части live flows.

## 2. Что ещё не даёт считать проект PostgreSQL-only production system

### 2.1. В `spring-panel` всё ещё есть отдельные SQLite runtime contours

По коду и документации остаются отдельные physical/runtime контуры:

- `panel_identity.db`;
- `monitoring.db`;
- `bot_runtime.db`;
- `clients.db`;
- `knowledge_base.db`;
- `objects.db`;
- `settings.db`.

Это означает, что проект ещё не живёт на едином canonical PostgreSQL storage even if main path already supports PostgreSQL-first start.

### 2.2. SQLite datasource graph всё ещё встроен в production wiring

В `spring-panel` остаются отдельные SQLite datasource-конфигурации и runtime-mode helpers для:

- primary/secondary sqlite paths;
- monitoring sqlite path;
- users sqlite path;
- objects/clients/knowledge/settings sqlite paths;
- env defaults, которые по-прежнему мыслят проект как multi-SQLite topology.

Пока этот graph остаётся рабочим runtime wiring, система ещё не переведена в final PostgreSQL contour.

При этом отдельные operator-facing direct SQLite probes уже начинают вычищаться: например, client profile refresh больше не должен открывать per-channel `bot-<channelId>.db` в external PostgreSQL runtime path.
Дополнительно часть ad-hoc SQLite DDL уже снята с live-bean’ов и возвращена под Flyway ownership; monitoring runtime alias, `settings.db` registry wiring и lazy-only bootstrap для `clients.db` / `knowledge_base.db` уже частично выведены из production-like graph, но доменное разделение secondary contours пока ещё остаётся.

### 2.3. Bootstrap всё ещё сохраняет SQLite compatibility mode

После стартового среза `01-183` first-run bootstrap больше не должен молча откатываться в SQLite при недоступном Docker, но SQLite compatibility mode всё ещё существует как явный override.

Это уже лучше, чем normal fallback-path, но всё ещё не финальная цель, где живой проект полностью мыслится через canonical PostgreSQL contour и SQLite остаётся только в test/import/legacy compatibility ролях.
Дополнительно one-time import/recovery из legacy SQLite уже переведён в explicit opt-in и больше не должен автоматически стартовать в каждом PostgreSQL runtime только из-за присутствия старых `*.db` файлов рядом с репозиторием.

### 2.4. Часть документации всё ещё описывает систему как multi-SQLite architecture

Это видно по:

- `docs/database_distribution.md`;
- `docs/database-paths.md`;
- `docs/environment_variables.md`;
- SQLite-specific migration/tests/runbook references.

Пока документация и код вместе описывают multiple SQLite contours как живой runtime, архитектурная миграция не завершена.

### 2.5. Incident module ещё не реализован как canonical backend domain

Изначальная постановка `01-181` включала не только infra/storage refactor, но и полноценный incident module.

На текущем состоянии:

- есть target-model/specification;
- есть частичные incident-related UI/ops артефакты;
- но нет признака завершённого canonical incident domain на backend-owned PostgreSQL storage.

## 3. Что должно стать next production scope

### 3.1. Canonical PostgreSQL consolidation

- свести live runtime/business/identity/monitoring/object/task/knowledge data к PostgreSQL;
- убрать production-like SQLite data sources;
- закрыть final schema ownership через Flyway/PostgreSQL.

### 3.2. Final transport/backend ownership

- довести active worker flows до queue/API boundary;
- исключить прямой доступ integration workers к business DB;
- оставить local runtime state только как technical/per-channel contour, если он реально нужен.

### 3.3. Infra contour

- Redis для sessions/leases/live coordination;
- RabbitMQ как завершённый transport backbone;
- MinIO/S3 для binary attachments;
- stateless multi-backend/multi-worker model.

### 3.4. Incident module

- incident lifecycle;
- signal -> incident model;
- dialog/task/object links;
- routes/watchers/history/alerts;
- operator UI and delivery flows.

## 4. Практический вывод

После `01-181` и `01-182` проект находится в состоянии:

- `PostgreSQL-first readiness`: да;
- `backend-owned main runtime path`: в основном да;
- `full PostgreSQL-only production contour`: ещё нет.

Следующий корректный scope — не латать ещё одну SQLite-точку по месту, а выполнять отдельную production-задачу на финальную консолидацию storage, infra contour и incident domain.
