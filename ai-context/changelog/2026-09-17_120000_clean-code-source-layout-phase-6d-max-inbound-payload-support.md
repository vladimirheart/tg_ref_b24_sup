# Clean-code/source-layout - P6d MAX inbound payload support

Date: 2026-09-17
Task: 01-260
Baseline: 0b44e98609f04e574bbcf61e721c5334c6e0820b

## Scope

Continue the java-bot slice with one MAX responsibility seam: move pure inbound JSON decoding out of the oversized MaxWebhookController into a package-private support owner.

## Changes

- add MaxInboundPayloadSupport for client-profile decoding, direct/forward text selection, forwarded-author attribution, incoming attachment decoding, and attachment-type normalization;
- route MaxWebhookController through the extracted decoder while preserving its HTTP, delivery-guard, session, ticket, storage and messaging orchestration;
- add focused decoder behavior tests and a MAX source-layout ownership contract;
- keep the existing MaxWebhookControllerTest unchanged as an integration regression for duplicate delivery, forwarded messages and reply targets.

## Ownership kept in MaxWebhookController

- webhook secret and HTTP response handling;
- ingress ownership and BotWebhookDeliveryGuardService claims;
- buildDeliveryKey and provider/reply message-id handling;
- session persistence and question-flow orchestration;
- blacklist, ticket, feedback and operator messaging service calls;
- MaxApiClient download and AttachmentService storage I/O.

## Non-goals

- no MAX webhook/API behavior changes;
- no delivery-key or deduplication changes;
- no attachment storage changes;
- no Spring wiring changes;
- no database/schema/config changes;
- no operational-script changes;
- no deployment.

## Validation

Targeted bot-max tests:

- MaxInboundPayloadSupportTest
- MaxWebhookJavaSourceLayoutContractTest
- MaxWebhookControllerTest

The operator also runs git diff --check in an isolated detached worktree before applying the same source transformation to the real checkout.
