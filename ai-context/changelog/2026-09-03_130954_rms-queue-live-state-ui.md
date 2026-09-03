# RMS queue live state UI V9

- Base main: 5292d6da3a3a495f8c7f8be813c21b37d423b685
- Time: 2026-09-03_130954

## Change

- Reuse the page-header info button hit target for RMS queue info.
- Ignore root-level PowerShell operator scripts in repository dirty-state validation.
- Use git diff --quiet for target-file freshness checks to avoid text/line-ending false positives.
- Accept the committed V3 layout where renderQueueState follows renderQueueLine without a blank line.
- Match QueueState by structure/whitespace instead of an exact text block.
- Match controller queue-state mapping by structure instead of CRLF-sensitive here-string equality.
- Normalize every generated multiline replacement to LF before restoring the target file newline style.
- Show human-readable queue phases: active check, inter-request pause, queued, scheduled wait, finishing.
- Persist current/next RMS and completed/total progress through backend_ops_command.progress_message.
- Refresh backend heartbeat whenever RMS queue progress changes.
- Expose phase and next_monitor_id in the RMS refresh-state API.
- Recreate panel-web and ops-worker on the same candidate image.
- Recover interrupted RMS commands owned by the replaced worker back to queued.

