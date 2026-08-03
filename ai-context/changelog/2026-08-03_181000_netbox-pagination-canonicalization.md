# 2026-08-03 18:10:00 - netbox pagination canonicalization

- Затронутые области:
  - `spring-panel/src/main/java/com/example/panel/service/NetBoxApiService.java`
  - `spring-panel/src/main/resources/static/js/settings-netbox-sync-runtime.js`
  - `ai-context/tasks/task-list.md`
  - `ai-context/tasks/task-details/01-176.md`
- Промпты пользователя:
  - `не сохраняет поля ни url ни token. плюс не даёт считать инфо, говоря о 403-й ошибке, хотя ты тестил и видел всю инфу`
- Что сделано:
  - зафиксирован реальный runtime-сбой из `logs/spring-panel.log`: `403 Authentication credentials were not provided` на absolute `next` URL NetBox;
  - добавлена канонизация NetBox URL к настроенному `base_url`, чтобы same-host `next` и media-ссылки не уводили sync в несовместимое представление хоста/схемы;
  - runtime блока NetBox после save/run теперь перечитывает backend-состояние секции `locations`, чтобы UI показывал реально сохранённую конфигурацию, а не только локальное JS-состояние.
