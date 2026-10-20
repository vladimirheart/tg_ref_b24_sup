# Обслуживание Docker-хранилища проекта

## Статус и область

Действует для Compose-файлов и Docker maintenance/launcher scripts проекта.

## Политика

- Собственные собираемые образы получают image label `io.iguana.project`,
  соответствующий фактическому Compose project name.
- Автоматическая очистка удаляет только dangling-образы с точной меткой проекта,
  созданные раньше retention. Именованные release/rollback tags сохраняются.
- Контейнеры, volumes, attachments, application logs и данные других проектов
  не входят в эту очистку. Нельзя вызывать общий system/image prune без фильтра.
- Maintenance без явного clean-флага выполняет только чтение.
- Автоочистка вызывается после успешного detached-запуска с build, не чаще
  заданного интервала; состояние и lock привязаны к Docker daemon и проекту.
- Ошибка или занятый lock не должны ломать успешный запуск приложения.
- Все Compose services ограничивают Docker stdout/stderr logs ротацией.
- Общий builder GC относится к настройкам Docker Engine машины. Сжатие VHDX,
  остановка Docker/WSL и общая очистка кэша остаются отдельным обслуживанием.
