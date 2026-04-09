# Усилен контур интеграций VK и MAX в настройках каналов

- Время: `2026-04-09 22:54:20 +0300`
- Файлы:
  - `spring-panel/src/main/resources/templates/settings/index.html`
  - `spring-panel/src/main/java/com/example/panel/controller/ChannelApiController.java`
  - `ai-context/tasks/task-list.md`
- Что сделано: в UI подключения канала добавлены явные требования для VK/MAX (обязательность токена, пошаговые инструкции, где получить значения, и webhook endpoint для MAX).
- Что сделано: исправлено поведение формы добавления канала — для VK токен больше не скрывается и валидируется как обязательный, вместе с `group_id` и `confirmation_token`.
- Что сделано: в карточку редактирования канала добавлен блок управления `MAX webhook secret` с отображением рабочего URL `.../webhooks/max`.
- Что сделано: в `ChannelApiController` добавлена backend-валидация platform-specific конфигурации (VK/MAX/Telegram), чтобы некорректные payload не сохранялись при прямых API-вызовах.
- Что сделано: в `task-list.md` зафиксирована выполненная задача `01-003` со статусом `🟣`.
