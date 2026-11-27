package com.example.panel.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Internal text boundary; no repositories, queues, scheduling or Spring lifecycle. */
final class RmsTextCodec {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger("com.example.panel.service.RmsLicenseMonitoringService");
    private final ObjectMapper objectMapper;

    RmsTextCodec(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    private static final Charset WINDOWS_OEM_CHARSET = Charset.forName("IBM866");

    private static final Charset WINDOWS_ANSI_CHARSET = Charset.forName("windows-1251");

    Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    String trimText(String value, int limit, String fallback) {
        String safe = StringUtils.hasText(value) ? value.trim() : fallback;
        if (!StringUtils.hasText(safe)) {
            return fallback;
        }
        if (safe.length() <= limit) {
            return safe;
        }
        return safe.substring(0, limit);
    }

    String decodeHttpResponse(HttpResponse<byte[]> response) {
        byte[] body = response.body() != null ? response.body() : new byte[0];
        Charset declaredCharset = detectHttpCharset(response.headers(), body);
        return decodeBytes(body, declaredCharset);
    }

    Charset detectHttpCharset(HttpHeaders headers, byte[] body) {
        String contentType = headers != null ? headers.firstValue("Content-Type").orElse("") : "";
        if (StringUtils.hasText(contentType)) {
            String lower = contentType.toLowerCase(Locale.ROOT);
            int charsetIndex = lower.indexOf("charset=");
            if (charsetIndex >= 0) {
                String value = contentType.substring(charsetIndex + "charset=".length()).trim();
                int semicolonIndex = value.indexOf(';');
                if (semicolonIndex >= 0) {
                    value = value.substring(0, semicolonIndex).trim();
                }
                value = value.replace("\"", "");
                try {
                    return Charset.forName(value);
                } catch (Exception ignored) {
                }
            }
        }
        String asciiPrefix = new String(body, 0, Math.min(body.length, 256), StandardCharsets.US_ASCII);
        java.util.regex.Matcher matcher = java.util.regex.Pattern
            .compile("encoding\\s*=\\s*['\"]([^'\"]+)['\"]", java.util.regex.Pattern.CASE_INSENSITIVE)
            .matcher(asciiPrefix);
        if (matcher.find()) {
            try {
                return Charset.forName(matcher.group(1).trim());
            } catch (Exception ignored) {
            }
        }
        return StandardCharsets.UTF_8;
    }

    String decodeProcessOutput(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        if (!isWindows()) {
            return decodeBytes(bytes, StandardCharsets.UTF_8);
        }
        return decodeBytes(bytes, WINDOWS_OEM_CHARSET, WINDOWS_ANSI_CHARSET, StandardCharsets.UTF_8);
    }

    String decodeBytes(byte[] bytes, Charset... preferredCharsets) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        java.util.ArrayList<Charset> candidates = new java.util.ArrayList<>();
        if (preferredCharsets != null) {
            for (Charset charset : preferredCharsets) {
                if (charset != null && !candidates.contains(charset)) {
                    candidates.add(charset);
                }
            }
        }
        for (Charset charset : List.of(StandardCharsets.UTF_8, WINDOWS_OEM_CHARSET, WINDOWS_ANSI_CHARSET)) {
            if (!candidates.contains(charset)) {
                candidates.add(charset);
            }
        }
        String best = new String(bytes, candidates.get(0));
        int bestScore = scoreDecodedText(best);
        for (int index = 1; index < candidates.size(); index++) {
            String candidate = decodeStrict(bytes, candidates.get(index));
            int score = scoreDecodedText(candidate);
            if (score < bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return normalizeLineEndings(best);
    }

    String decodeMaybeMojibake(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = normalizeLineEndings(value);
        byte[] bytes = new byte[normalized.length()];
        for (int index = 0; index < normalized.length(); index++) {
            bytes[index] = (byte) normalized.charAt(index);
        }
        String repaired = decodeBytes(bytes, StandardCharsets.UTF_8, WINDOWS_ANSI_CHARSET, WINDOWS_OEM_CHARSET);
        return scoreDecodedText(repaired) < scoreDecodedText(normalized) ? repaired : normalized;
    }

    String decodeStrict(byte[] bytes, Charset charset) {
        try {
            return charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
        } catch (CharacterCodingException ignored) {
            return new String(bytes, charset);
        }
    }

    int scoreDecodedText(String value) {
        if (value == null) {
            return Integer.MAX_VALUE;
        }
        int score = 0;
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (ch == '\uFFFD') {
                score += 100;
                continue;
            }
            if (Character.isISOControl(ch) && ch != '\r' && ch != '\n' && ch != '\t') {
                score += 5;
                continue;
            }
            if (isBoxDrawingChar(ch)) {
                score += 3;
                continue;
            }
            if (isSuspiciousMojibakeChar(ch)) {
                score += 3;
            }
        }
        if (containsCyrillic(value)) {
            score -= 8;
        }
        if (value.contains("<") && value.contains(">")) {
            score -= 3;
        }
        return score;
    }

    void addCandidate(List<String> candidates, String value) {
        if (StringUtils.hasText(value) && !candidates.contains(value)) {
            candidates.add(value);
        }
    }

    String recodeText(String value, Charset sourceCharset, Charset targetCharset) {
        if (!StringUtils.hasText(value) || sourceCharset == null || targetCharset == null) {
            return "";
        }
        byte[] bytes = value.getBytes(sourceCharset);
        return normalizeLineEndings(new String(bytes, targetCharset));
    }

    boolean containsCyrillic(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if ((ch >= '\u0410' && ch <= '\u044F') || ch == '\u0401' || ch == '\u0451') {
                return true;
            }
        }
        return false;
    }

    boolean isBoxDrawingChar(char ch) {
        return ch >= '\u2500' && ch <= '\u257F';
    }

    boolean isSuspiciousMojibakeChar(char ch) {
        return ch == '\u00D0'
            || ch == '\u00D1'
            || ch == '\u00E2'
            || ch == '\u00C2'
            || ch == '\u00C3';
    }

    int countOccurrences(String value, String token) {
        int count = 0;
        int offset = 0;
        while (true) {
            int index = value.indexOf(token, offset);
            if (index < 0) {
                return count;
            }
            count++;
            offset = index + token.length();
        }
    }

    String sanitizeServerName(String rawServerName, String currentServerName, String fallbackHost) {
        String decoded = trimText(repairDisplayedText(rawServerName), 255, "");
        if (StringUtils.hasText(decoded) && !looksLikeBrokenText(decoded)) {
            return decoded;
        }
        String current = trimText(repairDisplayedText(currentServerName), 255, "");
        if (StringUtils.hasText(current) && !looksLikeBrokenText(current)) {
            return current;
        }
        if (StringUtils.hasText(decoded) && !looksLikeBrokenText(decoded)) {
            return decoded;
        }
        return trimText(fallbackHost, 255, fallbackHost);
    }

    String resolveDisplayServerNameInternal(String metadataServerName,
                                                    String currentServerName,
                                                    String fallbackHost,
                                                    String detailsJson) {
        String sanitizedMetadata = sanitizeServerName(metadataServerName, currentServerName, fallbackHost);
        if (StringUtils.hasText(sanitizedMetadata) && !looksLikeBrokenText(sanitizedMetadata) && !sanitizedMetadata.equals(fallbackHost)) {
            return sanitizedMetadata;
        }
        String companyName = extractCompanyNameFromLicenseDetails(detailsJson);
        if (StringUtils.hasText(companyName) && !looksLikeBrokenText(companyName)) {
            return trimText(companyName, 255, fallbackHost);
        }
        if (StringUtils.hasText(sanitizedMetadata) && !looksLikeBrokenText(sanitizedMetadata)) {
            return sanitizedMetadata;
        }
        return trimText(fallbackHost, 255, fallbackHost);
    }

    String extractCompanyNameFromLicenseDetails(String detailsJson) {
        if (!StringUtils.hasText(detailsJson)) {
            return "";
        }
        try {
            List<LinkedHashMap<String, String>> items = objectMapper.readValue(
                detailsJson,
                new TypeReference<List<LinkedHashMap<String, String>>>() {
                }
            );
            for (Map<String, String> item : items) {
                String companyName = repairDisplayedText(firstNonBlank(
                    item.get("company_name"),
                    item.get("company"),
                    item.get("organization_name")
                ));
                if (StringUtils.hasText(companyName) && !looksLikeBrokenText(companyName)) {
                    return companyName;
                }
            }
        } catch (Exception ex) {
            log.debug("Failed to extract company_name from RMS license details", ex);
        }
        return "";
    }

    String repairDisplayedText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = normalizeLineEndings(HtmlUtils.htmlUnescape(value));
        List<String> candidates = new java.util.ArrayList<>();
        candidates.add(normalized);
        addCandidate(candidates, recodeText(normalized, WINDOWS_OEM_CHARSET, StandardCharsets.UTF_8));
        addCandidate(candidates, recodeText(normalized, WINDOWS_ANSI_CHARSET, StandardCharsets.UTF_8));
        addCandidate(candidates, recodeText(normalized, StandardCharsets.UTF_8, WINDOWS_ANSI_CHARSET));
        addCandidate(candidates, recodeText(normalized, StandardCharsets.UTF_8, WINDOWS_OEM_CHARSET));
        addCandidate(candidates, decodeMaybeMojibake(normalized));

        String best = normalized;
        int bestScore = scoreDecodedText(normalized);
        for (String candidate : candidates) {
            if (!StringUtils.hasText(candidate)) {
                continue;
            }
            int score = scoreDecodedText(candidate);
            if (looksLikeBrokenText(best) && !looksLikeBrokenText(candidate)) {
                best = candidate;
                bestScore = score;
                continue;
            }
            if (score < bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    boolean looksLikeBrokenText(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String trimmed = value.trim();
        if (containsCyrillic(trimmed)) {
            return false;
        }
        return trimmed.contains("в•Ё")
            || trimmed.contains("в•¤")
            || isLikelyBoxDrawingMojibake(trimmed)
            || scoreDecodedText(trimmed) >= 12;
    }

    boolean isLikelyBoxDrawingMojibake(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (isBoxDrawingChar(ch) || isSuspiciousMojibakeChar(ch)) {
                return true;
            }
        }
        return false;
    }

    String formatDiagnosticText(String value, int limit) {
        String normalized = normalizeLineEndings(HtmlUtils.htmlUnescape(value));
        if (!StringUtils.hasText(normalized)) {
            return "";
        }
        String formatted = repairDisplayedText(normalized).trim();
        if (looksLikeXml(formatted)) {
            formatted = prettyPrintXml(formatted);
        }
        return trimText(formatted, limit, "");
    }

    boolean looksLikeXml(String value) {
        return StringUtils.hasText(value) && value.trim().startsWith("<") && value.contains(">");
    }

    String prettyPrintXml(String xml) {
        try {
            Document document = parseXml(xml);
            javax.xml.transform.Transformer transformer = javax.xml.transform.TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            java.io.StringWriter writer = new java.io.StringWriter();
            transformer.transform(new javax.xml.transform.dom.DOMSource(document), new javax.xml.transform.stream.StreamResult(writer));
            return normalizeLineEndings(writer.toString());
        } catch (Exception ignored) {
            return normalizeLineEndings(xml);
        }
    }

    String normalizeLineEndings(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    String normalizeSearchToken(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace('ё', 'е');
        return normalized.replaceAll("[^a-z0-9а-я]+", " ").trim().replaceAll("\\s+", " ");
    }

    String resolveExceptionMessage(Throwable throwable, String fallbackMessage) {
        if (throwable == null) {
            return fallbackMessage;
        }
        if (StringUtils.hasText(throwable.getMessage())) {
            return throwable.getMessage().trim();
        }
        String simpleName = throwable.getClass().getSimpleName();
        if (StringUtils.hasText(simpleName)) {
            return fallbackMessage + " (" + simpleName + ")";
        }
        return fallbackMessage;
    }

    String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }
}
