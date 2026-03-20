package com.example.panel.service;

import com.example.panel.entity.Channel;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IntegrationNetworkServiceTest {

    private final SharedConfigService sharedConfigService = mock(SharedConfigService.class);
    private final IntegrationNetworkService service = new IntegrationNetworkService(sharedConfigService, new ObjectMapper());

    @Test
    void resolvesChannelOverrideBeforeBotAndProjectDefaults() {
        when(sharedConfigService.loadSettings()).thenReturn(Map.of(
            "integration_network", Map.of(
                "project", Map.of("mode", "proxy", "proxy", Map.of("host", "project-proxy", "port", 8080)),
                "bots", Map.of("mode", "vpn", "vpn", Map.of("name", "bots-vpn"))
            )
        ));

        Channel channel = new Channel();
        channel.setDeliverySettings("""
            {
              "network_route": {
                "mode": "proxy",
                "proxy": {
                  "scheme": "socks5",
                  "host": "channel-proxy",
                  "port": 1080,
                  "username": "bot",
                  "password": "secret"
                }
              }
            }
            """);

        IntegrationNetworkService.RouteSettings route = service.resolveBotRoute(channel);

        assertThat(route.mode()).isEqualTo("proxy");
        assertThat(route.proxySettings().host()).isEqualTo("channel-proxy");
        assertThat(route.proxySettings().scheme()).isEqualTo("socks5");
    }

    @Test
    void buildsProxyEnvironmentForBotProcess() {
        IntegrationNetworkService.RouteSettings route = IntegrationNetworkService.RouteSettings.fromMap(new LinkedHashMap<>(Map.of(
            "mode", "proxy",
            "proxy", Map.of(
                "scheme", "http",
                "host", "corp-proxy.local",
                "port", 3128,
                "username", "svc_bot",
                "password", "pwd"
            )
        )), true);

        Map<String, String> env = service.buildProcessEnvironment(route);

        assertThat(env)
            .containsEntry("APP_NETWORK_MODE", "proxy")
            .containsEntry("APP_NETWORK_PROXY_HOST", "corp-proxy.local")
            .containsEntry("APP_NETWORK_PROXY_PORT", "3128")
            .containsEntry("HTTP_PROXY", "http://svc_bot:pwd@corp-proxy.local:3128")
            .containsKey("JAVA_TOOL_OPTIONS");
        assertThat(env.get("JAVA_TOOL_OPTIONS")).contains("-Dhttp.proxyHost=corp-proxy.local");
    }
}
