# Clean-code/source-layout - P6o MAX webhook input support

Date: 2026-09-17
Task: 01-260
Baseline: e76996bfb3eb308c1f8b57b93e7c518123bc59ca

## Scope

Finish the MAX pure-helper extraction slice with one cohesive webhook-input seam: move webhook secret validation plus primitive JsonNode string/Long extraction out of MaxWebhookController while leaving HTTP handling, retries, delivery ownership and all service orchestration in the controller.

## Changes

- add MaxWebhookInputSupport as the package-private owner of primitive MAX webhook input policy;
- preserve blank configured-secret acceptance and exact configured-secret equality;
- preserve missing/null JsonNode string fallback to the empty string;
- preserve numeric Long extraction, trimmed text parsing and invalid-text null fallback;
- route session-conflict user-id extraction and inbound user/chat ids through the pure owner;
- remove secretValid/text/asLong helper methods from MaxWebhookController;
- add focused webhook-input tests and extend the MAX source-layout ownership contract;
- keep P6d-P6n MAX regressions in the targeted suite.

## Ownership kept in MaxWebhookController

- HTTP endpoint behavior and ResponseEntity construction;
- MaxBotProperties access and ingress enablement decisions;
- optimistic session mutation retry orchestration;
- webhook delivery claiming/completion/release;
- channel, blacklist, ticket, feedback, messaging, attachment and runtime-cache I/O.

## Non-goals

- no webhook secret behavior changes;
- no JsonNode extraction behavior changes;
- no response/status changes;
- no constructor or Spring wiring changes;
- no session/delivery behavior changes;
- no database/schema/config changes;
- no deployment.

## Validation

Targeted bot-max tests:

- MaxWebhookInputSupportTest
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
