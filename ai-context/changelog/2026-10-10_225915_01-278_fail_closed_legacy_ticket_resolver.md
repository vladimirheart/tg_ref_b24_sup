# 01-278: fail-closed resolver legacy ticket ID

- Время: `2026-10-10 22:59:15`
- Файлы: `spring-panel/src/main/java/com/example/panel/repository/TicketRepository.java`, `spring-panel/src/main/java/com/example/panel/service/{BotRuntimeTicketReadService,DialogAutoCloseSchedulerService,IncidentService,PanelTaskService}.java`, их тесты, `ai-context/tasks/task-details/01-278.md`
- Промт пользователя: `дальше. более широким пакетом`
- Что сделано: bare legacy `ticket_id` больше не резолвит произвольный `Ticket`. Repository читает не более двух кандидатов и возвращает запись только при ровно одном совпадении. Bot read, auto-close, incident relation и task linking используют этот fail-closed resolver; ambiguity приводит к отсутствию ticket, а не к доступу/изменению чужой записи.
- Границы: composite ingress paths сохранены; `business_id`, legacy aliases, Flyway-схема, рабочая БД, migration/backfill, feature flags и production rollout не менялись.
