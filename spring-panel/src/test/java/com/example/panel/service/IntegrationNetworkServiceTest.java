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
    void resolvesProfileBasedRouteFromSharedSettings() {
        when(sharedConfigService.loadSettings()).thenReturn(Map.of(
            "integration_network", Map.of(
                "project", Map.of("mode", "profile", "profile_id", "bots-proxy"),
                "bots", Map.of("mode", "inherit")
            ),
            "integration_network_profiles", java.util.List.of(
                Map.of(
                    "id", "bots-proxy",
                    "name", "Боты через корпоративный прокси",
                    "mode", "proxy",
                    "proxy", Map.of(
                        "scheme", "http",
                        "host", "proxy.internal",
                        "port", 3128
                    )
                )
            )
        ));

        IntegrationNetworkService.RouteSettings route = service.resolveProjectRoute();

        assertThat(route.mode()).isEqualTo("proxy");
        assertThat(route.profileId()).isEqualTo("bots-proxy");
        assertThat(route.proxySettings().host()).isEqualTo("proxy.internal");
        assertThat(route.proxySettings().port()).isEqualTo(3128);
    }

    @Test
    void resolvesProfileRouteWithFailoverContextForDirectProfiles() {
        when(sharedConfigService.loadSettings()).thenReturn(Map.of(
            "integration_network", Map.of(
                "project", Map.of(
                    "mode", "profile",
                    "profile_ids", java.util.List.of("primary-direct", "fallback-direct"),
                    "failover_downtime_seconds", 45
                )
            ),
            "integration_network_profiles", java.util.List.of(
                Map.of("id", "primary-direct", "mode", "direct"),
                Map.of("id", "fallback-direct", "mode", "direct")
            )
        ));

        IntegrationNetworkService.RouteSettings route = service.resolveProjectRoute();

        assertThat(route.mode()).isEqualTo("direct");
        assertThat(route.profileId()).isEqualTo("primary-direct");
        assertThat(route.profileIds()).containsExactly("primary-direct", "fallback-direct");
        assertThat(route.failoverDowntimeSeconds()).isEqualTo(45);
    }

    @Test
    void resolvesBotRouteFromBotsSectionBeforeProjectDefault() {
        when(sharedConfigService.loadSettings()).thenReturn(Map.of(
            "integration_network", Map.of(
                "project", Map.of("mode", "proxy", "proxy", Map.of("host", "project-proxy", "port", 8080)),
                "bots", Map.of("mode", "vpn", "vpn", Map.of("name", "bots-vpn", "endpoint", "vpn.example:9443"))
            )
        ));

        IntegrationNetworkService.RouteSettings route = service.resolveBotRoute(null);

        assertThat(route.mode()).isEqualTo("vpn");
        assertThat(route.vpnName()).isEqualTo("bots-vpn");
        assertThat(route.vpnEndpoint()).isEqualTo("vpn.example:9443");
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
            .containsEntry("HTTPS_PROXY", "http://svc_bot:pwd@corp-proxy.local:3128")
            .containsEntry("http_proxy", "http://svc_bot:pwd@corp-proxy.local:3128")
            .containsEntry("https_proxy", "http://svc_bot:pwd@corp-proxy.local:3128")
            .containsKey("JAVA_TOOL_OPTIONS");
        assertThat(env.get("JAVA_TOOL_OPTIONS"))
            .contains("-Dhttp.proxyHost=corp-proxy.local")
            .contains("-Dhttps.proxyHost=corp-proxy.local")
            .contains("-Dhttp.proxyUser=svc_bot")
            .contains("-Dhttps.proxyPassword=pwd")
            .contains("-Djdk.http.auth.tunneling.disabledSchemes=");
    }

    @Test
    void buildsSocksEnvironmentForBotProcess() {
        IntegrationNetworkService.RouteSettings route = IntegrationNetworkService.RouteSettings.fromMap(new LinkedHashMap<>(Map.of(
            "mode", "proxy",
            "profile_ids", java.util.List.of("bots-socks", "fallback-socks"),
            "proxy", Map.of(
                "scheme", "socks5",
                "host", "socks.internal",
                "port", 1080,
                "username", "svc_bot",
                "password", "pwd"
            )
        )), true);

        Map<String, String> env = service.buildProcessEnvironment(route);

        assertThat(env)
            .containsEntry("APP_NETWORK_MODE", "proxy")
            .containsEntry("APP_NETWORK_PROFILE_IDS", "bots-socks,fallback-socks")
            .containsEntry("ALL_PROXY", "socks5://svc_bot:pwd@socks.internal:1080")
            .containsEntry("all_proxy", "socks5://svc_bot:pwd@socks.internal:1080");
        assertThat(env.get("JAVA_TOOL_OPTIONS"))
            .contains("-DsocksProxyHost=socks.internal")
            .contains("-DsocksProxyPort=1080")
            .contains("-Djava.net.socks.username=svc_bot")
            .contains("-Djava.net.socks.password=pwd");
    }

    @Test
    void buildsVlessEnvironmentForBotProcessWithTokenAndAllProxy() {
        IntegrationNetworkService.RouteSettings route = IntegrationNetworkService.RouteSettings.fromMap(new LinkedHashMap<>(Map.of(
            "mode", "proxy",
            "proxy", Map.of(
                "scheme", "vless",
                "host", "vless.internal",
                "port", 7443,
                "token", "vless-token"
            )
        )), true);

        Map<String, String> env = service.buildProcessEnvironment(route);

        assertThat(env)
            .containsEntry("APP_NETWORK_MODE", "proxy")
            .containsEntry("APP_NETWORK_PROXY_SCHEME", "vless")
            .containsEntry("APP_NETWORK_PROXY_TOKEN", "vless-token")
            .containsEntry("ALL_PROXY", "vless://vless-token@vless.internal:7443")
            .containsEntry("all_proxy", "vless://vless-token@vless.internal:7443");
        assertThat(env.get("JAVA_TOOL_OPTIONS"))
            .contains("-DsocksProxyHost=vless.internal")
            .contains("-DsocksProxyPort=7443");
    }

    @Test
    void buildsVpnEnvironmentForBotProcess() {
        IntegrationNetworkService.RouteSettings route = IntegrationNetworkService.RouteSettings.fromMap(new LinkedHashMap<>(Map.of(
            "mode", "vpn",
            "vpn", Map.of(
                "name", "corp-vpn",
                "endpoint", "vpn.internal:7443"
            )
        )), true);

        Map<String, String> env = service.buildProcessEnvironment(route);

        assertThat(env)
            .containsEntry("APP_NETWORK_MODE", "vpn")
            .containsEntry("APP_NETWORK_VPN_NAME", "corp-vpn")
            .containsEntry("APP_NETWORK_FAILOVER_DOWNTIME_SECONDS", "120");
    }

    @Test
    void buildsDirectEnvironmentWithoutProxyVariables() {
        Map<String, String> env = service.buildProcessEnvironment(IntegrationNetworkService.RouteSettings.direct());

        assertThat(env)
            .containsEntry("APP_NETWORK_MODE", "direct")
            .containsEntry("APP_NETWORK_FAILOVER_DOWNTIME_SECONDS", "120")
            .doesNotContainKeys("HTTP_PROXY", "HTTPS_PROXY", "ALL_PROXY", "JAVA_TOOL_OPTIONS");
    }

    @Test
    void routeSettingsFromMapNormalizesInvalidModeToInheritWhenAllowed() {
        IntegrationNetworkService.RouteSettings route = IntegrationNetworkService.RouteSettings.fromMap(Map.of(
            "mode", "unexpected"
        ), true);

        assertThat(route.mode()).isEqualTo("inherit");
    }

    @Test
    void routeSettingsFromMapNormalizesInvalidModeToDirectWhenInheritDisallowed() {
        IntegrationNetworkService.RouteSettings route = IntegrationNetworkService.RouteSettings.fromMap(Map.of(
            "mode", "unexpected"
        ), false);

        assertThat(route.mode()).isEqualTo("direct");
    }

    @Test
    void routeSettingsFromMapDeduplicatesProfileIdsAndFallsBackToProfileId() {
        IntegrationNetworkService.RouteSettings route = IntegrationNetworkService.RouteSettings.fromMap(new LinkedHashMap<>(Map.of(
            "mode", "profile",
            "profile_id", "primary",
            "profile_ids", java.util.List.of("primary", "fallback", "primary", "  ")
        )), true);

        assertThat(route.profileId()).isEqualTo("primary");
        assertThat(route.profileIds()).containsExactly("primary", "fallback");
    }

    @Test
    void routeSettingsFromMapReadsCamelCaseFieldsAndClampsFailoverDowntime() {
        IntegrationNetworkService.RouteSettings route = IntegrationNetworkService.RouteSettings.fromMap(new LinkedHashMap<>(Map.of(
            "mode", "vpn",
            "profileIds", java.util.List.of("vpn-primary", "vpn-fallback", "vpn-primary"),
            "vpnName", "corp-vpn",
            "failoverDowntimeSeconds", 3
        )), true);

        assertThat(route.vpnName()).isEqualTo("corp-vpn");
        assertThat(route.profileIds()).containsExactly("vpn-primary", "vpn-fallback");
        assertThat(route.failoverDowntimeSeconds()).isEqualTo(10);
    }

    @Test
    void probeProfileRouteReportsIncompleteProxyProfile() {
        IntegrationNetworkService.RouteProbeResult result = service.probeProfileRoute(Map.of(
            "mode", "proxy",
            "proxy", Map.of(
                "host", "proxy.internal"
            )
        ));

        assertThat(result.reachable()).isFalse();
        assertThat(result.message()).isEqualTo("Прокси-профиль заполнен не полностью.");
        assertThat(result.host()).isEqualTo("proxy.internal");
        assertThat(result.port()).isZero();
    }
}
