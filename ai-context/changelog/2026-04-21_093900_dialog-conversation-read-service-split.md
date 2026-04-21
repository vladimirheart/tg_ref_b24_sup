# 2026-04-21 09:39 MSK — dialog conversation read service split

## Что сделано

- добавлен `DialogConversationReadService`, который забрал из giant
  `DialogService` conversation read-сценарии:
  `loadHistory`, `loadPreviousDialogHistory`, `loadTicketCategories`;
- `DialogService` оставлен backward-compatible: legacy методы не исчезли, а
  делегируют в новый conversation read-layer; `loadDialogDetails` тоже теперь
  собирается через него;
- `DialogReadService` переведён на новый слой для `history` и `previous history`;
- `DialogWorkspaceService` переведён на новый слой для основного history payload;
- `PublicFormApiController` переведён на новый слой для чтения переписки по
  session token;
- из `DialogService` удалены локальные helper-блоки conversation read-слоя,
  которые больше не нужны после выноса;
- добавлен targeted sqlite-based test `DialogConversationReadServiceTest`;
- обновлены roadmap, architecture audit и task-detail `01-024`.

## Проверка

- `spring-panel\\mvnw.cmd -q "-Dtest=DialogConversationReadServiceTest,DialogClientContextReadServiceTest,PublicFormApiControllerWebMvcTest,DialogWorkspaceControllerWebMvcTest,DialogMacroControllerWebMvcTest,DialogSlaRuntimeServiceTest,DialogListReadServiceTest" test`
- `spring-panel\\mvnw.cmd -q -DskipTests compile`

## Эффект

- giant `DialogService` получил второй самостоятельный read-layer поверх
  уже вынесенного client-context слоя;
- `history/previous-history/public-form` сценарии перестали быть скрыто
  завязаны на монолитный service;
- следующий service-level пакет теперь логично брать либо в lookup/list-assembly,
  либо в write-side bounded contexts.
