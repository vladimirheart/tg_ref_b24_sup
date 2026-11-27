package com.example.panel.service;

import com.example.panel.entity.RmsLicenseMonitor;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RmsProtocolComponentsTest {
    private final RmsTextCodec text = new RmsTextCodec(new ObjectMapper());
    private final RmsLicenseClient client = new RmsLicenseClient(new ObjectMapper(), text);
    private final RmsNetworkProbe network = new RmsNetworkProbe(text);

    @Test void endpointNormalizationKeepsHttpsDefaultAndExplicitPorts() {
        var plain = client.parseEndpointSafe("rms.example.test");
        assertThat(plain.normalizedAddress()).isEqualTo("https://rms.example.test");
        assertThat(plain.host()).isEqualTo("rms.example.test");
        assertThat(plain.port()).isEqualTo(443);
        var explicit = client.parseEndpointSafe("http://rms.example.test:8080");
        assertThat(explicit.scheme()).isEqualTo("http");
        assertThat(explicit.port()).isEqualTo(8080);
        assertThat(client.parseEndpointSafe("https://[::1]:444").port()).isEqualTo(444);
        assertThatThrownBy(() -> client.parseEndpointSafe(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void literalXmlAndTargetSelectionKeepLicenseAndExpiration() throws Exception {
        var xml = text.parseXml("<licenses><license id='100' name='RMS (Front Fast Food)' expiration='2026-12-30' quantity='3'/></licenses>");
        var details = client.parseLicenseDetails(xml);
        var monitor = new RmsLicenseMonitor(); monitor.setServerType("iikoRMS");
        var target = client.findTargetLicense(details, client.resolvePrimaryLicenseDescriptor(monitor));
        assertThat(target).isNotNull();
        assertThat(client.resolveLicenseExpiration(target).toLocalDate().toString()).isEqualTo("2026-12-30");
        assertThat(client.writeLicenseDetails(details)).contains("100", "2026-12-30");
    }

    @Test void chainUsesItsOwnDescriptorAndKioskKeepsItsId() {
        var chain = new RmsLicenseMonitor(); chain.setServerType("iikoChain");
        assertThat(client.resolvePrimaryLicenseDescriptor(chain).id()).isEqualTo("1300");
        assertThat(client.kioskConnectorLicenseDescriptor().id()).isEqualTo("36073118");
        var descriptors = List.of(Map.of("id", "100", "name", "RMS (Front Fast Food)"),
                                  Map.of("id", "1300", "name", "Chain (Store Connector (1 RMS))"));
        assertThat(client.findTargetLicense(descriptors, client.resolvePrimaryLicenseDescriptor(chain))).containsEntry("id", "1300");
    }

    @Test void xmlStillRejectsDoctypeBeforeResolvingExternalEntities() {
        assertThatThrownBy(() -> text.parseXml("<!DOCTYPE x [<!ENTITY xxe SYSTEM 'file:///must-not-be-read'>]><x>&xxe;</x>"))
            .isInstanceOf(org.xml.sax.SAXParseException.class);
    }

    @Test void explicitEncodingsPreserveRussianDiagnostics() {
        String phrase = "Проверка сервера выполнена успешно";
        // Auto-detection is preserved and compared separately against the original service.
        for (Charset charset : List.of(StandardCharsets.UTF_8, Charset.forName("IBM866"), Charset.forName("windows-1251"))) {
            assertThat(text.decodeStrict(phrase.getBytes(charset), charset)).isEqualTo(phrase);
        }
    }

    @Test void diagnosticsAndCommandsRetainTheirExistingDataContract() {
        assertThat(network.buildPingSuccessMessage("rms.example.test", "Pinging rms.example.test [127.0.0.1] with 32 bytes of data")).contains("127.0.0.1");
        assertThat(network.buildPingCommand("rms.example.test")).endsWith("rms.example.test");
        assertThat(network.buildTracerouteCommand("rms.example.test")).endsWith("rms.example.test");
        assertThat(text.trimText("  abcdef  ", 3, "fallback")).isEqualTo("abc");
        assertThat(text.firstNonBlank("", "  value  ", "other")).isEqualTo("value");
        assertThat(client.parseFlexibleDate("2026-12-30").getOffset()).isEqualTo(ZoneOffset.UTC);
    }
}
