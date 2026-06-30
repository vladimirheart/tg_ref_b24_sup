# 2026-06-30 10:06:01 - settings bootstrap bundles

## Промпты пользователя

- `давай следующий более широкий шаг по задаче`

## Что изменено

- `spring-panel/src/main/resources/static/js/settings-page-config-runtime.js`
  расширен до полноценного bundled config builder: помимо flat compatibility
  aliases он теперь собирает section objects `dialog`, `admin`, `channels`,
  `parameters`, `appearance`, `locations`;
- `spring-panel/src/main/resources/static/js/settings-page-bootstrap-runtime.js`
  переведён на эти section bundles: верхний bootstrap runtime теперь читает
  subdomain config в первую очередь из `dialog/admin/channels/parameters/
  appearance/locations`, а старые top-level `options.*` оставляет только как
  transition fallback;
- заодно bundled `parameters`/`appearance`/`locations`/`channels`/`admin`
  wiring дотянуто до mount contracts leaf shell/runtime слоёв, так что
  page-level bootstrap contract стал ближе к bounded subdomains, а не к
  одному длинному плоскому options-объекту;
- `ai-context/tasks/task-details/01-129.md` обновлён: зафиксировано, что
  bundled subdomain contract уже дошёл и до bootstrap-уровня, а remaining
  scope ещё сильнее сместился к данным и точечным compatibility-хвостам.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-page-config-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-page-bootstrap-runtime.js`
- `git diff --check -- spring-panel/src/main/resources/static/js/settings-page-config-runtime.js spring-panel/src/main/resources/static/js/settings-page-bootstrap-runtime.js ai-context/tasks/task-details/01-129.md ai-context/changelog/2026-06-30_100601_settings-bootstrap-bundles.md`
