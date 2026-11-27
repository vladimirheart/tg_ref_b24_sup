# Владельцы модулей настроек и общих стилей

Размер файла — повод проверить обязанности, а не основание для механического дробления.

- `auth-management.js` владеет состоянием, загрузкой, capabilities, событиями и
  внешними точками входа. Фото, профиль/пароль, роли и оргструктура используют
  отдельные `auth-management-{photo,user,roles,organization}.js` с явным host context.
  Не копировать mutable state и не подменять экземпляр через prototype mixins.
  Сохранять identity связанных обработчиков и вызываемые host hooks.
- `bot-settings.js` владеет lifecycle страницы и каноническим состоянием.
  Model, rating и question editor читают его через live getters/setters.
  Не читать контекст в момент создания фабрики: часть значений инициализируется позже.
  Не возвращать перекрывающие объявления функций. Шаблон загружает зависимости
  перед host; fixture/CDP tools должны загружать тот же набор.
- `RmsLicenseMonitoringService` владеет очередью, lifecycle, транзакциями и DTO/API.
  `RmsLicenseClient`, `RmsTextCodec`, `RmsNetworkProbe` — внутренние компоненты
  протокола/текста/команд. Не переносить в них repositories и scheduling.
- Общие правила страниц: `app/_ui-foundation.scss`. Кнопка закрытия:
  `style/_close-button.scss`. Уведомления, controls, resize — отдельные style partials.
  Не добавлять ещё один глобальный override ради локального исключения.
  Сохранять порядок Sass emission; исходник SCSS и собранный CSS изменять вместе.
- Исторические recovery snapshots не возвращать в Git. Проверка/восстановление:
  `docs/architecture/recovery-archive.md`. Рабочие БД не являются архивными артефактами.

При изменении этих границ: Node composition tests, соответствующие browser QA,
targeted Java tests и сравнение ресурсов готового JAR. Для общих стилей использовать
`tools/shared-ui-style-qa.cjs` с pinned `UI_BASELINE_REF`; разрешать только явно
обоснованные изменения вычисленных стилей.
