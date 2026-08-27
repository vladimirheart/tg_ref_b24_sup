# 01-212 - panel lifecycle backup runner

## User prompt

> ой жопа. мешает очень. пусть запускается в полном фоне и только тогда, когда запускается панель.
> да и у меня обновлён репо.

## Repository base

Fresh main HEAD: `ba7a059d228057ac7df049a7f0f2dd7d16d5d59e`.
The latest commit already contains the manual backup UI/API/queue work and its source-contract synchronization, plus unrelated task 01-215 readiness work.

## Change

- replace periodic Scheduled Task/cron model with one hidden panel-lifecycle daemon;
- add Windows/Unix start and stop helpers with PID/stop-signal lifecycle;
- bind local Windows runner to `spring-panel/run-windows.bat`;
- bind Docker production runner to production up/down helpers;
- preserve panel-web no-Docker-socket boundary;
- reload admin backup policy inside daemon;
- update runner heartbeat semantics and shorten active window;
- prevent scheduled failure retry storms;
- turn old installer scripts into legacy scheduler removal helpers;
- update UI wording, tests, task evidence and runbook.

The apply helper also tries to remove the already-installed legacy Windows Scheduled Task after verification. Failure to remove it due to permissions is reported as a warning and does not roll back repository changes.

No git add/commit/push/reset/checkout/clean is performed.
