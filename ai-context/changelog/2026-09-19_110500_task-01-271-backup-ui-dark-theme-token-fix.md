# 01-271 — Backup UI dark theme token fix

## Причина

Повторная ручная screenshot-приёмка после compactness pass выявила две dark-theme регрессии: theme-neutral blocks рендерились светлыми/белыми, а subtitle/часть section text получали тёмный Bootstrap color на тёмном modal background.

## Изменения

- backup-specific surfaces/text/borders переведены с palette-dependent Bootstrap `--bs-*` variables на Iguana `--color-*`, `--surface-*`, `--state-*` theme tokens;
- dark override теперь совпадает с реальным Iguana theme contract `[data-theme="dark"]`; Bootstrap selector оставлен как compatibility fallback;
- context line, probe summary и secondary sections в dark theme используют тёмную theme-aware surface вместо light Bootstrap tertiary background;
- header note, probe/manual metadata, path labels и write-probe label используют theme-aware muted text;
- source-contract закрепляет новый selector/tokens и запрещает возврат ключевых Bootstrap palette variables в backup SCSS.

## Safety

UI-only source pass. Backend, runtime JS, destination probe/auth, credentials, host runner, backup/restore и production runtime не изменяются validate/apply оператором.
