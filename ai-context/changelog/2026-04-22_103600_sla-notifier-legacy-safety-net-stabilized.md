# 2026-04-22 10:36:00

## Что сделано

- синхронизированы legacy assertions в `SlaEscalationWebhookNotifierTest`
  с текущим notifier contract:
  route naming для pool-routing и strict review-path expectations;
- подтверждено, что notifier production wiring после detachment от
  `DialogService` не ломает existing legacy test suite;
- обновлены `01-024`, roadmap и architecture audit под восстановленный
  notifier safety net.

## Проверка

- `spring-panel\mvnw.cmd -q "-Dtest=SlaEscalationWebhookNotifierTest" test`
- `spring-panel\mvnw.cmd -q -DskipTests compile`

## Примечания

- оба прогона завершились успешно;
- `logs/spring-panel.log` обновился от локальных прогонов и не редактировался вручную.
