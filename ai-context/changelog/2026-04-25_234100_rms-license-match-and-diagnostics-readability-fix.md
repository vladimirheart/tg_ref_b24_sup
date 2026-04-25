# 2026-04-25 23:41:00 - RMS license match and diagnostics readability fix

## Что изменено

- Расширен парсинг лицензий RMS: теперь учитываются не только дочерние XML-узлы,
  но и атрибуты элементов, а также дополнительные поля для `id`, имени и даты
  окончания лицензии.
- Улучшен поиск целевой лицензии `RMS (Front Fast Food)` с `id=100` через более
  гибкий матч по данным ответа RMS.
- Для RMS-диагностики добавлены нормализация кодировки, форматирование XML и
  более читаемое отображение длинных блоков в UI.

## Затронутые файлы

- `spring-panel/src/main/java/com/example/panel/service/RmsLicenseMonitoringService.java`
- `spring-panel/src/main/resources/templates/analytics/rms-control.html`
- `spring-panel/src/main/resources/static/js/rms-monitoring.js`
- `ai-context/tasks/task-list.md`
- `ai-context/tasks/task-details/01-053.md`

## Кратко

Исправление направлено на два симптома сразу: ложную ошибку
`Не найдена лицензия RMS (Front Fast Food) с id=100` при нестандартной
структуре XML-ответа RMS и плохую читаемость диагностических данных в UI.
Теперь RMS-ответ обрабатывается гибче, а текст диагностики нормализуется и
отображается многострочно.
