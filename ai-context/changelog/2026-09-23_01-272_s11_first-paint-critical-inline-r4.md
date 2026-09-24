# 01-272 S11 R4 - first-paint critical inline contract

## Context

The S11 R3 source/preview reached the correct final hydrated UI, but manual follow-up showed the first paint still flashed static page help and the full dialogs layout. The earlier acceptance is superseded and reopened.

## Root cause

The R3 guards were compiled into app.css. Several templates keep stable historical app.css URLs, so a browser can reuse cached CSS during the first paint while later JavaScript still normalizes the DOM correctly. This exactly explains why screenshots after load looked correct while the initial frame still jumped.

## Change

- Add a tiny critical first-paint style block to the shared ui-head fragment so it arrives with server-rendered HTML and cannot depend on app.css cache freshness.
- Add a shared root prepaint marker before body parsing; content-disclosure releases it after header/disclosure initialization.
- Keep dynamic data-no-disclosure subtitles visible.
- Reuse the existing synchronous dialogs-prepaint runtime, but make its root class effective from inline critical CSS rather than only from app.css.
- Add source-contract coverage for the inline first-paint contract.

## Boundaries

No backend, API, DB, queue, permission, dialog business, or operational semantics change. No generated CSS is edited by this slice.
