# 01-275 S2 MAX client avatars R4

Date: 2026-09-25

## Scope

Restore automatic MAX client avatars without changing Telegram behavior.

## Implementation

- GET /chats/{chatId} resolves dialog_with_user from the official MAX API.
- avatar_url/full_avatar_url are downloaded only from the existing trusted MAX CDN perimeter.
- downloads are bounded to 8 MiB and stored in the canonical avatars domain under runtime user-id filenames.
- refresh is asynchronous on a bounded executor and throttled to one hour after success.
- client_avatar_history receives content-fingerprint/provider evidence without persisting signed CDN URLs.
- no schema migration is introduced.

## Runtime ownership

This slice changes bot runtime code and is intended for the bot-runner image/runtime in the later 01-275 production rollout.
