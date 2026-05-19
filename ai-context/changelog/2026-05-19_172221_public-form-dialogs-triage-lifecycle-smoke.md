# 2026-05-19 17:22:21 - Public form dialogs triage lifecycle smoke

## Промт пользователя

- `давай дальше более широким пакетом`

## Что сделано

- Расширен `PublicFormFlowSmokeIntegrationTest` на более широкий
  `public-form -> dialogs` lifecycle/triage слой.
- Добавлен live-сценарий для `/api/dialogs`, который фиксирует
  `summary`, `statusKey` переходы `new -> waiting_operator ->
  waiting_client`, critical SLA escalation signals и ownership lifecycle
  после `takeTicket` и `operator reply` для `web_form` тикета.
- Добавлен соседний projection-сценарий на
  `/api/dialogs/{ticketId}` и `/api/dialogs/{ticketId}/history/previous`,
  который закрепляет `resolved` status, sorted categories
  (`billing`, `vip`) и continuity ранее закрытого `web_form` тикета при
  следующем обращении того же requester.
- Для поддержания runnable targeted suite приведены к актуальной
  сигнатуре конструктора `SettingsTopLevelUpdateServiceTest` и
  `SettingsUpdateSharedConfigIntegrationTest`: оба теста теперь подают
  mock `NotificationRoutingService` вместо устаревшего двухаргументного
  конструктора.
- Актуализированы `docs/ARCHITECTURE_AUDIT_2026-04-08.md` и
  `docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md` под новый
  list/triage/details runtime bridge пакет.

## Проверка

- `spring-panel\.\mvnw.cmd "-Dtest=PublicFormFlowSmokeIntegrationTest" test`
- `spring-panel\.\mvnw.cmd "-Dtest=PublicFormFlowSmokeIntegrationTest,PublicFormApiContractServiceTest,PublicFormApiResponseServiceTest,PublicFormApiControllerWebMvcTest,PublicFormControllerWebMvcTest,SettingsTopLevelUpdateServiceTest,SettingsUpdateSharedConfigIntegrationTest" test`

## Что дальше

- Следующим широким пакетом можно добирать уже почти end-to-end operator
  workflow поверх этого rail: list ordering after reopen, unread/read
  continuity между `dialogs` list и details, а также adjacent quick-action
  projections для `resolve/categories/reopen` через transport endpoints.
