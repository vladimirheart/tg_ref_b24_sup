# 01-264 clean-code/source-layout - Java phase 5q

## Scope
- Extract manual-override tracking rules from ObjectPassportService into package-private ObjectPassportManualOverrideModel.
- Preserve the current merge behavior, existing override ordering, intentional blank values, metadata-key exclusions, and Objects.deepEquals comparison semantics.
- Keep create/update/upsert orchestration, transaction boundaries, runtime datasource selection, and Connection lifecycle in ObjectPassportService.
- Preserve the public ObjectPassportService constructor signature and all external consumers.
- Rewire the existing ObjectPassportManualOverrideGuardTest to the dedicated model owner and add metadata-key regression coverage.
- No deployment.

## Why
- Manual override tracking is pure domain logic and does not need JDBC, storage, datasource, or transaction ownership.
- Moving it out reduces ObjectPassportService without broadening the write-side extraction boundary.

## Checks
- ObjectPassportService owns a package-private ObjectPassportManualOverrideModel instance and delegates manual-update tracking to it.
- ObjectPassportManualOverrideModel owns _manual_overrides merge/tracking rules.
- Existing override order and intentional blank-field tracking stay unchanged.
- Keys starting with _, plus id and is_new, remain excluded from override tracking.
- Runtime datasource, Connection lifecycle, and transaction boundaries stay in ObjectPassportService.
- Generated Sass CSS noise is restored after Maven.
- git diff --check passes.

## Non-goals
- No CRUD transaction extraction.
- No replace-all, photo mutation/storage, NetBox, appeal, equipment, list, payload, persistence, endpoint, schema, scheduler, or deployment changes.
