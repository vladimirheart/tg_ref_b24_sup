# Clean-code/source-layout - P6l MAX active message support

Date: 2026-09-17
Task: 01-260
Baseline: 0d7fedefd4bc5b1f7c9294475468cc0bfb4a1d2a

## Scope

Continue the java-bot slice with one feature-cohesive pure seam: move MAX active-ticket operator message rendering out of MaxWebhookController while keeping ticket recording, channel guards and message delivery in the controller.

## Changes

- add MaxActiveMessageSupport as the package-private owner of active-ticket operator message text rendering;
- preserve client display label, ticket id, text/message-type fallback and attachment reference/count wording;
- preserve stored attachment reference precedence over attachment count;
- route notifyOperatorsAboutActiveMessage(...) through the pure owner;
- keep the controller guard for missing channel/ticket and MessagingService delivery;
- add focused message-policy tests and extend the MAX source-layout ownership contract;
- keep P6d-P6k MAX regressions in the targeted suite.

## Ownership kept in MaxWebhookController

- active ticket lookup and stale-ticket handling;
- ActiveInboundClientMessageCommand construction and ticket recording;
- channel/ticket notification guard and MessagingService delivery;
- attachment download/storage/fallback and error handling;
- session, feedback, blacklist and runtime-cache orchestration;
- HTTP ingress, webhook ownership and delivery guard.

## Non-goals

- no active-message wording changes;
- no ticket persistence or routing changes;
- no attachment behavior changes;
- no Spring wiring changes;
- no database/schema/config changes;
- no operational-script changes;
- no deployment.

## Validation

Targeted bot-max tests:

- MaxActiveMessageSupportTest
- MaxAttachmentMetadataSupportTest
- MaxUnblockMessageSupportTest
- MaxIncidentFlowSupportTest
- MaxConversationSessionTest
- MaxQuestionOptionSupportTest
- MaxQuestionInputSupportTest
- MaxWebhookJavaSourceLayoutContractTest
- MaxWebhookControllerTest
- MaxDeliveryIdentitySupportTest
- MaxInboundPayloadSupportTest

The operator also runs git diff --check in an isolated detached worktree before applying the same source transformation to the real checkout.
