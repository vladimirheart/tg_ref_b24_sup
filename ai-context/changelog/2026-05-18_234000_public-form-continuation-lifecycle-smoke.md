# 2026-05-18 23:40:00 - Public form continuation lifecycle smoke

## Промт пользователя

- `давай дальше более широким пакетом`

## Что сделано

- Расширен `PublicFormFlowSmokeIntegrationTest` до continuation/session
  lifecycle runtime contract: добавлены platform-specific continuation
  сценарии для `telegram` и `max`, telegram deep-link generation и
  rotate-on-read token lifecycle.
- Smoke integration теперь использует temp `shared-config.dir`, чтобы
  runtime settings для `public_form_session_token_rotate_on_read` были
  проверены в изолированном `SpringBootTest` контексте с SQLite.
- Актуализированы `docs/ARCHITECTURE_AUDIT_2026-04-08.md` и
  `docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md` под более широкий
  integration/e2e runtime coverage пакет.

## Проверка

- `spring-panel\.\mvnw.cmd "-Dtest=PublicFormFlowSmokeIntegrationTest,PublicFormApiContractServiceTest,PublicFormApiResponseServiceTest,PublicFormApiControllerWebMvcTest,PublicFormControllerWebMvcTest" test`

## Что дальше

- Следующим пакетом можно уже идти в ещё более широкий public-form/dialog
  lifecycle contract: например, visibility/history/continuation interplay
  после нескольких чтений и mixed platform/runtime settings.
