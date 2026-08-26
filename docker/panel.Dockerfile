FROM maven:3.9.9-eclipse-temurin-17 AS builder

WORKDIR /workspace

COPY spring-panel/pom.xml ./pom.xml
COPY spring-panel/src/ ./src/

RUN mvn -DskipTests package \
    && mkdir -p /workspace/out \
    && cp target/panel-*.jar /workspace/out/app.jar

FROM eclipse-temurin:17-jre-jammy

WORKDIR /opt/iguana/panel

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=builder /workspace/out/app.jar /opt/iguana/panel/app.jar
COPY java-bot/ /opt/iguana/java-bot/
COPY docker/panel-entrypoint.sh /usr/local/bin/iguana-panel-entrypoint

RUN chmod +x /usr/local/bin/iguana-panel-entrypoint

EXPOSE 8080

ENTRYPOINT ["/usr/local/bin/iguana-panel-entrypoint"]
