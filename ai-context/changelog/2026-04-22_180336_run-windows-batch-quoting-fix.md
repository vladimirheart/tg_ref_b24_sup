# 2026-04-22 18:03:36 - run-windows batch quoting fix

## Что изменено

- В `spring-panel/run-windows.bat` убран хрупкий механизм передачи UTF-8 JVM-аргументов
  через `spring-boot.run.jvmArguments` с вложенными кавычками.
- Переключён запуск панели на передачу UTF-8 параметров через `JAVA_TOOL_OPTIONS`.
- Обновлён вызов Maven в batch-скрипте без проблемного `EXTRA_JVM_ARG`.

## Почему

- После предыдущего изменения `run-windows.bat` падал на Windows с ошибкой
  `=UTF-8 was unexpected at this time.`
- Причина была в том, что `cmd.exe` не экранирует вложенные кавычки обратным
  слешем внутри `set "VAR=..."`.

## Затронутые файлы

- `c:/Users/SinicinVV/git_h/tg_ref_b24_sup/spring-panel/run-windows.bat`

## Проверка

- `c:/Users/SinicinVV/git_h/tg_ref_b24_sup/spring-panel/mvnw.cmd -q -DskipTests compile`
- тестовый запуск `run-windows.bat` дошёл до старта Maven и приложения;
  во временном stderr подтверждено:
  `Picked up JAVA_TOOL_OPTIONS: -Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8`
