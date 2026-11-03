#!/bin/sh
set -eu
mkdir -p /work/java-bot /tmp/wrapper/.mvn
cd /input
tar --exclude=target --exclude=.mvn -cf - . | tar -xf - -C /work/java-bot
cp /input/mvnw /tmp/wrapper/mvnw
cp -a /wrapper-cache/apache-maven-3.9.6 /tmp/wrapper/.mvn/
sed -i 's/\r$//' /tmp/wrapper/mvnw /tmp/wrapper/.mvn/apache-maven-3.9.6/bin/mvn
chmod u+x /tmp/wrapper/.mvn/apache-maven-3.9.6/bin/mvn
cd /work/java-bot
exec sh /tmp/wrapper/mvnw -B -o -f pom.xml -pl :bot-max -am \
  -Dmaven.repo.local=/maven-cache '-Dtest=Max*Test,MaxSessionLossRedisIT,MaxInitialQuestionnaireMediaRedisIT,PanelActiveTicketAvailabilityTest,PanelTicketFollowupContextTest,SharedQuestionnaireClientTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test
