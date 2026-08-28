# Next-chat handoff and PowerShell rules

Date: 2026-08-28
Related tasks: `01-217`, `01-218`

User prompt:
- "хорошо. подготовь md-файл для дальнейшей работы в новом чате и внеси в него правила создания PS-скриптов"

Changes:
- added a dedicated next-chat handoff at `ai-context/handoffs/2026-08-28_storage-cutover-handoff.md`;
- captured the verified production storage state after successful fallback cutover and manual UI/media verification;
- captured the 20 reviewed known-unrecoverable historical attachments contract and authoritative gate expectations;
- linked the current purge/rollback runbook and explicitly recorded that physical purge has not been executed;
- recorded the separate PostgreSQL boolean SQL defect in `UiPreferenceService` without mixing it into storage cutover;
- added production PowerShell rules based on actual Windows PowerShell 5.1 incidents: parser-first validation, `${var}:` interpolation safety, native exit/output handling, UTF-8 no-BOM, LF-only shell helpers, Docker label discovery instead of unreliable Compose `ps`, JSON `docker inspect`, mandatory `-ValidateOnly`, backup/mutation ordering, targeted recreate, health/env verification, rollback boundaries, and destructive-operation prohibitions;
- added repo workflow reminders for task status, changelog, GitHub-first inspection and no unnecessary Docker rebuild for operator-only script/docs changes.

Files and areas:
- `ai-context/handoffs/2026-08-28_storage-cutover-handoff.md`
- `ai-context/changelog/2026-08-28_164500_next-chat-handoff-and-powershell-rules.md`

Safety:
- documentation only;
- no production containers changed;
- no `.env` changes;
- no PostgreSQL/MinIO/local storage mutation;
- no purge or rollback action executed.

Verification:
- handoff content was assembled from the user-provided successful live cutover outputs, manual UI/media confirmation, current task/runbook files, and the PowerShell/Docker failure modes observed during the production session.
