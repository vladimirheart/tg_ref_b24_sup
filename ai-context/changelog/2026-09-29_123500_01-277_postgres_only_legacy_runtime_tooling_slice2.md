# 01-277 — PostgreSQL-only legacy runtime/tooling cleanup, S2

## Инициирующее сообщение

> погнали

Пользователь подтвердил целевой контракт: проект должен работать только на PostgreSQL; MySQL, SQLite и H2 не нужны.

## Изменение

- удалён legacy SQLite archive/import/recovery runtime graph из spring-panel;
- удалены source-path properties и archive configuration;
- удалены one-time import, reconciliation, critical recovery, monitoring compaction и legacy bot shard consolidation runners;
- удалены legacy SQLite staging/verification scripts и compose override;
- удалены dead worker/local bot database compatibility branches из live runtime config/source;
- migration chains не входят в этот slice и удаляются отдельно в S3;
- remaining SQLite/H2 test fixtures переводятся на PostgreSQL отдельно в S4.

## Safety

Этот source slice не запускает DB migrations, не меняет PostgreSQL data, runtime containers, queues, environment или images.
