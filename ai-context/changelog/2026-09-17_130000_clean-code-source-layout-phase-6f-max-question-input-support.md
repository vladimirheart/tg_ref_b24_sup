# Clean-code/source-layout - P6f MAX question input support

Date: 2026-09-17
Task: 01-260
Baseline: 994e2aef0733178b417e46c05aaf5f63820ddacb

## Scope

Continue the java-bot slice with one pure MAX question/input policy seam: move question classification, prompt text construction and direct choice-answer normalization out of the oversized MaxWebhookController into a package-private support owner.

## Changes

- add MaxQuestionInputSupport for preset/select/choice/optional classification, skip/back labels, prompt guidance and numeric/text choice answer resolution;
- route MaxWebhookController input validation, prompt rendering, option-mode branching and ticket choice-label classification through the extracted owner;
- keep dynamic option lookup and preset/location cache resolution in MaxWebhookController;
- add focused question-input behavior tests and extend the MAX source-layout ownership contract;
- keep existing MAX webhook, delivery identity and inbound payload tests in the targeted regression suite.

## Ownership kept in MaxWebhookController

- HTTP ingress, webhook secret, ownership and delivery-guard orchestration;
- session persistence and mutation retry handling;
- resolveQuestionOptions plus preset/location cache and settings lookup;
- blacklist, ticket, feedback and operator messaging service calls;
- incoming attachment download and storage I/O.

## Non-goals

- no question-flow behavior or wording changes;
- no dynamic option/preset/location lookup changes;
- no session persistence changes;
- no webhook/dedup changes;
- no Spring wiring changes;
- no database/schema/config changes;
- no operational-script changes;
- no deployment.

## Validation

Targeted bot-max tests:

- MaxQuestionInputSupportTest
- MaxWebhookJavaSourceLayoutContractTest
- MaxWebhookControllerTest
- MaxDeliveryIdentitySupportTest
- MaxInboundPayloadSupportTest

The operator also runs git diff --check in an isolated detached worktree before applying the same source transformation to the real checkout.
