## Summary
- stopped scroll bleed from the dialog modal into the underlying page at the edges of the history and sidebar panes
- added explicit dialog participants with backend storage, operator picker endpoints, notification routing, and modal UI
- added reassignment of an open dialog to another panel user from the dialog modal

## Prompt
`тогда забирай в работу задачи 102, 103, 104`

## What Changed
- in `dialogs.js`, added wheel/touch scroll containment for `#dialogDetailsHistory` and the dialog sidebar scroll area so edge scrolling no longer leaks to the page behind the modal
- in `app.css`, added `overscroll-behavior: contain` for the dialog history/sidebar and styles for participant cards and the reassignment current-owner block
- in `dialogs/index.html`, added:
  - `Участники` and `Передать` buttons in the dialog modal header
  - a sidebar section with the current participant list
  - a participant management modal with add/remove UI
  - a reassignment modal with the new-responsible picker
- added `DialogParticipantService` plus `DialogOperatorOption` and `DialogParticipantDto` to store manual participants in `ticket_participants`, read available operators from the panel users table, and load participant profiles
- in `DialogReadController` and `DialogReadService`, added read endpoints for `/api/dialogs/operators` and `/api/dialogs/{ticketId}/participants`
- in `DialogQuickActionsController` and `DialogQuickActionService`, added:
  - `POST /api/dialogs/{ticketId}/participants`
  - `DELETE /api/dialogs/{ticketId}/participants/{username}`
  - `POST /api/dialogs/{ticketId}/reassign`
- updated `NotificationService.findDialogRecipients(...)` so manual participants are included in dialog notifications
- updated `takeTicket(...)` so if a participant takes ownership, they are removed from the extra-participant list and remain only as the responsible user
- moved tasks `01-102`, `01-103`, and `01-104` to `🟣` in `ai-context/tasks/task-list.md`

## Verification
- `spring-panel/./mvnw.cmd -q -DskipTests compile`

## Why It Matters
- operators can now safely scroll long dialog histories without accidentally moving the main dialogs page underneath the modal
- supervisors and shift partners can be explicitly attached to a dialog and receive its updates without changing the primary owner
- an active dialog can be handed off in the UI when responsibility needs to move between employees during a shift change
