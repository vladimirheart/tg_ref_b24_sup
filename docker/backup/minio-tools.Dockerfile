FROM minio/mc:RELEASE.2025-07-21T05-28-08Z AS mc
FROM busybox:1.36.1

COPY --from=mc /usr/bin/mc /usr/bin/mc
ENTRYPOINT ["/bin/sh"]
