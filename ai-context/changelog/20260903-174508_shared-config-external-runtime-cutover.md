# Shared config external runtime cutover

- Replaced the production shared-config default bind from tracked `config/shared` to `../iguana-runtime/tg_ref_b24_sup/shared-config`.
- Added host-side copy-on-empty initialization in both production launchers and a read-only `shared-config-check` compose gate.
- Kept backup and legacy recovery overlays on the same runtime directory.
- Updated host backup-policy defaults to resolve the external runtime directory.
- Added a source-contract regression test preventing the tracked production bind from returning.
- Preserves any currently running legacy static bot service during targeted cutover instead of requiring manual removal.
- No historical iiko credentials are restored automatically.