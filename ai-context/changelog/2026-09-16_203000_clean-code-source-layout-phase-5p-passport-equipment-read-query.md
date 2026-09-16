# 01-263 clean-code/source-layout - Java phase 5p

## Scope
- Complete equipment-catalog read ownership inside ObjectPassportEquipmentCatalogQuery.
- Move stored-passport loading and payload collection out of ObjectPassportService while keeping the existing aggregation/deduplication rules in the same query owner.
- Keep runtime datasource selection, Connection lifecycle, the public ObjectPassportService facade, and all transaction ownership unchanged.
- Preserve the public ObjectPassportService constructor signature and SettingsItEquipmentService consumer contract.
- Add direct H2 regression coverage for usage_count, object_count, catalog_ids, archive filtering, and normalized candidate deduplication.
- No deployment.

## Why
- The query already owned equipment normalization and aggregation, but ObjectPassportService still loaded all passport records and assembled an intermediate payload list.
- Giving the query an already-open Connection makes the equipment read path cohesive without moving datasource or transaction ownership.

## Checks
- ObjectPassportEquipmentCatalogQuery receives ObjectPassportPersistence in its package-private constructor.
- listCandidates(Connection) owns persistence.loadAllStoredPassports(connection) and payload collection.
- Existing aggregation semantics remain in the query owner.
- ObjectPassportService only opens the Connection and delegates listEquipmentCatalogCandidates().
- Runtime datasource and Connection ownership stay in ObjectPassportService.
- SettingsItEquipmentServiceTest remains in the targeted selector.
- Generated Sass CSS noise is restored after Maven.
- git diff --check passes.

## Non-goals
- No passport mutations or transaction extraction.
- No photo, NetBox, appeal, payload-model, or list-query behavior changes.
- No endpoint, schema, datasource, scheduler, or deployment changes.
