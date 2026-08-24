# 2026-08-24 09:25 - Windows UTF-8 log/console v36

## Причина

После v35 production code и targeted tests стали зелёными, но direct Windows Maven test output показывал русские сообщения как mojibake (`╨...`). Это делает operator/debug логи непригодными для чтения и не может считаться допустимым production состоянием.

## Диагностика

- Java sources и Maven compiler/reporting encoding уже UTF-8;
- panel `logback-spring.xml` и java-bot `logback-spring.xml` уже явно используют `<charset>UTF-8</charset>` для console/file appenders;
- `spring-panel/run-windows.bat` уже выполняет `chcp 65001` и задаёт UTF-8 JVM options;
- direct `spring-panel/mvnw.cmd` / `java-bot/mvnw.cmd` обходили этот bootstrap, поэтому Windows console/Surefire boundary могла интерпретировать UTF-8 bytes как OEM code page.

## Изменения

- оба Windows Maven wrapper переводят console в code page 65001;
- Maven JVM получает UTF-8 file/stdout/stderr options при любом direct wrapper invocation;
- Surefire forked JVM получает те же options через POM;
- добавлены `Utf8RuntimeEncodingTest` в panel и bot-core;
- тест фиксирует default charset, наличие UTF-8 Logback encoder и печатает читаемый кириллический smoke marker;
- production runbook дополнен командами чтения log files через `Get-Content -Encoding UTF8`.

## Проверка

- `spring-panel\\mvnw.cmd -q "-Dtest=Utf8RuntimeEncodingTest,BotRuntimeContractServiceTest,BotProcessServiceTest" test`;
- `java-bot\\mvnw.cmd -q -pl bot-core -am "-Dtest=Utf8RuntimeEncodingTest,ExternalDatabaseSettingsResolverTest,BotDatabaseRuntimeModeTest,DataSourceConfigTest,EngagementTasksTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`;
- в обоих выводах marker `UTF-8 console smoke: Бот готов — журнал читается.` должен отображаться без mojibake;
- `git diff --check`.