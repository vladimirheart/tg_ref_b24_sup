# 01-278 R118: GENERAL исключён из основных legacy dialog routes

Checkpoint: 2026-10-16 11:51:32 MSK.

Промт пользователя: «продолжай».
Контекст: «продолжи по задаче 01-278»; ранее пользователь уточнил:
«ок. продолжай. отступление: проект крутится на другой локальной машине, если это важно».

Baseline HEAD/origin/main: d4ad49584c8c1823e02d0fafa784cbe954f12528.
R117 проверен по parent b8977ce44f883b1b279d8b3891d0e12bebe38491 и 22 файлам.

Изменения:

- В 8 рабочих Java-файлах отделён non-GENERAL допуск старой панели от
  внутренней проверки уникальности ticket ID. GENERAL закрыт в основных
  legacy list/summary/detail/history/participants/client context и ticket-bound
  authorization. BUSINESS/unresolved не классифицируются как GENERAL.
- SQL исключает GENERAL до counts, нумерации и pagination предыдущих диалогов;
  SQL mark-read/participants rechecks защищают от поздней смены marker.
- Все формы legacy HTTP attachment downloads проверяют owner scope до
  binary I/O, включая Range. Отдельный R117 public-ID API сохраняет GENERAL READ.
  Trusted bot assignment/participants и внутренние storage операции не изменены.
- Добавлены PostgreSQL boundary/guard tests и HTTP denial test; существующие
  тестовые схемы приведены к marker V56 с default FALSE. Обновлены S0-контракт
  и detail задачи. Итоговый scope: 34 файла; новых migrations/flags нет.

Валидация в detached worktree:

- 996/996 test cases в 25 suites GREEN по Surefire XML: permissions,
  raw-ID collisions/exact whitespace/Unicode, unresolved compatibility,
  SQL snapshots/TOCTOU, general direct/previous reads, counters/profile,
  attachment owners/traversal/HTTP Range/byte equality и GENERAL READ regression.
- Первый прогон: 284 cases, один RED в новом numbering fixture из-за близких
  timestamps и прежнего timezone display behavior. Исправлены только времена
  fixture; затронутый suite повторён вместе с дополнительными регрессиями.
  Assertions изоляции и прежние защиты сохранены. Основные 663 Java sources
  и 480 test sources скомпилированы repository Maven wrapper.
- Diff check GREEN. Exact file set и SHA-256 проверяются guarded operator
  перед переносом тех же байтов в основной checkout. Артефакты — в ignored .tmp.

Рабочий экземпляр расположен на другой локальной машине. Агент туда не
подключался; рабочие DB migrations/backfill/config/flags и deployment не
выполнялись. Staging/commit/push остаются ручными действиями пользователя.

Это частичная изоляция legacy UI: full business strict scope, SSE/fanout,
notifications/tasks/AI/background и прочие writers остаются отдельными gates.
GENERAL write permissions, shared-channel UX, graceful resume/version registry,
provider E2E, S2B и полная S3–S6 PENDING. Статус 01-278 🟡, rollout запрещён.
