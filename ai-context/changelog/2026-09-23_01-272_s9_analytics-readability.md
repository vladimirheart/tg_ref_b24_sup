# 2026-09-23 — task 01-272 — Analytics readability S9 R1

## Read-only audit

- analytics index combines bot runtime, integration transport and workspace rollout/governance on one long operational surface;
- page-level monitoring navigation and CSV export currently share the same visual weight;
- top-level metric and table chrome is denser than the reports S8 grammar;
- operational warnings, errors and recovery actions must remain visible.

## R1 implementation

- compact page header with a scrollable monitoring route strip;
- separate in-page jump navigation for Runtime ботов / Транспорт / Workspace rollout;
- shared 'ui-icon-control' for CSV export and safe refresh/reset actions;
- shared 'data-content-disclosure-help' for top-level static section explanations;
- flatter metric cards, tighter table rhythm and a denser workspace filter bar;
- top-level section names are normalized for faster scanning while technical terminology is preserved where it carries operational meaning.

## Intentionally unchanged

- backend/API/DB/schema;
- analytics JS data loading and action IDs;
- Replay failed inbound / Requeue failed publish operational controls;
- governance/review form semantics and deep diagnostic content;
- non-panel runtime services.

## Validation / preview boundary

- source apply has no runtime mutation;
- Maven recovery may remove only ignored 'spring-panel/target' before a fresh compile;
- generated CSS keeps only intended 'app.css'; unrelated generated CSS is restored from HEAD file-by-file;
- preview may rebuild/recreate only 'panel-web' with rollback tag and non-panel identity guard;
- no stage/commit/push before manual acceptance.

## R2 — progressive disclosure

- R1 manual review accepted the compact top-level hierarchy and isolated the remaining density problem in Workspace rollout / deep transport diagnostics.
- Transport alerts and global recovery actions stay visible; replay/worker/history tables move under a native details disclosure.
- Workspace keeps six primary KPI cards visible and moves nineteen secondary KPI/readiness cards under a compact details disclosure.
- Rollout weekly/legacy edit controls and SLA/macro policy review forms use native details while preserving every existing element ID and JS binding.
- Operational status/error surfaces, scorecard and decision state remain visible; no backend/API/DB/schema change is introduced.
- Runtime preview remains panel-web-only with rollback and non-panel identity guards.

## Manual acceptance — GREEN

- S9 принят пользователем после R2 panel-web preview.
- Compact header, monitoring route strip и in-page navigation приняты.
- Transport diagnostics/recovery disclosure сохраняет replay, worker, incident и history surfaces; headline alerts и recovery actions остаются видимыми.
- Workspace rollout показывает шесть primary KPI постоянно и девятнадцать secondary KPI/readiness после раскрытия; rollout decision и scorecard остаются открытыми.
- Weekly/legacy/governance review disclosures открываются, существующие controls и save-action bindings сохранены.
- Validation/preview GREEN; этот checkpoint закрывает только S9, overall `01-272` остаётся `🟡`.
