# 2026-06-16 15:55:00 - adjacent spam read-side consumers

## Что сделано
- добран следующий adjacent runtime слой вокруг `mark_spam` без ухода в
  `public form`: в `DialogListIntegrationTest`,
  `DialogDetailsIntegrationTest` и `DialogWorkspaceIntegrationTest`
  добавлены live сценарии, где `POST /api/dialogs/{ticketId}/take` и
  `POST /spam` проходят перед последующим reread соответствующего
  consumer route;
- `DialogListIntegrationTest` теперь подтверждает, что после
  `take -> spam` dialog остаётся в `waiting_operator` /
  `my_dialogs.unanswered`, сохраняет `rawResponsible` и получает merged
  categories c `Спам`;
- `DialogDetailsIntegrationTest` теперь проверяет, что details summary
  после `take -> spam` возвращает того же responsible, объединённые
  категории и ожидаемое reread-поведение по `unreadCount`;
- `DialogWorkspaceIntegrationTest` теперь закрепляет тот же moderation
  loop для workspace route: audit trail `take` / `mark_spam`,
  workflow ownership, action guards и сохранённые ticket categories
  проходят через live HTTP round-trips.

## Почему это важно
- раньше read-side continuity вокруг `mark_spam` в основном жила в
  `DialogQuickActionsIntegrationTest`, а соседние `list/details/workspace`
  consumer'ы не подтверждали ту же проекцию на собственных reread-маршрутах;
- новый пакет делает явной текущую runtime семантику, что spam-маркировка
  не выбрасывает dialog из list-side unread bucket сама по себе, но
  обязана синхронно обновлять categories, responsible и audit/event
  surfaces на соседних consumer'ах.

## Проверка
- `./mvnw.cmd -Dtest=DialogListIntegrationTest test`
- `./mvnw.cmd -Dtest=DialogDetailsIntegrationTest test`
- `./mvnw.cmd -Dtest=DialogWorkspaceIntegrationTest test`
