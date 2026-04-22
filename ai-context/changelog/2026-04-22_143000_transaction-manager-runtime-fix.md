# 2026-04-22 14:30:00 — Runtime fix для transactionManager после выноса monitoring.db

## Что сделано

- Восстановлен стандартный primary bean `transactionManager` через `JpaTransactionManager`.
- Выравнен `SslCertificateMonitoringService.findAll()` на явный `monitoringTransactionManager`, чтобы SSL-мониторинг не зависел от default transaction manager.
- Повторно проверен запуск панели после правок: ошибка `A component required a bean named 'transactionManager'` больше не воспроизводится.

## Затронутые файлы

- `spring-panel/src/main/java/com/example/panel/config/SqliteDataSourceConfiguration.java`
- `spring-panel/src/main/java/com/example/panel/service/SslCertificateMonitoringService.java`

## Проверка

- `spring-panel\\mvnw.cmd -q -DskipTests compile`
- запуск `spring-panel\\run-windows.bat`
- проверка лога `logs\\spring-panel.log`: приложение доходит до рабочих scheduler-потоков RMS без ошибки по `transactionManager`
