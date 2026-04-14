# VLESS в сетевых маршрутах переведен на host/port/token

- Время: `2026-04-10 10:49:00 +0300`
- Файлы:
  - `spring-panel/src/main/resources/templates/settings/index.html`
  - `spring-panel/src/main/java/com/example/panel/service/IntegrationNetworkService.java`
  - `ai-context/tasks/task-list.md`
  - `ai-context/tasks/task-details/01-006.md`
- Что сделано: в формах маршрутов (проект/боты/канал/профиль) добавлен отдельный `token` для `vless`, а поля логина/пароля скрываются для этой схемы.
- Что сделано: в JS-нормализацию прокси добавлено поле `token` и совместимость со старыми данными (`vless` может подтянуть token из legacy username/password).
- Что сделано: добавлены проверки сохранения — для `vless` token обязателен в маршрутах и профилях.
- Что сделано: backend-модель `ProxySettings` расширена полем `token`, а для `vless` сборка proxy URL использует `token@host:port`.
- Что сделано: задача `01-006` переведена в `🟣`.
