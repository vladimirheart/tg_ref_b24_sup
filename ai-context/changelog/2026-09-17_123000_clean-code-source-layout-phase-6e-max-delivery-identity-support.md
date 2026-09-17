# Clean-code/source-layout - P6e MAX delivery identity support

Date: 2026-09-17
Task: 01-260
Baseline: 1bed78dc9b06e37836e8057191067af37e677f11

## Scope

Continue the java-bot slice with one MAX transport responsibility seam: move delivery-key and provider/reply message identity derivation out of the oversized MaxWebhookController into a package-private pure support owner.

## Changes

- add MaxDeliveryIdentitySupport for deterministic delivery keys, provider message IDs and reply target IDs;
- route MaxWebhookController deduplication claim and active-message persistence through the extracted identity owner;
- remove UUID and UTF-8 fallback-ID machinery from MaxWebhookController;
- add focused identity behavior tests and extend the MAX source-layout ownership contract;
- keep existing MaxWebhookControllerTest and MaxInboundPayloadSupportTest in the targeted regression suite.

## Ownership kept in MaxWebhookController

- webhook secret and HTTP response handling;
- ingress ownership and BotWebhookDeliveryGuardService claim orchestration;
- session persistence and question-flow orchestration;
- blacklist, ticket, feedback and operator messaging service calls;
- incoming attachment download and storage I/O.

## Non-goals

- no delivery-key algorithm changes;
- no provider/reply ID behavior changes;
- no webhook/dedup semantics changes;
- no attachment parsing/storage changes;
- no Spring wiring changes;
- no database/schema/config changes;
- no operational-script changes;
- no deployment.

## Validation

Targeted bot-max tests:

- MaxDeliveryIdentitySupportTest
- MaxWebhookJavaSourceLayoutContractTest
- MaxWebhookControllerTest
- MaxInboundPayloadSupportTest

The operator also runs git diff --check in an isolated detached worktree before applying the same source transformation to the real checkout.
