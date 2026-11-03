package com.example.supportbot.max;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.supportbot.config.BotIngressCoordinationProperties;
import io.lettuce.core.resource.DefaultClientResources;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.*;
import org.springframework.data.redis.connection.lettuce.*;
import org.springframework.data.redis.core.*;

/** Opt-in only through the existing owned R282 runner; no live/default Redis address. */
class MaxInitialQuestionnaireMediaRedisIT extends MaxInitialQuestionnaireMediaTest {
    static DefaultClientResources resources;static LettuceConnectionFactory factory;static StringRedisTemplate redis;static String nonce;
    final String namespace="r284-media-"+UUID.randomUUID();
    static String required(String key){var value=System.getenv(key);if(value==null||value.isBlank())throw new IllegalStateException("Disposable Redis setting required: "+key);return value;}
    @BeforeAll static void connect(){
        nonce=required("QUESTIONNAIRE_REDIS_OWNER");assertThat(nonce).matches("[0-9a-f-]{36}");
        assertThat(required("QUESTIONNAIRE_REDIS_HOST")).isEqualTo("r282-redis");assertThat(required("QUESTIONNAIRE_REDIS_PORT")).isEqualTo("6379");
        var server=new RedisStandaloneConfiguration("r282-redis",6379);server.setPassword(required("QUESTIONNAIRE_REDIS_PASSWORD"));
        resources=DefaultClientResources.builder().ioThreadPoolSize(2).computationThreadPoolSize(2).build();
        factory=new LettuceConnectionFactory(server,LettuceClientConfiguration.builder().clientResources(resources).commandTimeout(Duration.ofSeconds(2)).shutdownTimeout(Duration.ofMillis(100)).build());factory.afterPropertiesSet();
        redis=new StringRedisTemplate(factory);redis.afterPropertiesSet();assertOwner();assertThat(redis.opsForValue().get("r282:acceptance-only")).isEqualTo("true");
    }
    static void assertOwner(){assertThat(redis.opsForValue().get("r282:owner")).isEqualTo(nonce);}
    @Override protected MaxQuestionnaireSelectionTest fixture(){return new MaxQuestionnaireSelectionTest(){
        @Override protected BotIngressCoordinationProperties coordinationProperties(){var p=new BotIngressCoordinationProperties();p.setMode("redis");p.setLeaseNamespace(namespace);return p;}
        @Override @SuppressWarnings("unchecked") protected ObjectProvider<StringRedisTemplate> redisProvider(){ObjectProvider<StringRedisTemplate> p=mock(ObjectProvider.class);when(p.getIfAvailable()).thenReturn(redis);return p;}
        @Override protected void discardDeliveryCache(){var keys=ownedKeys("bot-webhook-delivery:*");if(!keys.isEmpty())redis.delete(keys);}
    };}
    Set<String> ownedKeys(String suffix){assertOwner();var keys=new LinkedHashSet<String>();try(var cursor=redis.scan(ScanOptions.scanOptions().match(namespace+":"+suffix).count(100).build())){while(cursor.hasNext()){String key=cursor.next();assertThat(key).startsWith(namespace+":");keys.add(key);assertThat(keys.size()).isLessThan(10000);}}return keys;}
    @AfterEach void cleanup(){if(redis!=null){var keys=ownedKeys("*");if(!keys.isEmpty())redis.delete(keys);assertThat(ownedKeys("*")).isEmpty();}}
    @AfterAll static void disconnect(){if(factory!=null)factory.destroy();if(resources!=null)resources.shutdown();}
}
