# 2026-06-18 13:04:30 - workspace handoff auto processing contract

## Промпты пользователя

- `продолжи`

## Что изменено

- в `spring-panel/src/test/java/com/example/panel/controller/DialogWorkspaceIntegrationTest.java`
  добавлен сценарий `workspaceApiClearsAutoProcessingOverlayAcrossReassignAndNextFollowUp()`;
- сценарий фиксирует live runtime-контракт `workspace` для
  `auto_processing -> reassign -> waiting_client -> next follow-up ->
  waiting_operator` без захода в `publicform`;
- дополнительно в `clean()` добавлена очистка `ticket_ai_agent_state`,
  чтобы `workspace`-срез с AI overlay не тащил состояние между тестами;
- обновлены `docs/ARCHITECTURE_AUDIT_2026-04-08.md` и
  `docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md`, чтобы observed
  handoff semantics была зафиксирована уже для `list/details/workspace`
  как единого read-side коридора.

## Проверка

- `.\mvnw.cmd "-Dtest=DialogWorkspaceIntegrationTest#workspaceApiClearsAutoProcessingOverlayAcrossReassignAndNextFollowUp" test`
- `git diff --check -- spring-panel/src/test/java/com/example/panel/controller/DialogWorkspaceIntegrationTest.java`
