# 01-278 R263: durable квитанции feedback и безопасное восстановление результата

Время: 2026-11-02 08:55 MSK.

Промпт пользователя: «делай».

## Выполнено

- Проверены clean HEAD/origin/main/actual remote249acbf535b82fc694363120c04561cb1768f79b,
  parenta3e934882, exact scope6 и hash manifests R262.
- Backend-owned V85 сохраняет старые ledger rows и добавляет receipt journal;
  runtime bean не выполняет DDL и fail-closed без готовой схемы. На рабочей БД
  миграция НЕ выполнялась. Старые миграции/checksums не менялись.
- ProviderSendResult/observer и mandatory5-argument dispatcher сохраняют nonce
  до SDK и positive message ID отдельно до final delivered. Полный exact ledger
  context + platform/sender fingerprint/prompt SHA256, без prompt/token/body.
- Telegram/VK/MAX typed adapters извлекают проверенный ID SDK/HTTP ответа,
  TG/MAX проверяют recipient, VK исключает overflow. Новый путь не вызывает
  провайдера повторно; старые boolean/support-chat методы сохранены.
- На replay committed receipt разрешает только sending/uncertain → delivered,
  без SDK/admission/recapture/new claim. Подмена контекста/nonce/sender/user/ID
  запрещена; отсутствие receipt/unknown/failed не открывает повтор. Accepted_at
  — локальное время наблюдения ответа, не подтверждение чтения клиентом.
- 769 unique tests/21 suites GREEN:484 core,31 adapters,254 PG/Rabbit/HTTP;
  135 новых. Source/replay независимы, повтор не суммируется. Flyway10.17
  baseline84→V85/validate сохраняет pre-V85 row; lost commit prepare/record,
  caller rollback/предел3,256-char boundary и context poison проверены real PG.
- Новые65 core +22 SDK/HTTP +48 broker recovery cases:4 scope ×3 routes,
  consumer/service restore с явным replay, revoked current catalog/channel не
  вызываются для записи исторического факта. False/lost reply без receipt
  остаются blocked. Live provider, client callback и process kill не подменяются.
- Первый offline compile probe потребовал отсутствующий Flyway9.22.3; test-only
  dependency прибита к panel10.17. Real PG обнаружил ограничение regex bound256;
  эквивалентные charset + length1..256 исправляют SQL, тесты не ослаблялись.
- 292 compiled inputs frozen/284 module classes,212 core/205 unrelated exact;
  1435 panel classes reused с provenance, новый reactor core в acceptance.
  Fixture использует owned schema на каждый экземпляр и удаляет только её.
- Рабочие18 containers/config/БД V84/settings455/accounts/grants/ledger17delivered/
  queues прежние; readinessUP. PG192/Rabbit256MiB/1CPU test-only containers и
  одноразовые данные удалены. Документы task/S0/BOT актуализированы.

## Ограничения и остаток

Это source-only пакет, не rollout. Runtime остаётся R261: runnerR260/image904f,
web/workerR257/image90cd. Нет working migration/backfill/flags, image build/deploy,
внешних acceptance messages, staging/commit/push. Прежний inboundDLQ1/healthDOWN
не исправлялся, rollback/backup pointers сохранены.

Для выпуска нужен отдельный V85 backup/rollback/schema gate перед запуском
новых children. Нужны scoped operational review/retention, startup/batch recovery,
сверка unknown без сохранённой квитанции, live SDK/client rating callback,
in-flight full process restart и остальные S3–S6. Нет exactly-once, server-side
idempotency promise или гарантии прочтения клиентом. S2B снятR190/задача🟡.

## Области и evidence

26 файлов: core DTO/observer/messaging/dispatcher/receipt service и tests,
три платформенных adapter/provider и tests, V85, test-only Maven resources/Flyway,
acceptance fixture/new recovery suite, task detail/S0/BOT и эта append-only запись.
Локальные operators/proofs — .tmp/01-278-r263-evidence/ (не входят в git).
