## Summary
- moved participant chips next to the `Участники` button and switched their avatar rendering to the same stable avatar component used for responsible operators

## Prompt
`аватар участников перенеси не под локацию, а слева от кнопки "Участники". Аватар у участников появился, но он пустой, а реально что в аватаре отображается справа от иконки и в полном размере, чего быть не должно`

## What Changed
- in `spring-panel/src/main/resources/templates/dialogs/index.html`, moved the participants block from the client details column into the header actions area directly before the `Участники` button
- in `spring-panel/src/main/resources/static/js/dialogs.js`, added `buildParticipantAvatarMarkup(...)` and switched participant cards/chips to reuse `dialog-responsible-avatar` instead of a separate participant-avatar implementation
- in `spring-panel/src/main/resources/static/js/dialogs.js`, hide the header participants container when no extra participants are attached to the dialog
- in `spring-panel/src/main/resources/static/css/app.css`, updated the header actions layout for wrapping and added chip sizing rules based on the shared responsible-avatar component

## Verification
- `rg -n "dialog-details-header-participants|dialog-details-participant-pill|dialog-responsible-avatar has-image|buildParticipantAvatarMarkup|dialogDetailsParticipantsSection" spring-panel/src/main/resources/templates/dialogs/index.html spring-panel/src/main/resources/static/js/dialogs.js spring-panel/src/main/resources/static/css/app.css`
- `git diff -- spring-panel/src/main/resources/templates/dialogs/index.html spring-panel/src/main/resources/static/js/dialogs.js spring-panel/src/main/resources/static/css/app.css`

## Why It Matters
- participant avatars now sit exactly where operators expect them in the modal header controls
- reusing the already working responsible-avatar markup removes the broken state where the chip looked empty while the real image spilled outside at full size
