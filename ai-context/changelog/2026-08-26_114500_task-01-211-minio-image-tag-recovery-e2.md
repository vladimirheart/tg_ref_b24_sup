# 01-211 Phase E2 — MinIO image tag recovery

Date: 2026-08-26 11:45 +03:00
Task: 01-211

## Smoke failure

The isolated Docker role/scale smoke failed before any backend role started:

```text
failed to resolve reference "docker.io/minio/minio:RELEASE.2026-07-23T15-54-02Z": not found
```

The compose file accidentally pinned future/non-existent 2026 MinIO tags.

Docker Hub exposes the intended matching releases as:

- `minio/minio:RELEASE.2025-07-23T15-54-02Z`;
- `minio/mc:RELEASE.2025-07-21T05-28-08Z`.

## Recovery

- correct both tags in `docker-compose.production-contour.yml`;
- extend Docker topology source contract to reject the bad 2026 tags and require the pinned 2025 tags;
- validate base/edge Compose;
- explicitly pull both MinIO images;
- run `git diff --check`.

The next gate remains the same isolated Docker role/scale smoke.
