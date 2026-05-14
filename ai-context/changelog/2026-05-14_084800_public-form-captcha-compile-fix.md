## Summary
- fixed `spring-panel` compilation after `PublicFormService` refactoring left one stale `readDialogConfig()` call
- aligned shared-secret CAPTCHA lookup with the extracted runtime config helper
- revalidated module compilation after the targeted patch

## Prompt
`\\tg_ref_b24_sup\\spring-panel> .\\run-windows.bat ... [ERROR] PublicFormService.java:[682,33] cannot find symbol method readDialogConfig()`

## What Changed
- replaced the stale `readDialogConfig().get("public_form_captcha_shared_secret")` access in `PublicFormService` with `readDialogConfigString("public_form_captcha_shared_secret", "")`
- kept CAPTCHA shared-secret behavior on the same runtime dialog-config source, but through the current helper API
- added task record `01-086` for this compile regression fix

## Why It Matters
- `spring-panel` can compile again after the `PublicFormRuntimeConfigService` extraction
- the fix is intentionally narrow, so it restores startup without reopening unrelated `PublicFormService` refactoring work
