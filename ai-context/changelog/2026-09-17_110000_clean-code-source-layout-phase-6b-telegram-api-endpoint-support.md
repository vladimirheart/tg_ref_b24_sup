# Clean-code/source-layout — P6b Telegram API endpoint support

Date: 2026-09-17
Task: 01-260
Baseline: 4f801f4758f46d15def47208440f9cec5ebaf320

## Scope

Continue the java-bot slice with one pure network-configuration seam: move Telegram Bot API root/base URL normalization out of the oversized SupportBot into a package-private support owner.

## Changes

- add TelegramApiEndpointSupport for root URL normalization and /bot base URL construction;
- route SupportBot bot-options, startup-log and file-download URL call sites through the extracted support owner;
- move the endpoint-only SupportBotTest contract to TelegramApiEndpointSupportTest;
- extend the existing SupportBot source-layout ownership guard.

## Ownership kept in SupportBot

- environment reads;
- DefaultBotOptions and proxy selection;
- startup credential verification and lifecycle;
- Telegram execute/send calls;
- HttpURLConnection file download I/O;
- token access and file URL assembly.

## Non-goals

- no endpoint values or behavior changes;
- no proxy behavior changes;
- no Spring wiring changes;
- no database/schema/config changes;
- no operational-script changes;
- no deployment.

## Validation

Targeted bot-telegram tests:

- TelegramApiEndpointSupportTest
- SupportBotJavaSourceLayoutContractTest
- SupportBotChoiceInputContractTest

The operator also runs git diff --check in an isolated detached worktree before applying the same source transformation to the real checkout.
