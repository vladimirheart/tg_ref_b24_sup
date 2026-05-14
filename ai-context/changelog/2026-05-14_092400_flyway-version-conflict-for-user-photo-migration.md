## Summary
- fixed Spring startup failure caused by a duplicate Flyway migration version
- moved the `users.photo` SQLite Java migration off version `20`, which was already occupied by a SQL migration
- revalidated startup path after cleaning stale compiled classes

## Prompt
`Found more than one migration with version 20 ... V20__password_reset_requests.sql ... V20__add_photo_column_to_users`

## What Changed
- deleted the conflicting Java migration `V20__add_photo_column_to_users`
- re-added the same schema change as `V35__add_photo_column_to_users`, which stays after the existing SQLite migration chain
- recorded the startup regression as task `01-089`

## Why It Matters
- Flyway can build a single unambiguous migration graph again
- the app can start while still preserving the `users.photo` schema fix required for avatar persistence
