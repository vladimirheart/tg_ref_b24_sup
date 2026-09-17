# Правило: guarded change operator и Windows PowerShell compatibility

## Статус

Действует для operator/patch-скриптов, которые изменяют рабочий checkout.

## Цель

Любое автоматически применяемое изменение сначала доказывает корректность в изолированном состоянии, и только после этого записывается в реальный checkout.

## 1. Обязательная схема operator

Рекомендуемые режимы:

- `--self-test` — проверка самого operator и его transformation invariants без изменения пользовательского checkout;
- `--validate` — проверка baseline/guards и sandbox validation без записи project changes в реальный checkout;
- `--apply` — sandbox validation, затем применение тех же validated changes в реальный checkout.

`--apply` должен выполнять шаги в таком порядке:

1. проверить exact baseline `HEAD` и `origin/main`;
2. проверить clean/allowed working-tree state;
3. проверить pinned content/blob guards для изменяемых файлов;
4. создать detached temporary worktree от baseline;
5. применить transformation только туда;
6. выполнить targeted tests/build/lint/compile/diff checks;
7. проверить exact changed-file set;
8. только после `sandbox_green=true` выполнить ту же deterministic transformation в реальном checkout;
9. повторно проверить changed-file set и `git diff --check`;
10. завершиться без staging/commit/push/deploy.

## 2. Transformation guards должны быть phase-specific

Conflict/idempotence guard должен проверять маркер именно текущей фазы/изменения.

Запрещенный паттерн: copy-paste guard, который ищет marker предыдущей фазы и считает его конфликтом. Уже существующий marker прошлого шага обычно является ожидаемой частью baseline.

Для anchor-based transform:

- anchor должен быть уникальным;
- operator должен проверять ожидаемое число совпадений до замены;
- при 0 или >1 совпадений завершаться RED;
- по возможности использовать минимальный устойчивый semantic anchor, а не огромный indentation-sensitive блок.

## 3. Self-test обязан проверять emitted target code

`node --check` проверяет только JavaScript самого operator и недостаточен.

Если operator генерирует/изменяет другой язык, self-test должен проверять именно итоговый emitted content:

- Java: по возможности `javac`/Maven test-compile или минимальный compile harness;
- PowerShell: parser/runtime smoke на Windows PowerShell совместимом окружении;
- JSON/YAML: parse round-trip;
- regex/string escaping: assertions над фактически сгенерированным файлом, а не только над JS template literal.

Особенно проверяются вложенные кавычки и escape sequences при цепочке `JS -> Java/PowerShell/JSON`.

## 4. Windows PowerShell 5.1: scalar/array semantics

Нельзя предполагать, что результат функции или native command всегда является массивом.

Опасно:

```powershell
(Invoke-GitLines @('rev-parse', 'HEAD'))[0].Trim()
```

При единственной строке PowerShell может scalar-unroll результат, и `[0]` станет первым `System.Char`.

Безопасный паттерн:

```powershell
$headLines = @(Invoke-GitLines @('rev-parse', 'HEAD'))
if ($headLines.Count -ne 1) {
    throw "Expected exactly one HEAD line, got $($headLines.Count)"
}
$head = ([string]$headLines[0]).Trim()
```

Любой code path, который индексирует output pipeline/native command, сначала материализует `@(...)` и валидирует count/type.

## 5. Native stderr не равен failure

Windows PowerShell 5.1 может превращать harmless native stderr (например Git LF/CRLF warning) в `ErrorRecord`.

Для Git/Maven/native utilities:

- решение об успехе принимает exit code (`$LASTEXITCODE`), а не сам факт stderr;
- stderr можно сохранять и показывать, но не превращать автоматически в RED;
- `$ErrorActionPreference` вокруг native command нужно использовать осознанно и восстанавливать после вызова.

## 6. `git status --porcelain` нельзя trim'ить целиком

Leading status columns являются частью протокола Git.

Нельзя делать `.Trim()` над полной строкой `git status --porcelain`, если затем анализируется статус/путь.

Допустимо удалять только перевод строки, который уже отделен shell/API слоем. Разбор выполняется с сохранением первых status columns.

## 7. LF/CRLF и platform-stable metrics

Transformation и comparison должны быть устойчивы к Windows checkout:

- textual match, где это уместно, нормализует CRLF/CR -> LF;
- если считаются source bytes для архитектурного baseline, считать UTF-8 bytes после одинаковой EOL normalization;
- не создавать diff только из-за line-ending conversion.

## 8. Не сравнивать cross-runtime snapshots через locale collation

PowerShell `Sort-Object`, JavaScript `localeCompare()` и другие runtime могут сортировать punctuation/case по-разному.

Для независимой cross-runtime валидации:

- лучше сравнивать map/dictionary, keyed by canonical path, независимо от порядка;
- если порядок является частью формата, обе стороны используют явно одинаковый ordinal/invariant comparator;
- нельзя считать mismatch serialization доказательством metric mismatch, пока не проверено содержимое по ключам.

## 9. Проверять данные, а не только serialization

При сравнении baseline/report snapshots сначала диагностировать отдельно:

- одинаков ли набор ключей/paths;
- одинаковы ли тип/category;
- одинаковы ли numeric metrics;
- отличается ли только ordering/formatting.

Если отличаются только порядок или pretty-print, это defect comparator/canonicalization, а не defect source calculation.

## 10. Maven для `java-bot`

При targeted multi-module проверках использовать repository Maven wrapper и корректный reactor scope, например через `-f pom.xml -pl :<module> -am`, если такой вызов соответствует текущей структуре проекта.

Не подменять targeted test run случайным system Maven, если repo wrapper доступен.

## 11. Versioning после RED

После defect самого operator:

- real checkout до `source_applied=true` не трогать;
- исправленную версию выпускать новым именем (`-v2`, `-v3` и т.п.);
- предыдущие operator files не требовать удалять без необходимости;
- новый status allowlist должен перечислять только конкретные известные имена этих artifacts;
- сохранять baseline, если `main` действительно не изменился; если изменился — сначала fresh verify и новый baseline.

## 12. Truthful RESULT block

Минимальный итог operator должен позволять однозначно понять, что произошло:

```text
phase=...
status=GREEN|RED
mode=...
baseline=...
head=...
origin_main=...
sandbox_green=true|false
source_applied=true|false
staged=false
committed=false
pushed=false
deployment=false
changed_project_files=N
changed_files=...
operator_sha256=...
```

Не выводить `head`/`origin_main`, если они фактически не были прочитаны; не выводить `source_applied=true`, если была только sandbox validation.
