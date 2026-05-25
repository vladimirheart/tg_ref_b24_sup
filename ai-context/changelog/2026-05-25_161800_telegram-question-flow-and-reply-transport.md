## 2026-05-25 16:18:00 — telegram question flow and reply transport

### User prompt

- «по телеграм ещё вопросы:
  * после выбора ресторана, приходит сообщение:
  "укывеапрр

  Чтобы вернуться к предыдущему вопросу, напишите "Назад"." и илшь после любого сообзения клиента приходит "Опишите проблему

  Чтобы вернуться к предыдущему вопросу, напишите "Назад"."
  * при попытке отправить сообщение оператором, кнопка "Отправить" становится неактивной примерно на минуту и мообщение не отправляется»

### Что сделано

- из `config/shared/settings.json` удалён тестовый custom-вопрос `укывеапрр` из шаблона и активного `question_flow`, поэтому после `location_name` сценарий снова переходит к штатному шагу `Опишите проблему`;
- `DialogReplyTransportService` переведён на `IntegrationNetworkService` и channel-aware Telegram Bot API URL;
- для Telegram reply/edit/delete/media добавлен явный request timeout `15s`, чтобы панель не висела на долгом direct timeout до `api.telegram.org`;
- transport теперь использует тот же `base_url` / legacy mirror fallback, что и остальная Telegram-интеграция панели.

### Проверка

- `spring-panel`: `./mvnw.cmd -q -DskipTests compile`
