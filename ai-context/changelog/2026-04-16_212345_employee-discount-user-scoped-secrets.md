# 2026-04-16 21:23:45 - employee-discount-user-scoped-secrets

## Что сделано

- контур автоматизации `Bitrix24 + iiko automation` переведен с local-machine конфига на личные user-scoped доступы пользователя панели;
- добавлен `EmployeeDiscountAutomationCredentialService` с хранением персональных Bitrix24/iiko credentials в `settings_parameters` под ключом `employee_discount_automation_credentials.v1`;
- удален `LocalMachineIntegrationsConfigService`, чтобы старая схема с файловыми секретами больше не использовалась этим runtime-контуром;
- обновлены `Bitrix24RestService` и `IikoDirectoryService`:
  - чтение credentials идет из личного конфига пользователя панели;
  - статус подключения возвращает только безопасные данные;
  - исправлен выбор `Authorization` для iiko: при наличии `token` используется только `Bearer`, без дублирования `Basic`;
- обновлены `EmployeeDiscountAutomationService` и `EmployeeDiscountAutomationController`:
  - добавлен endpoint сохранения личных доступов;
  - все сетевые операции идут в контексте текущего пользователя;
  - per-task ошибки интеграций перехватываются без падения всего запуска;
- обновлен UI блока `Bitrix24 + iiko automation`:
  - добавлены поля личных доступов Bitrix24/iiko;
  - секреты не возвращаются обратно в браузер;
  - пустые `webhook/password/token` сохраняют уже существующее секретное значение;
- обновлены документация и task-detail `01-033` под новую модель хранения секретов.

## Проверка

- `./mvnw.cmd -q -DskipTests compile` в `spring-panel` проходит успешно.

## Ограничения

- задача `01-033` остается в статусе `🟡`, потому что боевой iiko-мутатор по-прежнему требует точных `iikocard` endpoint-ов и payload-формата;
- персональные секреты теперь отделены по пользователям панели, но отдельный слой шифрования в БД для них пока не добавлялся.
