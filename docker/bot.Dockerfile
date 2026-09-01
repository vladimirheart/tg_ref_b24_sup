FROM maven:3.9.9-eclipse-temurin-17 AS builder

ARG BOT_MODULE

WORKDIR /workspace

COPY java-bot/ ./java-bot/

WORKDIR /workspace/java-bot

RUN test -n "$BOT_MODULE" \
    && mvn -pl "$BOT_MODULE" -am -DskipTests package \
    && mkdir -p /workspace/out \
    && cp "$BOT_MODULE"/target/"$BOT_MODULE"-*.jar /workspace/out/app.jar

FROM eclipse-temurin:17-jre-jammy

WORKDIR /opt/iguana/bot

COPY --from=builder /workspace/out/app.jar /opt/iguana/bot/app.jar

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /opt/iguana/bot/app.jar"]
