# UI noise reduction and dialog column order

## What changed

- moved the settings page helper summary out of the standalone "Центр конфигурации системы" card into a collapsible `Подробнее` block in the page hero;
- strengthened the reports dashboard restaurant filter with a more explicit selection container, helper copy, and selected-count feedback;
- split Notion integration settings on the knowledge base page into a dedicated settings button plus a separate modal bubble;
- added dialog list column reordering through the existing columns modal, with drag-and-drop order control and persisted local preference;
- softened shared hover feedback so common panels and controls highlight without the annoying lift/jump effect.

## Files touched

- `spring-panel/src/main/resources/templates/settings/index.html`
- `spring-panel/src/main/resources/templates/dashboard/index.html`
- `spring-panel/src/main/resources/templates/knowledge/list.html`
- `spring-panel/src/main/resources/templates/dialogs/index.html`
- `spring-panel/src/main/resources/static/js/dialogs-shell-runtime.js`
- `spring-panel/src/main/resources/static/js/dialogs.js`
- `spring-panel/src/main/resources/scss/app/_dialogs.scss`
- `spring-panel/src/main/resources/scss/app/_knowledge.scss`
- `spring-panel/src/main/resources/scss/app/_unified-ui.scss`
- regenerated CSS bundles in `spring-panel/src/main/resources/static/css/`

## Verification

- ran `.\mvnw.cmd -q process-resources` in `spring-panel` to rebuild SCSS bundles and catch template/resource syntax regressions.
