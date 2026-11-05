# 01-278 / R287 — подготовка isolated MAX media chain

Промт пользователя:

> делай

Дата:2026-11-05, Europe/Moscow +03.

Test-only пакет, product code/POM/schema/session JSON не меняются. В существующий
opt-in bot-feedback-acceptance добавлены MAX fixture,38-case ChainIT и26-case
wire/isolation/bytecode contract. Модуль исключён из обычного runtime reactor;
IT запускается только явно. Это подготовка приёмки, не выполненный PG/Rabbit gate.

Здесь fresh JVM137 tests/8 suites GREEN,26 новых, failures/errors/skips0.
Проверены реальные bot publisher → panel DTO wire для4 типов и обоих scope,
opaque Unicode/raw ID и positive BIGINT выше2^53/Long.MAX_VALUE, отказ рабочих/
неоднозначных endpoints и nonce до соединений, exact loaded panel bytecode.
Source baseline6113a034b (R286), parent7aa9642d4; HEAD/origin/main/actual remote
и14 prior changed paths/blob content сверены с сохранённой R286 source-копией.
Hash-pinned panel R285 и core R286 соответствуют всем текущим main Java sources;
2640 retained class hashes unchanged. Core143 main/60 tests и11 acceptance
test sources freshly compiled; после review fixture ещё раз compiled и26/26
contract cases GREEN. Windows PowerShell5.1 parser/self-test проверяет output
containment, hash, missing executable и native exit7. Это не выполнение Docker
веток оператора и не positive/rollback/concurrency proof настоящей инфраструктуры.

На этой source/test-машине Docker CLI указывает на локальный desktop-linux
npipe, engine pipe отсутствует. Docker Desktop/daemon не запускались, remote
Docker не использовался. IntegrationExecuted=false, IntegrationPassed неизвестен,
38 scenarios PREPARED/PENDING_DOCKER; их нельзя прибавлять к137 passed tests.
Рабочий проект и Docker на другой машине/в другой сессии; их state здесь
не измерялся. Исторические R280/R281/R282 и rollback/private archive сохранены.

Prepared IT создаёт только собственные PostgreSQL16/Rabbit3.13, fake credentials,
nonce-labelled internal network, random loopback ports и отдельную r287 schema.
Production admission/controller/HMAC/client, outbox serialization+publisher
confirms, Rabbit converter/listener, inbox transaction advice, advisory/root
locks, byte verifier и metadata JDBC участвуют в цепочке. Ticket/history/channel/
activity repositories — узкие JDBC-backed adapters, не полный JPA/Boot stack.
Синтетическая DDL на owned PG, local filesystem bytes, без Flyway на рабочей БД,
S3/MAX CDN/provider, session Redis или application YAML/.env.

Матрица38:12 positive BUSINESS/GENERAL ×1/2/10 files ×33/Long.MAX_VALUE;
2 committed-before-ACK broker requeues с original bytes/redelivered flag;
6 rollback/retry (history second item/metadata second item/activity);
4 missing/hash second file;2 post-commit service reconstruction;
2 competing quota transactions с real pg_locks barrier;6 canonical root drift;
4 initial-media PostgreSQL JSONB scoped/legacy lookup. Это заявленная матрица
для следующего запуска, не результат. Broker requeue не full JVM kill/launcher
restart; reconstruction не моделирует потерю процесса. Полные process kill,
current binding rebind, quota reservation/drain/uncertain-result/rollback release,
live provider и общий cross-platform/S6 остаются.

Tracked operator:java-bot/bot-feedback-acceptance/run-max-media-acceptance.ps1.
На машине с Linux Docker engine и JDK17, после commit/push и обновления checkout,
из корня выполнить:

```powershell
powershell -NoProfile -File .\java-bot\bot-feedback-acceptance\run-max-media-acceptance.ps1
```

Оператор требует clean source commit, создаёт detached worktree в fresh repo/.tmp,
сначала compile панели из того же commit и26 contracts, затем38 IT. Рабочие
targets не пересобирает; source worktree/logs/XML/source hashes/r287-proof.json
сохраняются. Full mode допускает только локальный Linux Docker context; снимки
прежних containers хранят только hashes config/ENV/mounts и проверяют прежние
image/start/running/restarts после теста. Cleanup касается только точных labels
owner+nonce/двух test images и пустой owned internal network. Testcontainers
может скачать test images при первом запуске. Рабочий Compose не вызывается.
Если Docker недоступен, full mode останавливается до тестов, не объявляет GREEN.
-ValidateOnly выполняет compile/contracts без Docker и пишет IntegrationPassed=null;
-SelfTest проверяет сам оператор без builds/Docker. Эти режимы не закрывают gate.

Scope9:3 new test Java,runner,acceptance README,task/S0/BOT/new append-only
changelog. Root apply отдельно от staging/commit/push/deploy; commit/push остаются
пользователю. Rebuild/restart рабочего приложения для этого test-only пакета
не требуется; R284/R285/R286 source ещё требуют отдельного coordinated release.
Новых migrations/flags нет, V83/V84/V85 не повторять. S2B снятR190, задача🟡.

Изменённые файлы:

- `ai-context/changelog/2026-11-05_01-278_max_media_chain_preparation_r287.md`
- `ai-context/rules/03-multibusiness-s0-contract.md`
- `ai-context/tasks/task-details/01-278.md`
- `docs/BOT_RUNTIME_CONTRACT.md`
- `java-bot/bot-feedback-acceptance/README.md`
- `java-bot/bot-feedback-acceptance/run-max-media-acceptance.ps1`
- `java-bot/bot-feedback-acceptance/src/test/java/com/example/acceptance/MaxFollowupMediaChainIT.java`
- `java-bot/bot-feedback-acceptance/src/test/java/com/example/acceptance/MaxMediaChainContractTest.java`
- `java-bot/bot-feedback-acceptance/src/test/java/com/example/acceptance/MaxMediaChainFixture.java`
