## Summary
- made dialog participants open the employee profile and added an active-dialog counter to the `Мои диалоги` section

## Prompt
`то, что нужно. добавь возможность открыть профиль этого сотрудника.
в части "Мои диалоги" добавь счётчик активных диалогов, в которых оператор присутствует`

## What Changed
- in `spring-panel/src/main/resources/static/js/dialogs.js`, added `buildParticipantProfileHref(...)` and made both header participant chips and participant cards in the management modal link to `/users/{profile}`
- in `spring-panel/src/main/resources/static/css/app.css`, added participant-link styling and disabled default underline on participant chips so profile links keep the compact dialog UI appearance
- in `spring-panel/src/main/resources/templates/dialogs/index.html`, added `#dialogMyDialogsCount` to the `Мои диалоги` heading
- in `spring-panel/src/main/resources/static/js/dialogs.js`, updated `renderMyDialogsPanel()` to show the current total of active dialogs displayed in that panel

## Verification
- `rg -n "dialogMyDialogsCount|buildParticipantProfileHref|dialog-details-participant-link|dialog-details-participant-pill" spring-panel/src/main/resources/templates/dialogs/index.html spring-panel/src/main/resources/static/js/dialogs.js spring-panel/src/main/resources/static/css/app.css`
- `git diff -- spring-panel/src/main/resources/templates/dialogs/index.html spring-panel/src/main/resources/static/js/dialogs.js spring-panel/src/main/resources/static/css/app.css`

## Why It Matters
- operators can jump straight from the dialog modal to a colleague profile without searching the users section manually
- the `Мои диалоги` block now immediately shows how many active dialogs are currently attached to the operator in that panel state
