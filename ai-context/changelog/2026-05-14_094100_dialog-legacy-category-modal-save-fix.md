## Summary
- fixed category saving from the legacy dialog modal
- preserved active dialog state when the categories modal temporarily hides the parent legacy modal
- restored the legacy dialog modal after closing category selection

## Prompt
`в открытой модалке диалога при клике на "Категории", открывается подалка выбора категории и закрывается модалка диалога, но даже после выбора категории, она не сохраняется в диалоге. при этом если выбирать категории после клика на "Закрыть обращение", всё работает корректно`

## What Changed
- replaced the legacy dialog modal `Категории` button bootstrap data-api with an explicit JS trigger
- added `detailsCategoryModalState` in `dialogs.js` to distinguish a real dialog close from a temporary hide caused by the category picker
- skipped the destructive `dialogDetailsModal` reset when the parent modal is hidden only for the child category modal
- after closing the category modal, the legacy dialog modal is shown again with the same active ticket context

## Why It Matters
- category auto-save once again has access to the active dialog ticket id in the legacy modal flow
- operators no longer lose modal context when they only want to tag the dialog with categories
