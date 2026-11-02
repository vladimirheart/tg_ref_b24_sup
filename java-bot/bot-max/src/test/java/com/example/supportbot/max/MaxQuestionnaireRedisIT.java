package com.example.supportbot.max;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.supportbot.config.BotIngressCoordinationProperties;
import com.example.supportbot.service.BotSessionStoreService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.resource.DefaultClientResources;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Opt-in: the unchanged handler matrix against an explicitly owned, disposable Redis. */
class MaxQuestionnaireRedisIT extends MaxQuestionnaireSelectionTest {
    private static final String OWNER_LABEL="io.iguana.owner";
    private static final String OWNER="01-278-r270-redis";
    private static final String CONTAINER_NAME="/iguana-r270-redis-acceptance";
    private static final String SENTINEL="r270:owner";
    private static final ObjectMapper JSON=org.springframework.http.converter.json.Jackson2ObjectMapperBuilder.json().build();
    private static DefaultClientResources resources;
    private static LettuceConnectionFactory factory;
    private static StringRedisTemplate redis;
    private static String nonce,password,containerId,imageId;
    private static int port;
    private final String namespace="r270-"+UUID.randomUUID();

    @BeforeAll static void connectOwnedRedis() throws Exception {
        nonce=required("QUESTIONNAIRE_REDIS_OWNER");
        password=required("QUESTIONNAIRE_REDIS_PASSWORD");
        containerId=required("QUESTIONNAIRE_REDIS_CONTAINER_ID");
        imageId=required("QUESTIONNAIRE_REDIS_IMAGE_ID");
        port=Integer.parseInt(required("QUESTIONNAIRE_REDIS_PORT"));
        assertThat(nonce).matches("[0-9a-f-]{36}");
        assertThat(containerId).matches("[0-9a-f]{64}");
        assertThat(imageId).matches("sha256:[0-9a-f]{64}");
        assertThat(port).isBetween(1024,65535);
        verifyOwnedContainer(); // No default address, application configuration or reusable live resource.
        resources=DefaultClientResources.builder().ioThreadPoolSize(2).computationThreadPoolSize(2).build();
        reconnect();assertOwnedRedis();
        assertThat(redis.opsForValue().get("r270:acceptance-only")).isEqualTo("true");
    }

    private static String required(String name) {
        String value=System.getenv(name);
        if(value==null || value.isBlank())throw new IllegalStateException("Required isolated Redis setting: "+name);
        return value;
    }
    private static String docker(String... arguments) throws Exception {
        var command=new ArrayList<String>();command.add("docker");command.addAll(List.of(arguments));
        var process=new ProcessBuilder(command).redirectErrorStream(true).start();
        var bytes=java.util.concurrent.CompletableFuture.supplyAsync(()->{
            try {return process.getInputStream().readAllBytes();}
            catch(java.io.IOException exception) {throw new java.io.UncheckedIOException(exception);}
        }); // Drain before waitFor: inspect output can fill the small Windows pipe.
        if(!process.waitFor(30,TimeUnit.SECONDS)) {process.destroyForcibly();throw new IllegalStateException("Docker acceptance operation timed out");}
        String output=new String(bytes.get(5,TimeUnit.SECONDS),StandardCharsets.UTF_8);
        assertThat(process.exitValue()).as("Docker acceptance operation").isZero();return output;
    }
    private static void verifyOwnedContainer() throws Exception {
        JsonNode node=JSON.readTree(docker("inspect",containerId)).get(0);
        assertThat(node.path("Id").asText()).isEqualTo(containerId);
        assertThat(node.path("Name").asText()).isEqualTo(CONTAINER_NAME);
        assertThat(node.path("Image").asText()).isEqualTo(imageId);
        assertThat(node.at("/Config/Labels").path(OWNER_LABEL).asText()).isEqualTo(OWNER);
        assertThat(node.at("/Config/Labels").path("io.iguana.test-nonce").asText()).isEqualTo(nonce);
        assertThat(node.at("/HostConfig/PortBindings").path("6379/tcp").get(0).path("HostIp").asText()).isEqualTo("127.0.0.1");
        assertThat(node.at("/NetworkSettings/Ports").path("6379/tcp").get(0).path("HostPort").asInt()).isEqualTo(port);
        assertThat(node.at("/HostConfig/Memory").asLong()).isEqualTo(64L*1024*1024);
        assertThat(node.at("/State/OOMKilled").asBoolean()).isFalse();
    }
    private static void reconnect() {
        if(factory!=null)factory.destroy();
        var server=new RedisStandaloneConfiguration("127.0.0.1",port);server.setPassword(password);
        var client=LettuceClientConfiguration.builder().clientResources(resources)
            .commandTimeout(Duration.ofSeconds(2)).shutdownTimeout(Duration.ofMillis(100)).build();
        factory=new LettuceConnectionFactory(server,client);factory.afterPropertiesSet();
        if(redis==null)redis=new StringRedisTemplate();
        redis.setConnectionFactory(factory);redis.afterPropertiesSet();
    }
    private static void assertOwnedRedis() {
        assertThat(redis.opsForValue().get(SENTINEL)).as("Disposable Redis ownership sentinel").isEqualTo(nonce);
    }
    @Override protected BotIngressCoordinationProperties coordinationProperties() {
        var result=new BotIngressCoordinationProperties();result.setMode("redis");result.setLeaseNamespace(namespace);return result;
    }
    @Override @SuppressWarnings("unchecked") protected ObjectProvider<StringRedisTemplate> redisProvider() {
        ObjectProvider<StringRedisTemplate> provider=mock(ObjectProvider.class);when(provider.getIfAvailable()).thenReturn(redis);return provider;
    }
    private Set<String> keys(String suffix) {
        assertOwnedRedis();
        var found=new LinkedHashSet<String>();
        try(var cursor=redis.scan(ScanOptions.scanOptions().match(namespace+":"+suffix).count(100).build())) {
            while(cursor.hasNext()) {
                String key=cursor.next();assertThat(key).startsWith(namespace+":");found.add(key);
                assertThat(found.size()).as("Bounded disposable namespace").isLessThan(10000);
            }
        }
        return found;
    }
    @Override protected void discardDeliveryCache() {
        String before=redis.opsForValue().get(sessionKey());
        Set<String> index=redis.opsForSet().members(indexKey());
        Set<String> delivery=keys("bot-webhook-delivery:*");
        assertThat(delivery).isNotEmpty();redis.delete(delivery);
        assertThat(redis.opsForValue().get(sessionKey())).isEqualTo(before);
        assertThat(redis.opsForSet().members(indexKey())).isEqualTo(index);
    }
    @AfterEach void cleanOnlyOwnNamespace() {
        if(redis!=null) {Set<String> own=keys("*");if(!own.isEmpty())redis.delete(own);assertThat(keys("*")).isEmpty();}
    }
    @AfterAll static void disconnect() {
        if(factory!=null)factory.destroy();
        if(resources!=null)resources.shutdown();
    }
    private String sessionKey() {return namespace+":bot-session:max:channel:55:user:1001";}
    private String indexKey() {return namespace+":bot-session-index:max:channel:55";}

    @ParameterizedTest @ValueSource(booleans={false,true})
    void redisServerRestartAndDeliveryCacheLossPreserveFrozenQuestionnaire(boolean general) throws Exception {
        r268Choose(4,general);send("r270-first","One");
        String raw=r268Raw();var scope=state().scope();var selection=state().selection();
        assertThat(redis.opsForValue().get(sessionKey())).isEqualTo(raw);
        assertThat(redis.opsForSet().members(indexKey())).containsExactly("1001");
        assertThat(redis.getExpire(sessionKey(),TimeUnit.SECONDS)).isPositive();
        assertThat(redis.getExpire(indexKey(),TimeUnit.SECONDS)).isPositive();
        Set<String> delivery=keys("bot-webhook-delivery:*");assertThat(delivery).isNotEmpty();
        var markers=new LinkedHashMap<String,String>();for(String key:delivery)markers.put(key,redis.opsForValue().get(key));
        assertOwnedRedis();verifyOwnedContainer();
        factory.destroy();factory=null;
        docker("restart","--time","10",containerId); // Exact owned test container only, never Compose/live Redis.
        reconnect();verifyOwnedContainer();assertOwnedRedis();
        assertThat(redis.opsForValue().get(sessionKey())).isEqualTo(raw);
        for(var marker:markers.entrySet())assertThat(redis.opsForValue().get(marker.getKey())).isEqualTo(marker.getValue());
        assertThat(redis.opsForSet().members(indexKey())).containsExactly("1001");
        assertThat(redis.getExpire(sessionKey(),TimeUnit.SECONDS)).isPositive();
        assertThat(redis.getExpire(indexKey(),TimeUnit.SECONDS)).isPositive();
        var independentStore=new BotSessionStoreService(coordinationProperties(),JSON,redisProvider());
        assertThat(independentStore.loadAll("max",55L,MaxConversationSession.State.class)).hasSize(1);
        assertThat(independentStore.load("max",55L,1001L,MaxConversationSession.State.class).orElseThrow().rawPayload()).isEqualTo(raw);
        assertThat(state().scope()).isEqualTo(scope);assertThat(state().selection()).isEqualTo(selection);
        r268ResetDeliveryCache();
        assertThat(send("r270-first","Changed retry after restart").getBody()).containsEntry("questionnaire_replayed",true);
        assertThat(r268Raw()).isEqualTo(raw);
        send("r270-second","Two");send("r270-last","Three");
        r268AssertCommand(general,Map.of("free-first","One","free-second","Two","free-third","Three"));
        assertThat(state().selection().submissionEventKey()).isEqualTo("update:r270-last");
    }

    @Test void realLuaCasRejectsStaleWriterAndKeepsChannelsUsersAndNamespacesIsolated() {
        var first=new BotSessionStoreService(coordinationProperties(),JSON,redisProvider());
        var second=new BotSessionStoreService(coordinationProperties(),JSON,redisProvider());
        String raw=first.saveIfUnchanged("max",55L,1001L,null,"initial").orElseThrow();
        assertThat(second.saveIfUnchanged("max",55L,1001L,null,"stale-create")).isEmpty();
        String winner=second.saveIfUnchanged("max",55L,1001L,raw,"winner").orElseThrow();
        assertThat(first.saveIfUnchanged("max",55L,1001L,raw,"stale-update")).isEmpty();
        assertThat(first.load("max",55L,1001L,String.class).orElseThrow().rawPayload()).isEqualTo(winner);
        assertThat(first.load("max",56L,1001L,String.class)).isEmpty();
        assertThat(first.load("max",55L,1002L,String.class)).isEmpty();
        var other=coordinationProperties();other.setLeaseNamespace(namespace+"-other");
        var isolated=new BotSessionStoreService(other,JSON,redisProvider());
        assertThat(isolated.load("max",55L,1001L,String.class)).isEmpty();
        assertThat(first.loadAll("max",55L,String.class)).extracting(item->item.payload()).containsExactly("winner");
    }
}
