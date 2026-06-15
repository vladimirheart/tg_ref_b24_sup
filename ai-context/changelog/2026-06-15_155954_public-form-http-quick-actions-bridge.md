# 2026-06-15 15:59:54 - public form http quick actions bridge

## Что сделано
- переведён оставшийся public-form smoke bridge на реальные HTTP quick
  actions: `PublicFormFlowSmokeIntegrationTest` теперь использует live
  `POST /api/dialogs/{ticketId}/take`, `POST /resolve` и `POST /reopen`
  вместо прямых вызовов `DialogQuickActionService`;
- обновлены end-to-end сценарии вокруг public form:
  read bridge, previous history, dialogs list bridge, details/history
  projection и notification lifecycle теперь подтверждают одинаковую
  controller-level boundary между `web_form` и operator-side dialog APIs;
- из `DialogListIntegrationTest` и `DialogReadIntegrationTest` удалены
  неиспользуемые `DialogQuickActionService` import/field, чтобы не держать
  ложное впечатление о service fallback в уже переведённых adjacent
  integration tests.

## Почему это важно
- раньше public-form smoke слой частично обходил quick-action controllers,
  поэтому внешняя форма могла выглядеть зелёной даже при расхождении между
  service orchestration и реальным HTTP permission/payload contract;
- новый пакет выравнивает smoke coverage с остальными audit-hardening
  сценариями и делает quick-action drift заметным уже на границе
  `public form -> operator dialogs`.

## Проверка
- `./mvnw.cmd -Dtest=PublicFormFlowSmokeIntegrationTest,DialogListIntegrationTest,DialogReadIntegrationTest test`
