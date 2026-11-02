package com.example.acceptance;

import static org.assertj.core.api.Assertions.*;
import static com.example.acceptance.FeedbackProcessWorker.Point;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

/** Force-kill a whole delivery JVM, then consume the broker's original unacked redelivery in another JVM. */
class FeedbackProcessRestartTest {
    static FeedbackChainFixture chain;
    @BeforeAll static void setup()throws Exception{chain=new FeedbackChainFixture();}
    @AfterAll static void stop(){if(chain!=null)chain.close();}
    static Stream<Arguments> crashes(){
        return FeedbackDeliveryChainTest.owners().flatMap(owner->Arrays.stream(Point.values()).filter(p->p!=Point.NONE)
            .map(point->Arguments.of(owner.get()[0],owner.get()[1],point)));
    }
    @Test void processRestartSmoke()throws Exception{killedProcessRecoversOnlyCommittedAcceptanceWithoutResend("BUSINESS","telegram",Point.AFTER_RECEIPT);}
    @ParameterizedTest @MethodSource("crashes")
    void killedProcessRecoversOnlyCommittedAcceptanceWithoutResend(String scope,String platform,Point point)throws Exception{
        chain.reset(scope,platform);var event=chain.enqueue();chain.publish();
        String queue="iguana.integration.outbound.feedback-prompt."+platform+".channel."+FeedbackChainFixture.CHANNEL+".bot";
        Map<String,Object> ledger,receipt;
        String payload;long killedPid;
        boolean accepted=point==Point.AFTER_RECEIPT||point==Point.AFTER_DELIVERED;
        int providerCalls=point==Point.AFTER_CLAIM||point==Point.AFTER_PREPARE?0:1;
        try(var child=new Worker(platform,point)){
            child.await("BOUNDARY "+point);child.await("REDELIVERED false");
            payload=child.value("PAYLOAD ");killedPid=child.process.pid();
            assertThat(chain.admin.getQueueInfo(queue).getMessageCount()).isZero();
            assertThat(chain.admin.getQueueInfo(queue).getConsumerCount()).isEqualTo(1);
            ledger=chain.jdbc.queryForMap("SELECT * FROM integration_outbound_event_deliveries WHERE event_id=?",event.eventId());
            var receipts=chain.jdbc.queryForList("SELECT * FROM integration_outbound_delivery_receipts WHERE event_id=?",event.eventId());
            assertThat(receipts).hasSize(point==Point.AFTER_CLAIM?0:1);receipt=receipts.isEmpty()?Map.of():receipts.get(0);
            assertThat(ledger.get("status")).isEqualTo(point==Point.AFTER_CLAIM?"processing":point==Point.AFTER_DELIVERED?"delivered":"sending");
            assertThat(receipt.get("provider_message_id")).isEqualTo(accepted?"r263.provider.1":null);
            assertThat(chain.providerAttempts).hasSize(providerCalls);
            child.kill();assertThat(child.process.exitValue()).isNotZero();
        }
        awaitQueue(queue,1,0);
        var outbox=chain.jdbc.queryForMap("SELECT * FROM integration_transport_outbox WHERE event_id=?",event.eventId());
        // Recovery cannot acquire new admission or create a new provider call, even after revocation.
        chain.jdbc.update("UPDATE channels SET is_active=FALSE");chain.credentials.set(List.of());chain.apiUnavailable.set(true);
        int recipients=chain.recipientCalls.get(),senders=chain.senderCalls.get();
        try(var restarted=new Worker(platform,Point.NONE)){
            assertThat(restarted.process.pid()).isNotEqualTo(killedPid);
            restarted.await("REDELIVERED true");restarted.await("DISPATCHED");
            assertThat(restarted.value("PAYLOAD ")).isEqualTo(payload);
            assertThat(restarted.lines.stream().filter(s->s.equals("R265_DISPATCHED")).count()).isEqualTo(1);
        }
        // No consumers/unacked remain after graceful close: an empty queue proves the redelivery was ACKed.
        awaitQueue(queue,0,0);assertThat(chain.admin.getQueueInfo(queue+".dlq").getMessageCount()).isZero();
        var after=chain.jdbc.queryForMap("SELECT * FROM integration_outbound_event_deliveries WHERE event_id=?",event.eventId());
        if(point==Point.AFTER_RECEIPT){
            assertThat(after.get("status")).isEqualTo("delivered");assertThat(after.get("delivered_at")).isEqualTo(receipt.get("accepted_at"));
            for(String field:List.of("status","updated_at","delivered_at")){ledger.remove(field);after.remove(field);}
        }
        assertThat(after).isEqualTo(ledger);
        var receipts=chain.jdbc.queryForList("SELECT * FROM integration_outbound_delivery_receipts WHERE event_id=?",event.eventId());
        assertThat(receipts).isEqualTo(receipt.isEmpty()?List.of():List.of(receipt));
        assertThat(chain.jdbc.queryForMap("SELECT * FROM integration_transport_outbox WHERE event_id=?",event.eventId())).isEqualTo(outbox);
        assertThat(chain.providerAttempts).hasSize(providerCalls);assertThat(chain.recipientCalls).hasValue(recipients);assertThat(chain.senderCalls).hasValue(senders);
        assertThat(chain.bridgeFailures).isEmpty();assertThat(chain.consumerFailures).isEmpty();
    }
    void awaitQueue(String queue,int messages,int consumers){
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(()->{
            var info=chain.admin.getQueueInfo(queue);assertThat(info).isNotNull();
            assertThat(info.getMessageCount()).isEqualTo(messages);assertThat(info.getConsumerCount()).isEqualTo(consumers);
        });
    }
    static Stream<Arguments> unsafeEndpoints(){
        return Stream.of(Arguments.of(0,"jdbc:postgresql://postgres:5432/iguana"),Arguments.of(0,"jdbc:postgresql://127.0.0.1:5432/r259_chain"),
            Arguments.of(1,"public"),Arguments.of(2,"5672"),Arguments.of(3,"https://example.com:10000"),
            Arguments.of(3,"http://127.0.0.1:10000/provider"),Arguments.of(4,"unknown"),Arguments.of(5,"RESET"));
    }
    @ParameterizedTest @MethodSource("unsafeEndpoints")
    void workerRejectsNonIsolatedInputsBeforeConnecting(int index,String value){
        String[] args={"jdbc:postgresql://127.0.0.1:10001/r259_chain","r263_chain_"+"a".repeat(32),"10002","http://127.0.0.1:10003","telegram","NONE"};
        args[index]=value;assertThatIllegalArgumentException().isThrownBy(()->FeedbackProcessWorker.Config.parse(args));
    }
    static final class Worker implements AutoCloseable {
        final Process process;
        final List<String> lines=new CopyOnWriteArrayList<>();
        Worker(String platform,Point point)throws IOException{
            String executable=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString();
            var builder=new ProcessBuilder(executable,"-Xmx96m","-XX:MaxMetaspaceSize=96m","-XX:ReservedCodeCacheSize=24m","-Xss512k",
                "-Dfile.encoding=UTF-8","-cp",System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")),
                FeedbackProcessWorker.class.getName(),chain.datasource.getJdbcUrl(),chain.datasource.getSchema(),Integer.toString(chain.connections.getPort()),
                chain.api.getBaseUrl(),platform,point.name());
            // A subprocess must never inherit working datasource/provider secrets or Spring runtime configuration.
            Map<String,String> inherited=new HashMap<>(builder.environment());builder.environment().clear();
            for(String key:List.of("SystemRoot","WINDIR","TEMP","TMP"))if(inherited.containsKey(key))builder.environment().put(key,inherited.get(key));
            process=builder.redirectErrorStream(true).start();
            var reader=new Thread(()->{
                try(var input=new BufferedReader(new InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8))){
                    for(String line;(line=input.readLine())!=null;){if(lines.size()<2000)lines.add(line);}
                }catch(IOException ignored){/* close/forced death closes the pipe */}
            },"r265-child-output");reader.setDaemon(true);reader.start();
        }
        void await(String marker){
            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(25)).untilAsserted(()->{
                assertThat(process.isAlive()).withFailMessage("Child exited: %s",lines).isTrue();
                assertThat(lines).withFailMessage("No %s in child output: %s",marker,lines).contains("R265_"+marker);
                assertThat(lines.stream().noneMatch(s->s.startsWith("R265_FAILURE"))).isTrue();
            });
        }
        String value(String prefix){return lines.stream().filter(s->s.startsWith("R265_"+prefix)).findFirst().orElseThrow().substring(5+prefix.length());}
        void kill()throws InterruptedException{
            assertThat(process.isAlive()).isTrue();process.destroyForcibly();assertThat(process.waitFor(10,TimeUnit.SECONDS)).isTrue();
        }
        @Override public void close()throws Exception{
            if(!process.isAlive())return;
            try{process.getOutputStream().write('\n');process.getOutputStream().flush();assertThat(process.waitFor(10,TimeUnit.SECONDS)).isTrue();assertThat(process.exitValue()).isZero();}
            finally{if(process.isAlive()){process.destroyForcibly();process.waitFor(10,TimeUnit.SECONDS);}}
        }
    }
}
