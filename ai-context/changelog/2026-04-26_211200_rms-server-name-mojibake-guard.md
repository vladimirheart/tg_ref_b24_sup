# 2026-04-26 21:12:00 - RMS server name mojibake guard

## Что изменено

- Проанализирован свежий `spring-panel.log` и зафиксирована отдельная runtime-
  проблема с `SQLITE_BUSY` по `SPRING_SESSION`.
- Для имени RMS добавлена защита от битой перекодировки при чтении
  `get_server_info.jsp`.
- Если новый `serverName` выглядит как mojibake, система теперь предпочитает
  сохранить текущее корректное имя вместо затирания его битой строкой.

## Затронутые файлы

- `logs/spring-panel.log`
- `spring-panel/src/main/java/com/example/panel/service/RmsLicenseMonitoringService.java`
- `ai-context/tasks/task-list.md`
- `ai-context/tasks/task-details/01-054.md`

## Кратко

Колонка `Имя` портилась не из-за UI, а из-за runtime-обновления metadata из RMS.
После исправления панель осторожнее относится к сомнительному `serverName` и не
перезаписывает нормальное имя явным mojibake.
