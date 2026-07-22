# 2026-07-21 18:12:35 — remove runtime auto close hours fallback

## Контекст
- Пользователь: `давай следующий шаг`
- Значимый контекст из `01-150`: после cleanup consumers/tests/fixtures следующим прямым шагом оставалось снять bot-runtime fallback `migration:auto_close_hours` из `MaintenanceTasks`, чтобы top-level `auto_close_hours` окончательно перестал быть operational contract.

## Что сделано
- В `java-bot/bot-core/src/main/java/com/example/supportbot/service/MaintenanceTasks.java` удалён runtime fallback для top-level `auto_close_hours`:
  - helper `resolveMigrationOnlyAutoCloseSelection(...)` удалён;
  - при отсутствии `auto_close_config` deprecated top-level `auto_close_hours` больше не влияет на runtime policy;
  - bot runtime теперь явно пишет warning и использует `default:auto_close`.
- В `java-bot/bot-core/src/test/java/com/example/supportbot/service/MaintenanceTasksTest.java` обновлены ожидания:
  - top-level `auto_close_hours` без canonical config больше не задаёт custom duration;
  - `auto_close_hours=0` больше не выключает runtime auto-close;
  - policy source теперь остаётся `default:auto_close`, а legacy top-level поле фиксируется только как ignored deprecated residue.
- В `ai-context/tasks/task-details/01-150.md` обновлены execution log, текущая точка остановки и следующий шаг:
  - top-level `auto_close_hours` больше не является ни write-contract, ни bot-runtime contract;
  - следующий cleanup смещён на root `unblock_request_cooldown_minutes`.

## Проверки
- `java-bot\\mvnw.cmd -pl bot-core "-Dtest=MaintenanceTasksTest" test`

## Следующий шаг
- Перейти к аудиту и cleanup root `unblock_request_cooldown_minutes`, а затем добивать финальную зачистку shared settings schema и remaining migration-only paths.
