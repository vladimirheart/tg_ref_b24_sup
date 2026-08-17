# 2026-08-17 11:45:56 - operator relay workflow alignment

## Пользовательский промпт

`хорошо. давай следующие шаги`

## Что изменено

- в `spring-panel` `BotRuntimeTicketWriteService` расширен workflow-alignment для `recordOperatorRelay(...)` из internal bot write API;
- panel-side relay path теперь после записи `chat_history` и обновления `ticket_active` дополнительно:
  - назначает `responsible`, если он ещё не задан;
  - добавляет replying operator в `ticket_participants`, если ответственным уже назначен другой оператор;
- это выравнивает support-chat operator reply semantics с обычным panel-side `DialogReplyService`, чтобы runtime reply не оставался урезанным по workflow ownership;
- обновлён `BotRuntimeTicketWriteServiceTest`:
  - проверяется auto-assign `responsible` при первом operator relay;
  - проверяется auto-add в `ticket_participants`, если reply делает не текущий ответственный;
- подтверждено, что `BotRuntimeWriteApiControllerWebMvcTest` остаётся зелёным и write-контракт internal bot API не сломан.

## Проверка

- `cmd /c "mvnw.cmd -q -Dtest=BotRuntimeTicketWriteServiceTest,BotRuntimeWriteApiControllerWebMvcTest test"` в `spring-panel`
- `cmd /c "mvnw.cmd -q -DskipTests compile"` в `spring-panel`
