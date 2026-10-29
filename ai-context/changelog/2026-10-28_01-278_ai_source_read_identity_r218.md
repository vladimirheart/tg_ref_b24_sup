# 01-278 R218 — библиотека AI и общий SQL-барьер автора

Работа начата2026-10-28, Europe/Moscow. Baseline HEAD/origin/main/actual remote
18a00b3dc38ba7b2624cca40deda9be941a28c77 + известные uncommitted R21712 paths,
проверенные по hashes. R217 сохранён без reset/stash/commit. Runtime checkpointR216
и старые rollback/backup не менялись. Финальная валидация завершена2026-10-29.

Промт пользователя:

> давай дальше

## Изменение

Пять APIs выбора бизнеса/CONFIGURE/каталога/карточки/истории ScopedAiSourceService
принимают Authentication вместо String identity. Bound ID/exact raw username
проверяются самим SQL, оба AI-контроллера передают сессию. Active business,
READ vs ADMIN CONFIGURE, current unique account, MEMORY origin/collision,
catalog privacy/filter-before-limit и прежние read failures сохранены.

Дополнительный review обнаружил Java/PG case-fold gap U+0130: i+U+0307 vs i.
READ мог взять статус/права другого folded account даже при ID/raw join. Общий
BOUND_ACTOR теперь требует own account enabled/not blocked и совпадение SQL fold
с n.user_identity. Он используется5 readers и4 create/revise writers. При
расхождении — отказ, не fallback на соседа. Нет новых grants/normalization policy.
CAS/content/review/source ID/raw audit/атомарность сохранены. Statement-time,
не response-wide/global lock; history может отказать после ранее разрешённого find.

## Валидация

Финальный прогон **810 tests/13 suites GREEN**, failures/errors/skips0. Те же3
frozen source files/8 class hashes/case names, freshness13 XML проверена.
215 новых frozen cases:163 SQL+25 real-login/CSRF HTTP+27 supplemental SQL.
RED188/87/0 на R217; intermediate783/12 GREEN оказался недостаточен — дополнительный
RED27/22/0 на intermediate bound-ID/raw join воспроизвёл Unicode gap во всех
путях чтения/записи. Источники/assertions зафиксированы до исправлений.
595 existing cases: прежние570/9 +25 MEMORY HTTP. Пять fixtures адаптированы:
61 identity argument,4 never matchers,1 exact ID/raw spy; inverse сохраняет assertions.
Compile-v1 missing Mockito matcher import исправлен fully-qualified вызовом,
без ослабления проверок; исходный log сохранён.

Maven/JDK17, PostgreSQL16 Testcontainers/fresh schemas, offline dependencies;
1353 Java и83 migration raw inputs проверены.1832 inputs: только4 generated CSS
EOL-only после штатного Sass. Новых schema/dependencies/flags нет, JS не rerun:
UI/JS не менялись. Working datasource/provider calls не использовались.
Evidence `.tmp/01-278-r218-evidence/`; main apply только после финального GREEN,
same patch/replay/exact path/hash guards. Scope11 source/test +3 documentation.

## Граница

Runtime остаётся webR215/rolloutR216, workerR198, foundationOFF. R217/R218 требуют
отдельного web build; rollout/migration/backfill/flags здесь не выполнялись.
Старые R184/R198/R204/R207/R216 rollback/V83 artifacts сохранены. Известный
health503 из-за прежнего inboundDLQ=1 не скрыт, readinessUP; очередь не трогали.
Следующие identity gaps: retrieve/isCurrentCandidate/handoff; child/GENERAL/
policies/privacy/delivery/fleet/flags/S6 открыты, historical S2B снятR190, Task🟡.
Staging/commit/push нет, ручные команды фиксации не предлагаются.
