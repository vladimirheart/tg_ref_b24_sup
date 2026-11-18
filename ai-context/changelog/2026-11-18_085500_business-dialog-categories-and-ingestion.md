# 2026-11-18 — категории BUSINESS и регистрация обращения

## Исправлено

- Обычная карточка Inbox больше не отправляет оператора на технический сценарий BUSINESS при выборе категорий.
- Категории BUSINESS-обращения изменяются через канонический журнал business work: с проверкой прав, ревизии и аудитом, без legacy-таблиц.
- Категории можно уточнять и после закрытия обращения: статус не меняется, клиенту сообщение не отправляется.
- Добавлена миграция V97, разрешающая запись такого события в неизменяемый журнал.
- Причина пропажи нового обращения выявлена в рассинхронизации образов: `ops-worker` обрабатывал новую версию анкеты старым кодом и отправлял событие в DLQ. В релиз включён перезапуск worker на том же образе, что и панель.

## Дополнено

- Версия единого вопросника теперь считается по каноническому JSON: порядок ключей в вариантах ответа не влияет на SHA-256 между `bot-runner` и `ops-worker`.
- V98 разрешает BUSINESS-обращению из единого вопросника сохранять источник `QUESTIONNAIRE_FIELD` без legacy catalog hash; остальные контракты provenance сохранены.
- Реле feedback-уведомлений теперь выбирает только собственные outbox-события и не может перевести создание обращения из `bot.inbound.ticket-created` в ошибку.

## Проверка

- `BusinessTicketWorkPostgresTest#inboxCategoryUpdateUsesTheBusinessRecordEvenAfterTheDialogIsClosed`
- `DialogQuickActionsControllerWebMvcTest`
- `DialogQuickActionsQueryControllerWebMvcTest`
- `UnifiedChannelQuestionnaireServiceFingerprintTest`
- `SelectedBusinessTicketCreationPostgresTest#unifiedBusinessQuestionnairePersistsFieldProvenanceWithoutCatalogHash`
- `OutboundFeedbackPromptPublishOutboxServiceTest#dispatchDoesNotClaimInboundTicketCreationEvents`
