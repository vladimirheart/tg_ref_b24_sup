# Clean-code/source-layout - P6j MAX unblock message support

Date: 2026-09-17
Task: 01-260
Baseline: 0b86d88400071664f6bf3aecf3a52a043152ce65

## Scope

Continue the java-bot slice with one feature-cohesive pure seam: move MAX unblock-request message text policy out of MaxWebhookController while keeping blacklist mutation, cooldown calculation and message delivery in the controller.

## Changes

- add MaxUnblockMessageSupport as the package-private owner of unblock operator/client text rendering;
- preserve operator request-id, user-id, reason, timestamp and status formatting;
- preserve client created/cooldown/pending responses, including legacy retry-minute rounding;
- keep BlacklistService.requestUnblock(...) and decision unpacking at the controller boundary;
- keep MessagingService send operations in MaxWebhookController;
- remove buildUnblockResponse/formatRetryAfter/formatTimestamp and DateTimeFormatter ownership from the controller;
- add focused text-policy tests and extend the MAX source-layout ownership contract;
- keep P6d-P6i MAX regressions in the targeted suite.

## Ownership kept in MaxWebhookController

- blacklist status and unblock mutation I/O;
- cooldown settings lookup and Duration construction;
- operator/client message delivery;
- session, ticket, feedback, runtime-cache and attachment orchestration;
- HTTP ingress, webhook ownership and delivery guard.

## Non-goals

- no unblock wording changes;
- no cooldown or retry behavior changes;
- no blacklist persistence changes;
- no message destination changes;
- no Spring wiring changes;
- no database/schema/config changes;
- no operational-script changes;
- no deployment.

## Validation

Targeted bot-max tests:

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
