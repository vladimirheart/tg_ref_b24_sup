# 01-278 R239 — атомарное погашение pending feedback

Время: 2026-10-31, 12:29 +03:00, Europe/Moscow; текущая Windows/Docker-сессия.
Промт пользователя:

> давай дальше. при необходимости пересобирать - пересобирай, ОЗУ высвободил.
> комит и пуш разрешаю до конца суток

## Изменение

HEAD/origin/main/actual remote 2db4e0edf3ed42aac22e174822696a1fef22e9f3,
parent708352ed4, проверены subject/все8 paths/content R238; main чистый.
Изменения владельца сохранены. Разрешение commit/push действует до конца
31 октября по Москве; source apply, публикация и rollout разделены.

Bot-runtime storeFeedback получает pending request отдельным JPA query с
PESSIMISTIC_WRITE до проверки root/expiry/rating. Блокировка строки живёт до
commit/rollback. Повторные конкурентные ответы на один request ID не создают
повторную оценку/event. Обычный repository findById и read/dispatch callers
прежние. Ошибка pending save больше не перехватывается: оценка, outbox и expiry
сохраняются либо откатываются вместе. Сохранены validations/raw ID/BIGINT/
nullable scope/expiry/result/existing-rating identity и root/outbox guards R238.
Новых SQL migrations/колонок/flags нет.

## Проверки

54 новых cases на real Hibernate/PostgreSQL:12 двухпоточных duplicate/replay,
6 pending-row NOWAIT checks (55P03 внутри транзакции, lock доступен после),
36 rollback cases (3 failure stages ×new/existing ×3 scopes ×2 raw IDs).
JpaRepositoryFactory/SharedEntityManager/JpaTransactionManager/JDBC работают
в одной транзакции, не mocked persistence. Failure injection делегирует
настоящим repositories; обходов working datasource/provider нет.

Предварительный draft выявил неоднозначную generic assertThat типизацию и
Mockito spy/Spring proxy unwrapping errors. Исправлены типизация и test double
на delegatesTo без изменения assertions. Валидный frozen RED-v2 на compiled
R238:54/30 assertion failures/0 errors/skips,24 controls. GREEN846/10 suites,
0 failures/errors/skips; новые source/3 class hashes/case names после RED-v2
прежние. В3 существующих fixtures изменено только имя mocked lookup;
их остальные строки/assertions и все792 прежних cases сохранены и выполнены.
Controller filters=false проверяет token/delegation, не full security chain.
83 старые migrations применялись только в isolated scope/watch test schema.

Verified trusted R235 JAR SHA256
2e7eef902d715d9ddcc16831acb1832d2960d704f3dd90364dc40574e3f3e0d3,
R238 baseline1422 class hashes. Green1422 classes/1417 unrelated прежние,
изменены repository и4 service/record class files. Incremental3 main/11 test
sources, не full build. Owned640MiB helper сохранён для отдельной artifact
сборки после публикации. Source/evidence .tmp/01-278-r239-{source,evidence}/,
detached replay/manifest/content guards/diff check/правдивый final-result.json.

## Runtime и остаток

На source-фазе все18 containers/IDs/images/start/restarts/ENV/mounts/HostConfig
и Compose/env/shared inputs прежние, mutable backup heartbeat отдельно.
Flyway/settings/accounts/authorities/roles fingerprints прежние;83 migrations/
V83 повторно не запускались. Web R234 e1b85101f/1 healthy/restart0,
readiness200UP, health503DOWN из-за прежнего inboundDLQ1. Новый rollback
iguana-panel:rollback-r240-web-20261031-2643c85d1874 сохранён на R234 image
sha256:2643c85d1874a0627c5211dd5e582d96aad3b684449c466bc0e47ce7512a27f3.
Старые rollback pointers сохранены; фактический rollback не запускался.
Working migration/backfill/flags/queues/provider/worker/bot/infra не менялись.

Pending/public/business generation provenance по-прежнему не хранится в схеме;
конкуренция разных request IDs одного обращения/owning rating persistence,
relay/operator-edit/activity/other raw producers/children, ABA/phantom/epoch,
PENDING policies/AI/fleet/provider/flags/S6 и DLQ1 gate остаются. S2B снятR190,
задача🟡. R236–R239 ещё не развёрнуты на момент этой source-фазы; web rollout
будет отдельным checkpoint R240, а не объявлением полной приёмки.

Scope9:2 main,3 old fixtures,1 new test,task/S0/этот append-only changelog.
Guarded source operator заканчивается staged=false/committed=false/pushed=false/
deployment=false. Отдельная разрешённая публикация фиксируется publish.json;
этот changelog не является доказательством выполненного commit/push/deploy.
