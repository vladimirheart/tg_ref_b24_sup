# R15 — учётные данные и ручной приоритет инцидента

Время: 24.10.2026, 14:49:25 Europe/Moscow.
Задача:01-281, статус🟣.

## Промт пользователя:

```text
"**Не найдено значение ключа доступа**
Результат сохранённой проверки.
Настройка интеграции есть, но ключ пустой, не сохранён или недоступен приложению.
**На что влияет**
Интеграция может потерять доступ после истечения или отзыва ключа. Плановая дата замены сама по себе не доказывает отказ интеграции.
**Что проверить**
Откройте соответствующую настройку и проверьте сохранение ключа, не публикуя его значение.
Настройка
Channel network route · тостов proxy password"

какой ключ? какой интеграции? непонятно.

это точно не критика. да и изменить приоритет невозможно
```

## Фактически выполнено

- Показаны тип учётных данных, конкретный канал/профиль и место настройки.
  Пароль прокси не называется ключом интеграции; сохранённые данные не
  подменяются текущим диагнозом, секреты не раскрываются.
- Исправлен discovery невыбранного локального proxy и необязательной
  авторизации: нет новых phantom rows, старые not_required/ok с историей.
  Рабочий scheduler подтвердил исправление для канала «тостов».
- Dropdown приоритета в верхнем блоке, обе формы сохраняют выбор. Ручной
  приоритет хранится транзакционно в существующих events, автоматический
  refresh не затирает его; защищённое событие и pessimistic locks без миграции.
- 230 Java tests/54suites,32 JS cases+42 legacy checks,100PNG,
  source audit74templates/47pages; sandbox-first guarded apply.
- Оба web/worker обновлены на75b33456…9144f, healthy. Раздельные rollback
  pointers сохранены. Env/mounts, runtime JAR/HTTP hashes, FlywayV81,
  неизменность13остальныхcontainers/4bot JVM и monitoring policy сверены.
  Без migrations/backfill/flags и ручных business writes. Штатные jobs идут.
- Точные19файлов перечислены ниже и в отчёте с командами владельцу.
  Staging/commit/push не выполнялись; рабочая authenticated-приёмка остаётся.

## Файлы

- ai-context/changelog/2026-10-24_144925_incident-credentials-priority-r15.md
- ai-context/rules/06-incident-readable-descriptions.md
- ai-context/tasks/task-details/01-281.md
- docs/ui/incident-credentials-priority-r15.md
- spring-panel/src/main/java/com/example/panel/repository/IncidentRepository.java
- spring-panel/src/main/java/com/example/panel/service/CredentialRotationRegistryService.java
- spring-panel/src/main/java/com/example/panel/service/IncidentService.java
- spring-panel/src/main/resources/static/js/credential-rotation-monitoring.js
- spring-panel/src/main/resources/static/js/incident-descriptions.js
- spring-panel/src/main/resources/static/js/incidents-workbench.js
- spring-panel/src/main/resources/templates/analytics/credential-rotation.html
- spring-panel/src/main/resources/templates/incidents/index.html
- spring-panel/src/test/java/com/example/panel/service/CredentialRouteApplicabilityTest.java
- spring-panel/src/test/java/com/example/panel/service/IncidentClosurePostgresqlTest.java
- spring-panel/src/test/java/com/example/panel/service/IncidentPriorityPostgresqlTest.java
- spring-panel/src/test/java/com/example/panel/ui/IncidentWorkbenchCredentialRotationUiContractTest.java
- spring-panel/src/test/js/incident-credentials-r15.test.js
- spring-panel/src/test/js/incident-descriptions-r13.test.js
- tools/incident-credentials-priority-qa.cjs

Подробный checkpoint, границы и воспроизведение: docs/ui/incident-credentials-priority-r15.md.
