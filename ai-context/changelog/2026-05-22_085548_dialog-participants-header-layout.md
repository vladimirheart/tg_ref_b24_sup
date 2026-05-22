## Summary
- moved dialog participants from the sidebar into the modal header and normalized participant avatar rendering to icon-sized chips/cards

## Prompt
`когда добавляю нового участника, отображается его аватар в полном размере, а нужен как иконка от его аватара. и сам список участников переведи в шапку модалки диалога, отображая аватарки и имя оператора.
после добавления нового оператора к диалогу пропадает меню "Сводка".`

## What Changed
- in `spring-panel/src/main/resources/templates/dialogs/index.html`, moved the dialog participants display from the left sidebar into the modal header under the client/location block
- in the same template, removed the old sidebar participants section so it no longer competes with or pushes the `Сводка` area
- in `spring-panel/src/main/resources/static/js/dialogs.js`, added compact inline participant chips for the header while keeping the richer participant cards inside the participant management modal
- in `spring-panel/src/main/resources/static/css/app.css`, added fixed-size avatar styles with `object-fit: cover` for participant images and added header chip/card layout styles

## Verification
- `git diff -- spring-panel/src/main/resources/templates/dialogs/index.html spring-panel/src/main/resources/static/js/dialogs.js spring-panel/src/main/resources/static/css/app.css`
- `rg -n "dialogDetailsParticipantsSection|dialogDetailsParticipantsState|dialogDetailsParticipantsList|dialog-details-participant-pill|dialog-details-participant-avatar" spring-panel/src/main/resources/templates/dialogs/index.html spring-panel/src/main/resources/static/js/dialogs.js spring-panel/src/main/resources/static/css/app.css`

## Why It Matters
- participant avatars now stay compact and predictable instead of stretching the UI
- the operator list is visible directly in the header where it is easier to scan during dialog work
- removing the sidebar participants block prevents it from visually displacing the `Сводка` section after adding participants
