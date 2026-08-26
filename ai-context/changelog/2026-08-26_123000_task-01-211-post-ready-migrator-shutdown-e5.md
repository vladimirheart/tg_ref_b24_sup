# 01-211 Phase E5 — post-ready migrator shutdown

Date: 2026-08-26 12:30 +03:00
Task: 01-211

## Runtime evidence

The latest isolated Docker smoke proves that migration and application startup succeed:

```text
Successfully applied 40 migrations to schema "public", now at version v40
Started PanelApplication
Iguana runtime role selected: role=migrator
```

The process then fails during its own one-shot shutdown:

```text
RuntimeMigrationExitListener - ... closing application context
Application run failed
java.lang.IllegalStateException:
  AnnotationConfigServletWebServerApplicationContext ... has been closed already
...
EventPublishingRunListener.ready(...)
```

## Root cause

`RuntimeMigrationExitListener` closes the Spring context from inside an
`ApplicationReadyEvent` listener. Spring's event multicaster still has additional
ready listeners to invoke, so the next listener tries to resolve a bean from an
already-closed BeanFactory.

## Recovery

- Remove `RuntimeMigrationExitListener`.
- Capture the context returned by `SpringApplication.run(...)`.
- For `MIGRATOR + exitAfterMigration`, close the context only after `run(...)`
  has returned, i.e. after ready-event publication is complete.
- Update the lifecycle source-contract so an event-listener shutdown cannot
  silently return.
- Improve Docker smoke diagnostics to print the last db-migrate log lines
  immediately on a non-zero migrator exit.

## Status

Task remains `🟡` until the full Docker role/scale smoke is green.
