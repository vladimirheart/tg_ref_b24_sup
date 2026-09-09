# Source layout policy

## Основной принцип

Файл делится по устойчивой ответственности, а не по каждому отдельному use-case и не по формальному числу строк. Один модуль должен иметь одну понятную причину изменения и минимальный публичный контракт.

## Entry points and facades

- `app.scss`, `settings.scss`, `sidebar.scss`, `style.scss` — только композиция SCSS-модулей.
- Крупный legacy partial при миграции сначала становится compatibility aggregator, который подключает новые partials в прежнем cascade order.
- Текущие browser JS entrypoint/runtime filenames сохраняются как facades, пока шаблоны и глобальные `window.*` contracts не переведены безопасно.
- Thymeleaf page-файл должен преимущественно собирать fragments, а не содержать весь feature.
- Java package migration выполняется feature-by-feature, без массового package move.

## Dependency direction

1. shared/core не зависит от feature;
2. feature может зависеть от shared/core;
3. один feature не импортирует внутренние файлы другого feature — только его явный public facade/contract;
4. circular dependencies запрещены;
5. generated assets не являются источником истины.

## Review thresholds

Это триггеры архитектурного review, а не жёсткое правило «разрезать любой ценой»:

| Source | Target | Responsibility review |
| --- | ---: | ---: |
| SCSS partial | <= 15 KB | > 20 KB |
| Browser JS module | <= 25 KB | > 40 KB |
| Thymeleaf page/fragment | <= 30 KB | > 30 KB / ~500 lines |
| Java class | ~150–400 lines | > ~500 lines |
| Operational script | <= 20 KB where practical | > 20 KB |

Исключения: generated CSS, vendored assets, migrations, fixtures, generated code, исторические документы и safety-critical workflow, где разбиение ухудшит атомарность/проверяемость.

## Target frontend layout

```text
scss/
  app.scss
  app/
    dialogs/
    passports/
    dashboard/
    analytics/
    operations/
  settings/
    shell/
    equipment/
    dialogs/
    channels/
    locations/
    partners/
    network/
  sidebar/
    shell/
    navigation/
    account/
    actions/
    notifications/
  style/
    tokens/
    base/
    typography/
    forms/
    theme/

static/js/
  core/
  dialogs/
  settings/
    equipment/
    dialogs/
    channels/
    locations/
    partners/
    network/
  analytics/
  auth/
  incidents/
  monitoring/
  sidebar/
```

## Target template layout

```text
templates/
  <feature>/
    index-or-page.html
    fragments/
      <independently-changing-ui-unit>.html
```

В fragment выносится самостоятельно меняющийся modal/form/table/workspace section. Не создаются fragments для каждого контейнера или нескольких строк разметки.

## Target Java layout

```text
com.example.panel/
  shared/
    web/
    security/
    persistence/
    observability/
    storage/
  dialogs/
    api/
    application/
    domain/
    infrastructure/
  settings/
    equipment/
    channels/
    locations/
    partners/
    network/
  monitoring/
    rms/
    iiko/
    provider/
    certificate/
    backup/
  passports/
  analytics/
  auth/
  incidents/
  knowledge/
```

`application` оркестрирует use-cases, `domain` хранит правила/модели без transport-specific деталей, `infrastructure` содержит DB/API/storage adapters, `api — HTTP contracts. Уже маленькие связные сервисы не дробятся ради соответствия схеме.

## Migration rules

- один structural refactor = отдельный commit от functional change;
- публичные DOM/data-attributes/API/window contracts сохраняются до отдельного migration step;
- для SCSS сначала проверяется эквивалентность generated CSS;
- для JS сохраняется compatibility facade и добавляются targeted source/runtime tests;
- для Java сначала выделяются seams/ports, затем переносятся пакеты;
- production scripts рефакторятся последними, с сохранением validate/apply, rollback и guard semantics;
- новый код не добавляется в legacy god-file, если ответственность уже имеет выделенный module.
