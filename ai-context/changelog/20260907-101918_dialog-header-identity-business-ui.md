# 01-255 — компактная идентификационная шапка диалога

- 01-254 подтверждена пользователем и переведена в 🟢.
- Внутренний dialog/ticket ID убран из отображаемой meta-строки.
- Header показывает только `Диалог № <номер>`.
- Локация перенесена в ту же строку сразу после номера; подписи `Диалог` и `Локация` одинаково выделены.
- Avatar и meta-copy выровнены по верхнему краю; нижний padding header уменьшен.
- В центр header добавлен business badge текущего диалога с поддержкой configured business colors.
- Business badge обновляется из fallback row и после details API response.
- Cache-busters app.css/dialogs runtime обновлены до 20260907-1.
- Добавлен source-contract test; generated CSS напрямую не редактируется.
- 01-255 остаётся 🟣 до production rollout и визуального подтверждения.
