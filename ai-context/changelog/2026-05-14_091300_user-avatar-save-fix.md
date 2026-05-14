## Summary
- fixed saving a user avatar after successful photo upload in auth management
- removed stale `users` schema caching from the auth API update path
- added a SQLite migration to ensure the `users.photo` column exists for older databases

## Prompt
`у пользоавателя загружую картинку на аватар, она загружается, но при попытке сохранить изменения, возвразает: "Нет данных для обновления" и соответственно не сохраняется изменение`

## What Changed
- `AuthManagementApiController` now reloads the current `users` table columns inside create/update/list role-usage flows instead of relying on a constructor-time schema snapshot
- helper methods for optional field insertion and update-column appending now receive the current column set explicitly
- added Flyway Java migration `V20__add_photo_column_to_users` to create `users.photo` when an older SQLite `users` table still lacks it

## Why It Matters
- avatar upload and avatar save now become one consistent flow: the uploaded file URL can be persisted into the user card
- older SQLite environments no longer silently ignore avatar updates just because the schema predates the `photo` field
