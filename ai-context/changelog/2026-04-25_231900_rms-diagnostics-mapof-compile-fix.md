# 2026-04-25 23:19:00 - compile fix for RMS diagnostics response

## Что изменено

- Исправлена сборка `RmsLicenseMonitoringApiController`: ответ diagnostics endpoint больше не
  собирается через `Map.of(...)` с числом пар больше допустимого для Java 17.
- Для payload диагностики использован `LinkedHashMap`, чтобы сохранить читаемый порядок полей
  и убрать compile-time ошибку.

## Затронутые файлы

- `spring-panel/src/main/java/com/example/panel/controller/RmsLicenseMonitoringApiController.java`

## Кратко

`run-windows.bat` падал ещё на этапе компиляции, потому что метод `Map.of` в Java 17 поддерживает
ограниченное число аргументов. После замены на `LinkedHashMap` проект снова успешно собирается и
может быть запущен дальше.
