# Incident card density and readable cause header v34b

Date: 2026-08-25
Task: 01-183

Recovery patch after v34 stopped on a whitespace-sensitive JS anchor.

Changes:
- compact Metadata rows;
- stop right detail-column card stretching;
- readable incident cause in card header;
- Russian human-readable cause for known integration transport/checkpoint incidents;
- safe resume from partially patched template.

Verification:
- node --check spring-panel/src/main/resources/static/js/incidents-workbench.js
- spring-panel\mvnw.cmd -q -DskipTests test-compile
- git diff --check
