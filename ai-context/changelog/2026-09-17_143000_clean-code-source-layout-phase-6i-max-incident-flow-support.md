# Clean-code/source-layout - P6i MAX incident flow support

Date: 2026-09-17
Task: 01-260
Baseline: 9fb08fae1451a57ba5cb22fe068fa685f0dc8aa0

## Scope

Continue the java-bot slice with one pure incident-flow normalization seam: move fixed MAX location-question ordering, defaults and metadata carry-over out of MaxWebhookController while keeping BotSettingsService reads in the controller.

## Changes

- add MaxIncidentFlowSupport as the package-private owner of pure configured-flow normalization;
- keep botSettingsService.questionFlow(settings) at the controller boundary and pass the resulting DTO list into the pure owner;
- preserve fixed business/location_type/city/location_name ordering, default prompts, exclusions, binding/dashboard/routes metadata and the final problem question;
- remove CORE_LOCATION_FIELDS, buildIncidentFlow/defaultPrompt and their now-local imports from MaxWebhookController;
- add focused incident-flow behavior tests and extend the MAX source-layout ownership contract;
- keep P6d-P6h MAX regressions in the targeted suite.

## Ownership kept in MaxWebhookController

- BotSettingsService reads and runtime settings ownership;
- MaxConversationSession lifecycle plus BotSessionStoreService load/save/delete CAS orchestration;
- location/preset runtime cache reads;
- HTTP ingress, webhook ownership/delivery guard, ticket, feedback, messaging and attachment I/O.

## Non-goals

- no question order/default wording behavior changes;
- no settings lookup changes;
- no session persistence or routing changes;
- no webhook/dedup changes;
- no Spring wiring changes;
- no database/schema/config changes;
- no operational-script changes;
- no deployment.

## Validation

Targeted bot-max tests:

- MaxIncidentFlowSupportTest
- MaxConversationSessionTest
- MaxQuestionOptionSupportTest
- MaxQuestionInputSupportTest
- MaxWebhookJavaSourceLayoutContractTest
- MaxWebhookControllerTest
- MaxDeliveryIdentitySupportTest
- MaxInboundPayloadSupportTest

The operator also runs git diff --check in an isolated detached worktree before applying the same source transformation to the real checkout.
