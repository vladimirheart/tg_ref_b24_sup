# Нет новых данных — нет инцидента; отдельные настройки проверок — R18

Время:25.10.2026,18:01:56 Europe/Moscow. Задача01-281, 🟣.

## Промт пользователя:

```text
INC-225

Данные загружены: 25.10.2026, 16:45:06. Это отдельная текущая выборка, а не состав сохранённой проверки выше. Старые записи с ошибкой не означают новые сбои сегодня.

#### Уведомления и обновление экрана

**Обновление данных на открытых страницах панели**

UiEventOutboxWatcher · ui-event-outbox-watch

После последней обработанной записи более новых нет. Давно не менялась только служебная запись о ходе работы; задержка данных этим не подтверждается.

Последнее продвижение по данным: 22.10.2026, 15:44:21. Порог возраста позиции: 10 мин.



такая инфа вообще не должна приходить в инциденты да ещё и в критику!



INC-224 - тоже:

**Данные конкретной задачи**

Данные загружены: 25.10.2026, 16:45:06. Это отдельная текущая выборка, а не состав сохранённой проверки выше. Старые записи с ошибкой не означают новые сбои сегодня.

#### Уведомления и обновление экрана

**Уведомления операторам об оценках клиентов**

Сохранённая позиция наблюдателя оценок · operator-notification-watch.feedbacks

Давно не менялась служебная запись о ходе работы. Данных недостаточно, чтобы сказать, остались ли необработанные записи.

Последнее продвижение по данным: 24.08.2026, 18:56:00. Порог возраста позиции: 20 мин.



INC-236

**Состояние очереди и фоновых задач**

Данные загружены: 25.10.2026, 17:26:57. Это отдельная текущая выборка, а не состав сохранённой проверки выше.

#### Уведомления и обновление экрана

**Уведомления операторам о новых сообщениях**

OperatorNotificationWatcher · operator-notification-watch.chat-history

После последней обработанной записи более новых нет. Давно не менялась только служебная запись о ходе работы; задержка данных этим не подтверждается.

Последнее продвижение по данным: 20.10.2026, 17:59:50. Порог возраста позиции: 15 мин.



это не является инцидентом. нет новых сообщений и нет - в целом нужна настройка таких мониторингов
```

## Фактически выполнено

- Idle/unknown/retired progress cursors больше не создают инцидент.
  Действующая задача требует новых данных перед неизменной позицией
  в непрерывной истории наблюдений дольше допуска, не просто старого updated_at.
  Fresh-after-idle и cursor progress не вызывают ложный critical.
- Retired feedback checkpoint сохранён без проверок/новых observations.
  Queue/sustained отделены от checkpoint history, duplicate aggregate выведен
  из эксплуатации. Подтверждённая worker delay автоматически high; ручной priority сохранён.
- В общий shared policy добавлены queue/UI/operator child directions:
  включение/час/неделя/до даты/бессрочно. Parent имеет приоритет, проверки реально
  останавливаются, notifications/escalations/outbox учитывают exact signal key.
  Рабочая доставка и watchers не останавливаются, attempts сохраняются.
- UI обновляет зависимые статусы без потери соседних drafts, прежние permissions/CSRF.
- 267Java tests/56suites,46JS+42exact-ID checks,160browser combinations/160PNG,
  audit74templates/47pages GREEN.21source paths guarded-applied,3metadata, итог24.
- Web+worker healthy на image08ef0f54…3be0; JARca6b7de3…fac6.
  16class entries6families+3UI resources; остальные entry hashes прежние.
  Rollback R18web/worker→R17 сохранён; новые policy keys несовместимы со старым R17.
- Штатный monitor снял INC-221/222/223/224/225/236 без ручного изменения статусов.
  Checkpoints/failed records, policy SHA и FlywayV81 сохранены;13прочих containers
  и4bot JVM прежние. Без migrations/backfill/flags/replay/requeue/manual business writes.
- Staging/commit/push не выполнялись; пользовательская приёмка остаётся.
  Отчёт и команды: docs/ui/incident-idle-checkpoint-policy-r18.md.

## Точные24 файлов

- ai-context/changelog/2026-10-25_180156_incident-idle-checkpoint-policy-r18.md
- ai-context/rules/05-monitoring-policy-and-employee-pickers.md
- ai-context/rules/06-incident-readable-descriptions.md
- ai-context/tasks/task-details/01-281.md
- docs/ui/incident-idle-checkpoint-policy-r18.md
- spring-panel/src/main/java/com/example/panel/service/IncidentMonitoringPolicyService.java
- spring-panel/src/main/java/com/example/panel/service/IncidentRouteDeliveryOutboxService.java
- spring-panel/src/main/java/com/example/panel/service/IncidentService.java
- spring-panel/src/main/java/com/example/panel/service/integration/IntegrationTransportHealthSnapshotScheduler.java
- spring-panel/src/main/java/com/example/panel/service/integration/IntegrationTransportIncidentMonitor.java
- spring-panel/src/main/java/com/example/panel/service/integration/IntegrationTransportOpsService.java
- spring-panel/src/main/resources/static/js/incident-descriptions.js
- spring-panel/src/main/resources/static/js/incident-workbench-ui.js
- spring-panel/src/main/resources/templates/incidents/index.html
- spring-panel/src/test/java/com/example/panel/controller/IncidentPolicyAndUserOptionsWebMvcTest.java
- spring-panel/src/test/java/com/example/panel/service/IncidentMonitoringPolicyServiceTest.java
- spring-panel/src/test/java/com/example/panel/service/IncidentRouteDeliveryOutboxServiceTest.java
- spring-panel/src/test/java/com/example/panel/service/MonitoringDirectionGuardsTest.java
- spring-panel/src/test/java/com/example/panel/service/TransportIncidentNoisePostgresqlTest.java
- spring-panel/src/test/java/com/example/panel/service/integration/IntegrationTransportIncidentMonitorTest.java
- spring-panel/src/test/java/com/example/panel/service/integration/IntegrationTransportOpsServiceTest.java
- spring-panel/src/test/js/incident-descriptions-r13.test.js
- spring-panel/src/test/js/incident-runtime-monitors-r18.test.js
- tools/incident-runtime-monitors-qa.cjs
