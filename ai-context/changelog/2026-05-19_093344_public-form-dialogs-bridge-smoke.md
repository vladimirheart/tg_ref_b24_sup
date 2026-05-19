# 2026-05-19 09:33:44 - Public form dialogs bridge smoke

## Промт пользователя

- `хорошо, продолжай`

## Что сделано

- Расширен `PublicFormFlowSmokeIntegrationTest` на более широкий
  `public-form -> dialogs` bridge/runtime слой.
- Добавлен integration-сценарий на live read bridge для `web_form`
  тикета: `/api/dialogs/{ticketId}` и `/api/dialogs/{ticketId}/history`
  теперь проверяются на shared conversation history после public-form
  submit и operator reply.
- Тем же пакетом закреплён read-marker contract через
  `DialogReadService.loadDetails(...)`: в smoke-сценарии теперь
  подтверждается `last_read_at` update для ответственного оператора.
- Добавлен integration-сценарий на `/api/dialogs/public-form-metrics`,
  который фиксирует живой runtime bridge между public-form traffic и
  dialogs-side metrics snapshot по `views/submits/sessionLookups` и
  `sessionLookupMisses`.
- Актуализированы `docs/ARCHITECTURE_AUDIT_2026-04-08.md` и
  `docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md` под следующий
  bridge/runtime hardening слой.

## Проверка

- `spring-panel\.\mvnw.cmd "-Dtest=PublicFormFlowSmokeIntegrationTest" test`
- `spring-panel\.\mvnw.cmd "-Dtest=PublicFormFlowSmokeIntegrationTest,PublicFormApiContractServiceTest,PublicFormApiResponseServiceTest,PublicFormApiControllerWebMvcTest,PublicFormControllerWebMvcTest" test`

## Что дальше

- Следующим широким пакетом можно уже идти в почти end-to-end public-form
  runtime rail вокруг dialog list visibility, category/state projections и
  adjacent operator workflows, если нужен ещё более плотный bridge между
  public-form и основным dialogs runtime.
