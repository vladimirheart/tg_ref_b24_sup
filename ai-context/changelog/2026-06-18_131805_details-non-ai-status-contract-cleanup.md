# 2026-06-18 13:18:05 - details non ai status contract cleanup

## Промпты пользователя

- `продолжи`

## Что изменено

- в `spring-panel/src/test/java/com/example/panel/controller/DialogDetailsIntegrationTest.java`
  убраны три размытых ожидания `waiting_operator | auto_processing`;
- `detailsApiRefreshesResponsibleAndStatusAfterReassignResolveAndReopenLifecycle()`
  теперь явно фиксирует `waiting_operator` после `reopen` в non-AI ветке;
- `detailsApiRefreshesDialogUnreadLoopWithoutImplicitlyAckingBellNotifications()`
  теперь дважды явно фиксирует `waiting_operator` для unread-loop без seeded
  `ticket_ai_agent_state`;
- обновлены `docs/ARCHITECTURE_AUDIT_2026-04-08.md` и
  `docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md`, чтобы этот cleanup был
  отражён как сужение read-side контракта, а не как изменение runtime.

## Проверка

- `Remove-Item -LiteralPath 'spring-panel/target' -Recurse -Force -ErrorAction SilentlyContinue; .\\spring-panel\\mvnw.cmd "-Dtest=DialogDetailsIntegrationTest#detailsApiRefreshesResponsibleAndStatusAfterReassignResolveAndReopenLifecycle" test`
- `.\spring-panel\mvnw.cmd "-Dtest=DialogDetailsIntegrationTest#detailsApiRefreshesDialogUnreadLoopWithoutImplicitlyAckingBellNotifications" test`
- `git diff --check -- spring-panel/src/test/java/com/example/panel/controller/DialogDetailsIntegrationTest.java docs/ARCHITECTURE_AUDIT_2026-04-08.md docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md`
