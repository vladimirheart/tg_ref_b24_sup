# 2026-09-22 — task 01-272 — Knowledge Base compact overview S5

## Пользовательский запрос

- Продолжить 01-272 с Knowledge Base после принятого checkpoint S2-S4.
- На общем экране базы знаний убрать статическое пояснение интеграции Notion за компактный info affordance.
- Уплотнить quick info по состоянию/источнику.
- Быстрые действия, включая проверку подключения, перевести в иконки и разместить рядом с настройками интеграции.
- Не создавать отдельный one-off UI pattern.

## Реализация

- В shared 'content-disclosure.js' добавлен явный reusable opt-in 'data-content-disclosure-help' и поддержка context-specific 'data-disclosure-label'.
- Knowledge Base использует этот shared pattern для пояснения Notion вместо постоянно видимого текста.
- Проверка подключения, preview импорта и обновление изменённых перенесены в header integration controls и стали icon-only с доступными 'title' / 'aria-label'.
- Status cards сведены в компактный unified strip; operational meta остаётся видимым, но длинные значения не раздувают layout.
- Добавлен source contract для S5.

## Safety

- runtime mutation=false;
- DB mutation=false;
- queue mutation=false;
- stage=false;
- commit=false;
- push=false;
- reset/clean/checkout не используются.
