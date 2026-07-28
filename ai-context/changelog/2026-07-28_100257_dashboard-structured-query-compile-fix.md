# 2026-07-28 10:02:57 - dashboard structured query compile fix

- Затронутые области:
  - `spring-panel/src/main/java/com/example/panel/service/DashboardAnalyticsService.java`
- Пользовательский промпт:
  - `spring-panel> .\\run-windows.bat ... [ERROR] /.../DashboardAnalyticsService.java:[403,21] reference to query is ambiguous`
- Что сделано:
  - устранена compile-ошибка в загрузке structured-атрибутов для dashboard analytics;
  - неоднозначный вызов `JdbcTemplate.query(...)` заменён на явный `ResultSetExtractor`, чтобы код стабильно компилировался на текущем Spring API и JDK toolchain проекта.
- Проверки:
  - `spring-panel\\mvnw.cmd -q -DskipTests compile` — success
