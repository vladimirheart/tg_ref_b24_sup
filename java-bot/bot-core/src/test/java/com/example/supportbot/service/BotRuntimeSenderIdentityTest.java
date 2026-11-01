package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.*;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

class BotRuntimeSenderIdentityTest {
    static Stream<String> platforms(){return Stream.of("telegram","vk","max");}
    static Stream<Arguments> invalid(){return platforms().flatMap(p->Stream.of("missing-channel","zero-channel","foreign-platform","unknown-platform","missing-platform","null-token","blank-token","placeholder-token").map(c->Arguments.of(p,c)));}
    @ParameterizedTest @MethodSource("invalid")
    void unresolvedRuntimeCannotBorrowTheDestinationIdentity(String platform,String change){
        long channel=42;String runtimePlatform=platform,token="token";
        switch(change){case "missing-channel" -> channel=-1;case "zero-channel" -> channel=0;case "foreign-platform" -> runtimePlatform=platform.equals("vk")?"max":"vk";case "unknown-platform" -> runtimePlatform="unknown";case "missing-platform" -> runtimePlatform="";case "null-token" -> token=null;case "blank-token" -> token=" ";case "placeholder-token" -> token="YOUR_TELEGRAM_BOT_TOKEN";default -> throw new AssertionError(change);}
        assertThat(new BotRuntimeSenderIdentity(channel,runtimePlatform).forToken(platform,token)).isNull();
    }
    @ParameterizedTest @MethodSource("platforms")
    void sharedYamlBindsCanonicalIdNotExternalChatId(String platform)throws Exception{
        var environment=new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("launcher",Map.of("APP_BOT_CHANNEL_ID","9007199254740993","APP_BOT_PLATFORM",platform,"GROUP_CHAT_ID","-100","VK_OPERATOR_CHAT_ID","77")));
        for(var source:new YamlPropertySourceLoader().load("shared",new ClassPathResource("application-shared.yml")))environment.getPropertySources().addLast(source);
        var runtime=new BotRuntimeSenderIdentity(Long.parseLong(environment.getProperty("app.bot-runtime.channel-id")),environment.getProperty("app.bot-runtime.platform"));
        var first=runtime.forToken(platform," token ");var second=runtime.forToken(platform,"rotated");
        assertThat(first.channelId()).isEqualTo(9_007_199_254_740_993L);assertThat(first.platform()).isEqualTo(platform);
        assertThat(first.tokenSha256()).hasSize(64).isNotEqualTo(second.tokenSha256());
        assertThat(first.toString()).doesNotContain(" token ",first.tokenSha256());
    }
}
