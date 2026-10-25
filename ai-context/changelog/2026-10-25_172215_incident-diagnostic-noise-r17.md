# Ошибки отдельных записей исключены из инцидентов — R17

Время:25.10.2026,17:22:15 Europe/Moscow. Задача01-281, 🟣.

## Промт пользователя:

```text
**Сообщение клиента в существующее обращение — 4**

При обработке сообщения не удалось записать в базу сведения о вложении: ошибка SQL при записи в chat_attachment_metadata. Из этой ошибки не следует, что сам файл потерян.

- Обращение: f5b6bedf-dfac-4683-940c-5617b57314fe. Запись: 03298e2e-8d69-4586-84d6-447a0897b3f7. Последнее изменение записи: 24.08.2026, 15:51:22.
- Обращение: f5b6bedf-dfac-4683-940c-5617b57314fe. Запись: fed9a9db-c5a9-4dc4-8117-e78157b4ec04. Последнее изменение записи: 24.08.2026, 15:51:16.
- Обращение: f5b6bedf-dfac-4683-940c-5617b57314fe. Запись: 027d030b-9675-4daa-b287-31b75ae04d6c. Последнее изменение записи: 24.08.2026, 15:50:36.
- Обращение: f5b6bedf-dfac-4683-940c-5617b57314fe. Запись: acb203fa-571d-4a4e-922e-c4d0ac6bd1e8. Последнее изменение записи: 24.08.2026, 15:50:22.



такого я точно не хочу видеть в инцидентах. в логах - да, но не как отдельный инцидент для отвлеяения, и уже тем более в в критическом приоритете - это даже на "важный" не тянет
```

## Фактически выполнено

- Failed inbound/outbound records исключены из incident projection текущего
  snapshot и истории sustained pressure; старые severity не возвращают critical.
  Это не замена приоритета: сами ошибки больше не основание инцидента.
- Список UUID/SQL/дат отдельных сообщений убран из карточки. Существующие
  read-only diagnostics, ошибки, история и recovery controls сохранены.
  Error-only legacy signals снимает обычный монитор с честной причиной.
- Systemic queue/stale/checkpoint критерии и ручной priority не менялись.
  INC-221/INC-236 остаются из-за фоновых задач; новая штатная проверка уже
  исключила failed4 из сигнала. SQL-ошибка не объявляется исправленной.
- Sandbox-first:255Java tests/56suites,42JS cases+42exact-ID checks,
  140browser combinations/140PNG, audit74templates/47pages GREEN.
  17validated paths guarded-applied;4metadata paths, итог21.
- Web+worker healthy на image15bfdcd6…40d0, JAR0f6e8f81…666d.
  Ровно9class entries двух families и3UIresources; остальные entries прежние.
  Раздельные rollback R17web→R16 и R17worker→R15 сохранены.
- Full env/mounts/HTTP/JAR checks GREEN;13excludedcontainers/4bot JVM прежние.
  Четыре исходные failed записи и policy hash неизменны, FlywayV81, skip migrations.
  Без migrations/backfill/flags/manual incident writes/replay/requeue.
- Staging/commit/push не выполнялись; пользовательская приёмка остаётся.
  Отчёт и команды: docs/ui/incident-diagnostic-noise-r17.md.

## Точные21 файлов

- ai-context/changelog/2026-10-25_172215_incident-diagnostic-noise-r17.md
- ai-context/rules/05-monitoring-policy-and-employee-pickers.md
- ai-context/rules/06-incident-readable-descriptions.md
- ai-context/tasks/task-details/01-281.md
- docs/ui/incident-diagnostic-noise-r17.md
- spring-panel/src/main/java/com/example/panel/service/integration/IntegrationTransportIncidentMonitor.java
- spring-panel/src/main/java/com/example/panel/service/integration/IntegrationTransportOpsService.java
- spring-panel/src/main/resources/static/js/incident-descriptions.js
- spring-panel/src/main/resources/static/js/incidents-workbench.js
- spring-panel/src/main/resources/templates/incidents/index.html
- spring-panel/src/test/java/com/example/panel/service/TransportIncidentNoisePostgresqlTest.java
- spring-panel/src/test/java/com/example/panel/service/integration/IntegrationTransportIncidentMonitorTest.java
- spring-panel/src/test/java/com/example/panel/service/integration/IntegrationTransportOpsServiceTest.java
- spring-panel/src/test/js/fixtures/incident-descriptions-r13.cjs
- spring-panel/src/test/js/incident-concrete-problems-r14.test.js
- spring-panel/src/test/js/incident-descriptions-r13.test.js
- spring-panel/src/test/js/incident-noise-policy-r17.test.js
- spring-panel/src/test/js/incident-worker-details-r16.test.js
- tools/incident-concrete-problems-qa.cjs
- tools/incident-noise-policy-qa.cjs
- tools/incident-worker-details-qa.cjs
