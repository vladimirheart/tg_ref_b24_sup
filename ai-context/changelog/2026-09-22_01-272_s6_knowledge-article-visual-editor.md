# 2026-09-22 — task 01-272 — Knowledge Base article visual editor S6

## Пользовательский запрос

- Продолжить системный UI/UX-pass после принятого Knowledge Base compact overview S5.
- Для статьи базы знаний уплотнить quick info и card chrome.
- Убрать raw-Markdown-only editing: редактирование должно быть визуальным/inline из текста.
- Toolbar и основные editor actions должны оставаться доступны при прокрутке длинной статьи.

## Изменения

- article metadata переводится из набора отдельных карточек в compact inline fact strip;
- Notion source metadata уплотняется в одну строку, sync/edit/download/upload actions используют общий icon-only pattern;
- rendered article surface становится reusable visual editing surface через `contenteditable` только после явного входа в edit mode;
- добавлен `markdown-visual-editor.js` с formatting toolbar и Markdown serialization;
- project-specific knowledge blocks (TOC / empty block / callout) сериализуются обратно в source constructs;
- sticky toolbar содержит formatting и save/cancel actions;
- новая статья автоматически стартует в visual edit mode;
- side attachments card sticky только на desktop;
- добавлен source-contract `KnowledgeArticleVisualEditorUiSourceContractTest`.

## Safety

- runtime mutation=false на source apply/validate;
- DB mutation=false;
- queue mutation=false;
- backend/API/schema mutation=false;
- stage=false;
- commit=false;
- push=false;
- reset/clean/broad checkout не используются;
- preview допускает только targeted `panel-web` recreate с rollback tag и non-panel identity guard.

## Manual preview follow-up — R2

- S6 R1 preview: header/edit entry accepted; toolbar stickiness rejected; save round-trip was not yet tested.
- Root cause of non-sticky toolbar is the shared `.ops-section-card { overflow: hidden; }` ancestor contract; R2 overrides overflow only for the Knowledge Base article main card.
- Added current block-type selector and active formatting states so caret/selection context is explicit.
- Added selection bubble toolbar and `/` block menu inspired by the interaction patterns used by Notion (selection formatting + slash commands), without introducing an external editor dependency or a new persisted block schema.
- R2 remains source + targeted panel-web preview only; no stage/commit/push before manual acceptance.

## Existing-article save regression — R3

- Manual R2 acceptance found that `Сохранить` triggered the browser unsaved-changes confirmation and POST `/knowledge-base/articles` returned `INTERNAL_ERROR`.
- Read-only panel-web logs showed PostgreSQL SQLState `42804`: `knowledge_articles.created_at` is `TIMESTAMP WITH TIME ZONE`, while the JPA converter bound a varchar value; the transaction rolled back before notification/redirect, so article changes were not persisted.
- `KnowledgeArticle` now uses native `OffsetDateTime` persistence for `externalUpdatedAt`, `createdAt`, and `updatedAt`; no DB migration is performed.
- Visual editor submit now sets explicit `submitting` state after Markdown serialization, so `beforeunload` is skipped only for an intentional form submit; cancel/navigation protection remains for genuine unsaved changes.
- Regression contract covers both submit-state behavior and native temporal mapping.
- Runtime scope remains panel-web-only for preview; commit/push remain false pending manual save acceptance.

## Manual acceptance — GREEN

- Пользователь подтвердил, что после R3 существующая статья успешно сохраняется и изменение остаётся после повторного открытия.
- Ложное системное предупреждение browser `beforeunload` при штатном `Сохранить` больше не появляется.
- R2 Notion-style editing UX и R3 PostgreSQL temporal-binding fix приняты как единый S6 checkpoint.
- Overall task `01-272` остаётся `🟡`; этот commit фиксирует только принятый Knowledge Base article slice.
