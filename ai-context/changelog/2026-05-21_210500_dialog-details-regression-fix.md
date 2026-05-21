## Summary
- fixed a dialog details modal regression where the details renderer crashed before showing summary, location, problem, and metrics

## Prompt
`в модалке диалога перестали отображаться сводка (пишет: Не удалось загрузить детали: rawResponsible is not defined), локация, проблема, метрики (пишет Метрики недоступны.)`

## What Changed
- in `spring-panel/src/main/resources/static/js/dialogs.js`, fixed the `updateDetailsResponsible(...)` call inside `openDialogDetails(...)` to pass `responsibleRaw` as `rawResponsible`
- this restores the details rendering path so the dialog summary, location, problem block, and metrics no longer fall into the catch handler because of a frontend `ReferenceError`

## Verification
- `rg -n "\\brawResponsible\\b|responsibleRaw" spring-panel/src/main/resources/static/js/dialogs.js`

## Why It Matters
- opening a dialog modal once again renders the details panel instead of failing early and replacing important operator context with fallback error text
