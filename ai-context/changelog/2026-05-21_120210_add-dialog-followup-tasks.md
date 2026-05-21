## Summary
- added three new dialog backlog tasks for modal scroll isolation, multi-user panel participation, and dialog reassignment between operators

## Prompt
`давай ещё три задачи:
1. при открытой модалке диалога скролл передаётся и на основную страницу, когда долистываешь до верхнего или нижнего края истории диалога
2. добавь возможность добавления к диалогу других пользователей панели
3. сделай возможность переадресовывать диалог с пользователя на пользователя. например когда один сотрудник уходит со смены, появляется возможность переадресовать незакрытый диалог на своего сменщика.`

## What Changed
- added task `01-102` for scroll bleed from dialog modal history into the underlying page
- added task `01-103` for adding other panel users to a dialog
- added task `01-104` for reassigning an open dialog from one panel user to another

## Why It Matters
- the dialog backlog now captures the next UX and workflow issues without losing them in chat history
- each requested item has its own task code, so we can detail and implement them independently
