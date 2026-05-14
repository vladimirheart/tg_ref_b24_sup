## Summary
- fixed inactive settings page state when opening user create/edit modals from `usersModal`
- moved auth-management child modals to `document.body` before Bootstrap modal initialization
- applied the fix centrally so it also covers password, photo preview, and org-members submodals

## Prompt
`открываю любого пользователя на редактирование и страница становится неактивной. такое-же поведение и при попытке добавить нового пользователя`

## What Changed
- added `moveManagedModalToBody(...)` to `auth-management.js`
- before binding Bootstrap modal instances, now move `authUserDetailsModal`, `authUserPasswordModal`, `authUserPhotoPreviewModal`, and `orgMembersModal` under `document.body`
- kept the existing parent-sheet suspension logic intact, but removed the DOM nesting/layering risk for child modals

## Why It Matters
- nested user-management forms no longer risk opening behind the settings sheet or leaving only a blocking overlay visible
- one shared fix covers both editing an existing user and creating a new one
