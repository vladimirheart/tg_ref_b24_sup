# 2026-04-26 21:03:00 - RMS license parser compile fix

## Что изменено

- Исправлен compile-time баг в новом парсере `licenseData` для RMS.
- В разборе модульных ограничений вызов `extractFirstText(...)` для `Node`
  заменён на `extractFirstDescendantText(...)`, который и предназначен для
  чтения вложенных значений из DOM-узла.

## Затронутые файлы

- `spring-panel/src/main/java/com/example/panel/service/RmsLicenseMonitoringService.java`

## Кратко

`run-windows.bat` падал на компиляции из-за несоответствия типов `Node` и
`Document` в хелпере чтения XML. После исправления проект снова успешно
собирается.
