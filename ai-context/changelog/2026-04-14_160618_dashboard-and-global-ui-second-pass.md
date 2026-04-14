# 2026-04-14 16:06:18 - второй проход по dashboard и глобальному UI

## Что изменено

- Из `sidebar` убран обзорный блок `Workspace / Support control center`, чтобы
  навигация стала компактнее и чище.
- На `dashboard` все графики переведены на единый визуальный конфиг:
  проектная палитра,
  общие grid/ticks/tooltip,
  единый стиль bar/pie chart-рендера.
- На `dashboard` добавлен новый график `Нагрузка по сотрудникам` на базе
  `staff_time_stats`, чтобы страница лучше раскрывала распределение работы по
  команде.
- Убраны шумные pop-up уведомления `Загрузка данных...` и `Данные успешно загружены`
  при обычной загрузке дашборда; error-уведомления сохранены.
- В секции `Соберите нужный срез` уменьшены и смягчены кнопки `Применить` и
  `Сбросить`.
- Через `style.css` расширено влияние нового визуального языка на остальной
  проект: обновлены базовые кнопки, outline-кнопки, поля ввода, вкладки и
  таблицы.

## Затронутые файлы

- `ai-context/changelog/2026-04-14_160618_dashboard-and-global-ui-second-pass.md`
- `spring-panel/src/main/resources/static/css/style.css`
- `spring-panel/src/main/resources/templates/dashboard/index.html`
- `spring-panel/src/main/resources/templates/fragments/navbar.html`
