# 2026-08-24 18:16:00 вЂ” dialog media storage root v51 follow-up

## РџСЂРёС‡РёРЅР°

Browser diagnostic for fresh Telegram photo/voice/video-note/animation returned HTTP 400 `File not found` from `/api/attachments/tickets/by-storage-key` before media decoding. The sampled file existed under `java-bot/attachments`, while the panel `.env` canonical setting was `APP_STORAGE_ATTACHMENTS=../attachments`.

## Р§С‚Рѕ РёР·РјРµРЅРµРЅРѕ

- `spring-panel` default attachment root aligned with `.env.example`: `../attachments`;
- bot-core fallback now respects `APP_STORAGE_ATTACHMENTS` and otherwise uses `../attachments` instead of `attachments`;
- panel bot-runtime contract sends an absolute `APP_STORAGE_ATTACHMENTS` and `SUPPORT_BOT_ATTACHMENTS_DIR` to child bot processes;
- regression coverage added for canonical absolute attachment-root propagation;
- legacy local files are copied from `java-bot/attachments` to the configured canonical root without overwriting or deleting the source.

## РџСЂРѕРІРµСЂРєР° РїРѕСЃР»Рµ РїРµСЂРµР·Р°РїСѓСЃРєР°

- endpoint full GET returns 200 for fresh media;
- `Range: bytes=0-99` returns 206 for audio/video;
- photo/voice/video-note/animation render in the dialog;
- split Send control still switches Enter/Ctrl+Enter and Shift+Enter keeps a newline.

Task `01-190` remains рџџЈ pending manual browser smoke.