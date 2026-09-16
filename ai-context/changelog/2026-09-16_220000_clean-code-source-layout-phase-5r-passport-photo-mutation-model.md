# 01-265 clean-code/source-layout - Java phase 5r

## Scope
- Complete pure photo-mutation rule ownership in ObjectPassportPhotoModel.
- Move update-photo caption/category mutation, delete-photo list filtering, stored-name projection, and photo-not-found semantics out of ObjectPassportService.
- Preserve ObjectPassportService ownership of photo lookup orchestration, transaction boundaries, persistence updates, storage writes/deletes, runtime datasource selection, and Connection lifecycle.
- Preserve existing single-title behavior exactly, including no implicit title promotion after deleting the current title photo.
- Extend ObjectPassportPhotoModelTest with direct update/delete/not-found regression coverage and tighten the Java source-layout contract.
- No deployment.

## Why
- ObjectPassportPhotoModel already owns normalization, category rules, mutable projection, and the single-title invariant.
- Keeping pure list mutation rules in the same owner removes duplicate photo-domain logic from ObjectPassportService without broadening storage or transaction ownership.

## Checks
- updatePhoto delegates pure list mutation to ObjectPassportPhotoModel.
- deletePhoto delegates list removal and stored_name projection to ObjectPassportPhotoModel.
- ResponseStatusException 404 semantics for missing photo IDs remain unchanged.
- Transaction, persistence, storage I/O, runtime datasource, and Connection ownership remain in ObjectPassportService.
- Generated Sass CSS noise is restored after Maven.
- git diff --check passes.

## Non-goals
- No upload storage flow extraction.
- No CRUD, replace-all, NetBox, appeal, equipment, list, persistence, endpoint, schema, scheduler, or deployment changes.
