package com.example.panel.security;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice
public class SettingsReadResponseAdvice implements ResponseBodyAdvice<Object> {
    @Override public boolean supports(MethodParameter type, Class<? extends HttpMessageConverter<?>> converter) { return true; }

    @Override
    @SuppressWarnings("unchecked")
    public Object beforeBodyWrite(Object body, MethodParameter type, MediaType mediaType,
                                  Class<? extends HttpMessageConverter<?>> converter, ServerHttpRequest request,
                                  ServerHttpResponse response) {
        if (!(body instanceof Map<?, ?> raw)) return body;
        Map<String, Object> values = (Map<String, Object>) raw;
        String path = request.getURI().getPath();
        if (request instanceof org.springframework.http.server.ServletServerHttpRequest servlet)
            path = SettingsWritePermissionFilter.requestPath(servlet.getServletRequest());
        var actor = SecurityContextHolder.getContext().getAuthentication();
        if (path.startsWith("/api/settings/page-data/")) {
            Map<String, Object> result = new LinkedHashMap<>(values);
            if (values.get("data") instanceof Map<?, ?> data)
                result.put("data", SettingsSecretMasker.section((Map<String, Object>) data, actor));
            return result;
        }
        if (path.startsWith("/api/settings/parameters")) {
            if (values.get("data") instanceof Map<?, ?> data) {
                Map<String, Object> result = new LinkedHashMap<>(values);
                result.put("data", parameters((Map<String, Object>) data));
                return result;
            }
            return parameters(values);
        }
        if (path.startsWith("/api/channels") || path.startsWith("/api/bot-credentials")
                || path.startsWith("/api/bots/") || path.equals("/api/channel-notifications")) {
            if (PanelAccessPolicy.canEdit(actor, "channels")) return body;
            Map<String, Object> result = new LinkedHashMap<>((Map<String, Object>) SettingsSecretMasker.redact(values));
            if (path.endsWith("/runtime-contract")) result.put("contract", Map.of("restricted", true));
            return result;
        }
        if (path.startsWith("/api/settings/"))
            return SettingsSecretMasker.forBlock(body, SettingsWritePermissionFilter.blockFor(path), actor);
        return body;
    }

    private Map<String, Object> parameters(Map<String, Object> groups) {
        Map<String, Object> result = new LinkedHashMap<>();
        var actor = SecurityContextHolder.getContext().getAuthentication();
        groups.forEach((key, value) -> result.put(key, SettingsSecretMasker.forBlock(value, PanelAccessPolicy.parameterBlock(key), actor)));
        return result;
    }
}
