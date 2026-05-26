# 2026-05-26 18:25:30 — dialog workspace quickaction audit related-events

## Что сделано
- `DialogWorkspaceIntegrationTest` расширен live
  `SpringBootTest + SQLite` сценарием, который проходит через реальные HTTP
  quick-action endpoints `reassign -> resolve -> participants_remove`, а затем
  проверяет `/api/dialogs/{ticketId}/workspace`;
- в runtime payload теперь явно закреплено, что `context.related_events`
  содержит audit trail controller-level quick actions, а не только конечный
  `status/responsible` outcome;
- отдельно зафиксированы operator-visible audit details:
  `reassign: success (responsible_redirected)`,
  `quick_close: success (updated)` и
  `participants_remove: success (participant_removed)`.

## Проверка
- `spring-panel\.\mvnw.cmd "-Dtest=DialogWorkspaceIntegrationTest,DialogReadIntegrationTest,DialogDetailsIntegrationTest" test"`

## Дальше
- добрать следующий более тонкий operator UX/runtime слой вокруг dialog
  consumers: notification/read-marker refresh loop и соседние consumer
  projections, завязанные на тот же status/owner/action lifecycle.
