# Storage local-source audit

Date: 2026-08-28

Context:
- canonical storage mapping repair completed with 52 canonical objects already present;
- 20 attachment metadata mappings remained absent from both canonical MinIO and legacy unprefixed MinIO;
- several missing rows still reference an older Windows checkout under `Documents\tg_bot\...\java-bot\attachments`.

Changes:
- added `scripts/docker-production-storage-local-source-audit.ps1`;
- audit reads only metadata rows currently marked `availability_status=missing`;
- PostgreSQL text values are transferred as UTF-8 hex to avoid Windows console mojibake;
- audit discovers existing attachment roots from absolute `legacy_attachment_ref` values;
- current and discovered legacy roots are indexed once and checked for exact storage-key paths and basename matches;
- unique, ambiguous, and missing local candidates are reported separately;
- no database rows, MinIO objects, or local files are modified;
- added `DockerProductionStorageLocalSourceAuditSourceContractTest` to enforce the read-only contract.

Operational note:
- keep `APP_STORAGE_OBJECT_LEGACY_LOCAL_FALLBACK_ENABLED=true` until remaining mappings are restored or explicitly accepted as irrecoverable and the cutover criteria are revised.
