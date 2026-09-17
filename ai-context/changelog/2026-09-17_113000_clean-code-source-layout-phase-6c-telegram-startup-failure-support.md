# Clean-code/source-layout — P6c Telegram startup failure support

Date: 2026-09-17
Task: 01-260
Baseline: 56cde1653ad169a721427f4e37d0525a296fdb3d

## Scope

Continue the java-bot slice with one pure startup-diagnostics seam: move Telegram startup failure classification and message construction out of the oversized SupportBot into a package-private support owner.

## Changes

- add TelegramStartupFailureSupport for deepest-cause resolution, proxy-tunnel classification, connectivity classification, and startup diagnostic text;
- keep the package-level SupportBot.describeStartupFailure(...) facade because TelegramLongPollingLifecycle already depends on that orchestration boundary;
- keep credential verification, Telegram execute(GetMe), exception capture, API-root resolution and IllegalStateException ownership in SupportBot;
- add focused behavior tests for proxy-tunnel, connectivity and generic failures;
- extend the existing SupportBot source-layout ownership guard.

## Ownership kept in SupportBot

- Spring lifecycle and startup verification orchestration, including the existing describeStartupFailure(...) facade used by TelegramLongPollingLifecycle;
- environment and Telegram API endpoint resolution;
- Telegram execute/send calls;
- exception capture and IllegalStateException wrapping;
- credentials and BotProperties access.

## Non-goals

- no startup behavior or message changes;
- no endpoint/proxy configuration changes;
- no Spring wiring changes;
- no database/schema/config changes;
- no operational-script changes;
- no deployment.

## Validation

Targeted bot-telegram tests:

- TelegramStartupFailureSupportTest
- SupportBotJavaSourceLayoutContractTest
- TelegramApiEndpointSupportTest
- SupportBotChoiceInputContractTest

The operator also runs git diff --check in an isolated detached worktree before applying the same source transformation to the real checkout.
