# 01-278 / R280 — полная сборка R279, backend → все боты, аудит хранилища

Промт пользователя:

> темпов в проекте собралась уйма. могу безопасно почистить каталог "C:\Users\SinicinVV\git_h\tg_ref_b24_sup\.tmp" ?
> да, и ещё docker диск 50 гигов весит - не много-ли.
> плюс ко всему нужно запустить пересборку backend и затем всех ботов, в рамках последних изменений проекта

Дата:2026-11-03, Europe/Moscow. Работа в исходной runtime-сессии Windows/Docker,
не на C:\Users\sushi source/test-машине R276–R279. Свежие HEAD/origin/main/actual
remote1fea98958aa4dab74b515020978aaec7862af2be/clean; фактический runtime до выпуска
R274/R273/f787 backend и R272/R271/044d runner,18 project containers.

## Сборка и выпуск

- Git archive clean R279, полная production Maven/JDK17/Sass1.83.0 compile/package
  backend, bot-core и Telegram/VK/MAX. Maven containers768MiB/1CPU, тестовые JVM
  отдельно ограничены; рабочие services не останавливались ради памяти/сборки.
  718 fresh targeted tests/32 suites:80 panel/4,99 core/4,539 MAX/24,
  failures/errors/skips0. Это не полный repository test suite или live provider gate.
- JAR classes/resources/CRC проверены против compiled targets,1507 platform/panel
  classes,85 SQL resources; nested bot-core одинаков во всех3 platform archives.
  Vendored Bootstrap CSS/JS SHA384 exact. Новый image
  sha256:b0e12ff574c33fa6156ac4b863cd4bd81ae6ab8b5a82d89a75c64ce474bfdf0b,
  tag iguana-panel:account-r279-1fea98958-20261103. Это4 полностью собранных JAR
  поверх прежнего проверенного OS/runtime base826, не новый OS build.
- Сначала cold stop backend, ops-worker → panel-web; ready12:38 MSK.
  Затем cold stop всего bot-runner, старые feedback consumers0, новый runner
  ready12:41, последний child12:41:57. Compose --no-deps/--no-build/по1 replica.
  Web5e66fae50aa0/worker672c827f4cdc/runner53de0936724e healthy/restart0.
  Telegram1/3 и MAX2 перезапущены, VK JAR тоже собран, active VK channel нет.
- 61 live login/access/SSE/readiness +33 signed actual-child/read checks GREEN.
  Completed404/marker1/no-store, invalid HMAC без marker и positive canonical
  root DTO сверены на реальном backend с БД.12 running hashes exact.
  Общий health/readiness200/UP, production-readiness ready, все DLQ0.
- Все15 прочих containers/IDs/start/ENV/mounts/HostConfig unchanged, включая
  db-migrate/инфраструктуру. ENV/mounts новых3 ролей raw exact; HostConfig
  backend raw exact, runner только bind order/empty DNS с восстановлением
  исходного полного hash bounded guard. Flags/настройки не включались/не менялись.
- V85/85/settings455/accounts/grants/roots53/pending51/ratings36/outbox/ledger17
  delivered/receipt0/MAX session fingerprints exact. Web/worker Flyway skipped,
  runner проверил up-to-date V85; migrations0/backfill0. Provider acceptance sends0.
  Parent ERROR0; worker TLS WARN1, runner WARN8:6 missing PID file при cold start
  и2 прежних legacy Telegram Bot API mirror routes. Их политики не менялись.

Свежий pg_dump10340761 bytes/SHA256
98419B5532395E622591B193DDDE10B111CE221DAFE9EC71C6A7C477931C688B
успешно восстановлен в owned PG192MiB/network-none; verifier resources удалены,
working DB не восстанавливалась.3 новых rollback R280 pointers держат backend826/
runner85a, прежние pointers сохранены. Реального rollback не было; нужна
согласованная остановка новых children перед возвратом backend/runner.

## Storage и очистка

Старый .tmp:142,8789GiB суммарных file lengths,268 top-level entries,156 Git
worktrees до собственных metadata-копий. Ни старые worktrees, ни их локальные
изменения не удалялись. Для полного cleanup нужна отдельная проверка/retention,
простое рекурсивное удаление пока не рекомендовано.

Шесть исторических DB backups скопированы и hash-verified в
../iguana-runtime/tg_ref_b24_sup/recovery/; retained-backups.json связывает старые
и новые paths. Туда же скопирован private quarantine R275 body/HTTP/wire archive,
wire SHA2569EFE1108A7D40991A9EB86F0D4A5065A3E3D50DBD69B9773FF468B4251C1292E.
DB copies/private archive current-user ACL, same-host, не off-host backup.
Новый release/Compose/operator/private ENV snapshot/backup/proofs вне .tmp:
../iguana-runtime/tg_ref_b24_sup/releases/r280-1fea98958-20261103/.
Overlay требует preserved-ENV operator, не bare up. После выпуска существующие
Docker containers не имеют .tmp mounts или Compose-file labels.

После выпуска Docker df:61 images19,54GB/reclaimable4,112GB,20 containers2,628GB/
reclaimable2,482GB,11 volumes4,811GB/reclaimable125,6MB,222 cache entries28,76GB/
reclaimable7,105GB. Shared layers не считать независимыми слагаемыми/точным
размером VHDX. Старые2 test containers/volumes/именованные rollback images не
чистились; общего prune/сжатия VHDX/остановки Docker или WSL не было.
Удалены только7 собственных завершённых build/check containers этой фазы;
их logs/artifacts и скачанный Linux Sass cache сохранены вне .tmp.

До runtime mutation были остановлены/исправлены отдельными tooling versions:
PowerShell quoting Maven -D, offline Linux Sass cache miss, перенос Redis probe
с его before.json/Lua dependency, конфликт helper name Docker с native CLI,
CRLF shell wrapper только в isolated source copy. Полный успешный build отдельно.
Первая live проверка channel3 попала до startup marker; после его появления
повтор GREEN без изменения assertions/нового restart/клиентских отправок.

Обновлены task/S0/BOT через isolated metadata source/replay, старые runtime
checkpoints сохранены. Task🟡, S2B снятR190; scoped MAX byte writer, полный
Redis/live provider gate, policies/AI/S6 остаются. Source R276–R279 активирован,
повторный release не требуется. Staging/commit/push не выполнялись.
Evidence: runtime release directory/sealed-proof.json/test-proof.json/
image-proof.json/own-cleanup-result.json/storage-inventory.json и recovery index.
