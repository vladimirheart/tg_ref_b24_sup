# v50 — dialog media preview/player and send mode

Дата: 2026-08-24

## Причина

v48 восстановил transport и PostgreSQL media path, но live smoke показал browser-level проблемы: photo не имел inline preview, Telegram animation `.mp4` возвращала unsupported format/MIME, voice `.ogg` не воспроизводился, а native audio controls визуально не соответствовали dialog UI. Дополнительно способ отправки был жёстко привязан к shortcut.

## Изменения

- attachment HTTP responses получили deterministic MIME mapping для основных image/video/audio форматов;
- ticket attachment endpoints поддерживают `Accept-Ranges: bytes` и single-range `206 Partial Content`;
- добавлен regression test для `.mp4` range и `.ogg` MIME;
- добавлен media enhancement runtime: image/video fallback, autoplay loop для animation и custom audio player;
- добавлен общий browser preference отправки: `Ctrl+Enter` или `Enter`, при `Enter` — `Shift+Enter` для новой строки;
- shortcut preference применяется к legacy modal и workspace composer без изменения transport semantics.

## Не входит

Полный edit ledger/service-event timeline остаётся в 01-189.
