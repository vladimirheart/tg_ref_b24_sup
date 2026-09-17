# Clean-code/source-layout - P6n MAX feedback input support

Date: 2026-09-17
Task: 01-260
Baseline: 2af859b7be0afdc121ec6f7eec8da6bf0711d122

## Scope

Continue the java-bot slice with one cohesive pure feedback-input seam: move numeric rating normalization, configured-value membership, integer parsing and invalid-rating prompt text out of MaxWebhookController while preserving feedback lookup, settings access, persistence and message delivery in the controller.

## Changes

- add MaxFeedbackInputSupport as the package-private owner of MAX feedback input policy;
- preserve trimmed digits-only candidate recognition before pending-feedback lookup;
- preserve exact configured string membership before parsing;
- preserve Integer.parseInt conversion and invalid-rating wording;
- route tryHandleFeedback(...) through the pure owner;
- remove the feedback-only java.util.Set import from MaxWebhookController;
- add focused feedback-policy tests and extend the MAX source-layout ownership contract;
- keep P6d-P6m MAX regressions in the targeted suite.

## Ownership kept in MaxWebhookController

- FeedbackService pending request lookup and feedback persistence;
- BotSettingsService settings, allowed-values, scale and response lookup;
- MessagingService delivery and HTTP response construction;
- session, ticket, blacklist, attachment and runtime-cache orchestration;
- HTTP ingress, webhook ownership and delivery guard.

## Non-goals

- no feedback rating behavior or wording changes;
- no settings/default-scale changes;
- no feedback persistence changes;
- no service call reordering across the pending-request boundary;
- no Spring wiring changes;
- no database/schema/config changes;
- no operational-script changes;
- no deployment.

## Validation

Targeted bot-max tests:

- MaxFeedbackInputSupportTest
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
