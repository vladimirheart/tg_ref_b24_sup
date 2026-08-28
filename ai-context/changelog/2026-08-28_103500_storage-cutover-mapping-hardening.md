# 01-217 / 01-218 - canonical MinIO mappings and cutover audits

## Промпт пользователя

`зеркалит, чтобы забирать уже существующие медиа. можно конечно перенести файлы, но нужны соответствия. остальное тоже давай делать`

## Что изменено

- Bulk `mc mirror` сохранён как быстрый способ забрать уже существующие legacy media, но теперь он пишет в тот же object-key namespace, который использует runtime: `<APP_STORAGE_OBJECT_KEY_PREFIX>/<domain>/...`.
- `APP_STORAGE_OBJECT_KEY_PREFIX` явно проброшен через production Compose в `panel-web`, `ops-worker` и bot runtime; `.env.example` документирует `APP_STORAGE_OBJECT_KEY_PREFIX=iguana` и переходный `APP_STORAGE_OBJECT_LEGACY_LOCAL_FALLBACK_ENABLED=true`.
- `scripts/docker-production-storage-backfill.ps1` больше не считает наличие local-файла достаточным доказательством миграции. Для каждой строки `chat_attachment_metadata` строится точный canonical S3 key из `storage_key`, при наличии legacy source выполняется точечный copy, после чего обязательный `mc stat`; только S3-verified rows переводятся в `available`.
- Для legacy `java-bot/attachments` используется `legacy_attachment_ref` как дополнительная подсказка при выборе source root.
- Скрипт больше не угадывает Docker network по имени каталога: MinIO client запускается через текущий Compose project/service `minio-init`.
- MinIO credentials передаются в ephemeral client через process/container environment, без текстовой подстановки секретов в shell-script.
- Добавлен `-ValidateOnly`: выполняется только PowerShell parse + `docker compose config -q`, без чтения/изменения runtime data.
- Добавлен read-only `scripts/docker-production-storage-cutover-audit.ps1`: проверяет `chat_attachment_metadata.storage_key -> canonical S3 attachment`, хвост `chat_history` без metadata и `users.photo -> canonical S3 panel avatar`.
- Добавлен отдельный read-only `scripts/docker-production-client-avatar-cutover-audit.ps1`: для клиентов с `client_avatar_history` проверяет runtime keys `<userId>.jpg` / `<userId>_full.jpg`, используемые `AvatarService`.
- Java fallback tests теперь проверяют exact S3 object keys `iguana/attachments/...` и `iguana/avatars/...`, а source-contract test запрещает возврат к network guessing, secret string replacement, destructive cleanup и неверному `$LASTEXITCODE:` синтаксису.

## Дополнительная проверка перед runtime

- При сверке с актуальной документацией MinIO обнаружено, что `mc mirror` поддерживает `--overwrite`, а `mc cp` не имеет такого option. Точечный mapped copy исправлен с `mc cp --overwrite ...` на поддерживаемый `mc cp ...` до первого runtime запуска.
- Local legacy files ни backfill, ни audits не удаляют.
- `APP_STORAGE_OBJECT_LEGACY_LOCAL_FALLBACK_ENABLED` должен оставаться `true`, пока оба read-only cutover audit не пройдут GREEN и пользователь вручную не проверит dialogs/media/avatars.

## Текущее состояние проверки

- Source-contract hardening добавлен.
- GitHub Actions для этих коммитов автоматически не запускались.
- В среде AI нет доступного Windows PowerShell 5.1 / pwsh runtime, поэтому реальный parser/runtime gate должен начаться на операторской Windows машине только с `-ValidateOnly`.
- Rebuild/redeploy, actual canonical backfill, read-only live audits, `fallback=false` и purge на этом шаге ещё не выполнялись.
