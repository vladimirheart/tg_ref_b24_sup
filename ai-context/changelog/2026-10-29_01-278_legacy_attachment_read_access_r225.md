# 01-278 R225 — current business READ перед legacy attachment download

Время: 2026-10-29, 12:18 +03:00; текущая Windows/Docker-сессия тестового экземпляра.
Промт пользователя:

> делай

Контекст: подтверждение следующего пакета — бизнес-доступ к старым URL вложений,
предложенного после source R224/rollout R223. Это source scope, не новый deploy.

## Изменение

Свежие HEAD/origin/main/actual remote d6273481ea7854e2060441f0ba0d2e6f071d06fa,
parent aa69238ca88000e99b7503d4132485627713397f. Checkout чистый; все13 paths/content
предыдущего проверенного R224 сохранены, subject/parent/scope/content проверены.

Новый LegacyAttachmentReadAccess обязателен в AttachmentService, без fallback
конструктора. Direct/path/key downloads (оба overloads/4 HTTP routes) после
существующего PermissionService/page/metadata/owner proof повторяют current
account/root/business READ одним SQL до любого binary open. Требуются bound
PanelUserPrincipal/positive ID/exact raw name/status, own enabled/not blocked,
SQL case-fold uniqueness, unique raw root и explicit non-GENERAL. Shared READ
policy требует active business и membership/ALL_BUSINESSES с4 known READ roles;
global admin/name admin не даёт business-data bypass. SQL fold берётся от того
же ID/raw-name row, не из Java-normalized имени или поиска соседнего аккаунта.
UNRESOLVED только strictOFF/current account; foundationOFF не отключает guard.
Отказ/SQL failure — прежний empty404, без local/S3 open. Остальные storage APIs
(upload/delete/describe/knowledge/avatar/background) не изменены.

## Валидация

546 tests/16 suites одним selected run GREEN, failures/errors/skips0.
Новые frozen252: SQL180 + real form-login/R189/session/CSRF HTTP72. Baseline v2
на неизменном product:155 assertion failures/errors0, controls97 GREEN; те же
3 source hashes/8 class hashes/252 case names после исправления GREEN.
Покрыты READ роли/ALL, exact raw names/BIGINT, rename/delete+reuse/status,
Unicode neighbour/ambiguity, admin refusal, flags/GENERAL/UNRESOLVED, late
account/grant/root changes после metadata lookup, storage read errors, no binary
open при отказе и Range byte integrity. Два metadata provider проверены с
наблюдаемым isolated binary backend; внешний MinIO/S3 не вызывается.

Прежние294 cases/14 suites: actual local_fs/loopback HTTP, URL opaque escaping/
traversal, metadata ownership, rollback upload, descriptors/photo и GENERAL guard.
13 old fixtures лишь получают19 dependency constructor args; permitted stub
изолирует новый collaborator, assertions/другая логика прежние, inverse exact.
Новые security tests используют real SQL guard. Первый compile нового HTTP test
нашёл конфликт FILE из wildcard AssertJ import; до frozen v2/RED импорт заменён
на assertThat. После RED новые assertions не менялись; v1 log/hash сохранены.

Полный main/test compile JDK17 в bounded896MiB helper; Testcontainers PostgreSQL16,
fresh schemas, без working datasource. 1365 Java/83 migration sources/1844 inputs
совпали;4 generated CSS EOL-only после Sass. UI/JS/SCSS/dependencies/schema/flags
не менялись. Свежие XML/log/source/class/input hashes, exact scope, replay и
diff checks проверены перед main apply; fixtures inverse и frozen cases сохранены.

## Границы и остаток

Scope22:2 main,13 adapted old tests,3 frozen new tests/fixture,1 permitted stub,
task/S0/этот changelog. Guard — statement-time current READ до open, не общий
metadata/READ transaction lock, не full child business/generation attribution и
не отзыв уже начатого stream. Эти media/child/provenance границы остаются в плане.
Также открыты GENERAL String entry lookup, policy/AI privacy/delivery, fleet/
controlled flags/S6 и прежний DLQ1. Historical S2B снятR190, задача🟡.

Source apply без staging/commit/push/deploy. Стенд не rebuild/restart; последний
runtime R223: webR222/workerR198/foundationOFF/readinessUP/health503DLQ1.
Миграции/backfill/flags/queue/provider не трогались, V83 не повторялась;
rollback/backup сохранены. Evidence .tmp/01-278-r225-evidence/ и detached source/
replay; runtime проверен отдельно от source validation.
