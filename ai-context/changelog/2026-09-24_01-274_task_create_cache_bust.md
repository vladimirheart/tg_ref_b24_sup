# 01-274 task create cache bust

Date: 2026-09-24
Task: 01-274

## Context

R15 fixed first task creation and removed implicit project selection, but the Tasks template retained the older tasks.js cache key. A browser could therefore reuse the pre-R15 runtime after deployment.

## Change

- bump only the Tasks runtime cache key to the validated R15 behavior;
- preserve the existing CSRF fix and task sequence fix unchanged;
- no DB migration or runtime mutation in this source package.
