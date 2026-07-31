# Стратегия data lifecycle и масштабируемого хранения Iguana

## 1. Зачем нужен этот документ

Iguana уже не выглядит как "просто Spring Boot + несколько SQLite-файлов". Это stateful support-система, в которой одновременно растут:

- история диалогов;
- transport history каналов;
- вложения;
- knowledge assets;
- monitoring history;
- telemetry и audit;
- резервные и переносимые пакеты.

Если не разделить эти типы данных по lifecycle, проект упрётся сразу в несколько проблем:

- раздувание `panel_runtime.db` и соседних БД;
- тяжёлые переносы между машинами;
- дорогие резервные копии;
- долгий startup и диагностика;
- эксплуатационная неясность: что хранить вечно, а что обязано чиститься.

Этот документ задаёт целевую модель хранения Iguana с заделом на рост.

## 2. Основной принцип

Нельзя хранить все данные Iguana по одному правилу.

Нужно разделять:

1. `Business facts` - факты обращения и операторской работы.
2. `Transport/runtime history` - bot-side ingress и технические дубли.
3. `Attachment metadata` - факты о файлах.
4. `Attachment binaries` - сами тяжёлые файлы.
5. `Monitoring / telemetry / audit` - техническая история.
6. `Archive / backup / export` - отдельный слой жизненного цикла.

Главная идея:

- в БД надолго живут факты и индексы;
- тяжёлые бинарные файлы выносятся из operational runtime;
- техническая история имеет короткий retention;
- переносы и бэкапы работают по режимам, а не по принципу "копировать всё всегда".

## 3. Целевые классы данных

### 3.1 Business facts

Сюда относятся:

- тикеты и диалоги;
- сообщения в их канонической panel-проекции;
- клиенты и операторские действия;
- маршрутизация, question flow, аналитические атрибуты;
- knowledge- и object-related прикладные сущности;
- факты закрытия, назначения, переадресации, оценки.

Целевая политика:

- это долгоживущий source of truth;
- хранится бессрочно или по отдельной бизнес-политике;
- живёт в `panel_runtime.db` и связанных canonical business-контурах;
- не смешивается с техническим шумом.

### 3.2 Transport/runtime history

Сюда относятся:

- bot ingress history;
- служебные дубли transport-сообщений;
- channel-local runtime state;
- transport-only traces, которые уже спроецированы в панель.

Целевая политика:

- хранить ограниченно;
- использовать для reconciliation и диагностики;
- не держать бессрочно, если бизнес-факт уже записан в panel-runtime.

Рекомендуемый retention:

- `90-180` дней по умолчанию;
- до `365` дней только при обоснованной интеграционной необходимости.

### 3.3 Attachment metadata

Сюда относятся:

- ID вложения;
- ticket/dialog/message linkage;
- исходное имя файла;
- MIME type;
- размер;
- hash;
- storage key;
- storage class;
- время загрузки;
- владелец/источник;
- признак архивированности;
- legal hold и retention override.

Целевая политика:

- metadata - это бизнес-факт;
- metadata живёт дольше самих бинарных файлов;
- metadata должна оставаться в БД даже тогда, когда бинарник ушёл в cold archive.

### 3.4 Attachment binaries

Сюда относятся сами тяжёлые файлы:

- клиентские вложения;
- operator reply media;
- изображения, документы, voice/video;
- knowledge assets;
- фото и иные бинарные артефакты.

Целевая политика:

- не хранить это в long-term как обычные локальные файлы рядом с кодом;
- отделить binaries от metadata;
- перевести хранение на storage abstraction;
- поддержать локальный диск как fallback/dev-режим и `S3`/`MinIO` как target-state.

### 3.5 Monitoring / telemetry / audit

Сюда относятся:

- monitoring history;
- AI ops telemetry;
- operator workspace telemetry;
- технический audit и troubleshooting traces.

Целевая политика:

- короткий retention;
- отдельные canonical контуры;
- long-term аналитика только через rollup-таблицы и агрегаты.

## 4. Целевая storage-архитектура

## 4.1 Runtime БД

Целевая БД-топология уже частично зафиксирована в `docs/db/sqlite-target-topology.md`.

Для growth strategy важно закрепить:

- `panel_runtime.db` - только business source of truth;
- `panel_identity.db` - identity/access;
- `monitoring.db` - monitoring history;
- `panel_telemetry.db` - техническая telemetry/history;
- `bot_runtime.db` - transport/runtime ingress контур.

### Что важно дополнительно

- attachment metadata относится к business-слою и не должна жить в `attachments/` как единственный источник истины;
- binaries вложений не должны оставаться частью "монолитного переносимого каталога" как единственного варианта эксплуатации;
- history архива должна проектироваться отдельно от hot runtime.

## 4.2 Attachment storage abstraction

Нужен единый storage-порт, например концептуально:

- `AttachmentStorage`
- `store(stream, metadata)`
- `open(storageKey)`
- `delete(storageKey)`
- `moveToCold(storageKey)`
- `exists(storageKey)`
- `generateDownloadDescriptor(storageKey)`

Минимальные реализации:

1. `LocalFilesystemAttachmentStorage`
2. `S3CompatibleAttachmentStorage`

Под `S3Compatible` понимается:

- AWS S3;
- MinIO;
- любой S3-совместимый объектный storage.

### Почему это важно

Пока все файлы живут в `attachments/`, проект масштабируется только как "папка на диске". Это плохо для:

- роста объёма;
- резервного копирования;
- multi-host эксплуатации;
- дешёвого архива;
- контролируемого lifecycle.

## 4.3 Горячий и холодный слой

Для binaries нужен tiering:

- `Hot` - быстрый доступ для новых и часто открываемых файлов;
- `Warm` - редко используемые, но ещё онлайн;
- `Cold` - архивное хранение;
- `Deleted` - физически удалённые по retention.

Пример целевого поля в metadata:

- `storage_class`: `hot`, `warm`, `cold`, `deleted`
- `storage_provider`: `local`, `s3`, `minio`
- `storage_key`
- `archived_at`
- `delete_after`

## 5. Retention-политика по умолчанию

Это стартовые defaults. Они должны быть конфигурируемыми, но не произвольными.

| Категория | Default policy | Комментарий |
| --- | --- | --- |
| Business facts | бессрочно | source of truth |
| Transport/runtime history | `180` дней | для reconciliation и каналов |
| Monitoring raw history | `30` дней | уже согласуется с `01-140` |
| Telemetry raw history | `60` дней | troubleshooting, не business-факт |
| Attachment metadata | бессрочно | metadata важнее binaries |
| Attachment binaries hot | `90` дней | быстрый доступ к свежим файлам |
| Attachment binaries warm | до `365` дней | ещё онлайн, но не hot |
| Attachment binaries cold | `1-3` года | архив по policy |
| Legal hold data | без удаления до снятия hold | исключение из правил |

## 5.1 Важное уточнение по вложениям

Не все файлы можно удалять одинаково.

Исключения:

- файлы активных или незакрытых обращений;
- вложения, привязанные к knowledge base;
- файлы, попавшие под legal hold;
- регуляторно значимые документы;
- operator-marked keep-forever случаи.

То есть retention должен опираться не только на возраст файла, но и на его прикладной контекст.

## 6. Что делать с большой историей диалогов

### 6.1 Не всё должно оставаться в hot runtime

Когда объём переписки растёт, основная боль возникает не только от БД, но и от того, что:

- каждый backup тащит весь historical хвост;
- migrations и vacuum становятся тяжелее;
- перенос проекта между машинами дорожает;
- UI начинает чаще упираться в тяжёлые history запросы.

### 6.2 Целевая модель

Нужен archive-контур для закрытых старых диалогов.

Варианты:

1. `Dialog archive` как отдельная SQLite/PostgreSQL БД.
2. `Cold export` в партиционированный архивный storage с индексом.
3. Гибрид:
   - metadata + summary в runtime;
   - full old history в archive.

### 6.3 Рекомендуемое правило

Для Iguana разумный target-state:

- активные и свежие диалоги живут в hot runtime;
- закрытые старые диалоги после N дней становятся archive-кандидатами;
- в hot runtime остаются summary, индекс, аналитические атрибуты и ссылка на archive;
- полная старая переписка открывается по explicit archive-fetch сценарию.

Стартовая рекомендация:

- `archive candidate`: закрыт и не обновлялся `180+` дней.

## 7. Режимы экспорта и переносов

Один из практических источников роста - это то, что любой перенос сегодня легко превращается в копирование "всего подряд".

Нужны режимы экспорта:

### `full`

- код;
- документация;
- все runtime БД;
- `attachments`;
- `bot_databases`;
- shared config;
- archive/backup metadata при необходимости.

Использовать для:

- полного переноса среды;
- cold standby;
- forensic backup.

### `runtime`

- код;
- документация;
- runtime БД;
- shared config;
- без исторических тяжёлых вложений или только с hot/warm подмножеством.

Использовать для:

- быстрого запуска нового узла;
- технической миграции;
- тестового восстановления.

### `light`

- код;
- документация;
- конфиги;
- без боевых данных.

Использовать для:

- dev/test;
- dry-run инсталляций;
- демонстрационной среды.

### `archive`

- только архивные БД/файлы;
- только long-tail attachments;
- отдельно от operational runtime.

Использовать для:

- long-term хранение;
- разгрузка горячего контура;
- восстановление конкретных исторических периодов.

## 8. Наблюдаемость и квоты

Если storage растёт, но никто это не видит, архитектура будет проиграна даже при хорошем дизайне.

Минимум, что нужно измерять:

- размер `attachments`;
- размер каждого SQLite-контура;
- темп роста по дням и неделям;
- число новых файлов и их общий вес;
- top N самых тяжёлых каналов;
- top N самых тяжёлых типов вложений;
- число archive-кандидатов;
- число файлов под legal hold;
- время выполнения cleanup jobs;
- объём данных в каждом export-режиме.

Нужны пороги и алерты:

- диск заполнен на `70%`, `85%`, `95%`;
- `attachments` выросли более чем на X GB за Y дней;
- cleanup job не запускался более N часов;
- archive backlog растёт быстрее, чем разгружается.

## 9. Решение по SQLite и PostgreSQL

Не нужно раньше времени "переезжать всё в Postgres только потому, что данных стало больше".

Более зрелое решение:

1. Сначала разделить lifecycle и storage tiers.
2. Вынести тяжёлые binaries.
3. Убрать технический шум из hot business DB.
4. Добавить archive policy.
5. Уже после этого измерить, остаётся ли bottleneck у SQLite.

То есть:

- рост файлов почти всегда лечится раньше storage-tiering, чем сменой СУБД;
- рост monitoring/telemetry лечится retention;
- рост history лечится archive policy;
- миграция на Postgres нужна только если упираемся в concurrency, write pressure, reporting complexity или объём hot business facts даже после предыдущих шагов.

## 10. Phased rollout

## Этап 1. Полная инвентаризация и метрики

Сделать:

- inventory всех типов файлов в `attachments/`;
- inventory attachment references в БД;
- базовые growth metrics;
- карту самых тяжёлых каналов, форматов и сценариев;
- отчёт по current export sizes.

Результат:

- команда понимает, что именно растёт и сколько это стоит.

## Этап 2. Storage abstraction для вложений

Сделать:

- ввести attachment storage interface;
- оставить local filesystem adapter;
- добавить `S3/MinIO` adapter;
- перевести новые загрузки на storage abstraction.

Результат:

- проект перестаёт быть жёстко привязан к локальной папке `attachments/`.

## Этап 3. Metadata-first модель

Сделать:

- нормализовать attachment metadata в canonical таблице;
- хранить `hash`, `size`, `storage_key`, `storage_class`, `provider`;
- добавить дедупликацию по hash там, где это безопасно;
- отделить metadata от physical path.

Результат:

- storage можно менять, не ломая прикладную модель.

## Этап 4. Retention и cold archive

Сделать:

- cleanup jobs;
- warm/cold/archive transitions;
- legal hold exclusions;
- archive policy для закрытых старых диалогов;
- режим `archive` в export tooling.

Результат:

- operational среда перестаёт тащить весь long-tail.

## Этап 5. Export modes и backup discipline

Сделать:

- режимы `full`, `runtime`, `light`, `archive`;
- манифест состава пакета;
- проверку размера и completeness пакета;
- отдельные инструкции восстановления по каждому режиму.

Результат:

- перенос и резервирование становятся предсказуемыми.

## Этап 6. Архив диалогов и разгрузка hot runtime

Сделать:

- archive-контур для старых закрытых диалогов;
- summary/index в hot runtime;
- on-demand retrieval из архива;
- независимую политику backup/restore для archive.

Результат:

- long-tail история перестаёт без конца раздувать боевой runtime.

## 11. Что не надо делать

- Не удалять старые вложения без metadata и traceability.
- Не хранить object-storage ссылки как единственный источник истины без локальной metadata-модели.
- Не пытаться решать рост файлов только `VACUUM` и ручной чисткой папок.
- Не вводить десятки новых БД без чёткой lifecycle-логики.
- Не смешивать export для запуска среды и archive для долгого хранения.
- Не делать archive policy одинаковой для всех типов файлов и данных.

## 12. Короткие продуктовые решения по умолчанию

Если нужно принять pragmatic default прямо сейчас:

1. `Business metadata` хранить бессрочно.
2. `Attachment binaries` выносить в storage abstraction.
3. `Monitoring` и `telemetry` чистить aggressively.
4. `Transport duplicates` хранить ограниченно.
5. `Closed old dialogs` архивировать отдельно от hot runtime.
6. `Export` всегда делать в режимах, а не "всё целиком".

## 13. Следующие implementation-задачи

Из этой стратегии естественно вытекают отдельные работы:

1. Инвентаризация и метрики роста данных.
2. Attachment storage abstraction и metadata model.
3. S3/MinIO backend для вложений.
4. Cleanup и retention jobs по категориям.
5. Archive policy для закрытых диалогов.
6. Export modes и package manifest.
7. Dashboard наблюдаемости по storage growth.

## 14. Итог

Правильная масштабируемость Iguana - это не "куда-то сложить побольше диска", а управляемый lifecycle:

- факты живут долго;
- технический шум живёт мало;
- большие файлы живут отдельно;
- архивы живут отдельно от runtime;
- переносы и бэкапы работают по сценариям, а не по привычке копировать весь каталог проекта.

Именно эта модель даёт проекту шанс расти без постоянного operational перегрева.
