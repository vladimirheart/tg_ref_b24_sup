# 01-266 clean-code/source-layout - Java phase 5s

## Scope
- Extract object-passport cases read-model assembly from ObjectPassportService into package-private ObjectPassportCasesQuery.
- Keep ObjectPassportAppealQuery as the owner of support-message SQL and appeal matching.
- Keep ObjectPassportService ownership of runtime datasource selection and Connection lifecycle.
- Preserve the public ObjectPassportService constructor signature and existing cases response shape.
- Add direct H2 regression coverage for stored-passport context, appeal deduplication, projection totals, and not-found semantics.
- No deployment.

## Non-goals
- No transaction, CRUD, replace-all, photo storage, NetBox, equipment, list, schema, endpoint, or scheduler changes.
