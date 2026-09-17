# Clean-code/source-layout - P6g MAX question option support

Date: 2026-09-17
Task: 01-260
Baseline: 8960aead5bf68c80e5f1fe9aee0dceb4e2f64e12

## Scope

Continue the java-bot slice with one pure MAX question-option policy seam: move select-label normalization, location-tree traversal, preset-definition extraction and excluded-option filtering out of the oversized MaxWebhookController into a package-private support owner.

## Changes

- add MaxQuestionOptionSupport for pure select, location-tree, preset-definition and exclusion transformations;
- keep resolveQuestionOptions/resolvePresetOptions orchestration in MaxWebhookController so runtime cache reads stay at the controller boundary;
- route dynamic option resolution through the extracted pure owner;
- remove the now-local Objects import and tree/list conversion helpers from MaxWebhookController;
- add focused option behavior tests and extend the MAX source-layout ownership contract;
- keep existing MAX webhook, inbound payload, delivery identity and question-input tests in the targeted regression suite.

## Ownership kept in MaxWebhookController

- locationTree/presetDefinitions and cache refresh ownership;
- RuntimeConfigService and BotSettingsService calls;
- HTTP ingress, webhook secret, ownership and delivery-guard orchestration;
- session persistence and question-flow orchestration;
- ticket, feedback, messaging and attachment storage I/O.

## Non-goals

- no option ordering or exclusion behavior changes;
- no preset/location cache behavior changes;
- no runtime config/settings lookup changes;
- no session persistence changes;
- no webhook/dedup changes;
- no Spring wiring changes;
- no database/schema/config changes;
- no operational-script changes;
- no deployment.

## Validation

Targeted bot-max tests:

- MaxQuestionOptionSupportTest
- MaxQuestionInputSupportTest
- MaxWebhookJavaSourceLayoutContractTest
- MaxWebhookControllerTest
- MaxDeliveryIdentitySupportTest
- MaxInboundPayloadSupportTest

The operator also runs git diff --check in an isolated detached worktree before applying the same source transformation to the real checkout.
