# 01-278 R132: каталог и ручной review AI-источников бизнеса

Checkpoint: 2026-10-19 11:08:19 MSK.

Промт пользователя: «продолжай».

Baseline HEAD/origin/main и remote main: ffba7dac1818ed34314c77ec1ced9eaecca3eda6,
parent abe247f6bf385017cf1c28cfe492b5559b08e1fe. Fresh baseline, чистый основной
checkout, message и фактические 8 файлов R131 проверены. Source и validation
выполнены в отдельном detached worktree.

Scope — 11 project files: новый controller, две Thymeleaf страницы, navigation
service/link, расширение ScopedAiSourceService, новый real HTTP/PostgreSQL test,
расширение source SQL test, S0 contract, detail задачи и этот changelog.

Каталог открывает один явно выбранный business с current READ и без body/origin
в summary; фильтры и MEMORY provenance применяются до keyset LIMIT 25. Editor
показывает escaped text и последние 10 metadata events, private HTML no-store.
PAGE_KNOWLEDGE_BASE и existing foundation/uiSelector gates обязательны. READ не
означает CONFIGURE: менять могут только live business ADMIN или ALL ADMIN.
Все writes используют стандартные CSRF filters и final SQL authorization.

HTTP create — только KNOWLEDGE DRAFT, actor из Authentication. Ручной review,
возврат в draft и archive не включают автоответы. reviseExpected доказывает
source ID вместе с business/kind/exact key/revision: stale/cross-business/
recreated form даёт 409 без overwrite. Existing internal revise сохранён.
MEMORY можно читать/review только с current origin; creation из диалога PENDING.
Malformed types дают 400. Encoded redirect сохраняет opaque key и не добавляет
общую model в URL; title/body не передаются в redirect или audit history.

Валидация: 174/174 cases GREEN / 8 свежих XML без failures/errors/skips:
HTTP/Thymeleaf/реальные security filters 25, source SQL 82, scoped ingress 53,
navigation/page regressions 14. Проверены реальная cookie/XOR form roundtrip,
CSRF/page denial, draft/review, escaping, key pagination, immutable identity/CAS,
revocation/disabled/blocked/archive, late statement reproof и MEMORY origin.
Flyway V1–V63 и compile 671 main / 498 test sources GREEN; XML freshness сверена
с final Java/templates и собственной suite. Diff check и exact-set/SHA-256 guard;
generated CSS исключён.

Первичный run дал 4 failures из 174 без errors. Исправлены binding→400 и URL
roundtrip; redirect явно запрещает model export. Исправлены test expectations
для existing method-security 403 и CSRF fixture (стандартный test csrf() менял
repository filter и мешал реальной cookie). Production security не ослаблялась.
После source fixes полный targeted run дал остальные 149 cases GREEN и ещё один
HTTP fixture failure: тест не принимал rotated XSRF/SESSION cookies. Теперь test
переносит cookie jar как браузер и требует точный redirect. После этой test-only
правки повторены все 25 HTTP cases; остальные 149 свежих GREEN reports сохранены
без повторения. Оба failed logs сохранены; production CSRF поведение не менялось.

Рабочие DB/config/backfill/flags/deployment и runtime другой машины не затронуты.
Staging/commit/push не выполнялись. PostgreSQL — Testcontainers, external services
mocked; browser/live-provider E2E не проверялся. Новых migrations/flags нет.
State/control UI, dialogue-origin MEMORY curation, privacy/retention, ranking,
worker retrieval и scoped media/LLM/client delivery остаются PENDING, auto-reply
не открыт. Остальные S2B/S3–S6 gates сохраняются; задача 🟡, rollout не разрешён.
