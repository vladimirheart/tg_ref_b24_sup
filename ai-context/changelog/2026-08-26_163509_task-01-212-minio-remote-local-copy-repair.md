# 01-212 MinIO remote-to-local snapshot repair

## User runtime evidence

The user reran the complete backup smoke after cleanup/seed repair.

Observed:
- PostgreSQL backup GREEN;
- PostgreSQL isolated restore GREEN;
- MinIO seed verification did not fail;
- MinIO backup published files=0;
- MinIO isolated restore passed with objects=0;
- the final smoke assertion rejected the snapshot because exactly one seeded object was expected.

## Root cause

The MinIO backup used mc mirror from an S3/MinIO source to a local filesystem target. For downloading bucket contents to a filesystem snapshot, use mc cp --recursive.

## Change

- create the source inventory before download;
- count source objects from inventory.jsonl;
- replace remote-to-local mc mirror with mc cp --recursive;
- compare source object count with local snapshot file count;
- fail on any mismatch;
- add source_object_count to the manifest;
- update source-contract coverage and recovery runbook.

## Status

01-212 remains YELLOW pending complete GREEN Docker backup/restore smoke and real off-host DR proof.

No stage/commit/push/reset/checkout/clean is performed by this repair helper.