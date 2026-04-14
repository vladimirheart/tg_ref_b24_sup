# 2026-04-14 16:38:51 - цветовая читаемость графиков и polishing settings/modals

## Что изменено

- На `dashboard` убрана логика приглушения bar-chart серым по клику, из-за
  которой графики становились хуже читаемыми.
- Палитра графиков расширена и переведена на более насыщённые проектные цвета
  без серых значений внутри самих графиков; каналам и другим bar-chart
  назначены полноценные multi-color palette.
- Подписи и tooltip bar-chart теперь считают проценты от полного видимого
  набора данных без состояния "полуприглушённого" элемента.
- В `settings` усилен hero-блок, обзорная карточка и плитки модулей:
  более мягкие поверхности, живые тени и аккуратная hover-иерархия.
- Для модальных окон в `settings` и базовом UI добавлены более аккуратные
  радиусы, surface/background, header/footer, кнопка закрытия и плавная
  анимация появления, чтобы формы ощущались легче и чище.

## Затронутые файлы

- `ai-context/changelog/2026-04-14_163851_dashboard-chart-colors-and-settings-modals-pass.md`
- `ai-context/tasks/task-list.md`
- `spring-panel/src/main/resources/static/css/style.css`
- `spring-panel/src/main/resources/templates/dashboard/index.html`
- `spring-panel/src/main/resources/templates/settings/index.html`
