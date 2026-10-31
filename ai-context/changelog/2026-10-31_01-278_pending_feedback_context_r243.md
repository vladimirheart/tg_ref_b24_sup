# 01-278 R243 — подготовка сохранённого контекста pending feedback

Время:2026-10-31,14:54+03:00, текущая Windows/Docker-сессия, Europe/Moscow.
Инициирующий промт:

> давай дальше. по ресурсам рабочей машины ничего дополнительно изменить не могу

Значимые уточнения и действующее разрешение:

> освободил сколько получилось. запускай на имеющихся мощностях. самые больший потребители это vmmemwsl и vscode, где мы с тобой работаем
> комит и пуш разрешаю до конца суток

## Изменения

Scope8:новая V84__pending_feedback_context.sql, новый
PendingFeedbackContextMigrationPostgresqlTest.java, две SQL fixtures
pending-feedback-context-before.sql / pending-feedback-context-contract.sql,
AutomationRunOwnershipPostgresqlTest.java (target83), task01-278, S0, этот changelog.
Baseline HEAD/origin/main/actual remote df1bfc6da86bad3e70782c008326b91f14e4c880,
clean; parent94c2d812c и metadata3 paths проверены. Source work в detached копии.

Новые pending получают из единственного exact root под FOR SHARE неизменяемые
epoch/public/business/GENERAL плюс прежнюю owner/raw/channel identity. Missing,
duplicate, foreign owner/channel и partial/spoofed context дают23514. UPDATE
не может перепривязать identity/created_at/context, но сохраняет expiry/source/
sent_at lifecycle writes. Старые pending остаются unbound, без inferred context.

Новый root INSERT всегда генерирует новый PostgreSQL-owned UUID epoch. Смена
user/raw/channel/public/business/GENERAL меняет его, включая ABA. Обычные status/
work updates не меняют epoch; это не lifecycle generation или GENERAL revision.
V84 при будущем применении создаёт технические epochs существующим корням,
не классифицируя исторические pending. Canonical schema остаётся backend-owned.
Java-bot schema/producers/runtime consumers в этом пакете не менялись.

## Проверки

Native PostgreSQL16: все83 прежних SQL, old-data fixture, V84 и156 SQL assertions
GREEN. Unchanged V83 даёт ожидаемый assertion RED storage contract exists;
frozen3 new test inputs после этого не менялись. BUSINESS/GENERAL/UNRESOLVED,
exact padded/Unicode/quoted IDs/BIGINT>2^53, capture/immutable/replay lifecycle,
identity rotations/ABA/recreate, rollback/foreign/duplicates/spoof/legacy-unbound.
Отдельный двухсессионный probe:55P03 на root FOR UPDATE NOWAIT до rollback,
затем lock доступен и probe request не сохранён. Это row-lock, не phantom proof.

GREEN30 JUnit/5 suites одним полным selected run,0 failures/errors/skips:
новый Flyway V83→V84 test1 (внутри те же156 SQL assertions), automation8/outbox16/
GENERAL upgrade3/BUSINESS upgrade2.156 assertions не прибавляются к30 JUnit как
отдельные tests. Предыдущие assertions сохранены; один automation fixture pinned
target83 для исходного V82→V83 «ровно1» контракта. R241900 tests не повторялись.

Verified R241 JAR SHA25679815e522ea82f9bb211ec77b7ad6cd88c37789a26e44a02b0e1ffaf09ebbf3f,
1422 unchanged main classes;8 test sources compiled, не full build. Первый compile
не включал два support класса, compile-v2 добавил их без правки tests. Первый
Java run остановился на Metaspace144MiB до XML, не product failure. Полный повтор
успешен с heap160/meta224/code32/stack512k в том же helper512MiB. PG192MiB/1CPU,
helper1.5CPU, sequential; WSL/VS Code/working services ради ОЗУ не останавливались.
RED logs/operators сохранены, frozen assertions не менялись, никакого skip ради RAM.

Detached replay/exact8 content guards/diff checks предшествуют main apply;
source apply без staging/commit/push/deploy, публикация — отдельная разрешённая
фаза с проверкой московской даты и remote. Факты Git — publish.json, не обещание
в момент source validation. Evidence .tmp/01-278-r243-evidence/:native results/
lock logs/compiled hashes/fresh XML/runtime+DB snapshots/manifests/apply results.

## Граница и остаток

Это source-only schema preparation, НЕ выполнение рабочей миграции. В рабочей
БД83/V83, webR241/runtimeR242 прежние; migrations/backfill/flags/queues/provider/
worker/bots/infra не менялись. Пересборка/restart не нужны для этой подготовительной
фазы; автоматическое обновление db-migrate не разрешено. Rollback tags сохранены.
Следующий пакет: entity/projection и saved epoch/tuple proof в pending read,
prompt dispatch, storeFeedback, retry/recovery. Consumers пока не проверяют
новые columns: end-to-end feedback isolation не заявляется. Открыты lifecycle
generation/DB uniqueness/non-cooperating writers/phantoms/cache/owning rating,
media/policies/AI/fleet/questionnaires/provider/flags/S6/DLQ1; S2B снятR190, задача🟡.
