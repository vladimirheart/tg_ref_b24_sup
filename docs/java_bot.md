# Java bot build and run

The Java bots are packaged as a multi-module Maven project under `java-bot`. The Maven Wrapper scripts must be run with an explicit goal (for example `package`, `test`, or `spring-boot:run`). Running `mvnw.cmd` without a goal will fail with `No goals have been specified for this build`.

## Build the full project

```powershell
PS> cd java-bot
PS> .\mvnw.cmd package
```

Linux/macOS:

```bash
cd java-bot
./mvnw package
```

## Build a single bot module

```powershell
PS> cd java-bot
PS> .\mvnw.cmd -pl bot-telegram -am package
```

`-pl` selects the module, and `-am` builds required dependencies from `bot-core`.

## Run tests

```powershell
PS> cd java-bot
PS> .\mvnw.cmd test
```

## Run a bot from the shaded JAR

After packaging, run the generated JAR (example for Telegram):

```powershell
PS> java -jar bot-telegram/target/bot-telegram-0.0.1-SNAPSHOT.jar
```

> ℹ️  The bots expect the same `.env` and `config/shared` setup described in [docs/environment_variables.md](environment_variables.md).
