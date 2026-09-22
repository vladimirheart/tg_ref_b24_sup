# 2026-09-22 — 01-272 Dialogs corrective S2 R3

## Trigger

- Manual preview of S1 accepted sidebar but rejected dialogs list behavior.
- Root cause: dead `dialogCompactToggle` reference stopped dialogs initialization before list-only binding, column state/order application, problem cleanup and SLA refresh.

## Correction

- remove dead compact binding and add explicit regression guard;
- migrate existing browser column preferences once: checkbox/select hidden, actions first;
- broaden problem-prefix cleanup;
- render SLA as status + timing and use `resolvedAt` for closed-ticket overdue determination;
- fix icon-only Open visual and left-edge action menu direction;
- preserve accepted sidebar implementation unchanged.

## Safety

- source-only apply; runtime/db/queue mutation false;
- no stage/commit/push;
- task 01-272 remains YELLOW until manual UI acceptance.
