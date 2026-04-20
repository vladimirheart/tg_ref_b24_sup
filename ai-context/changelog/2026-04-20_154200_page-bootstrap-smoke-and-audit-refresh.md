# 2026-04-20 15:42:00 — page bootstrap smoke и актуализация аудита

## Что сделано

- Добавлены page/runtime smoke tests для `dashboard`, `analytics`, `clients`,
  `knowledge` и `settings`.
- Явно проставлены `data-ui-page` на шаблонах `analytics`, `clients` и
  `knowledge`, чтобы page presets и smoke-проверки опирались на один контракт.
- `AnalyticsControllerWebMvcTest` адаптирован под текущий security/template
  contract (`principal` + `csrf`) без ломки существующего POST-набора.
- Обновлён `ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md` под новый smoke-пакет.
- Актуализирован `ARCHITECTURE_AUDIT_2026-04-08.md`: документ больше не
  описывает проект только в состоянии "до рефакторинга", а разделяет уже
  сниженные риски и оставшиеся архитектурные hotspots.

## Зачем

Следующий рефакторинг по roadmap уже идёт не в пустоту: есть проверка раннего
UI bootstrap не только на `dialogs`, а и на нескольких ключевых страницах.
Параллельно сам audit снова стал полезным рабочим документом, а не только
историческим снимком до начала 01-024.

## Проверка

- `spring-panel\mvnw.cmd -q "-Dtest=DashboardControllerWebMvcTest,ClientsControllerWebMvcTest,KnowledgeBaseControllerWebMvcTest,ManagementControllerWebMvcTest,AnalyticsControllerWebMvcTest" test`
- `spring-panel\mvnw.cmd -q -DskipTests compile`
