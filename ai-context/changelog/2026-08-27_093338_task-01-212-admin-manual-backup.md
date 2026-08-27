# 01-212 - manual backup action in admin UI

## User prompt

> вроде всё ок, но из админки непонятно как запустить бэкап вручную, хотя настройки бэкапирования есть

Runtime evidence supplied by the operator also showed a successful local tar.gz backup and restore rehearsal for PostgreSQL, empty MinIO and shared config.

## Change

- add a clear `Запустить backup сейчас` control to Backup & recovery;
- queue manual requests through shared config instead of exposing Docker/host execution to panel-web;
- add host-runner heartbeat and operation status polling;
- support optional restore rehearsal matching Critical/Full/Custom scope;
- require explicit local-test acknowledgement when destination is not an external failure domain;
- prevent enabled automatic schedules from running on a local/non-DR destination;
- change host runner cadence to one minute;
- add tests, source-contract coverage and runbook documentation.

No git add/commit/push/reset/checkout/clean is performed by the apply helper.
