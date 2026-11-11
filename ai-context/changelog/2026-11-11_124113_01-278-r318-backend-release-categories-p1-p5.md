# R318 — выпуск текущего backend с R317 и категориями P1–P5

Время:2026-11-11,12:41 MSK. Рабочая машина runtime-сессии.
Общий статус01-278 остаётся🟡; изменения категорий включены как отдельный блок
актуального репозитория, вне функционального scope01-278.

Промт пользователя:
«репо обновлён. проверь последние изменения и запусти необходимые пересборки. изменения были не только в рамках текущей задачи»

## Проверенное состояние и выполненный выпуск

- Fresh clean HEAD/origin/main/actual remote63f1fb416e9af73fa80a2a78a808924795bf0188.
  R316/5341feee7 соответствует уже работавшему payload; новые ee98d3583 исправляют
  CAS захвата inbox,63f1fb416 добавляет P1–P5/шаблоны/дашборд. Сверены все новые
  файлы, не только task01-278. Bot/DTO/infra/schema/POM изменений нет.
- Сохранены фактический Compose, env/settings hashes,18 non-target containers,
  число web/worker replicas и rollback tags R318 с прежним imagec05e3e159639.
  Источники/тесты/упаковка подготовлены в detached worktree63f1fb416.
- Принятый R316 cache сверен с1961 inputs/1465 Java и1913 JAR payloads.
  Актуальных inputs1965/Java1469; пересобраны5 main/3 test и4 ресурса.
 2397 unchanged classes/1916 packaged payloads проверены. Maven package и
  Docker image build выполнены; это targeted compile, не полный Java/Sass suite.
- Через Compose --no-deps/--no-build пересозданы только panel-web/ops-worker,
  по1 replica/healthy/restart0. Imagea7518faf8c41ab493326f63cd967c9cff09c2b89331803b88ff3c8504eabe79a,
  tag iguana-panel:work-r318-63f1fb416-20261111, committed application payload63f1fb416.
  JAR SHA25612e93f6cff8b90c7b969825804e7d2b35ef6c365fefe5df985061f4f9cd07837.
  Source manifest SHA256a80c386949d58c31ac213ce0de904cb7916198c59f2872392a849738ecdcfb27.
- Actual Compose:
  ../iguana-runtime/tg_ref_b24_sup/releases/r318-backend-update-20261111/private/compose.final.json.
  Root .env, ENV/HostConfig/mounts/settings/flags прежние; foundation/ui-selector ON,
  manual creation OFF. V91 и история Flyway сохранены, migrator/backfill/provisioning
  не запускались. Боты R295, их PID/start time и18 остальных containers прежние;
  отдельный rebuild/restart ботов не требовался.
- Rollback tags rollback-r318-panel-web-20261111 и rollback-r318-ops-worker-20261111
  сохраняют R316/imagec05e3e159639; original Compose в private/compose.before.json.
  Прежние release/rollback pointers не удалялись. Реальный откат не разыгрывался.

## Проверки

342 Java tests/9 suites GREEN, failures/errors/skips0:15 concurrent claims R317,
88 scoped Spring/JPA/HTTP,9 inbox,60 incoming,20 creation,123 AI delivery/recovery,
5 priority analytics,7 settings config,15 timestamp/filters.
Только nonce-owned PostgreSQL, container/volume удалены; working DB не использована.
Рабочие контейнеры во время тестов не менялись.

Штатный tools/category-priorities-qa.cjs выполнен в Edge320/1440:
create/edit/reload/rename/delete, P1–P5/defaultP3/legacy unassigned,
category/priority/direction charts/empty states;4 screenshots сохранены.
18 actual browser checks подтвердили реальный login, dashboard/settings200,
штатный read-only POST отчёта, новые maps, exact JS hash и все P1–P5 в
несохранённом шаблоне. Прочие browser writes блокировались; working settings
не сохранялись, внешние тестовые сообщения/AI approvals/UNKNOWN retry не запускались.
Live JS errors0.

Health/readiness200UP,5 DLQ0. Неожиданных ERROR/FATAL/OOM0.
Один ERROR web оставлен ранним smoke GET к /api/dashboard/data вместо штатного
POST; оператор исправлен, положительные проверки GREEN. Запись сохранена,
не скрыта; приложение ради неё не менялось. Ранние operator diagnostics сохранены.

## Файлы проекта

Обновлены только документы: README,01-278 task details,BOT_RUNTIME_CONTRACT,
AI/shared-work/common-system runbooks, production-e2e-smoke S6 и этот changelog.
Runtime R318/свежие hashes/Compose/rollback отражены; R317 release снят с остатка.
Полная S6/provider/restore/fleet/rollback приёмка не объявляется выполненной.
Выпуск включает отдельное feature-изменение категорий; новых функциональных
source-правок в этом запросе нет. Metadata сначала сверена в sandbox,
затем применяется в primary; staging/commit/push не выполняются.

Evidence вне .tmp: ../iguana-runtime/tg_ref_b24_sup/releases/r318-backend-update-20261111/
(preflight/cache/baseline-bytecode,validation/proof.json,bytecode,
browser-fixture,image,deployment,live-smoke,runtime-final proofs).
Private snapshots/logs защищены ACL и не входят в Git.
