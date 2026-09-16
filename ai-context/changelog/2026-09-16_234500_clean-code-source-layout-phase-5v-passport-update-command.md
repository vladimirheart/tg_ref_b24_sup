# 01-269 clean-code/source-layout - Java phase 5v

## Scope
- Extract update-passport write choreography from ObjectPassportService into package-private ObjectPassportUpdateCommand.
- Move stored-passport loading, manual-override merge, payload normalization/validation, object/passport persistence updates, and response projection into the command owner.
- Keep ObjectPassportService ownership of manual-vs-external update choice, transaction boundaries, runtime datasource selection, and Connection lifecycle.
- Preserve the public ObjectPassportService constructor signature and NetBox upsert behavior.
- Add direct H2 regression coverage proving manual updates add overrides while external-source updates preserve but do not add them.
- No deployment.

## Non-goals
- No create, replace-all, photo mutation/storage, read-query, tasks, schema, endpoint, or scheduler changes.
