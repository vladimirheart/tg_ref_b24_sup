# Правило: historical non-PostgreSQL SQL topology — superseded

## Статус

Superseded задачей `01-277`. Этот файл сохранён только как navigation anchor для старых ссылок.

## Текущий SQL-контракт

- PostgreSQL — единственная SQL/реляционная БД проекта.
- `spring-panel`, `java-bot` и dynamic bot children используют только PostgreSQL datasource contract.
- runtime DB modes, local SQL fallbacks, legacy SQL import/recovery tooling и non-PostgreSQL migration/test harnesses удалены.
- Redis, RabbitMQ и MinIO/S3 остаются отдельными infrastructure boundaries и не являются SQL-БД.

## Исторические ссылки

Старые changelog/task records могут описывать прежние DB modes, migration chains и compatibility perimeter. Они являются историческими записями и не должны использоваться как current runtime guidance.
