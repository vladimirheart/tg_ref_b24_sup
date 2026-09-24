# 01-274 task create sequence hotfix

Date: 2026-09-24
Task: 01-274

## Manual acceptance blocker

After the CSRF write fix reached production, project create succeeded but creating a task returned HTTP 500. The new task form also inherited the current project scope by default, which is not desired.

## Root cause

The PostgreSQL baseline defines tasks.seq as NOT NULL. PanelTaskService assigns task_seq before its first task insert, but TaskApiController attempted the insert with seq=null and only tried to copy the generated task id into seq after save. The first insert therefore cannot satisfy the schema contract.

## Fix

- TaskApiController now allocates the existing task_seq value before the first insert for new tasks.
- Existing task updates preserve their sequence.
- New task forms start with no selected projects, regardless of the current project list scope.
- Added a source-contract regression test for both properties.
- No DB migration, runtime mutation, queue mutation, or environment mutation is performed by the source hotfix runners.
