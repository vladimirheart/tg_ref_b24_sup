# 2026-05-05 16:27:00 - run-windows clean fallback

## Пользовательский промпт

```text
tg_ref_b24_sup\spring-panel> .\run-windows.bat
[INFO] Java runtime: 25.0.2 (major 25)
[WARN] This project is primarily tested on JDK 17. If build errors occur, set JAVA_HOME_17 to a JDK 17 path.
Starting Spring panel with C:\Users\SinicinVV\git_h\tg_ref_b24_sup\spring-panel\mvnw.cmd
[INFO] Running Maven clean phase before startup to remove stale compiled classes.
...
[ERROR] Failed to execute goal org.apache.maven.plugins:maven-clean-plugin:3.3.2:clean (default-clean) on project panel: Failed to clean project: Failed to delete C:\Users\SinicinVV\git_h\tg_ref_b24_sup\spring-panel\target\classes\com\example\panel\service
```

## Что сделано

- `spring-panel/run-windows.bat` разделён на две отдельные Maven-фазы: сначала best-effort `clean`, затем `spring-boot:run`.
- При ошибке `clean` скрипт теперь печатает предупреждение о возможной блокировке файлов в `target` и продолжает запуск панели.
- Добавлена переменная `SPRING_PANEL_SKIP_CLEAN`, чтобы можно было принудительно запускать панель без `clean`.
- В `ai-context/tasks` добавлена и зафиксирована задача `01-069` для этой правки.

## Проверка

- `cmd /c run-windows.bat`
- `powershell -Command "$env:SPRING_PANEL_SKIP_CLEAN='true'; cmd /c run-windows.bat"`

## Итог

- Локально `run-windows.bat` проходит дальше фазы `clean` и доходит до `spring-boot:run`.
- Текущий фактический стоп запуска уже не связан с `maven-clean-plugin`: приложение падает позже на Spring bean error `SlaRoutingPolicyService` (`No default constructor found`).
