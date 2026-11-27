package com.example.panel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.panel.entity.RmsLicenseMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.IDN;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/** Internal client boundary; no repositories, queues, scheduling or Spring lifecycle. */
final class RmsLicenseClient {
    private final ObjectMapper objectMapper;
    private final RmsTextCodec textCodec;
    private static final Logger log = LoggerFactory.getLogger("com.example.panel.service.RmsLicenseMonitoringService");

    RmsLicenseClient(ObjectMapper objectMapper, RmsTextCodec textCodec) {
        this.objectMapper = objectMapper;
        this.textCodec = textCodec;
        this.httpClient = buildUnsafeHttpClient();
    }

    static final String RMS_TARGET_LICENSE_ID = "100";

    static final String RMS_TARGET_LICENSE_TITLE = "RMS (Front Fast Food)";

    private static final String CHAIN_TARGET_LICENSE_ID = "1300";

    private static final String CHAIN_TARGET_LICENSE_TITLE = "Chain (Store Connector (1 RMS))";

    private static final String KIOSK_CONNECTOR_LICENSE_ID = "36073118";

    private static final String KIOSK_CONNECTOR_LICENSE_TITLE = "iikoConnector for Get Kiosk (iikoFront)";

    private static final int DEFAULT_RMS_PORT = 443;

    private static final long HTTP_TIMEOUT_MS = 15_000L;

    private static final DateTimeFormatter[] DATE_TIME_FORMATTERS = new DateTimeFormatter[]{
        DateTimeFormatter.ISO_OFFSET_DATE_TIME,
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    };

    private final HttpClient httpClient;

    String resolveServerTypeKey(RmsLicenseMonitor monitor) {
        return normalizeServerTypeKey(monitor == null ? null : monitor.getServerType());
    }

    String resolveServerTypeDisplay(RmsLicenseMonitor monitor) {
        String normalized = resolveServerTypeKey(monitor);
        if (StringUtils.hasText(normalized)) {
            return normalized;
        }
        if (monitor == null || !StringUtils.hasText(monitor.getServerType())) {
            return "";
        }
        return monitor.getServerType().trim();
    }

    ServerMetadata fetchServerMetadata(RmsLicenseMonitor monitor) throws Exception {
        String baseUrl = buildBaseUrl(monitor);
        String serverInfoResponse = getText(baseUrl + "/resto/get_server_info.jsp?encoding=UTF-8");
        Document serverInfoXml = textCodec.parseXml(serverInfoResponse);
        String serverName = textCodec.sanitizeServerName(extractFirstText(serverInfoXml, "serverName"), monitor.getServerName(), monitor.getHost());
        String serverVersion = extractFirstText(serverInfoXml, "version");
        String edition = normalizeEdition(extractFirstText(serverInfoXml, "edition"));

        String loginHtml = getText(baseUrl + "/resto/login.jsp");
        String brand = loginHtml.toLowerCase(Locale.ROOT).contains("syrve") ? "syrve" : "iiko";

        return new ServerMetadata(
            textCodec.trimText(serverName, 255, monitor.getHost()),
            textCodec.trimText(composeServerType(brand, edition), 128, "unknown"),
            textCodec.trimText(serverVersion, 128, ""),
            edition
        );
    }

    LicenseSnapshot fetchLicenseSnapshot(RmsLicenseMonitor monitor,
                                                 String serverEdition,
                                                 String serverVersion) throws Exception {
        String baseUrl = buildBaseUrl(monitor);
        String safeVersion = StringUtils.hasText(serverVersion) ? serverVersion.trim() : "8.0";
        String safeEdition = StringUtils.hasText(serverEdition) ? serverEdition.trim() : "default";
        String body = """
            <args>
                <entities-version>1</entities-version>
                <client-type>BACK</client-type>
                <enable-warnings>false</enable-warnings>
                <client-call-id>%s</client-call-id>
                <license-hash>-1938788177</license-hash>
                <restrictions-state-hash>5761</restrictions-state-hash>
                <obtained-license-connections-ids />
                <request-watchdog-check-results>true</request-watchdog-check-results>
                <use-raw-entities>true</use-raw-entities>
            </args>
            """.formatted(UUID.randomUUID());

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "text/xml");
        headers.put("X-Resto-LoginName", monitor.getAuthLogin());
        headers.put("X-Resto-PasswordHash", sha1Hex(monitor.getAuthPassword()));
        headers.put("X-Resto-BackVersion", safeVersion);
        headers.put("X-Resto-AuthType", "BACK");
        headers.put("X-Resto-ServerEdition", safeEdition);

        String response = postXml(
            baseUrl + "/resto/services/licensing?methodName=getForceDeveloperSandboxModeInfo&",
            body,
            headers
        );
        Document xml = textCodec.parseXml(response);
        List<Map<String, String>> details = parseLicenseDetails(xml);
        String diagnosticExcerpt = buildLicenseDiagnosticExcerpt(response, xml);
        LicenseDescriptor targetDescriptor = resolvePrimaryLicenseDescriptor(monitor);
        Map<String, String> targetLicense = findTargetLicense(details, targetDescriptor);
        if (targetLicense == null) {
            throw new LicenseSnapshotException(
                "Не найдена лицензия " + targetDescriptor.title() + " с id=" + targetDescriptor.id(),
                writeLicenseDetails(details),
                diagnosticExcerpt
            );
        }
        OffsetDateTime targetExpiration = resolveLicenseExpiration(targetLicense);
        if (targetExpiration == null) {
            throw new LicenseSnapshotException(
                "Не удалось определить дату окончания лицензии " + targetDescriptor.title() + " [id=" + targetDescriptor.id() + "]",
                writeLicenseDetails(details),
                diagnosticExcerpt
            );
        }
        int daysLeft = (int) ChronoUnit.DAYS.between(LocalDate.now(ZoneOffset.UTC), targetExpiration.toLocalDate());
        return new LicenseSnapshot(targetExpiration, daysLeft, writeLicenseDetails(details), diagnosticExcerpt);
    }

    String buildBaseUrl(RmsLicenseMonitor monitor) {
        String scheme = StringUtils.hasText(monitor.getScheme()) ? monitor.getScheme().trim() : "https";
        int port = monitor.getPort() != null && monitor.getPort() > 0 ? monitor.getPort() : DEFAULT_RMS_PORT;
        String host = monitor.getHost();
        if (host != null && host.contains(":") && !host.startsWith("[")) {
            return scheme + "://[" + host + "]:" + port;
        }
        return scheme + "://" + host + ":" + port;
    }

    HttpClient buildUnsafeHttpClient() {
        try {
            TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }
            };
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, trustAll, new SecureRandom());
            return HttpClient.newBuilder()
                .sslContext(context)
                .connectTimeout(Duration.ofMillis(HTTP_TIMEOUT_MS))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        } catch (Exception ex) {
            return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(HTTP_TIMEOUT_MS))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        }
    }

    String getText(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .GET()
            .timeout(Duration.ofMillis(HTTP_TIMEOUT_MS))
            .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode() + " для " + url);
        }
        return textCodec.decodeHttpResponse(response);
    }

    String postXml(String url, String body, Map<String, String> headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .timeout(Duration.ofMillis(HTTP_TIMEOUT_MS));
        headers.forEach(builder::header);
        HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode() + " для запроса лицензии");
        }
        return textCodec.decodeHttpResponse(response);
    }

    String extractFirstText(Document document, String tagName) {
        NodeList nodes = document.getElementsByTagName(tagName);
        return nodes.getLength() > 0 ? nodes.item(0).getTextContent() : "";
    }

    String extractChildText(Node node, String tagName) {
        if (node == null || !node.hasChildNodes()) {
            return "";
        }
        NodeList children = node.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child != null && child.getNodeType() == Node.ELEMENT_NODE && tagName.equalsIgnoreCase(child.getNodeName())) {
                return child.getTextContent();
            }
        }
        return "";
    }

    String normalizeEdition(String rawEdition) {
        String edition = StringUtils.hasText(rawEdition) ? rawEdition.trim().toLowerCase(Locale.ROOT) : "default";
        if ("default".equals(edition)) {
            return "IIKO_RMS";
        }
        if ("chain".equals(edition)) {
            return "IIKO_CHAIN";
        }
        return edition.toUpperCase(Locale.ROOT);
    }

    String composeServerType(String brand, String edition) {
        String safeBrand = StringUtils.hasText(brand) ? brand.trim().toLowerCase(Locale.ROOT) : "unknown";
        String safeEdition = StringUtils.hasText(edition) ? edition.trim() : "UNKNOWN";
        return safeBrand + " / " + safeEdition;
    }

    OffsetDateTime parseFlexibleDate(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String value = raw.trim();
        if (value.matches("^\\d+$")) {
            long epoch = Long.parseLong(value);
            if (value.length() <= 10) {
                return OffsetDateTime.ofInstant(Instant.ofEpochSecond(epoch), ZoneOffset.UTC);
            }
            return OffsetDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZoneOffset.UTC);
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (Exception ignored) {
        }
        try {
            return Instant.parse(value).atOffset(ZoneOffset.UTC);
        } catch (Exception ignored) {
        }
        for (DateTimeFormatter formatter : DATE_TIME_FORMATTERS) {
            try {
                if (formatter == DateTimeFormatter.ISO_OFFSET_DATE_TIME) {
                    return OffsetDateTime.parse(value, formatter);
                }
                LocalDateTime parsed = LocalDateTime.parse(value, formatter);
                return parsed.atOffset(ZoneOffset.UTC);
            } catch (Exception ignored) {
            }
        }
        try {
            return LocalDate.parse(value).atStartOfDay().atOffset(ZoneOffset.UTC);
        } catch (Exception ignored) {
            return null;
        }
    }

    EndpointTarget parseEndpointSafe(String rawEndpoint) {
        if (!StringUtils.hasText(rawEndpoint)) {
            throw new IllegalArgumentException("Укажите адрес RMS");
        }
        String candidate = rawEndpoint.trim();
        if (!candidate.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*$")) {
            candidate = "https://" + candidate;
        }
        URI uri;
        try {
            uri = URI.create(candidate);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Некорректный адрес RMS");
        }

        String scheme = Optional.ofNullable(uri.getScheme()).orElse("https").trim().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("Поддерживаются только адреса http/https");
        }

        HostPort hostPort = extractHostPort(uri);
        if (!StringUtils.hasText(hostPort.host())) {
            throw new IllegalArgumentException("Не удалось определить host RMS");
        }
        String host;
        try {
            host = IDN.toASCII(hostPort.host().trim(), IDN.ALLOW_UNASSIGNED).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Некорректный host RMS");
        }
        int port = hostPort.port() != null ? hostPort.port() : ("http".equals(scheme) ? 80 : DEFAULT_RMS_PORT);
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Порт RMS должен быть в диапазоне 1-65535");
        }
        String normalizedAddress = scheme + "://" + host + (("https".equals(scheme) && port == 443) || ("http".equals(scheme) && port == 80) ? "" : ":" + port);
        return new EndpointTarget(normalizedAddress, scheme, host, port);
    }

    HostPort extractHostPort(URI uri) {
        String host = uri.getHost();
        Integer port = uri.getPort() > 0 ? uri.getPort() : null;
        if (StringUtils.hasText(host)) {
            return new HostPort(host, port);
        }

        String authority = uri.getRawAuthority();
        if (!StringUtils.hasText(authority)) {
            return new HostPort(null, port);
        }
        String value = authority.trim();
        int at = value.lastIndexOf('@');
        if (at >= 0 && at < value.length() - 1) {
            value = value.substring(at + 1);
        }
        value = URLDecoder.decode(value, StandardCharsets.UTF_8);

        if (value.startsWith("[")) {
            int endBracket = value.indexOf(']');
            if (endBracket > 1) {
                String ipv6Host = value.substring(1, endBracket);
                Integer ipv6Port = null;
                if (endBracket + 1 < value.length() && value.charAt(endBracket + 1) == ':') {
                    ipv6Port = parsePort(value.substring(endBracket + 2));
                }
                return new HostPort(ipv6Host, ipv6Port);
            }
        }

        int firstColon = value.indexOf(':');
        int lastColon = value.lastIndexOf(':');
        if (firstColon > 0 && firstColon == lastColon) {
            return new HostPort(value.substring(0, firstColon), parsePort(value.substring(firstColon + 1)));
        }
        return new HostPort(value, null);
    }

    Integer parsePort(String rawPort) {
        if (!StringUtils.hasText(rawPort)) {
            return null;
        }
        try {
            return Integer.parseInt(rawPort.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Некорректный порт RMS");
        }
    }

    String sha1Hex(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        byte[] hash = digest.digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    List<Map<String, String>> parseLicenseDetails(Document xml) {
        List<Map<String, String>> items = new java.util.ArrayList<>();
        NodeList licenses = xml.getElementsByTagName("license");
        for (int index = 0; index < licenses.getLength(); index++) {
            Node node = licenses.item(index);
            if (node == null) {
                continue;
            }
            LinkedHashMap<String, String> item = new LinkedHashMap<>();
            appendNodeAttributes(item, node);
            NodeList children = node.getChildNodes();
            for (int childIndex = 0; childIndex < children.getLength(); childIndex++) {
                Node child = children.item(childIndex);
                if (child == null || child.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                collectLicenseNode(item, child, normalizeLicenseKey(child.getNodeName()));
            }
            enrichLicenseDetails(item, index);
            if (!item.isEmpty()) {
                items.add(item);
            }
        }
        if (!items.isEmpty()) {
            return items;
        }
        return parseEncodedLicenseData(xml);
    }

    List<Map<String, String>> parseEncodedLicenseData(Document xml) {
        String encodedLicenseData = extractFirstText(xml, "licenseData");
        if (!StringUtils.hasText(encodedLicenseData)) {
            return List.of();
        }
        String rawLicenseData = HtmlUtils.htmlUnescape(encodedLicenseData).trim();
        if (!textCodec.looksLikeXml(rawLicenseData)) {
            return List.of();
        }
        try {
            Document innerXml = textCodec.parseXml(rawLicenseData);
            return parseRestrictionsByModule(innerXml);
        } catch (Exception ex) {
            log.debug("Failed to parse encoded RMS licenseData payload", ex);
            return List.of();
        }
    }

    List<Map<String, String>> parseRestrictionsByModule(Document licenseDataXml) {
        Node restrictionsNode = firstElement(licenseDataXml.getElementsByTagName("restrictionsByModule"));
        if (restrictionsNode == null) {
            return List.of();
        }
        String licenseSerial = extractFirstText(licenseDataXml, "serialNumber");
        String companyName = textCodec.repairDisplayedText(extractFirstText(licenseDataXml, "companyName"));
        String generatedAt = extractFirstText(licenseDataXml, "generated");
        String overallExpiration = extractFirstText(licenseDataXml, "expiration");
        Map<String, Node> moduleNodes = parseModuleValueNodes(restrictionsNode);
        List<Map<String, String>> items = new java.util.ArrayList<>();
        for (Map.Entry<String, Node> entry : moduleNodes.entrySet()) {
            LinkedHashMap<String, String> item = buildLicenseItemFromModule(entry.getKey(), entry.getValue(), licenseSerial, companyName, generatedAt, overallExpiration);
            if (!item.isEmpty()) {
                items.add(item);
            }
        }
        return items;
    }

    Map<String, Node> parseModuleValueNodes(Node restrictionsNode) {
        Map<String, Node> modules = new TreeMap<>();
        String pendingKey = null;
        NodeList children = restrictionsNode.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child == null || child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            if ("k".equalsIgnoreCase(child.getNodeName())) {
                pendingKey = textCodec.trimText(child.getTextContent(), 100, "");
                continue;
            }
            if ("v".equalsIgnoreCase(child.getNodeName()) && StringUtils.hasText(pendingKey)) {
                modules.put(pendingKey, child);
                pendingKey = null;
            }
        }
        return modules;
    }

    LinkedHashMap<String, String> buildLicenseItemFromModule(String moduleId,
                                                                     Node moduleValueNode,
                                                                     String licenseSerial,
                                                                     String companyName,
                                                                     String generatedAt,
                                                                     String overallExpiration) {
        LinkedHashMap<String, String> item = new LinkedHashMap<>();
        if (!StringUtils.hasText(moduleId) || moduleValueNode == null) {
            return item;
        }
        item.put("id", moduleId);
        item.put("module_id", moduleId);
        item.put("title", resolveModuleTitle(moduleId));
        item.put("license_name", resolveModuleTitle(moduleId));
        if (StringUtils.hasText(licenseSerial)) {
            item.put("serial_number", licenseSerial);
        }
        if (StringUtils.hasText(companyName)) {
            item.put("company_name", companyName);
        }
        if (StringUtils.hasText(generatedAt)) {
            item.put("generated", generatedAt);
        }
        if (StringUtils.hasText(overallExpiration)) {
            item.put("license_expiration", overallExpiration);
        }
        appendNodeAttributes(item, moduleValueNode);
        String thresholdId = extractFirstDescendantText(moduleValueNode, "id");
        if (StringUtils.hasText(thresholdId)) {
            item.putIfAbsent("restriction_id", thresholdId);
        }
        String thresholdClass = extractThresholdClass(moduleValueNode);
        if (StringUtils.hasText(thresholdClass)) {
            item.put("threshold_class", thresholdClass);
        }
        String moduleExpiration = extractModuleExpiration(moduleValueNode);
        if (StringUtils.hasText(moduleExpiration)) {
            item.put("expiration", moduleExpiration);
            item.put("module_expiration", moduleExpiration);
        }
        String moduleFrom = extractFirstDescendantText(moduleValueNode, "from");
        if (StringUtils.hasText(moduleFrom)) {
            item.put("from", moduleFrom);
        }
        String connectionsCount = extractFirstDescendantText(moduleValueNode, "connectionsCount");
        if (StringUtils.hasText(connectionsCount)) {
            item.put("quantity", connectionsCount);
            item.put("connections_count", connectionsCount);
        }
        item.putIfAbsent("state", isModuleActive(moduleExpiration) ? "active" : "unknown");
        return item;
    }

    String resolveModuleTitle(String moduleId) {
        if (RMS_TARGET_LICENSE_ID.equals(moduleId)) {
            return RMS_TARGET_LICENSE_TITLE;
        }
        if (CHAIN_TARGET_LICENSE_ID.equals(moduleId)) {
            return CHAIN_TARGET_LICENSE_TITLE;
        }
        if (KIOSK_CONNECTOR_LICENSE_ID.equals(moduleId)) {
            return KIOSK_CONNECTOR_LICENSE_TITLE;
        }
        return "Модуль " + moduleId;
    }

    String extractThresholdClass(Node moduleValueNode) {
        NodeList thresholds = ((org.w3c.dom.Element) moduleValueNode).getElementsByTagName("threshold");
        Node thresholdNode = firstElement(thresholds);
        if (thresholdNode == null) {
            return "";
        }
        Node classAttribute = thresholdNode.getAttributes() != null ? thresholdNode.getAttributes().getNamedItem("cls") : null;
        return classAttribute != null ? textCodec.trimText(classAttribute.getNodeValue(), 255, "") : "";
    }

    String extractModuleExpiration(Node moduleValueNode) {
        for (String tagName : List.of("to", "expiration", "validTo", "valid_to", "threshold")) {
            String value = extractFirstDescendantText(moduleValueNode, tagName);
            if (!StringUtils.hasText(value)) {
                continue;
            }
            if (parseFlexibleDate(value) != null) {
                return value.trim();
            }
        }
        return "";
    }

    String extractFirstDescendantText(Node parent, String tagName) {
        if (parent == null || !StringUtils.hasText(tagName)) {
            return "";
        }
        if (parent instanceof org.w3c.dom.Element element) {
            NodeList nodes = element.getElementsByTagName(tagName);
            Node first = firstElement(nodes);
            if (first != null) {
                return textCodec.trimText(first.getTextContent(), 500, "");
            }
        }
        return "";
    }

    boolean isModuleActive(String moduleExpiration) {
        OffsetDateTime expiresAt = parseFlexibleDate(moduleExpiration);
        return expiresAt != null && !expiresAt.isBefore(OffsetDateTime.now(ZoneOffset.UTC));
    }

    Node firstElement(NodeList nodes) {
        if (nodes == null) {
            return null;
        }
        for (int index = 0; index < nodes.getLength(); index++) {
            Node node = nodes.item(index);
            if (node != null && node.getNodeType() == Node.ELEMENT_NODE) {
                return node;
            }
        }
        return null;
    }

    String buildLicenseDiagnosticExcerpt(String response, Document xml) {
        String encodedLicenseData = extractFirstText(xml, "licenseData");
        if (StringUtils.hasText(encodedLicenseData)) {
            String rawLicenseData = HtmlUtils.htmlUnescape(encodedLicenseData);
            String decodedLicenseData = textCodec.repairDisplayedText(rawLicenseData);
            String formattedInner = textCodec.formatDiagnosticText(decodedLicenseData, 12_000);
            if (StringUtils.hasText(formattedInner)) {
                return formattedInner;
            }
        }
        return textCodec.formatDiagnosticText(response, 12_000);
    }

    Map<String, String> findTargetLicense(List<Map<String, String>> details, LicenseDescriptor descriptor) {
        if (details == null || details.isEmpty()) {
            return null;
        }
        for (Map<String, String> item : details) {
            String licenseId = textCodec.firstNonBlank(
                item.get("id"),
                item.get("license_id"),
                item.get("licenseid"),
                item.get("product_id"),
                item.get("module_id"),
                item.get("type_id"),
                item.get("module_license_id")
            );
            if (descriptor.id().equals(StringUtils.trimWhitespace(licenseId))) {
                return item;
            }
        }
        for (Map<String, String> item : details) {
            String title = textCodec.firstNonBlank(
                item.get("title"),
                item.get("name"),
                item.get("license_name"),
                item.get("license"),
                item.get("type"),
                item.get("license_type"),
                item.get("product"),
                item.get("module_name"),
                item.get("display_name")
            );
            if (StringUtils.hasText(title) && descriptor.title().equalsIgnoreCase(title.trim())) {
                return item;
            }
        }
        for (Map<String, String> item : details) {
            if (containsTargetLicenseMarkers(item, descriptor)) {
                return item;
            }
        }
        return null;
    }

    void enrichLicenseDetails(LinkedHashMap<String, String> item, int index) {
        String title = textCodec.firstNonBlank(
            item.get("name"),
            item.get("license_name"),
            item.get("license"),
            item.get("type"),
            item.get("license_type"),
            item.get("module"),
            item.get("product"),
            item.get("module_name"),
            item.get("display_name"),
            "Лицензия #" + (index + 1)
        );
        item.putIfAbsent("title", title);
        String expiration = textCodec.firstNonBlank(
            item.get("expiration"),
            item.get("expires_at"),
            item.get("valid_to"),
            item.get("expire_date"),
            item.get("expiration_date"),
            item.get("expires"),
            item.get("expire"),
            item.get("date_to"),
            item.get("end_date"),
            item.get("date_end")
        );
        if (StringUtils.hasText(expiration)) {
            item.put("expiration", expiration);
        }
        String quantity = textCodec.firstNonBlank(
            item.get("amount"),
            item.get("count"),
            item.get("quantity"),
            item.get("limit"),
            item.get("connections")
        );
        if (StringUtils.hasText(quantity)) {
            item.put("quantity", quantity);
        }
        String state = textCodec.firstNonBlank(
            item.get("status"),
            item.get("state"),
            item.get("active"),
            item.get("enabled")
        );
        if (StringUtils.hasText(state)) {
            item.put("state", state);
        }
    }

    void appendNodeAttributes(LinkedHashMap<String, String> item, Node node) {
        if (item == null || node == null) {
            return;
        }
        NamedNodeMap attributes = node.getAttributes();
        if (attributes == null) {
            return;
        }
        for (int index = 0; index < attributes.getLength(); index++) {
            Node attribute = attributes.item(index);
            if (attribute == null) {
                continue;
            }
            String key = normalizeLicenseKey(attribute.getNodeName());
            String value = textCodec.trimText(attribute.getNodeValue(), 500, "");
            if (StringUtils.hasText(key) && StringUtils.hasText(value)) {
                item.putIfAbsent(key, value);
            }
        }
    }

    void collectLicenseNode(LinkedHashMap<String, String> item, Node node, String keyPrefix) {
        if (item == null || node == null || node.getNodeType() != Node.ELEMENT_NODE) {
            return;
        }
        String currentKey = normalizeLicenseKey(keyPrefix);
        appendNodeAttributes(item, node);
        if (StringUtils.hasText(currentKey)) {
            appendPrefixedAttributes(item, node, currentKey);
        }
        String value = textCodec.trimText(node.getTextContent(), 500, "");
        if (StringUtils.hasText(currentKey) && StringUtils.hasText(value)) {
            item.putIfAbsent(currentKey, value);
        }
        NodeList children = node.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child == null || child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            String childKey = normalizeLicenseKey(child.getNodeName());
            String nextPrefix = StringUtils.hasText(currentKey) ? currentKey + "_" + childKey : childKey;
            collectLicenseNode(item, child, nextPrefix);
        }
    }

    void appendPrefixedAttributes(LinkedHashMap<String, String> item, Node node, String keyPrefix) {
        NamedNodeMap attributes = node.getAttributes();
        if (attributes == null) {
            return;
        }
        for (int index = 0; index < attributes.getLength(); index++) {
            Node attribute = attributes.item(index);
            if (attribute == null) {
                continue;
            }
            String key = normalizeLicenseKey(keyPrefix + "_" + attribute.getNodeName());
            String value = textCodec.trimText(attribute.getNodeValue(), 500, "");
            if (StringUtils.hasText(key) && StringUtils.hasText(value)) {
                item.putIfAbsent(key, value);
            }
        }
    }

    boolean containsTargetLicenseMarkers(Map<String, String> item, LicenseDescriptor descriptor) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        String normalizedTitle = textCodec.normalizeSearchToken(descriptor.title());
        boolean hasTargetId = false;
        boolean hasTargetName = false;
        for (Map.Entry<String, String> entry : item.entrySet()) {
            String key = textCodec.normalizeSearchToken(entry.getKey());
            String value = textCodec.normalizeSearchToken(entry.getValue());
            if (!StringUtils.hasText(value)) {
                continue;
            }
            if (!hasTargetId && key.contains("id") && descriptor.id().equals(value)) {
                hasTargetId = true;
            }
            if (!hasTargetId && descriptor.id().equals(value)) {
                hasTargetId = true;
            }
            if (!hasTargetName && matchesLicenseTitleMarker(value, normalizedTitle, descriptor)) {
                hasTargetName = true;
            }
        }
        return hasTargetName && (hasTargetId || !item.containsKey("id"));
    }

    boolean matchesLicenseTitleMarker(String value, String normalizedTitle, LicenseDescriptor descriptor) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        if (value.equals(normalizedTitle)) {
            return true;
        }
        if (RMS_TARGET_LICENSE_ID.equals(descriptor.id())) {
            return value.contains("rms") && value.contains("front fast food");
        }
        if (CHAIN_TARGET_LICENSE_ID.equals(descriptor.id())) {
            return value.contains("chain") && value.contains("store connector");
        }
        if (KIOSK_CONNECTOR_LICENSE_ID.equals(descriptor.id())) {
            return value.contains("iikoconnector") && value.contains("kiosk");
        }
        return false;
    }

    LicenseDescriptor resolvePrimaryLicenseDescriptor(RmsLicenseMonitor monitor) {
        if (isIikoChainServer(monitor)) {
            return new LicenseDescriptor(CHAIN_TARGET_LICENSE_ID, CHAIN_TARGET_LICENSE_TITLE);
        }
        return new LicenseDescriptor(RMS_TARGET_LICENSE_ID, RMS_TARGET_LICENSE_TITLE);
    }

    LicenseDescriptor kioskConnectorLicenseDescriptor() {
        return new LicenseDescriptor(KIOSK_CONNECTOR_LICENSE_ID, KIOSK_CONNECTOR_LICENSE_TITLE);
    }

    boolean isIikoChainServer(RmsLicenseMonitor monitor) {
        return "IIKO_CHAIN".equals(resolveServerTypeKey(monitor));
    }

    boolean isIikoRmsServer(RmsLicenseMonitor monitor) {
        return "IIKO_RMS".equals(resolveServerTypeKey(monitor));
    }

    String normalizeServerTypeKey(String rawServerType) {
        if (!StringUtils.hasText(rawServerType)) {
            return "";
        }
        String normalized = rawServerType.trim()
            .replace('\\', '/')
            .replace('-', '_')
            .replace(' ', '_')
            .toLowerCase(Locale.ROOT);
        if (normalized.contains("iiko_chain") || normalized.contains("iikochain") || "chain".equals(normalized)) {
            return "IIKO_CHAIN";
        }
        if (normalized.contains("iiko_rms")
            || normalized.contains("iikorms")
            || normalized.contains("iiko_office")
            || normalized.contains("iikooffice")
            || "office".equals(normalized)
            || "default".equals(normalized)) {
            return "IIKO_RMS";
        }
        return "";
    }

    OffsetDateTime resolveLicenseExpiration(Map<String, String> targetLicense) {
        if (targetLicense == null || targetLicense.isEmpty()) {
            return null;
        }
        for (String key : List.of(
            "expiration",
            "expires_at",
            "valid_to",
            "expire_date",
            "expiration_date",
            "expires",
            "expire",
            "date_to",
            "end_date",
            "date_end",
            "module_expiration",
            "module_expires_at"
        )) {
            OffsetDateTime parsed = parseFlexibleDate(targetLicense.get(key));
            if (parsed != null) {
                return parsed;
            }
        }
        for (Map.Entry<String, String> entry : targetLicense.entrySet()) {
            String normalizedKey = textCodec.normalizeSearchToken(entry.getKey());
            if (!normalizedKey.contains("expir")
                && !normalizedKey.contains("valid_to")
                && !normalizedKey.contains("date_to")
                && !normalizedKey.contains("end_date")) {
                continue;
            }
            OffsetDateTime parsed = parseFlexibleDate(entry.getValue());
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    String normalizeLicenseKey(String rawKey) {
        if (!StringUtils.hasText(rawKey)) {
            return "";
        }
        StringBuilder normalized = new StringBuilder();
        boolean previousUnderscore = false;
        for (char ch : rawKey.trim().toCharArray()) {
            if (Character.isLetterOrDigit(ch)) {
                if (Character.isUpperCase(ch) && normalized.length() > 0 && !previousUnderscore) {
                    normalized.append('_');
                }
                normalized.append(Character.toLowerCase(ch));
                previousUnderscore = false;
            } else if (!previousUnderscore) {
                normalized.append('_');
                previousUnderscore = true;
            }
        }
        String value = normalized.toString().replaceAll("^_+|_+$", "");
        return value.replaceAll("_+", "_");
    }

    String writeLicenseDetails(List<Map<String, String>> items) throws Exception {
        return objectMapper.writeValueAsString(items == null ? List.of() : items);
    }

    record EndpointTarget(String normalizedAddress, String scheme, String host, int port) {
    }

    record HostPort(String host, Integer port) {
    }

    record ServerMetadata(String serverName, String serverType, String serverVersion, String serverEdition) {
    }

    record LicenseSnapshot(OffsetDateTime expiresAt, int daysLeft, String detailsJson, String debugExcerpt) {
    }

    record LicenseDescriptor(String id, String title) {
    }

    static final class LicenseSnapshotException extends Exception {
        private final String detailsJson;
        private final String debugExcerpt;

        LicenseSnapshotException(String message, String detailsJson, String debugExcerpt) {
            super(message);
            this.detailsJson = detailsJson;
            this.debugExcerpt = debugExcerpt;
        }

        String detailsJson() {
            return detailsJson;
        }

        String debugExcerpt() {
            return debugExcerpt;
        }
    }
}
