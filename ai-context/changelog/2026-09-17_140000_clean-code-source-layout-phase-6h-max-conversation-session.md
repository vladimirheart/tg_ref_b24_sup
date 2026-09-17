# Clean-code/source-layout - P6h MAX conversation session

Date: 2026-09-17
Task: 01-260
Baseline: e68b409c87184ea047b9f178ee7eef6c8fc76cde

## Scope

Continue the java-bot slice with one cohesive MAX state-model seam: move the pure conversation session state machine out of MaxWebhookController while keeping persistence and runtime orchestration at the controller boundary.

## Changes

- add MaxConversationSession as the package-private owner of conversation state, history, routing, reuse/back behavior, first-response timeout checks, ticket attributes, summary text and snapshot state;
- move the persisted State and HistoryEvent records with that model;
- route MaxWebhookController session lifecycle through MaxConversationSession while preserving BotSessionStoreService load/save/delete ownership and optimistic concurrency behavior in the controller;
- remove QuestionOptionDto and QuestionRouteDto imports from MaxWebhookController because those DTOs are now model-internal;
- add focused state-machine tests and extend the MAX source-layout ownership contract;
- keep existing MAX webhook, inbound payload, delivery identity, question-input and question-option tests in the targeted regression suite.

## Ownership kept in MaxWebhookController

- BotSessionStoreService load/save/delete and persisted raw-payload CAS orchestration;
- scheduled silent-session expiration orchestration and user messaging;
- HTTP ingress, webhook secret, ownership and delivery-guard orchestration;
- runtime config/settings cache reads;
- ticket creation, feedback, operator messaging and attachment storage I/O.

## Non-goals

- no session behavior or routing changes;
- no session-store schema or payload-field changes;
- no optimistic locking changes;
- no timeout policy changes;
- no ticket/message behavior changes;
- no Spring wiring changes;
- no database/schema/config changes;
- no operational-script changes;
- no deployment.

## Validation

Targeted bot-max tests:

- MaxConversationSessionTest
- MaxQuestionOptionSupportTest
- MaxQuestionInputSupportTest
- MaxWebhookJavaSourceLayoutContractTest
- MaxWebhookControllerTest
- MaxDeliveryIdentitySupportTest
- MaxInboundPayloadSupportTest

The operator also runs git diff --check in an isolated detached worktree before applying the same source transformation to the real checkout.
