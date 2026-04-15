# 2026-04-15 08:54:03 - sheet-паттерн для settings и исправление применения тем

## Что изменено

- Крупные разделы `settings` переведены из тяжёлых центральных modal-окон в
  более спокойный `sheet`-паттерн внутри рабочей области панели.
- Для разделов `Пользователи панели и настройки доступа`, `Настройка диалогов`,
  `Параметры партнёров`, `Подключения IT-блока`, `Структура локаций`,
  `Юридические лица` и `Настройка вводимых данных` добавлены side-sheet модалки
  с разворотом по ширине и без конфликтующего backdrop-поведения.
- Добавлена клиентская логика, которая снимает лишнюю modal-блокировку body для
  sheet-окон и подсвечивает активную плитку настроек.
- Исправлен баг в `theme.js`: палитра больше не сбрасывается принудительно в
  `mono`, а берётся из `localStorage` или по умолчанию из `neo`.
- Глобальные utility-цвета (`text-primary`, subtle backgrounds, spinner state)
  переведены на проектные CSS variables, чтобы смена темы влияла на большее
  число элементов интерфейса.

## Проверка

- `spring-panel\\mvnw.cmd -q -DskipTests compile`

## Затронутые файлы

- `ai-context/tasks/task-list.md`
- `ai-context/tasks/task-details/01-021.md`
- `ai-context/changelog/2026-04-15_085403_settings-sheet-ui-and-theme-fix.md`
- `spring-panel/src/main/resources/templates/settings/index.html`
- `spring-panel/src/main/resources/static/js/theme.js`
- `spring-panel/src/main/resources/static/css/style.css`
