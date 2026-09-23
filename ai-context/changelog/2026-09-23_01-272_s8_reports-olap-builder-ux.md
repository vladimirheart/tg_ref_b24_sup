# 01-272 — S8 Reports OLAP builder UX

## Scope

- baseline: `f3e8ffe66683dd2304fa443de11272d7a37beb64`;
- redesign only the existing `Отчёты → Конструктор отчётов` presentation contract;
- preserve `POST /api/dashboard/olap-preview`, four dimensions and three source toggles;
- no backend/API/DB/schema changes;
- no dashboard runtime extraction in this slice.

## UX changes

- three compact zones: grouping, metrics, build;
- read-only current dashboard-filter context for period/restaurants;
- active metric pills with unselected table columns hidden on the client only;
- explicit loading, empty, validation and error result states;
- result row count and escaped dimension values;
- responsive single-column builder on narrow screens.

## Verification

- new `DashboardOlapBuilderUiSourceContractTest`;
- existing `DashboardControllerWebMvcTest` and `UiInfoDisclosureSourceContractTest`;
- inline dashboard JavaScript syntax check;
- Sass-generated `app.css` scope guard;
- panel-web-only preview with rollback tag and non-panel identity guard.

## R2 visual corrective

Manual R1 review found the dashboard-specific visual language too noisy and low-contrast in dark theme. R2 keeps the existing data/API contract and:

- converts report section labels from chart-colored pills to shared neutral text tokens;
- removes decorative `Live query`, `Realtime review` and the non-metric `Фокус панели` card;
- composes filters + three real KPI into one overview surface;
- flattens manager and OLAP tabs into single surfaces with separators instead of nested card chrome;
- improves the OLAP table with calm header treatment, numeric alignment, tabular numerals and subtle row hierarchy;
- leaves backend/API/DB and inline-dashboard-runtime decomposition out of scope.

## R4 compact controls

Manual preview review after the calm visual pass requested another density step for the Reports dashboard. R4 keeps the existing data/API contract and:

- constrains the visible period field width and lifts the restaurant selector to the top alignment of the overview filters;
- converts report quick period presets into compact shared icon-like controls with `W / M / Q / Y / ∞` labels;
- converts overview `Применить` / `Сбросить` actions into compact icon controls via shared decoration rules;
- removes the heavy KPI numeral weight in `Ключевых метриках смены`;
- restores a small info affordance for the manager and OLAP report titles.

## R7 shared controls and test correction

- fixes the R4 Java source-contract escaping error that blocked testCompile before preview;
- moves icon/shortcut dimensions, focus and hover behavior into shared `app/_ui-kit.scss` controls;
- report pages now decorate actions with shared `ui-icon-control` / `ui-shortcut-control` classes instead of owning a separate visual control set;
- uses Bootstrap Icons for apply/reset and short glyphs for period shortcuts;
- replaces decorative manager/OLAP pseudo-info marks with real accessible info buttons and tooltips;
- preserves the existing dashboard/manager/OLAP data and API contract.

## R8 refinement

- tightens the dashboard overview/KPI density and visually demotes empty heatmap cells;
- fixes the manager month shortcut so legacy `w-100` cannot stretch a shared shortcut control;
- turns manager empty/error output and OLAP initial/loading/validation/empty/error output into the shared inline-state surface;
- reduces duplicated manager/OLAP explanatory copy because the accepted `i` disclosure remains available;
- makes OLAP context and build action more compact while keeping the same request/response contract;
- adds reusable inline-state rules to the shared UI kit instead of creating a reports-only state component.

## R9 functional corrective

- Removed the string converter from `Task.createdAt` and `TaskHistory.at` so the existing OLAP range-count repository methods bind native `OffsetDateTime` values to PostgreSQL `TIMESTAMP WITH TIME ZONE` columns.
- No migration or API change.
- Manager no-data state now renders once: the supervisor summary card is hidden when it has no content.
- Added a PostgreSQL timestamp source contract test plus the manager single-empty-state UI contract.

## Manual acceptance — GREEN

- S8 принят пользователем после R10 panel-web preview.
- Manager report: при пустом результате остаётся один empty-state без дублирования.
- OLAP builder: `Собрать` успешно возвращает таблицу результата на реальном runtime вместо `Не удалось загрузить отчёт`.
- R10 validation и preview GREEN; non-panel containers сохранены без пересоздания.
- Этот checkpoint закрывает только S8; overall `01-272` остаётся `🟡`.
