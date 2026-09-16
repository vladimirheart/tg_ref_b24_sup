# 01-267 clean-code/source-layout - Java phase 5t

## Scope
- Extract single-passport details read-model assembly from ObjectPassportService into package-private ObjectPassportDetailsQuery.
- Keep ObjectPassportService ownership of runtime datasource selection and Connection lifecycle.
- Preserve ObjectPassportPersistence not-found semantics and ObjectPassportPayloadModel normalization.
- Preserve the public ObjectPassportService constructor signature and GET passport response shape.
- Add direct H2 regression coverage for detail normalization, photo URL projection, and not-found semantics.
- No deployment.

## Non-goals
- No transaction, CRUD, replace-all, photo storage, NetBox, equipment, list, cases, tasks, schema, endpoint, or scheduler changes.
