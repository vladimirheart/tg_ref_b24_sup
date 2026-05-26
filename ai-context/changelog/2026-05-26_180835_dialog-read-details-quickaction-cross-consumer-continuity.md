# 2026-05-26 18:08:35 — dialog read details quickaction cross-consumer continuity

## Что сделано
- `DialogDetailsIntegrationTest` расширен live
  `SpringBootTest + SQLite` сценарием, который проходит
  `reassign -> resolve -> reopen` и затем проверяет `/api/dialogs/{ticketId}`
  на updated `responsible/rawResponsible`, сохранённые categories и
  runtime-статус `waiting_operator`;
- `DialogReadIntegrationTest` добран соседним lifecycle-сценарием для
  `/api/dialogs/{ticketId}/participants`: после
  `reassign -> removeParticipant` route подтверждает обновлённый participant
  pool без drift относительно quick-action runtime;
- пакет прогнан вместе с уже существующим `DialogWorkspaceIntegrationTest`,
  чтобы подтвердить общий cross-consumer contract на одном и том же
  quick-action lifecycle.

## Проверка
- `spring-panel\.\mvnw.cmd "-Dtest=DialogWorkspaceIntegrationTest,DialogReadIntegrationTest,DialogDetailsIntegrationTest" test"`

## Дальше
- добрать более тонкий operator UX/runtime слой вокруг dialog consumers:
  audit/related-events continuity после quick actions, notification/read-marker
  refresh loop и соседние consumer projections, завязанные на тот же
  status/owner lifecycle.
