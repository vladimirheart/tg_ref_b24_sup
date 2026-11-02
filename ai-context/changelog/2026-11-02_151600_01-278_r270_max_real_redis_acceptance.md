# 01-278 R270: приёмка выбранного вопросника MAX на настоящем Redis

Промт пользователя: «давай дальше по задаче».
Контекст: продолжение01-278 после выпуска R269; рабочий экземпляр на этой машине,
ограниченные ресурсы без возможности дополнительно освободить ОЗУ.

## Выполнено

- Fresh clean HEAD/origin/main/actual remote7423a4919e7a4860d35ba69a152780a57694de97
  (R269,parentda7a15231),4 previous metadata/manifests exact. Исходный test blob
  5b3bc5799adcf88355a0e0915e640503e1e785c8/SHA256
  6870B69D7054070561102C156BEA36AE6BF6274E8B27AE6D4858ABE2D112B77E
  сохранён. Сначала изолированные source/replay worktrees, root не менялся до
  independent GREEN; runtime source/POM/resources не изменялись.
- Existing MaxQuestionnaireSelectionTest получил только protected factories,
  delivery-cache hook и доступ к test helpers. Обратное преобразование exact
  baseline доказывает сохранность всех109 сценариев/assertions. Новый opt-in
  MaxQuestionnaireRedisIT наследует их без копирования и выполняет на real
  BotSessionStoreService/guard Redis Lua/CAS. Default test mode остаётся direct.
-112 Redis executions:109 inherited (BUSINESS1–4/GENERAL2–4, answer/back/optional
  skip, ACK/prompt failure/dedup reset, CAS gap/optimistic retry/legacy) +2
  BUSINESS/GENERAL v4 restart +1 stale CAS/channel/user/namespace isolation.
  Graceful restart exact owned Redis, fresh Lettuce connection, independent
  store/loadAll: raw JSON/frozen scope/selection/answers/accepted journal/index/
  positive TTL/processed markers exact. После удаления только transport dedup
  keys replay старого ID не продвигает вопросник, fresh events завершают исходный
  selected command. Доставка prompt не объявляется exactly-once.
- Tracked run-redis-acceptance.ps1 принимает только pinned cached image ID,
  no pull/default live fallback/Compose/.env. Dedicated owned container/network/
  volume с owner+nonce, password/sentinel, loopback port,64MiB/CPU1, AOF/fsync
  always/noeviction. Java проверяет exact ID/name/image/labels/port/memory и
  sentinel; только уникальные namespaces и bounded SCAN, нет FLUSHDB. В конце
  DBSIZE2 (sentinels), owned resources удалены в finally/ENV restored. Фактически
  использован cached Redis7 image
  sha256:ff02b58f971e7d7d156a1267e283fcbbeee91773b6aa36c49dac28ecfe28eadf;
  рабочий Redis d21adeceb108 не останавливался/не очищался.
- Source/replay независимо:214 default executions/20 suites +112 Redis/1 suite,
  failures/errors/skips0;326 выполнений на копию, не652 уникальных теста.
  Source Redis24.76s/replay38.04s.251 frozen inputs/265 class hashes exact;
  все244 runtime classes (212 core+32 MAX) byte-exact прежнему R268 proof.
  Maven96MiB/test JVM192MiB/CPU2, offline, последовательные bounded прогоны;
  рабочий стенд не останавливался, дополнительная ОЗУ не запрашивалась.
- Исправлены только новые test/operator fixtures: recursion/splatting helper
  PowerShell, internal bridge без published host port, Windows inspect pipe
  drain до waitFor, plain ObjectMapper вместо штатного Jackson builder. Первый
  реальный прогон матрицы имел110/112 GREEN, две restart проверки упали на
  независимом mapper. После исправления112/112 GREEN; не product fixes и не
  ослабление assertions. Исходные failed logs/owned cleanup proofs сохранены.
- Fresh runtime guards: все18 working containers ID/image/ENV/mounts/HostConfig/
  started/restarts exact; runner R269/R268/da7a15231/image7259,
  web+worker R257/7b9fea55a/image90cd. Shared configuration exact. DB V85/85,
  settings455/accounts/grants/roots/pending/ratings/outbox/ledger17delivered/
  receipt0/flags и MAX session/index fingerprints прежние. ReadinessUP,
  feedback3 consumers/ready0/unacked0. Общий health503/inboundDLQ1 прежний;
  operational gate не закрыт. Backup/rollback R269 fresh-verified сохранены,
  working DB не восстанавливалась/откат не выполнялся.
- Task01-278/S0/BOT обновлены только после technical GREEN; historical task tail
  начиная Runtime R269 exact unchanged. Metadata source/replay отдельно сверены.
  Operators/proofs/reports: .tmp/01-278-r270-evidence/; source harness proof/Redis
  logs также в bot-max/target изолированных копий.7 project files в пакете.

## Не выполнено и остаток

Это test-only пакет: rebuild/deploy/restart рабочих сервисов не нужны и не
выполнялись. Нет migrations/db-migrate/bootstrap/repair/backfill/flags/requeue/
purge/provider/customer sends/staging/commit/push. Удалены только disposable
Redis containers/networks/volumes с синтетическими данными; тестовые данные
невосстановимы и не требуются, XML/логи/proofs сохранены.

Доказан graceful Redis restart и потеря только dedup keys при сохранной session,
не eviction самой сессии, kill/full bot application/launcher/fleet restart,
live MAX SDK/webhook/client callback, ретроактивная дедупликация старых IDs или
cross-platform rebind. Пять блоков прежние: live feedback/recovery/review;
оставшиеся provider/media/GENERAL/questionnaire/service-chat/child principals;
global/system policies; AI privacy/retention/delivery; общая приёмка/rollback и
первый production S6. Задача🟡, S2B снят решением R190.
