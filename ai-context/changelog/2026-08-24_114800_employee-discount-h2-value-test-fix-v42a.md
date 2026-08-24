# 2026-08-24 11:48 — Employee discount v42 H2 VALUE compatibility fix

## Пользовательский промт

Пользователь прислал вывод targeted tests v42: `EmployeeDiscountAutomationCredentialServiceTest` упал при создании H2 PostgreSQL-mode таблицы, потому что H2 трактует колонку `value` как keyword.

## Изменения

- `spring-panel/src/test/java/com/example/panel/service/EmployeeDiscountAutomationCredentialServiceTest.java`
  - в test-only H2 JDBC URL добавлен `NON_KEYWORDS=VALUE`;
  - production SQL и runtime-код автоматизации не менялись;
  - тест теперь может создать существующую `settings_parameters` schema shape и проверить именно PostgreSQL boolean contract (`is_deleted = FALSE`).

## Причина

`value` допустимо используется существующей PostgreSQL schema проекта, но H2 2.x резервирует это слово в своём parser. Ошибка возникала до выполнения тестируемого credential runtime и была ограничением test harness, а не подтверждением дефекта production PostgreSQL DDL.
