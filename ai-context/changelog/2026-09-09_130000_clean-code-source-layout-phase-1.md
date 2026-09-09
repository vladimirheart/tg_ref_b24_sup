# 2026-09-09 — clean-code source layout, phase 1

## User request

> «давай сделаем по проекту. отдельно проанализируй и предложи подобный формат дробления по остальному проекту - проект в целом должен отвечать требованиям чистого кода, чтобы не было горы раздутых файлов»

Контекст обсуждения: дробить не как «каждый use-case = отдельный файл», а по ответственности компонента.

## Phase 1

- зафиксирована общепроектная source-layout policy;
- создана задача 01-260 с картой последующих этапов;
- legacy `app/_passports.scss` превращается в compatibility aggregator;
- его CSS правила механически распределяются по responsibility partials с сохранением исходного cascade order;
- generated `static/css/app.css` должен остаться битово эквивалентным до/после split.

## Не затрагивается

Java/JS/template runtime behavior, API, DB/schema/data, Docker, NetBox и production services.
