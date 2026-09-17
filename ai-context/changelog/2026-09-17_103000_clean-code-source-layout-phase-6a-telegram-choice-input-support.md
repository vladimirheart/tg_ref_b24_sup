# Clean-code/source-layout — P6a Telegram choice-input support

Date: 2026-09-17
Task: 01-260
Baseline: cc37352690e57fee4402271e0ac44e6bf33df2cc

## Scope

First narrow java-bot seam after completing the Spring Panel P5 slice. Extract only stateless choice-input policy from the oversized Telegram SupportBot into a package-private support owner.

## Changes

- add TelegramChoiceInputSupport for direct numeric/text option resolution, alias normalization, case-insensitive option matching, and media guidance text;
- keep business-alias lookup through BotSettingsService in SupportBot;
- keep conversation/session state, question routing, Telegram execute/send calls, lifecycle, storage, persistence and scheduling in SupportBot;
- keep default start reply and no-active-dialog text in SupportBot because they are not choice-input policy;
- extend the existing choice-input contract test and add a source-layout ownership guard.

## Non-goals

- no Telegram endpoint or API behavior changes;
- no Spring wiring or lifecycle changes;
- no database/schema/config changes;
- no operational-script changes;
- no deployment.

## Validation

Targeted bot-telegram tests:

- SupportBotChoiceInputContractTest
- SupportBotJavaSourceLayoutContractTest
- SupportBotTest

The operator also runs git diff --check in an isolated detached worktree before applying the same source transformation to the real checkout.
