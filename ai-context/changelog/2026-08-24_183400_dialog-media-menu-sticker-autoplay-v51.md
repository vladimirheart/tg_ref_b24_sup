# Dialog media UX follow-up: compact metadata menu and sticker autoplay

Date: 2026-08-24 18:34
Task: 01-190
Status: AI code complete, manual browser smoke pending.

## Changes

- Moved attachment filename, type/size and download/open action out of the permanent message layout into a compact `в‹Ї` dropdown.
- Reused the existing positioned `chat-media-info` surface so media cards stay compact.
- Added explicit video-sticker class and resilient muted inline autoplay on media readiness events.
- Preserved autoplay after browser Blob fallback replaces a failed direct source.
- Explicitly starts TGS/Lottie animation after creation and `DOMLoaded`.
- Static WebP/PNG stickers remain static by design.

## Runtime evidence before this change

The canonical attachment endpoint returned HTTP 200 with `Accept-Ranges: bytes` after storage-root recovery, so this follow-up is intentionally limited to browser UX/playback rather than ingestion or storage lookup.

## Manual smoke required

1. Photo: only preview + `в‹Ї`; filename/type/download appear inside the menu.
2. Voice/audio: player remains compact; file metadata lives only in `в‹Ї`.
3. Video note: does not unexpectedly autoplay.
4. WebM/video sticker: autoplays muted, loops, and resumes after dynamic history render.
5. TGS sticker: animates automatically and loops.
6. Static WebP/PNG sticker: renders normally without fake animation.
7. Broken source: existing browser fallback still renders a useful error/download path.