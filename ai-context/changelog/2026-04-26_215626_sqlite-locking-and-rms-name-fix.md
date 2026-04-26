# 2026-04-26 21:56:26

## Заголовок

Снизить блокировки SQLite в `spring-panel` и усилить восстановление имени RMS после mojibake.

## Затронутые файлы

- `C:\Users\SinicinVV\git_h\tg_ref_b24_sup\ai-context\tasks\task-list.md`
- `C:\Users\SinicinVV\git_h\tg_ref_b24_sup\ai-context\tasks\task-details\01-055.md`
- `C:\Users\SinicinVV\git_h\tg_ref_b24_sup\spring-panel\src\main\java\com\example\panel\config\SqliteConnectionConfigSupport.java`
- `C:\Users\SinicinVV\git_h\tg_ref_b24_sup\spring-panel\src\main\java\com\example\panel\config\SqliteDataSourceConfiguration.java`
- `C:\Users\SinicinVV\git_h\tg_ref_b24_sup\spring-panel\src\main\java\com\example\panel\config\MonitoringSqliteDataSourceConfiguration.java`
- `C:\Users\SinicinVV\git_h\tg_ref_b24_sup\spring-panel\src\main\java\com\example\panel\config\SessionConfig.java`
- `C:\Users\SinicinVV\git_h\tg_ref_b24_sup\spring-panel\src\main\java\com\example\panel\repository\RmsLicenseMonitorRepository.java`
- `C:\Users\SinicinVV\git_h\tg_ref_b24_sup\spring-panel\src\main\java\com\example\panel\repository\SslCertificateMonitorRepository.java`
- `C:\Users\SinicinVV\git_h\tg_ref_b24_sup\spring-panel\src\main\java\com\example\panel\service\SslCertificateMonitoringService.java`
- `C:\Users\SinicinVV\git_h\tg_ref_b24_sup\spring-panel\src\main\java\com\example\panel\service\RmsLicenseMonitoringService.java`

## Что сделано

- Добавлен общий helper для SQLite-конфигурации с `WAL`, `busyTimeout`, `NORMAL` synchronous и `IMMEDIATE` transaction mode для primary и monitoring datasource.
- Из `SslCertificateMonitoringService` убрана длинная monitoring-транзакция, чтобы SQLite не держал snapshot во время сетевого SSL-check.
- В monitoring-репозитории SSL и RMS добавлен мягкий retry на краткие `SQLITE_BUSY` и `SQLITE_BUSY_SNAPSHOT`.
- В `RmsLicenseMonitoringService` усилено восстановление mojibake-строк, включая кейс с символами наподобие `╨...`, и добавлен fallback для пустого `ex.getMessage()` в RMS-логах.
- Добавлена отдельная задача `01-055` в task-flow для фикса блокировок SQLite и имени RMS.

## Проверка

- Выполнено: `spring-panel\mvnw.cmd -q -DskipTests compile`
- Результат: успешно
