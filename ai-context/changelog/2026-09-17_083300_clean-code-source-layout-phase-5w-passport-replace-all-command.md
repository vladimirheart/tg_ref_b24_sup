# 01-270 clean-code/source-layout - Java phase 5w

## Scope
- Extract replace-all passport DB choreography from ObjectPassportService into package-private ObjectPassportReplaceAllCommand.
- Move loading old passport records, collecting stored photo names, null-payload normalization, payload normalization/validation, table clearing, and reinsert orchestration into the command owner.
- Add ObjectPassportPersistence.deleteAllPassportData(Connection) so DELETE SQL remains persistence-owned rather than moving raw SQL into the command.
- Keep ObjectPassportService ownership of transaction boundaries, runtime datasource selection, Connection lifecycle, and photo-storage cleanup after successful commit.
- Update source-layout guards whose consumers legitimately move from ObjectPassportService to command owners.
- Add direct H2 regression coverage for rollback safety, validation rollback, stored-photo cleanup projection, and successful replacement.
- No deployment.

## Non-goals
- No photo file deletion before commit, no schema change, no endpoint change, no tasks implementation, and no deployment.
