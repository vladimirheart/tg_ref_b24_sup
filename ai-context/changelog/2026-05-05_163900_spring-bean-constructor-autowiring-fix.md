# 2026-05-05 16:39:00 - spring bean constructor autowiring fix

## Пользовательский промпт

```text
продолжи
```

## Что сделано

- Исправлен `SlaRoutingPolicyService`: основной конструктор помечен `@Autowired`, чтобы Spring не пытался создать bean через несуществующий no-arg constructor.
- Для серии Spring `@Service`-классов с несколькими публичными конструкторами добавлен явный `@Autowired` на основной конструктор.
- Исправлен аналогичный дефект в `WorkspaceGuardrailWebhookNotifier` и связанных SLA-routing сервисах, чтобы запуск панели не падал каскадом на `No default constructor found`.
- В `ai-context/tasks` добавлена и зафиксирована задача `01-070`.

## Проверка

- `powershell -Command "$env:SPRING_PANEL_SKIP_CLEAN='true'; cmd /c run-windows.bat"`
- `Get-NetTCPConnection -State Listen -LocalPort 8080,8081`

## Итог

- После серии DI-правок `run-windows.bat` больше не падал на ошибках создания этих Spring bean'ов.
- Во время проверки `spring-panel` поднимал listener на `8081`, когда `8080` уже был занят, что подтверждает успешный проход launcher'а и инициализации приложения до рабочего listening state.
- Тестовый runtime на `8081`, поднятый во время проверки, был затем остановлен.
