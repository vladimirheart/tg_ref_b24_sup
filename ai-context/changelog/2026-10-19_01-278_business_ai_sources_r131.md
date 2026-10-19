# 01-278 R131: business-owned knowledge/memory и typed retrieval

Checkpoint: 2026-10-19 09:59:46 MSK.

Промт пользователя: «продолжай».

Baseline HEAD/origin/main и remote main: abe247f6bf385017cf1c28cfe492b5559b08e1fe,
parent c891565e4e42d1f882e3c46ddc1e38d1338dccd0. Проверены fresh fetch, чистый
основной checkout, message и фактические 12 файлов R130. Все source changes
и проверки выполнены в detached worktree этого baseline.

Фактический scope — 8 файлов:

- V63: отдельные business-owned KNOWLEDGE/MEMORY и metadata events; immutable
  identity/origin, version guard, FK canonical business. Legacy rows не меняются.
- ScopedAiSourceService: draft/review/CAS, CONFIGURE в mutation SQL и READ на
  чтении, атомарный audit, current MEMORY origin, approved retrieval одного
  BUSINESS и reproof точного candidate payload/version перед будущим handoff.
- AiRetrievalService: отдельный typed API без legacy intent/context/candidates,
  bounded literal search и immutable result; autoReplyAllowed всегда false.
- Два новых PostgreSQL suites: 80 source cases и 1 upgrade V62→V63 с проверкой
  preservation, constraints, immutable identity/version/audit.
- S0 contract и detail задачи фиксируют границы и остающиеся gates.

Валидация: 228/228 cases GREEN в 9 свежих XML, без failures/errors/skips.
Сохранены R130 58+53, raw AI 28 и full Spring/JPA/HTTP 8 cases. Flyway V1–V63,
compile 670 main / 497 test sources и diff check GREEN. Exact-set и SHA-256
guard; Sass output исключён. PostgreSQL — Testcontainers, live E2E не проверялся.
Первичный run выявил неверное ожидание текста ошибки при id=id+1: PostgreSQL
блокировал GENERATED ALWAYS раньше trigger. Test использует id=DEFAULT для
проверки immutable trigger; повторный upgrade run GREEN. Остальные 227 cases
GREEN без изменений production code; failed initial log сохранён. Freshness
проверена относительно production Java/SQL и source каждой собственной suite.

Global/unclassified/legacy sources исключены даже для ALL_BUSINESSES. Разрешение
global sharing требует отдельной policy; текстовый business не является owner.
Новых HTTP/UI/worker learning paths нет; R130 incoming остаётся handoff-only.
Privacy/retention, ranking/evidence policy, page/CSRF, worker retrieval и scoped
media/LLM/client delivery PENDING, auto-reply не включён. V63 — source migration,
проверенная только на test DB. Рабочие DB/config/backfill/flags/deployment,
staging/commit/push и runtime на другой машине не затрагивались. Статус 🟡,
остальные gates PENDING, rollout не разрешён.
