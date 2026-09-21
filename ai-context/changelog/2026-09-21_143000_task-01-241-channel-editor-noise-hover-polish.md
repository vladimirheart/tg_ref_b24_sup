# 01-241: channel editor noise and settings hover polish

Дата: 2026-09-21
Задача: 01-241

## Контекст

После GREEN lifecycle acceptance пользователь продолжил визуальную приёмку: внутри настроек конкретного бота осталось много постоянных пояснений, global disclosure создаёт текстовые `Подробнее`, а на основной странице настроек обычные и accent tiles по-разному реагируют на hover.

## Что меняется

- channel editor исключается из generic `Подробнее` через ancestor-aware `data-no-disclosure`;
- статические section/field helper-тексты собираются под компактные `i` рядом с заголовками разделов;
- динамические факты (runtime status, template bindings, support chat state, effective network route) остаются видимыми;
- условные platform/profile helper-тексты остаются рядом со своими условными полями и не выносятся из контекста;
- краткая сводка сжимается до платформы и бота;
- нижняя кнопка `Закрыть` в channel editor удаляется, header close остаётся;
- hover/focus policy страницы Settings унифицируется: обычные и accent tiles, Bootstrap buttons и custom bare buttons получают заметную реакцию без движения.

## Граница изменений

UI/source-contract only. Lifecycle API, `bot-runner` ownership, child process semantics, DB/data and integration contracts не меняются. Финальный lifecycle acceptance по channel 3 остаётся валидным и повторно не запускается.

Task status временно `🟣 -> 🟡` до selective panel-web rollout и ручной визуальной проверки этого follow-up.
