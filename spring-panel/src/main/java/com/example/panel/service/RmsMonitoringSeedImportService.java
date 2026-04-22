package com.example.panel.service;

import com.example.panel.entity.RmsLicenseMonitor;
import com.example.panel.repository.RmsLicenseMonitorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@Order(140)
public class RmsMonitoringSeedImportService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RmsMonitoringSeedImportService.class);

    private static final String SEED_RESOURCE = "rms_addresses_seed.csv";
    private static final String DISABLED_MESSAGE = "Импортировано из seed CSV. Добавьте пароль и включите мониторинг.";

    private final RmsLicenseMonitorRepository repository;

    public RmsMonitoringSeedImportService(RmsLicenseMonitorRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        ClassPathResource resource = new ClassPathResource(SEED_RESOURCE);
        if (!resource.exists()) {
            return;
        }

        Set<String> existingAddresses = new HashSet<>();
        for (RmsLicenseMonitor monitor : repository.findAll()) {
            if (StringUtils.hasText(monitor.getRmsAddress())) {
                existingAddresses.add(monitor.getRmsAddress().trim().toLowerCase(Locale.ROOT));
            }
        }

        List<RmsLicenseMonitor> batch = new ArrayList<>();
        Set<String> seenInSeed = new LinkedHashSet<>();
        int skipped = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (!StringUtils.hasText(headerLine)) {
                return;
            }

            List<String> headers = parseCsvLine(stripBom(headerLine));
            Map<String, Integer> indexes = indexHeaders(headers);
            String line;
            while ((line = reader.readLine()) != null) {
                if (!StringUtils.hasText(line)) {
                    continue;
                }
                List<String> values = parseCsvLine(line);
                SeedRow row = mapRow(values, indexes);
                if (row == null || !row.importable()) {
                    skipped++;
                    continue;
                }

                String normalizedAddress = buildAddress(row.host(), row.port());
                String addressKey = normalizedAddress.toLowerCase(Locale.ROOT);
                if (existingAddresses.contains(addressKey) || !seenInSeed.add(addressKey)) {
                    skipped++;
                    continue;
                }

                OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
                RmsLicenseMonitor monitor = new RmsLicenseMonitor();
                monitor.setRmsAddress(normalizedAddress);
                monitor.setScheme("https");
                monitor.setHost(row.host());
                monitor.setPort(row.port());
                monitor.setAuthLogin(row.login());
                monitor.setAuthPassword("");
                monitor.setEnabled(Boolean.FALSE);
                monitor.setLicenseMonitoringEnabled(Boolean.TRUE);
                monitor.setNetworkMonitoringEnabled(Boolean.TRUE);
                monitor.setServerName(row.name());
                monitor.setServerType(row.type());
                monitor.setServerVersion(null);
                monitor.setLicenseStatus(RmsLicenseMonitoringService.LICENSE_STATUS_DISABLED);
                monitor.setLicenseErrorMessage(null);
                monitor.setLicenseExpiresAt(null);
                monitor.setLicenseDaysLeft(null);
                monitor.setLicenseLastCheckedAt(null);
                monitor.setLicenseLastNotifiedAt(null);
                monitor.setRmsStatus(RmsLicenseMonitoringService.RMS_STATUS_DISABLED);
                monitor.setRmsStatusMessage(DISABLED_MESSAGE);
                monitor.setPingOutput(null);
                monitor.setTracerouteSummary(null);
                monitor.setTracerouteReport(null);
                monitor.setTracerouteCheckedAt(null);
                monitor.setRmsLastCheckedAt(null);
                monitor.setCreatedAt(now);
                monitor.setUpdatedAt(now);
                batch.add(monitor);
            }
        }

        if (!batch.isEmpty()) {
            repository.saveAll(batch);
            log.info("Imported {} RMS seed records from {}", batch.size(), SEED_RESOURCE);
        } else {
            log.info("No RMS seed records were imported from {} (skipped: {})", SEED_RESOURCE, skipped);
        }
    }

    private String stripBom(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return value.charAt(0) == '\uFEFF' ? value.substring(1) : value;
    }

    private Map<String, Integer> indexHeaders(List<String> headers) {
        Map<String, Integer> indexes = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String header = headers.get(i);
            if (StringUtils.hasText(header)) {
                indexes.put(header.trim().toLowerCase(Locale.ROOT), i);
            }
        }
        return indexes;
    }

    private SeedRow mapRow(List<String> values, Map<String, Integer> indexes) {
        String name = readValue(values, indexes, "name");
        String type = readValue(values, indexes, "type");
        String host = readValue(values, indexes, "ip");
        String portRaw = readValue(values, indexes, "port");
        String login = readValue(values, indexes, "login");
        Integer port = parsePort(portRaw);
        if (port == null) {
            port = 443;
        }
        return new SeedRow(
            clean(name),
            clean(type),
            normalizeHost(host),
            port,
            clean(login)
        );
    }

    private String readValue(List<String> values, Map<String, Integer> indexes, String key) {
        Integer index = indexes.get(key);
        if (index == null || index < 0 || index >= values.size()) {
            return "";
        }
        return values.get(index);
    }

    private Integer parsePort(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return value > 0 && value <= 65535 ? value : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String normalizeHost(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private String buildAddress(String host, int port) {
        return port == 443 ? "https://" + host : "https://" + host + ":" + port;
    }

    private List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
                continue;
            }
            if (ch == ',' && !quoted) {
                values.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(ch);
        }
        values.add(current.toString());
        return values;
    }

    private record SeedRow(String name, String type, String host, Integer port, String login) {
        private boolean importable() {
            return StringUtils.hasText(name)
                && !name.trim().toUpperCase(Locale.ROOT).startsWith("CLOSED")
                && StringUtils.hasText(type)
                && StringUtils.hasText(host)
                && port != null
                && StringUtils.hasText(login);
        }
    }
}
