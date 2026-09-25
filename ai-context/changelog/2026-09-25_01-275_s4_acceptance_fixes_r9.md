# 01-275 S4 acceptance fixes R9

<!-- 01-275_S4_ACCEPTANCE_FIXES_R9_2026-09-25 -->

## Context

R8 production rollout was GREEN, but manual acceptance found that the sidebar still rendered the old density because generated runtime CSS had drifted from SCSS, and follow-up project routing needs to be per bot rather than global. Automatic MAX avatar refresh was confirmed working after a real inbound MAX message; the manual client refresh feedback incorrectly labelled the MAX channel as Telegram.

## Changes

- synchronize sidebar SCSS and generated CSS; reduce header top padding and remove the redundant outer account/footer frame;
- move auto-close follow-up project selection into each channel editor via existing delivery_settings, with no schema migration;
- route auto-close follow-up tasks from the ticket channel's delivery settings while preserving responsible/co-executor and fail-soft project behavior;
- stop writing the obsolete global follow_up_project_id from the auto-close Settings payload;
- make manual client refresh provider-aware for MAX and explicitly describe the inbound-event-driven avatar refresh path.

## Validation target

- node --check for changed Settings runtimes;
- spring-panel compile;
- DialogAutoCloseFollowUpTaskServiceTest;
- DialogAutoCloseSchedulerServiceTest;
- Task275SmallFollowUpsSourceContractTest;
- exact dirty-scope, source snapshot preservation and diff-check guards.
