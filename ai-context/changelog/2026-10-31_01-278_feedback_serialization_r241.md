# 01-278 R241 — сериализация оценок обращения

Время: 2026-10-31,14:25+03:00, Europe/Moscow; текущая Windows/Docker-сессия.
Промты пользователя:

> давай дальше
> Освобожу память
> освободил сколько получилось. запускай на имеющихся мощностях. самые больший потребители это vmmemwsl и vscode, где мы с тобой работаем

Значимое действующее разрешение предыдущего сообщения:

> комит и пуш разрешаю до конца суток

## Изменение

Baseline HEAD/origin/main/actual remote526a0d048ffbe7ae0dd3c1d6173420b46eb8eda0,
parentf09f29f95; subject/все3 paths/content R240 проверены, main чистый.
Bot-runtime получает exclusive frozen-root lock до existing-rating lookup,
JPA PESSIMISTIC_WRITE на выбранном rating и повторяет root proof после lookup.
Разные валидные pending request IDs сериализуются до insert/update: одна rating
row, оба запроса могут обновить её и каждый сохраняет event. Fresh now/expiry
проверяется после всех ожиданий/locks до entity setters/save/outbox/request expiry.
Expired-after-wait возвращает false,true без записей. Validation/raw/BIGINT/
nullable scope/existing identity/R238 frozen outbox/R239 rollback сохранены.
Новых schema/POM/migration/flags нет; обычные rating list readers не менялись.

## Проверки

Первый frozen набор42 на deployed R239 bytecode:30 assertion failures/12 controls,
0 errors/skips.42 real Hibernate/PostgreSQL cases проверяют distinct-request
concurrency/replay, root/selected-rating locks, root-wait expiry, rollback/release,
foreign-rating deny,3 scopes/2 raw IDs/new-existing rating. Preliminary GREEN888.
Review обнаружил ожидание самого rating lookup: добавлены отдельные12 scripted
repository-wait cases (real root SQL/transaction, mocked JPA). RED12/12 assertion
failures/0 errors/skips на preliminary R241, clock-check перенесён после всех locks.
Первый42-case source/bytecode не переписан. Final GREEN900/12 suites одним run,
0 failures/errors/skips; все846 прежних cases/source/assertions сохранены,
0 adapted fixtures. Обе новые source/4 bytecode hashes/case names frozen после
своих RED; дополнительные12 не объявлены real concurrent Hibernate wait proof.
Controller filters=false — token/delegation, не full security chain.

Trusted deployed R239 JAR SHA256
31d13466cfb1ab72eb7067c30ccb86b07f02340c6a51b791d57c22ebf5aa9763,
1422 baseline class hashes; green1422 classes/1417 unrelated прежние.
Incremental2 main/13 tests, не full build. По разрешению запуска на имеющихся
мощностях helper ограничен512MiB/1.5CPU, JVM heap224/metaspace144/code48MiB;
только owned test PostgreSQL192MiB/1CPU. WSL/VS Code/working services ради ОЗУ не
останавливались. Старые83 migrations только isolated scope/watch schema,
working datasource не использовалась. Verifier escaping defect исправлен v3
до source apply, assertions не ослаблялись.

## Runtime и остаток

Все18 containers/IDs/images/start/restarts/ENV/mounts/HostConfig и Compose/env/shared
inputs прежние, heartbeat отдельно. Flyway/settings/users/authorities/roles
fingerprints прежние,83 working migrations/V83 не повторялись. Web R239/1 healthy/
restart0/readinessUP, health503DLQ1 прежний. Новый rollback
iguana-panel:rollback-r242-web-20261031-09b20a52c8a4 сохранён на R239
image sha256:09b20a52c8a41c28158e81b4f90168506e59d5ec7e54beb14b37b24242229cb4;
старые pointers сохранены, реальный rollback не выполнялся. Working migrations/
backfill/flags/queues/provider/worker/bots/infra не менялись. R241 пока source-only;
deploy/cleanup helper — отдельный R242.

Остаются persisted pending/public/business generation, non-cooperating writers/
DB uniqueness/исторические дубли/JPA cache/phantoms/ABA/epoch, owning child/rating
attribution, relay/operator-edit/activity/other producers/media/delivery,
PENDING policies/AI/fleet/questionnaire/provider/flags/S6 и DLQ1 gate.
S2B снятR190; задача🟡. Это не полное закрытие feedback provenance/S6.

Scope7:2 main,2 new tests,task/S0/этот append-only changelog. Detached sandbox,
replay/exact scope/content guards/diff check; source apply заканчивается без
staging/commit/push/deploy. Отдельная публикация разрешена до конца31 октября
по Москве; реальные commit/push записываются в publish.json. Evidence
.tmp/01-278-r241-evidence/: split RED/preliminary888/final900 XML/logs/frozen
sources/classes/compiler proof/runtime/DB snapshots/manifest/guards/final JSON.
