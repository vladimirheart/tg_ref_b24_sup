# 01-212 - admin backup policy v2

## User requirements

The operator reported that no backup destination has been provisioned yet and requested the path/settings to be managed in the admin UI. The scope was expanded to require portable tar.gz recovery packages, separate critical/full schedules, custom component selection, and explicit restore component semantics.

## This slice

- resumes safely after the previous partial admin-settings apply;
- adds a complete non-secret backup policy model and `/api/settings/backup`;
- adds Settings -> Backup & recovery UI;
- stores policy in ignored shared `backup.properties`;
- makes PowerShell/Bash production helpers import that policy before Compose;
- keeps process environment as the highest-priority override;
- records tar.gz/component/scheduling requirements for the runtime archive migration.

The actual PostgreSQL/MinIO/file archive conversion is intentionally the next isolated runtime slice so the proven backup flow is not changed in the same patch as the admin-policy persistence layer.

No git add/commit/push/reset/checkout/clean is performed.
