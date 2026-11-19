# 19.11.2026 17:32 MSK — оформление и палитры Iguana (01-297)

Промты пользователя:

- «нууууу, по выделению да - неплохо. по дашборду, впрочем тоже, но орати внимание, что я могу плашки сами перемещать\\скрывать и возвращать их по дефолту. и linear как-то даже приятнее, нежели у тебя в превью)»
- «уже приятнее. а как с другими цветовыми гаммами\\темами будет выглядеть?»
- «уже интереснее. давай делать»

Baseline HEAD/origin/main: `86b9b4e25f9268e881ab96457175422202a97e08`.
Проверка в detached `.tmp/ui-refresh-20261119`, основной checkout до переноса чистый.

Изменены общие SCSS/CSS, navbar, настройки оформления, dashboard toolbar,
allowlist палитр UiPreferenceService/theme.js/ui-preferences.js и версии
ресурсов 50 самостоятельных страниц. Добавлены пять палитр light/dark/auto;
ключ `neo` теперь соответствует графитовой. Старые Catppuccin/Amber сохранены.
Сохранение в профиле и отдельный dashboardPanelLayout остаются прежними.
Pin/hide/drag/restore/reset доступны в режиме «Настроить»; прежний runtime
раскладки не изменён. Убран лишний визуальный вес navigation, метрик и сообщений.
Дополнено подключение общих стилей ранее пропущенной страницы AI reply rules.

Добавлены профильные round-trip/изоляционные тесты и browser QA;
актуализированы текущие asset versions и неполная knowledge/editor fixture.
Инвентарь теперь корректно различает fragment с body wrapper и страницу с head.
Прежний dialogs critical first-paint явно разрешён audit; новые inline-темы
и runtime stylesheets не добавлялись.

Sass/compile/package GREEN; 84 Java cases/12 suites без failures/errors/skips;
160 browser cases (8 MVC screens × 5 палитр × 2 темы × 2 viewport),
выбор/сохранение/reload/auto, разрешение appearance, native drag, pin/hide/
возврат/show-all/reset, мобильное меню и error/close visibility — GREEN.
Uncaught browser errors = 0. Native test PostgreSQL nonce-owned и остановлен.
Git diff check и точный manifest/guards проверяются при переносе.

Deploy, staging, commit/push, DB migrations и provider I/O не выполнялись.
Ручная приёмка не заявлена, задача 🟣. Подробнее:
`docs/ui/refined-palettes.md`, `ai-context/tasks/task-details/01-297.md`.
