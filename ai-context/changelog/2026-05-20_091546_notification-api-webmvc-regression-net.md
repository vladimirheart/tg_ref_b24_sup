# 2026-05-20 09:15:46 - Notification API WebMvc regression net

## Промт пользователя

- `хорошо, давай дальше`

## Что сделано

- Добавлен первый dedicated
  `spring-panel/src/test/java/com/example/panel/controller/NotificationApiControllerWebMvcTest.java`
  для `NotificationApiController`.
- В тестах закреплены identity-resolution ветки для transport boundary:
  `UserDetails -> username`, fallback на `authentication.getName()` и
  explicit `all`-поведение при отсутствии `Authentication`.
- `list`, `unread_count` и `markAsRead` теперь прикрыты отдельными WebMvc
  regression сценариями без зависимости от включённых security filters:
  principal передаётся напрямую как `Authentication`, что делает тест
  стабильнее после будущих security/runtime изменений.
- Актуализированы `docs/ARCHITECTURE_AUDIT_2026-04-08.md` и
  `docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md` под новый notification
  transport hardening шаг.

## Проверка

- `spring-panel\.\mvnw.cmd "-Dtest=NotificationApiControllerWebMvcTest" test`
- `spring-panel\.\mvnw.cmd "-Dtest=NotificationApiControllerWebMvcTest,PublicFormFlowSmokeIntegrationTest,PublicFormApiContractServiceTest,PublicFormApiResponseServiceTest,PublicFormApiControllerWebMvcTest,PublicFormControllerWebMvcTest" test`

## Что дальше

- Следующим пакетом можно либо добирать adjacent notification runtime
  coverage через живые integration сценарии `summary/read/reset`, либо
  переходить к следующему transport boundary с похожим identity/error
  contract хвостом.
