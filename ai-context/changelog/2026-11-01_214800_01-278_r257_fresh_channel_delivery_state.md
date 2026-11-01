# 01-278 R257: свежий сохранённый контекст канала перед отправкой Telegram

Дата: 2026-11-01, Europe/Moscow.
Промпт пользователя: «продолжай».

## Изменения

- База da881c6d5a59c443b0796f0cd9a67b83e40f76d0 — пользовательский metadata R256,
  parent0df04ecf6/scope4 проверены. Working R256 не пересоздавался.
- ChannelRepository: scalar native transport projection по exact BIGINT ID,
  REQUIRES_NEW/read-only/READ_COMMITTED, отдельно от JPA/OSIV cache и outer snapshot.
- ChannelTransportService: один immutable redacted transport snapshot до выбора
  targets на весь batch. Fresh DB/local state checks до network setup и каждого
  send. Delete/inactive/platform/token/binding/recipient/network/API config
  changes, missing/error deny, без reroute/retry. R255 credential checks intact.
- Три новых suites: persisted-state matrix197, batch context5, real PostgreSQL35.
  Old2 fixtures — только query stubs; inverse checked, старые598 assertions intact.
- Task/S0/BOT_RUNTIME_CONTRACT актуализированы, runtime checkpoint R256 сохранён.

## Проверки

RED197:187 assertion failures/10 controls,0 errors/skips (additive repository API,
прежний R255 dispatch). Frozen RED source/bytecode exact. Final835/15 suites:
237 новых/598 прежних,0 failures/errors/skips. Real Hibernate/JPA/transactions/
PostgreSQL16: outer REPEATABLE_READ и cached entity остаются старыми, fresh query
видит independent commit и запрещает handoff во всех4 entry variants.
BIGINT>2^53/nulls/stable sends/две цели/смена DB и local object проверены.
Реальный provider не вызывался; это не full feedback/fleet acceptance.

Native repo wrapper offline, heaps96/128MiB, JDK25 ByteBuddy option TEST-only.
Incremental2 main sources/1435 classes:4 existing changed,2 added,1429 unrelated
exact;2 прежних nested records имеют только debug metadata delta, javap -c -p
exact.17 compiled inputs exact. Archive SHA256
5380EFDEF34A4E5E1428EB8ABC0F0DE68B0F3B7C899C65C2DE85B6C2B253278F.
Не full Maven/Sass build, image/executable JAR не создавались.

Изолированная r257_test PostgreSQL192MiB/1CPU/loopback ephemeral port использована
только для тестов и удалена вместе с собственным container после identity guard.
Working18 и DB/settings/accounts/grants/history controls unchanged;
config unchanged кроме periodic backup-policy-runner.status. Runtime R256,
rollback pointers и backup сохранены. Рабочую БД не восстанавливали.
Нет deploy/migrations/backfill/flags/queue mutations/реальных provider messages/
NetBox/staging/commit/push.

## Граница и evidence

Последняя query/catalog check и provider I/O не атомарны: no lease/ABA/
in-flight revoke/exactly-once. Проверяются transport columns канала, не бизнес-
привязки/memberships/external chat participants/global network config. Прочие
direct/getMe/metadata paths и positive feedback/GENERAL/policies/AI/fleet/S6
остаются. Source R257 ещё не выложен. S2B снятR190, задача🟡.

Scope11:2 main sources,3 новых tests,2 fixture-only updates, BOT_RUNTIME_CONTRACT,
task,S0,этот append-only changelog. Detached sandbox/replay/exact source/blob/
scope/diff guards. Evidence .tmp/01-278-r257-evidence/: reports/proofs/frozen
source/bytecode/compiled manifests/archive/runtime snapshots. Старый operator
class-count RED сохранён; corrected v2 проверяет точные paths и record bytecode.
