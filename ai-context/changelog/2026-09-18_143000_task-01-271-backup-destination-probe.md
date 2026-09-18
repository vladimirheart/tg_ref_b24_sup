# 01-271 — Backup destination host probe/auth — phase B

## Время

2026-09-18 14:30 +03:00

## Контекст

Phase A уже зафиксировала typed destination model, non-secret credential_ref boundary и read-only probe contract. Phase B добавляет request/status execution через существующий host policy runner без Docker socket в panel-web.

## Что реализуется

- отдельная shared-config очередь `backup-destination-probe-*`;
- GET/POST `/api/settings/backup/probe`;
- read-only probe по умолчанию и явный `write_test` opt-in;
- Local/Mounted path -> path/read/free_space;
- Windows SMB -> DNS/TCP 445 -> credential resolution/current identity -> share/path/read/free_space;
- Unix SMB -> `smbclient`, Kerberos current identity либо transient 0600 auth file из host env credential-ref;
- host-managed credential convention `IGUANA_BACKUP_CREDENTIAL_<REF>_{USERNAME,PASSWORD,DOMAIN}`;
- password/private key/access key не попадают в request/status/API/logs;
- `destination_signature` связывает probe evidence с конкретной нормализованной destination;
- новое DR acknowledgement backend принимает только после успешного probe той же destination; legacy acknowledgement сохраняется только для неизменённой destination;
- UI показывает probe status/error/free-space и разрешает write/delete только отдельным switch.

## Validate safety

Validate-оператор не запускает probe, SMB, backup/restore, credential mutation, deploy/restart. Выполняются только source patch в sandbox, syntax checks и targeted Maven tests.
