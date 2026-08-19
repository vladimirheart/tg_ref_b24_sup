# PostgreSQL Full Production Gap Audit

Документ фиксирует фактический gap между текущим состоянием репозитория после `01-181`/`01-182` и целевым production contour, где система живёт на canonical `PostgreSQL` с backend-owned transport boundary.

Актуально на `2026-08-19`.

## 1. Что уже закрыто

- fresh `PostgreSQL-first` запуск проекта подготовлен;
- основные active runtime-path `Telegram` / `VK` / `MAX` уже смещены к backend-owned boundary;
- bot-side task/follow-up fallback больше не живёт как production-like bean-layer в `rabbitmq`-контуре;
- `java-bot` не должен владеть production schema в external PostgreSQL path;
- RabbitMQ уже начал использоваться как реальный transport boundary для части live flows.
- `java-bot` в `rabbitmq` contour больше не должен молча откатываться в local `JPA/SQLite` business-path, если internal panel API для ticket/channel/feedback/blacklist операций не настроен.
- отдельные `settings.db`, `bot_runtime.db`, `clients.db`, `knowledge_base.db`, `objects.db`, `panel_identity.db` и `monitoring.db` больше не висят как production-like Spring datasource graph в external PostgreSQL runtime;
- legacy `bot-<channelId>.db` выведен из live topology и переведён в backend-owned consolidation/import path;
- canonical incident domain реализован на backend-owned storage:
  - `incidents`;
  - `incident_relations`;
  - `incident_events`;
  - `incident_watchers`;
  - `incident_routes`;
- dialogs/tasks/object passports уже умеют читать incident summaries из canonical backend contour.

## 2. Что ещё не даёт считать проект PostgreSQL-only production system

### 2.1. SQLite compatibility perimeter всё ещё существует

Проект уже не живёт на multi-SQLite production wiring, но explicit compatibility perimeter всё ещё остаётся:

- `application-sqlite.yml` и `APP_DB_MODE=sqlite`;
- lazy bootstrap/helpers вокруг local SQLite path;
- legacy import/consolidation flows для старых `*.db`;
- SQLite-specific migrations/tests/dev runbooks.

Это допустимо как transitional/dev/import perimeter, но не является финальным production contour.

### 2.2. Runtime infra contour ещё не дожат до final production contract

Основной незакрытый gap уже не в datasource split, а в infra/runtime ownership:

- Redis ещё не зафиксирован как обязательный слой для sessions/leases/live coordination;
- MinIO/S3 abstraction для attachments остаётся target-state, а не обязательным live contract;
- stateless multi-backend / multi-worker coordination model ещё не закрыт end-to-end operational guardrail-ами;
- transport boundary уже умеет работать через RabbitMQ, но runtime по-прежнему сохраняет compatibility-варианты и не везде жёстко мыслится как RabbitMQ-first production model.

### 2.3. Bootstrap всё ещё сохраняет SQLite compatibility mode

После стартового среза `01-183` first-run bootstrap больше не должен молча откатываться в SQLite при недоступном Docker, но SQLite compatibility mode всё ещё существует как явный override.

Это уже лучше, чем normal fallback-path, но всё ещё не финальная цель, где живой проект полностью мыслится через canonical PostgreSQL contour и SQLite остаётся только в test/import/legacy compatibility ролях.
Дополнительно one-time import/recovery из legacy SQLite уже переведён в explicit opt-in и больше не должен автоматически стартовать в каждом PostgreSQL runtime только из-за присутствия старых `*.db` файлов рядом с репозиторием.

### 2.4. Attachment binaries всё ещё не закреплены за canonical object storage contour

Сейчас runtime attachment path по-прежнему в значимой степени мыслится через local filesystem directories.

Это лучше, чем хранить business state в SQLite, но для заявленного production contour остаётся отдельным gap:

- нужна обязательная `S3/MinIO`-совместимая storage boundary;
- нужны runbook/config contracts для multi-instance attachment serving;
- local disk должен остаться только compatibility/dev perimeter.

### 2.5. Часть документации всё ещё описывает систему как более split-топологию, чем она есть по факту

Это видно по:

- `docs/database_distribution.md`;
- `docs/database-paths.md`;
- `docs/environment_variables.md`;
- SQLite-specific migration/tests/runbook references.

Пока документация и код вместе описывают multiple SQLite contours как живой runtime, архитектурная миграция не завершена.

### 2.6. Incident module есть в backend contour, но ещё не закрывает весь operator/ops слой

Canonical incident domain на backend-owned storage уже появился, но remaining scope по incident-теме ещё есть:

- полноценный operator-facing UI lifecycle вокруг incident API;
- richer signal ingestion / automatic incident creation;
- завершённые alerting/runbook flows поверх новых incident сущностей;
- operational reporting, которая считает incident module first-class production feature, а не просто linked metadata.

## 3. Что должно стать next production scope

### 3.1. Canonical PostgreSQL consolidation

- удерживать уже достигнутую консолидацию как invariant;
- продолжать вычищать только те SQLite helper-paths, которые ещё могут быть ошибочно приняты за live contour;
- закрыть final schema ownership через Flyway/PostgreSQL для новых production-domain расширений.

### 3.2. Final transport/backend ownership

- довести active worker flows до queue/API boundary;
- исключить прямой доступ integration workers к business DB;
- оставить local runtime state только как technical/per-channel contour, если он реально нужен.

### 3.3. Infra contour

- Redis для sessions/leases/live coordination;
- RabbitMQ как безусловный live transport backbone;
- MinIO/S3 для binary attachments;
- stateless multi-backend/multi-worker model.

### 3.4. Incident module

- operator-facing lifecycle/UI поверх уже существующего backend domain;
- signal -> incident model и richer automation;
- alerts/delivery/runbook flows;
- production analytics/reporting для incident operations.

## 4. Практический вывод

После `01-181` и `01-182` проект находится в состоянии:

- `PostgreSQL-first readiness`: да;
- `backend-owned main runtime path`: в основном да;
- `canonical incident backend domain`: да, базовый слой реализован;
- `full PostgreSQL-only production contour`: ещё нет.

Следующий корректный scope — уже не chase за очередной SQLite-точкой, а финализация infra contour, attachment/object storage boundary, stronger multi-instance coordination и полноценный operator/ops слой вокруг incident domain.
