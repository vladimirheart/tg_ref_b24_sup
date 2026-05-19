# 2026-05-19 00:32:00 - Public form polling and history lifecycle smoke

## Промт пользователя

- `давай дальше более широким пакетом`

## Что сделано

- Расширен `PublicFormFlowSmokeIntegrationTest` на live polling/history
  lifecycle contract для `public-form`.
- Добавлен integration-сценарий, который фиксирует real-app
  `sessionPollingEnabled/sessionPollingIntervalSeconds` из shared runtime
  settings через `/api/public/forms/{channel}/config`.
- Добавлен integration-сценарий на shared conversation lifecycle после
  создания web-form session: operator reply, system notifications и
  `replyPreview` для threaded ответа теперь проверяются через реальный
  `/api/public/forms/{channel}/sessions/{token}` payload.
- Актуализированы `docs/ARCHITECTURE_AUDIT_2026-04-08.md` и
  `docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md` под следующий более
  широкий session/runtime coverage пакет.

## Проверка

- `spring-panel\.\mvnw.cmd "-Dtest=PublicFormFlowSmokeIntegrationTest,PublicFormApiContractServiceTest,PublicFormApiResponseServiceTest,PublicFormApiControllerWebMvcTest,PublicFormControllerWebMvcTest" test`

## Что дальше

- Следующим пакетом можно добирать live dialog/public-form interplay уже на
  `resolve/reopen` и adjacent previous-history/session continuity flows, если
  нужен ещё более широкий end-to-end lifecycle слой.
