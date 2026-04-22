# 2026-04-22 14:45:02 - UTF-8 runtime logging fix

## Что изменено

- Добавлена отдельная задача `01-047` в `ai-context/tasks/task-list.md`.
- Создана детализация `ai-context/tasks/task-details/01-047.md`.
- В `spring-panel/run-windows.bat` принудительно включён `chcp 65001` и
  добавлены UTF-8 JVM-аргументы для запуска панели.
- В `spring-panel/src/main/java/com/example/panel/service/RmsLicenseMonitoringService.java`
  чтение вывода системных команд переведено с системной кодировки на
  `StandardCharsets.UTF_8`.

## Затронутые файлы

- `c:/Users/SinicinVV/git_h/tg_ref_b24_sup/ai-context/tasks/task-list.md`
- `c:/Users/SinicinVV/git_h/tg_ref_b24_sup/ai-context/tasks/task-details/01-047.md`
- `c:/Users/SinicinVV/git_h/tg_ref_b24_sup/ai-context/changelog/2026-04-22_144502_utf8-runtime-logging-fix.md`
- `c:/Users/SinicinVV/git_h/tg_ref_b24_sup/spring-panel/run-windows.bat`
- `c:/Users/SinicinVV/git_h/tg_ref_b24_sup/spring-panel/src/main/java/com/example/panel/service/RmsLicenseMonitoringService.java`

## Проверка

- `c:/Users/SinicinVV/git_h/tg_ref_b24_sup/spring-panel/mvnw.cmd -q -DskipTests compile`
