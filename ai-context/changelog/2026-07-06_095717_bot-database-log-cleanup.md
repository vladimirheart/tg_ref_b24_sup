# 2026-07-06 09:57:17 — Bot database log cleanup

## Связанные задачи

- `01-140` — Вынести `monitoring_check_history` в `monitoring.db` и ограничить хранение логов 30 днями

## Пользовательский промпт

> вычисти сейчас все логи из "C:\Users\SinicinVV\git_h\tg_ref_b24_sup\spring-panel\bot_database.db"

## Затронутые файлы

- `ai-context/tasks/task-list.md`
- `spring-panel/bot_database.db`

## Что сделано

- Перед очисткой создан аварийный бэкап базы во временной папке: `C:\Users\SINICI~1\AppData\Local\Temp\bot_database_before_log_cleanup_20260706_095604.db`.
- Из `spring-panel/bot_database.db` удалены технические log/audit-данные из таблиц:
  - `ai_agent_event_log`
  - `dialog_action_audit`
  - `monitoring_check_history`
  - `workspace_telemetry_audit`
- Бизнес-данные диалогов и заявок не трогались: `chat_history`, `messages`, `tickets`, `notifications` и другие рабочие сущности оставлены без изменений.
- После удаления выполнен `VACUUM`; размер `bot_database.db` уменьшен с `105234432` байт (`100.36 MB`) до `26636288` байт (`25.40 MB`).

## Проверки

- Подсчитаны строки до и после очистки:
  - `ai_agent_event_log`: `184 -> 0`
  - `dialog_action_audit`: `131 -> 0`
  - `monitoring_check_history`: `33275 -> 0`
  - `workspace_telemetry_audit`: `2166 -> 0`
- Перепроверен физический размер файла после `VACUUM`: `26636288` байт.

## Примечания

- Шаг решает срочную ручную очистку, но не заменяет основную задачу `01-140`: monitoring history всё ещё нужно вынести в `monitoring.db` и ограничить retention 30 днями на уровне кода.
