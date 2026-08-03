package com.example.panel.service;

import com.example.panel.service.NetBoxSyncSettingsService.NetBoxSyncSettings;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class NetBoxApiService {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public NetBoxApiService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public List<Map<String, Object>> fetchSites(NetBoxSyncSettings settings) {
        return fetchPaginated(settings, "/api/dcim/sites/?limit=100");
    }

    public List<Map<String, Object>> fetchDevices(NetBoxSyncSettings settings, String siteId) {
        return fetchPaginated(settings, "/api/dcim/devices/?limit=200&site_id=" + encode(siteId));
    }

    public List<Map<String, Object>> fetchCircuits(NetBoxSyncSettings settings, String siteId) {
        return fetchPaginated(settings, "/api/circuits/circuits/?limit=200&site_id=" + encode(siteId));
    }

    public List<Map<String, Object>> fetchSiteImages(NetBoxSyncSettings settings, String siteId) {
        return fetchPaginated(
                settings,
                "/api/extras/image-attachments/?limit=200&object_type=dcim.site&object_id=" + encode(siteId)
        );
    }

    public DownloadedFile downloadFile(NetBoxSyncSettings settings, String absoluteOrRelativeUrl, String fallbackFilename) {
        if (!StringUtils.hasText(absoluteOrRelativeUrl)) {
            throw new IllegalArgumentException("Не указан URL файла NetBox");
        }
        URI uri = resolveUri(settings.baseUrl(), absoluteOrRelativeUrl);
        HttpRequest request = baseRequest(settings, uri).GET().build();
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            ensureSuccess(response.statusCode(), uri.toString(), response.body() == null ? "" : new String(response.body(), StandardCharsets.UTF_8));
            String contentType = firstHeader(response, "Content-Type");
            String filename = extractFilename(uri.getPath(), fallbackFilename);
            return new DownloadedFile(
                    filename,
                    contentType,
                    new ByteArrayInputStream(response.body() == null ? new byte[0] : response.body())
            );
        } catch (IOException ex) {
            throw new IllegalStateException("Не удалось скачать файл NetBox: " + uri, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Загрузка файла NetBox была прервана: " + uri, ex);
        }
    }

    private List<Map<String, Object>> fetchPaginated(NetBoxSyncSettings settings, String pathOrUrl) {
        List<Map<String, Object>> items = new ArrayList<>();
        String nextUrl = pathOrUrl;
        while (StringUtils.hasText(nextUrl)) {
            Map<String, Object> page = fetchMap(settings, nextUrl);
            Object resultsRaw = page.get("results");
            if (resultsRaw instanceof List<?> results) {
                for (Object item : results) {
                    if (item instanceof Map<?, ?> map) {
                        items.add(castMap(map));
                    }
                }
            }
            Object nextRaw = page.get("next");
            nextUrl = nextRaw == null ? "" : String.valueOf(nextRaw).trim();
        }
        return items;
    }

    private Map<String, Object> fetchMap(NetBoxSyncSettings settings, String pathOrUrl) {
        URI uri = resolveUri(settings.baseUrl(), pathOrUrl);
        HttpRequest request = baseRequest(settings, uri).GET().build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            ensureSuccess(response.statusCode(), uri.toString(), response.body());
            return objectMapper.readValue(response.body(), MAP_TYPE);
        } catch (IOException ex) {
            throw new IllegalStateException("Не удалось прочитать ответ NetBox: " + uri, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Запрос к NetBox был прерван: " + uri, ex);
        }
    }

    private HttpRequest.Builder baseRequest(NetBoxSyncSettings settings, URI uri) {
        if (settings == null || !StringUtils.hasText(settings.baseUrl())) {
            throw new IllegalStateException("Не настроен base URL NetBox");
        }
        if (!StringUtils.hasText(settings.apiToken())) {
            throw new IllegalStateException("Не настроен API token NetBox");
        }
        return HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("Authorization", "Token " + settings.apiToken());
    }

    private void ensureSuccess(int statusCode, String url, String body) {
        if (statusCode >= 200 && statusCode < 300) {
            return;
        }
        String snippet = body == null ? "" : body.trim();
        if (snippet.length() > 300) {
            snippet = snippet.substring(0, 300);
        }
        throw new IllegalStateException("NetBox вернул HTTP " + statusCode + " для " + url + (snippet.isEmpty() ? "" : ": " + snippet));
    }

    private URI resolveUri(String baseUrl, String pathOrUrl) {
        URI base = URI.create(baseUrl.endsWith("/") ? baseUrl : baseUrl + "/");
        URI candidate = URI.create(pathOrUrl);
        return candidate.isAbsolute() ? candidate : base.resolve(pathOrUrl.startsWith("/") ? pathOrUrl.substring(1) : pathOrUrl);
    }

    private String firstHeader(HttpResponse<?> response, String name) {
        return response.headers().firstValue(name).orElse("");
    }

    private String extractFilename(String path, String fallbackFilename) {
        if (StringUtils.hasText(path)) {
            int slashIndex = path.lastIndexOf('/');
            String filename = slashIndex >= 0 ? path.substring(slashIndex + 1) : path;
            if (StringUtils.hasText(filename)) {
                return filename;
            }
        }
        return StringUtils.hasText(fallbackFilename) ? fallbackFilename : "netbox-file";
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Map<?, ?> source) {
        return (Map<String, Object>) source;
    }

    public record DownloadedFile(String fileName, String contentType, InputStream inputStream) {
    }
}
