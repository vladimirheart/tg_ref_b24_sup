# 2026-09-22 — task 01-272 — UI info disclosure continuation R3.3

## Причина continuation

- R1 записал первые три runtime JS и остановился до SCSS на CRLF-sensitive end marker.
- Read-only diagnostic подтвердил exact dirty scope: только `content-disclosure.js`, `passport-detail-equipment-runtime.js`, `settings-partner-contacts-runtime.js`; staged/untracked отсутствовали.
- `content-disclosure.js` имел скрытый DOM defect: helper переносился в panel до попытки вставить wrapper через `element.parentNode`, а `element.hidden = true` дополнительно оставлял body скрытым после открытия panel.
- R2 заблокировался до apply на known-state validation и не внёс дополнительных изменений.
- R3.2 подтвердил repository Maven wrapper/bundled Maven, но заблокировался до `PLAN_GUARD`/первой записи: shared helper вставлялся раньше удаления legacy header interaction, из-за чего `let lockedOpen = false;` встречался дважды.

## R3.3

- fingerprint продолжения привязан к фактическим normalized SHA-256 трёх R1 partial files, а не к реконструированному LF/CRLF snapshot;
- generic helper disclosure переведён на общий `page-header-info` affordance и общий open/close binding;
- текстовые preview/`Подробнее`/`Скрыть` удалены из generic disclosure;
- equipment и partner contact используют compact info icon;
- SCSS обновлён без LF-only multiline matching;
- добавлен runtime source contract против возврата текстового «Подробнее»;
- generated CSS после Maven нормализуется к HEAD и получает только минимальный counterpart этого slice;
- создана задача `01-272` со статусом `🟡`.
- Windows Maven boundary использует repository `spring-panel/mvnw.cmd` через `cmd.exe`/ComSpec; R3.1 подтвердил, что глобального `mvn.cmd` нет в PATH. Bundled Maven обязателен, поэтому wrapper не должен ничего скачивать.

## Safety

- runtime mutation=false;
- DB mutation=false;
- queue mutation=false;
- stage=false;
- commit=false;
- push=false;
- reset/clean/checkout не используются.
