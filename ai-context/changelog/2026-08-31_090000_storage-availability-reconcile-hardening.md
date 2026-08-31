# 2026-08-31 — hardening S3 attachment availability reconcile after quarantine rollback

Связанная задача: `01-217`.

## Контекст production-инцидента

После успешного manifest-driven quarantine 80 legacy dialog files manual UI/media smoke выявил регрессию: исторические вложения в панели отображались как недоступные.

Quarantine был полностью откатан:

- восстановлено `80` exact-manifest files;
- восстановлено `12,703,648` bytes;
- source length/SHA verification прошла без ошибок;
- manifest files remaining in quarantine: `0`;
- PostgreSQL и MinIO rollback не изменял;
- avatar gate и runtime health после rollback были GREEN;
- physical deletion не выполнялся и не авторизован.

Read-only reconciliation после rollback доказал точную metadata boundary:

- `72` metadata rows / `72` unique storage keys;
- reviewed recoverable set: `52` keys;
- reviewed known-unrecoverable set: `20` keys;
- overlap: `0`;
- DB keys outside reviewed union: `0`;
- reviewed keys absent from DB: `0`;
- metadata id drift: `0`;
- все `72` rows на момент диагностики имели `availability_status=missing`.

Canonical MinIO byte audit затем доказал:

- recoverable objects: `52/52` `mc stat` success;
- recoverable objects: `52/52` full `mc cat` success;
- recoverable objects: `52/52` SHA-256 exactly match reviewed local manifest;
- recoverable missing: `0`;
- recoverable read failures: `0`;
- recoverable SHA mismatches: `0`;
- known-unrecoverable absent: `20/20`;
- known-unrecoverable unexpectedly present: `0`.

Финальный V3 audit process вернул exit `2` только после печати GREEN/result counters из-за CRLF в переданном через PowerShell pipeline `exit 0`; этот transport artifact не меняет уже полученные audit results и не является storage failure.

## Найденный failure mode

`ChatAttachmentMetadataAvailabilityService` использовал boolean availability path при reconcile. В S3-контуре boolean existence helper мог свести runtime S3 failure к `false`, после чего reconcile записывал `availability_status='missing'`.

Такое поведение не различало доказанное отсутствие объекта и неопределённый результат проверки object storage.

## Изменение в branch `fix/storage-availability-20260831`

`ChatAttachmentMetadataAvailabilityService` переведён на explicit S3 probe semantics:

- для S3 availability используется `openDialogAttachmentByStorageKey(...)`;
- успешный open => `available`;
- `NoSuchKey` или S3 `404` / `NoSuchKey` error code => `missing`;
- transient/non-404 S3 exception => metadata row остаётся без изменений;
- `IOException` probe failure переводится в runtime failure и также не меняет row;
- DB update выполняется только после определённого availability result;
- local/non-S3 path сохраняет существующую local existence semantics.

Dependency `AttachmentService` удалена из availability reconciler, чтобы reconcile не проходил через exception-swallowing boolean wrapper.

## Regression tests

`ChatAttachmentMetadataAvailabilityServiceTest` обновлён для проверки трёх обязательных outcomes:

1. readable S3 object => row updated to `available`;
2. confirmed `NoSuchKey` => row updated to `missing`;
3. transient S3 `503` => neither `available` nor `missing` update is issued for the row.

Также сохранены existing checks на отсутствие schema mutation и metadata backfill missing rows.

## Recovery / deployment contract

- Не выполнять ручной SQL repair до проверки нового migrator behavior.
- Сначала прогнать `spring-panel` unit tests на branch.
- Затем fast-forward/merge verified fix в `main`.
- Перед production deployment повторно проверить current production HEAD/state и GitHub `main`.
- Собрать новый panel image и запустить fresh `db-migrate` instance с новым кодом.
- Fixed migrator должен естественно восстановить exact status boundary: `52 available + 20 missing`.
- После migrator обязательно запустить `docker-production-storage-cutover-gate.ps1` и client-avatar gate.
- Только после GREEN gates повторить manual UI/media smoke на reviewed recoverable attachments.
- Не повторять quarantine до успешного UI/media smoke на исправленном runtime.
- Quarantine rollback evidence и `.env.storage-cutover-20260828-162458.bak` сохранить.
- `physical_delete_authorized=false`; physical purge не выполнялся и остаётся отдельным будущим решением оператора.

## Статус

🟣 AI implementation completed in isolated branch; ожидается unit-test/build validation и затем controlled production deployment/manual smoke.
