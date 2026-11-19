# Оформление Iguana: палитры и режимы

В настройках оформления доступны пять палитр: графитовая, хвойная, песочная,
северная и сливовая. У каждой есть светлый, тёмный и системный режим.
Выбор применяется сразу через существующий ThemeRuntime и сохраняется
как пользовательское UI preference, а не как общая настройка организации.
Системный режим реагирует на изменение оформления ОС без перезагрузки.

Графитовая палитра использует прежний ключ `neo`; существующие профили
продолжают работать без миграции. `forest`, `sand`, `north`, `plum` добавлены
в одинаковые allowlist клиента и сервера. Catppuccin и Amber Minimal доступны
в раскрываемом разделе предыдущих палитр и также сохраняются в профиле.

Общие поверхности, sidebar, таблицы, формы, сообщения диалога и настройки
используют общие токены. Критичность, ошибки, предупреждения и успешные
состояния сохраняют одинаковую семантику во всех палитрах. Настроенные
пользователем цвета категорий и бизнесов не переписываются.

## Дашборд

Кнопка «Настроить» открывает существующие элементы управления плашками:
закрепление, скрытие, перетаскивание, возврат отдельных скрытых виджетов,
«Показать все» и «Вернуть по умолчанию». «Готово» завершает редактирование.
Этот режим временный; раскладка сохраняется прежним механизмом
`dashboardPanelLayout`. Смена палитры и режима её не сбрасывает.
Период, рестораны, данные, расчёты и другие вкладки отчётов сохранены.

## Проверки и воспроизведение

SCSS является источником, CSS генерируется repository Maven wrapper.
Все самостоятельные страницы используют версию ресурсов
`20261119-refined-r1`; JS preferences/theme также обновлены до этой версии.
Профильные тесты проверяют round-trip новых и старых палитр, изоляцию
пользователей и сохранение независимой раскладки.

```powershell
cd spring-panel
.\mvnw.cmd '-Diguana.ui.capture=target/refined-rendered' '-Dtest=UiPreferenceServiceTest,NeutralOpsPageFamiliesWebMvcTest,NeutralOpsPublicScreensWebMvcTest,DashboardControllerWebMvcTest,DialogsControllerWebMvcTest,AnalyticsControllerWebMvcTest,ManagementControllerWebMvcTest,KnowledgeBaseControllerWebMvcTest,ClientsControllerWebMvcTest,ObjectPassportEquipmentEditorUiSourceContractTest,ObjectPassportsListUiSourceContractTest,DialogsColumnWidthsUiSourceContractTest' test
```

Для UiPreferenceServiceTest требуется отдельный тестовый PostgreSQL
(по умолчанию disposable Testcontainers). Рабочая база не используется.

Из корня, с установленным Playwright либо `PLAYWRIGHT_MODULE`:

```powershell
node tools/neutral-ops-source-audit.cjs
$env:IGUANA_UI_CAPTURES = Join-Path (Get-Location) 'spring-panel/target/refined-rendered'
node tools/neutral-ops-qa.cjs --inventory
node tools/refined-ui-qa.cjs
```

Браузерная проверка использует настоящие HTML из MVC captures и локальные
ресурсы. Ответы API заменены fixtures; внешняя сеть и рабочие записи
запрещены. Скриншоты и отчёт остаются в `spring-panel/target/refined-ui-qa`.
Это проверка UI, а не live-проверка доставки сообщений и интеграций.

Для установки нужны новый JAR/образ панели и пересоздание `panel-web`.
Схема БД, боты и их runtime этим изменением не затронуты. Перезапуск
контейнера с прежним образом не установит новые ресурсы и Java allowlist.

Локальная проверка 19.11.2026: 84 Java cases в 12 suites, 160 browser cases
на 8 настоящих MVC-экранах, отдельные интерактивные сценарии настроек и
дашборда, Sass/compile/package и source audit 50 самостоятельных страниц
прошли. Это не запуск полного Java suite и не production-приёмка.
