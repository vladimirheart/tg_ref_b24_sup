# 2026-05-18 23:31:00 - Public form runtime contract smoke

## Промт пользователя

- `давай дальше`

## Что сделано

- Расширен `PublicFormFlowSmokeIntegrationTest`: теперь он проверяет не
  только happy-path submit, но и missing channel, disabled form,
  malformed body и session miss в живом `SpringBootTest` контексте с
  SQLite.
- Зафиксирован runtime error contract для `public-form` API на уровне
  интеграционного слоя: structured `errorCode/path/timestamp` payload
  теперь проверяется не только моками, но и реальным приложением.
- Актуализированы `docs/ARCHITECTURE_AUDIT_2026-04-08.md` и
  `docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md` под этот переход от
  helper split к integration/e2e coverage.

## Проверка

- `spring-panel\.\mvnw.cmd "-Dtest=PublicFormFlowSmokeIntegrationTest,PublicFormApiContractServiceTest,PublicFormApiResponseServiceTest,PublicFormApiControllerWebMvcTest,PublicFormControllerWebMvcTest" test`

## Что дальше

- Следующим пакетом можно идти в ещё более широкий public-form runtime
  contract вокруг continuation/session lifecycle или dialog visibility,
  не возвращаясь к локальным controller/helper split-ам.
