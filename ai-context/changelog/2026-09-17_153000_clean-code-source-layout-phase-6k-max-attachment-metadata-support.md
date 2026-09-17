# Clean-code/source-layout - P6k MAX attachment metadata support

Date: 2026-09-17
Task: 01-260
Baseline: ead05c243888eecec0f5ce2b2512a97494e8b3df

## Scope

Continue the java-bot slice with one cohesive pure attachment-metadata seam: move filename/channel-id normalization and attachment-extension resolution out of MaxWebhookController while keeping download, persistence, fallback and error handling in the controller.

## Changes

- add MaxAttachmentMetadataSupport as the package-private owner of first-nonblank normalization and attachment extension policy;
- preserve filename extension precedence, sanitizing and length guard;
- preserve content-type and attachment-type fallback mappings plus the binary default;
- route incoming attachment original-name, extension and channel-public-id preparation through the pure owner;
- remove firstNonBlank/resolveAttachmentExtension/trimOrNull from MaxWebhookController;
- add focused metadata-policy tests and extend the MAX source-layout ownership contract;
- keep P6d-P6j MAX regressions in the targeted suite.

## Ownership kept in MaxWebhookController

- MaxApiClient attachment download and close lifecycle;
- AttachmentService persistence and StoredIncomingAttachment construction;
- fallback reference behavior and IOException/InterruptedException/runtime error handling;
- operator notification, session, ticket, feedback, blacklist and runtime-cache orchestration;
- HTTP ingress, webhook ownership and delivery guard.

## Non-goals

- no attachment naming or extension behavior changes;
- no download/storage/fallback behavior changes;
- no message or ticket behavior changes;
- no Spring wiring changes;
- no database/schema/config changes;
- no operational-script changes;
- no deployment.

## Validation

Targeted bot-max tests:

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
