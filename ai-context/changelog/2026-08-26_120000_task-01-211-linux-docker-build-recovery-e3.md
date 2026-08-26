# 01-211 Phase E3 — Linux Docker build recovery

Date: 2026-08-26 12:00 +03:00
Task: 01-211

## Smoke failure

The real Docker role/scale smoke passed infrastructure image resolution and entered the panel image build, then failed at:

```text
RUN chmod +x mvnw && ./mvnw -DskipTests package
/bin/sh: 1: ./mvnw: not found
```

The repository contains `spring-panel/mvnw`. Since `chmod` succeeds immediately before execution, the failure is consistent with a Windows checkout line-ending/shebang issue rather than a missing COPY source.

The same build sent roughly 1.06 GB of build context.

## Recovery

- Use `mvn` from the official Maven builder image.
- Copy only `spring-panel/pom.xml` and `spring-panel/src/` into the builder.
- Exclude Maven wrapper/cache, db-backup, recovery and temp panel artifacts from Docker context.
- Extend the Docker topology source-contract.
- Run an explicit panel image build probe before repeating the role/scale smoke.

## Status

Task remains `🟡` until the real Docker role/scale smoke is green.
