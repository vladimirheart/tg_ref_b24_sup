# Windows launch guide for the Spring panel

This guide describes how to run the Spring Boot replacement for the support panel on Windows 10/11 without relying on WSL.

## 1. Install prerequisites
1. Download and install [Microsoft Build of OpenJDK 17](https://learn.microsoft.com/java/openjdk/download#openjdk-17). During installation enable the option to set `JAVA_HOME`.
2. Install Apache Maven 3.9+ from the [official binaries](https://maven.apache.org/download.cgi) and add the `bin` directory to the `PATH`. Restart the terminal so the new PATH is applied.

> ℹ️  If you already use a JDK manager (SDKMAN!, IntelliJ, etc.) you only need to ensure that `java -version` prints 17.x and `mvn -v` reports Maven 3.9 or newer.

## 2. Clone the repository
```powershell
PS> git clone https://example.com/tg_ref_b24_sup.git
PS> cd tg_ref_b24_sup\spring-panel
```

## 3. Start the application
The repository ships with a helper script that works in *cmd.exe* and PowerShell:
```powershell
PS> .\run-windows.bat
```

The script will prefer the Maven Wrapper (`mvnw.cmd`) if you add it later; otherwise it calls the globally installed Maven and runs `spring-boot:run`.

After the dependencies download the panel becomes available at <http://localhost:8080/>. The default admin credentials are `admin` / `admin`.

## 4. Storage directories on NTFS
Uploaded files are stored under the working directory:

```
attachments
├── knowledge_base
└── avatars
```

If you prefer a different location on Windows (for example `D:\PanelData`), update `application.yml`:

```yaml
app:
  storage:
    attachments: D:/PanelData
    knowledge-base: D:/PanelData/knowledge_base
    avatars: D:/PanelData/avatars
```

Backslashes must be escaped when defined via environment variables or JVM system properties (e.g. `-Dapp.storage.attachments=C:\\PanelData`).

## 5. Useful commands
- Rebuild JAR: `mvn package`
- Run from the compiled JAR: `java -jar target/panel-0.0.1-SNAPSHOT.jar`
- Open the in-memory H2 console: browse to <http://localhost:8080/h2-console> and use `sa`/`sa`.

The application has been verified to create and normalize directories via `java.nio.file.Path`, so the same code paths work on both Windows and Linux.
