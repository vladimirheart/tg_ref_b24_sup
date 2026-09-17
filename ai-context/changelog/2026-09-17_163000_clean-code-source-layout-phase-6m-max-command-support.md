# Clean-code/source-layout - P6m MAX command support

Date: 2026-09-17
Task: 01-260
Baseline: ac4eaae31d09eec0366abc306c6822d510fc4bf0

## Scope

Continue the java-bot slice with one cohesive pure command-policy seam: move MAX start, unblock and cancel command recognition out of MaxWebhookController while preserving the exact legacy matching semantics.

## Changes

- add MaxCommandSupport as the package-private owner of MAX command recognition;
- preserve exact case-insensitive /start and /unblock matching without trimming;
- preserve cancel trimming, Locale.ROOT normalization and /cancel, cancel, отмена aliases;
- route controller command branches through the pure owner;
- remove isCancelCommand(...) from MaxWebhookController;
- add focused command-policy tests and extend the MAX source-layout ownership contract;
- keep P6d-P6l MAX regressions in the targeted suite.

## Ownership kept in MaxWebhookController

- blacklist resolution and unblock request handling;
- session load/save/delete and start/cancel orchestration;
- messaging, ticket, feedback, attachment and runtime-cache I/O;
- HTTP ingress, webhook ownership and delivery guard.

## Non-goals

- no command wording or matching changes;
- no new command aliases;
- no session or blacklist behavior changes;
- no message delivery changes;
- no Spring wiring changes;
- no database/schema/config changes;
- no operational-script changes;
- no deployment.

## Validation

Targeted bot-max tests:

- MaxCommandSupportTest
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
