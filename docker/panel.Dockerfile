FROM maven:3.9.9-eclipse-temurin-17 AS panel-builder

WORKDIR /workspace

COPY spring-panel/pom.xml ./pom.xml
COPY spring-panel/src/ ./src/

RUN mvn -DskipTests package \
    && mkdir -p /workspace/out \
    && cp target/panel-*.jar /workspace/out/app.jar

FROM maven:3.9.9-eclipse-temurin-17 AS bot-builder

WORKDIR /workspace/java-bot

COPY java-bot/ ./

RUN chmod +x mvnw \
    && mvn -f /workspace/java-bot/pom.xml -q -pl bot-telegram,bot-vk,bot-max -am -DskipTests package \
    && mkdir -p /workspace/out/dist \
    && cp bot-telegram/target/bot-telegram-*.jar /workspace/out/dist/bot-telegram-runtime.jar \
    && cp bot-vk/target/bot-vk-*.jar /workspace/out/dist/bot-vk-runtime.jar \
    && cp bot-max/target/bot-max-*.jar /workspace/out/dist/bot-max-runtime.jar

FROM eclipse-temurin:17-jre-jammy

WORKDIR /opt/iguana/panel

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        curl \
        iputils-ping \
        traceroute \
    && command -v ping >/dev/null \
    && command -v traceroute >/dev/null \
    && curl -fsSL http://nuc-cdp.digital.gov.ru/cdp/rootca_ssl_rsa2022.crt -o /tmp/russian-trusted-root-ca.crt \
    && echo "936a43fea6e8e525bcc0f81acd9c3d21b4fc4b9b68acea7906d698005afc6504  /tmp/russian-trusted-root-ca.crt" | sha256sum -c - \
    && keytool -importcert -noprompt -trustcacerts \
        -alias russian-trusted-root-ca \
        -file /tmp/russian-trusted-root-ca.crt \
        -keystore "$JAVA_HOME/lib/security/cacerts" \
        -storepass changeit \
    && rm -f /tmp/russian-trusted-root-ca.crt \
    && rm -rf /var/lib/apt/lists/*

COPY --from=panel-builder /workspace/out/app.jar /opt/iguana/panel/app.jar
COPY java-bot/ /opt/iguana/java-bot/
COPY --from=bot-builder /workspace/out/dist/ /opt/iguana/java-bot/dist/
COPY docker/panel-entrypoint.sh /usr/local/bin/iguana-panel-entrypoint

RUN chmod +x /usr/local/bin/iguana-panel-entrypoint

EXPOSE 8080

ENTRYPOINT ["/usr/local/bin/iguana-panel-entrypoint"]
