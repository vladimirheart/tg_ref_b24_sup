# Factual incident cause v34c

Date: 2026-08-25
Task: 01-183

User feedback:
The phrase "Проблемы интеграционного транспорта сохраняются несколько циклов мониторинга подряд" did not explain what was actually wrong.

Changes:
- build incident header cause from real health counters/event payloads;
- show streak + current transport symptoms for sustained pressure;
- show concrete bridge/checkpoint/worker symptoms;
- keep generic summary/description fallback for other incidents.

Verification:
- node --check spring-panel/src/main/resources/static/js/incidents-workbench.js
- spring-panel\mvnw.cmd -q -DskipTests test-compile
- git diff --check
