# Shared config runtime storage

Production runtime data must not live in tracked `config/shared` files.

## Contract

- `config/shared` in Git is a seed and test fixture only.
- Production runtime uses `IGUANA_SHARED_CONFIG_DIR`.
- The portable default is `../iguana-runtime/tg_ref_b24_sup/shared-config`.
- The default is outside the checkout on Windows, Linux, and macOS.
- `git pull`, `git checkout`, `git reset --hard`, and `git clean` inside the repository must not mutate runtime shared config.
- Official `docker-production-up.ps1/.sh` launchers copy the repository seed only when the runtime directory is empty.
- `shared-config-check` is read-only and blocks container startup if required runtime files are missing.
- An initialized runtime directory is never overwritten from the repository seed.
- `panel-web`, `ops-worker`, `bot-runner`, any explicitly running legacy static bot services, backup jobs, and legacy recovery overlays must resolve the same `IGUANA_SHARED_CONFIG_DIR`.
- Host backup-policy runners resolve the same external directory.

## Migration

Before switching an existing deployment, copy the complete live shared-config directory, including hidden/runtime control files, to the external directory and verify a SHA-256 manifest. Stop config writers for the final copy, then recreate only services that mount shared config.

Do not restore historical credentials from Git automatically. Re-add or rotate credentials through the application after storage cutover.

## Rollback

Keep the original bind-mounted directory unchanged until post-cutover acceptance. To roll back, restore the previous compose/env configuration and recreate only shared-config consumers.