# 2026-04-29 11:12:00 — RMS chain license and Excel export

## Что изменено

- Для chain-логики RMS целевая лицензия переключена на `Chain (Store Connector (1 RMS))` с `id=1300`.
- В общем списке RMS добавлена защита от устаревшего сообщения про `RMS (Front Fast Food) id=100` у chain-записей.
- В колонке даты окончания лицензии kiosk-лицензия теперь показывается только если её количество больше нуля.
- На странице RMS добавлена модалка выгрузки текущего видимого списка в Excel с выбором столбцов.
- В действиях строки RMS добавлено копирование адреса.

## Затронутые файлы

- `spring-panel/src/main/java/com/example/panel/service/RmsLicenseMonitoringService.java`
- `spring-panel/src/main/java/com/example/panel/controller/RmsLicenseMonitoringApiController.java`
- `spring-panel/src/main/resources/templates/analytics/rms-control.html`
- `spring-panel/src/main/resources/static/js/rms-monitoring.js`
- `ai-context/tasks/task-list.md`
- `ai-context/tasks/task-details/01-064.md`

## Зачем

Пользователь продолжал видеть неверную целевую лицензию для chain и старые fastfood-сообщения, а также просил операционные функции для работы со списком RMS без ручной обработки данных.
