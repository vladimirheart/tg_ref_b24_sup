# 01-268 clean-code/source-layout - Java phase 5u

## Scope
- Extract create-passport write choreography from ObjectPassportService into package-private ObjectPassportCreateCommand.
- Move payload normalization/validation, object/passport inserts, and create response projection into the command owner.
- Keep ObjectPassportService ownership of transaction boundaries, runtime datasource selection, and Connection lifecycle.
- Preserve the public ObjectPassportService constructor signature and create response shape.
- Add direct H2 regression coverage for persistence, response projection, and required-department validation.
- No deployment.

## Non-goals
- No update, replace-all, photo mutation/storage, NetBox lookup, list, details, cases, tasks, schema, endpoint, or scheduler changes.
