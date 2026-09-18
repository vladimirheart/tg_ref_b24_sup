package com.example.panel.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.util.StringUtils;

record BackupDestinationSettings(
        String type,
        String destinationPath,
        String server,
        String share,
        String subpath,
        String authMode,
        String username,
        String credentialRef
) {

    static final String DESTINATION_KEY = "IGUANA_BACKUP_DESTINATION_DIR";
    static final String TYPE_KEY = "IGUANA_BACKUP_DESTINATION_TYPE";
    static final String SERVER_KEY = "IGUANA_BACKUP_DESTINATION_SERVER";
    static final String SHARE_KEY = "IGUANA_BACKUP_DESTINATION_SHARE";
    static final String SUBPATH_KEY = "IGUANA_BACKUP_DESTINATION_SUBPATH";
    static final String AUTH_MODE_KEY = "IGUANA_BACKUP_DESTINATION_AUTH_MODE";
    static final String USERNAME_KEY = "IGUANA_BACKUP_DESTINATION_USERNAME";
    static final String CREDENTIAL_REF_KEY = "IGUANA_BACKUP_DESTINATION_CREDENTIAL_REF";

    static final String TYPE_LOCAL = "local-filesystem";
    static final String TYPE_SMB = "smb-unc";
    static final String TYPE_MOUNTED = "mounted-network-filesystem";

    static final String AUTH_NONE = "none";
    static final String AUTH_CURRENT_IDENTITY = "current-identity";
    static final String AUTH_CREDENTIAL_REF = "credential-ref";
    static final String AUTH_OS_MANAGED = "os-managed";

    static final List<String> SMB_READ_ONLY_PROBE_STEPS = List.of(
            "host",
            "tcp_445",
            "authentication",
            "share",
            "path",
            "read",
            "free_space"
    );

    static final List<String> FILESYSTEM_READ_ONLY_PROBE_STEPS = List.of(
            "path",
            "read",
            "free_space"
    );

    static final List<String> PROBE_ERROR_CODES = List.of(
            "HOST_UNREACHABLE",
            "PORT_UNREACHABLE",
            "AUTH_REQUIRED",
            "AUTH_FAILED",
            "SHARE_NOT_FOUND",
            "PATH_NOT_FOUND",
            "ACCESS_DENIED",
            "READ_FAILED",
            "WRITE_FAILED",
            "DELETE_FAILED",
            "INSUFFICIENT_SPACE",
            "TIMEOUT",
            "TLS_ERROR",
            "HOST_KEY_MISMATCH",
            "DEPENDENCY_MISSING",
            "UNKNOWN_ERROR"
    );

    private static final int MAX_PATH_LENGTH = 2048;
    private static final int MAX_FIELD_LENGTH = 512;
    private static final Set<String> TYPES = Set.of(TYPE_LOCAL, TYPE_SMB, TYPE_MOUNTED);
    private static final Set<String> SMB_AUTH = Set.of(AUTH_CURRENT_IDENTITY, AUTH_CREDENTIAL_REF);

    static BackupDestinationSettings fromStored(Map<String, String> values) {
        Map<String, String> source = values != null ? values : Map.of();
        String rawPath = normalizePath(source.get(DESTINATION_KEY));
        String type = normalizeType(source.get(TYPE_KEY), rawPath);

        if (TYPE_SMB.equals(type)) {
            UncParts parsed = parseUnc(rawPath);
            String server = normalizeSegment(firstText(source.get(SERVER_KEY), parsed.server()), "server");
            String share = normalizeSegment(firstText(source.get(SHARE_KEY), parsed.share()), "share");
            String subpath = normalizeSubpath(firstText(source.get(SUBPATH_KEY), parsed.subpath()));
            String authMode = normalizeSmbAuth(source.get(AUTH_MODE_KEY));
            String username = normalizeField(source.get(USERNAME_KEY));
            String credentialRef = normalizeField(source.get(CREDENTIAL_REF_KEY));
            if (AUTH_CREDENTIAL_REF.equals(authMode) && !StringUtils.hasText(credentialRef)) {
                authMode = AUTH_CURRENT_IDENTITY;
            }
            return new BackupDestinationSettings(
                    TYPE_SMB,
                    buildUnc(server, share, subpath),
                    server,
                    share,
                    subpath,
                    authMode,
                    username,
                    credentialRef
            );
        }

        if (TYPE_MOUNTED.equals(type)) {
            return new BackupDestinationSettings(
                    TYPE_MOUNTED,
                    rawPath,
                    "",
                    "",
                    "",
                    AUTH_OS_MANAGED,
                    "",
                    ""
            );
        }

        return new BackupDestinationSettings(
                TYPE_LOCAL,
                rawPath,
                "",
                "",
                "",
                AUTH_NONE,
                "",
                ""
        );
    }

    static BackupDestinationSettings fromPayload(Map<String, Object> payload) {
        Map<String, Object> source = payload != null ? payload : Map.of();
        rejectInlineSecrets(source);

        String rawPath = normalizePath(asString(source.get("destination_path")));
        String type = normalizeType(asString(source.get("destination_type")), rawPath);

        if (TYPE_SMB.equals(type)) {
            UncParts parsed = parseUnc(rawPath);
            String server = normalizeSegment(
                    firstText(asString(source.get("destination_server")), parsed.server()),
                    "server"
            );
            String share = normalizeSegment(
                    firstText(asString(source.get("destination_share")), parsed.share()),
                    "share"
            );
            String subpath = normalizeSubpath(
                    firstText(asString(source.get("destination_subpath")), parsed.subpath())
            );
            String authMode = normalizeSmbAuth(asString(source.get("destination_auth_mode")));
            String username = normalizeField(asString(source.get("destination_username")));
            String credentialRef = normalizeField(asString(source.get("destination_credential_ref")));

            if (!StringUtils.hasText(server) || !StringUtils.hasText(share)) {
                throw new IllegalArgumentException("Для SMB укажите server и share.");
            }
            if (AUTH_CREDENTIAL_REF.equals(authMode) && !StringUtils.hasText(credentialRef)) {
                throw new IllegalArgumentException(
                        "Для SMB credential-ref укажите ссылку на host-managed credential."
                );
            }

            return new BackupDestinationSettings(
                    TYPE_SMB,
                    buildUnc(server, share, subpath),
                    server,
                    share,
                    subpath,
                    authMode,
                    username,
                    credentialRef
            );
        }

        if (!StringUtils.hasText(rawPath)) {
            throw new IllegalArgumentException("Укажите путь backup-хранилища.");
        }
        if (isUnc(rawPath)) {
            throw new IllegalArgumentException("UNC path должен использовать destination type smb-unc.");
        }

        if (TYPE_MOUNTED.equals(type)) {
            return new BackupDestinationSettings(
                    TYPE_MOUNTED,
                    rawPath,
                    "",
                    "",
                    "",
                    AUTH_OS_MANAGED,
                    "",
                    ""
            );
        }

        return new BackupDestinationSettings(
                TYPE_LOCAL,
                rawPath,
                "",
                "",
                "",
                AUTH_NONE,
                "",
                ""
        );
    }

    boolean configured() {
        if (TYPE_SMB.equals(type)) {
            return StringUtils.hasText(server) && StringUtils.hasText(share);
        }
        return StringUtils.hasText(destinationPath);
    }

    boolean localFilesystem() {
        return TYPE_LOCAL.equals(type);
    }

    boolean credentialsConfigured() {
        return TYPE_SMB.equals(type)
                && AUTH_CREDENTIAL_REF.equals(authMode)
                && StringUtils.hasText(credentialRef);
    }

    List<String> probeSteps() {
        return TYPE_SMB.equals(type)
                ? SMB_READ_ONLY_PROBE_STEPS
                : FILESYSTEM_READ_ONLY_PROBE_STEPS;
    }

    String drClassification(boolean externalFailureDomainAcknowledged) {
        return drClassification(externalFailureDomainAcknowledged, false);
    }

    String drClassification(boolean externalFailureDomainAcknowledged, boolean probeVerified) {
        if (!configured()) {
            return "not_configured";
        }
        if (localFilesystem()) {
            return "not_dr";
        }
        if (probeVerified) {
            return externalFailureDomainAcknowledged ? "dr_verified" : "probe_verified";
        }
        return externalFailureDomainAcknowledged
                ? "acknowledged_unverified"
                : "external_unverified";
    }

    String signature() {
        String canonical = String.join("\u001f",
                type, destinationPath, server, share, subpath, authMode, username, credentialRef);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    Map<String, String> persistedFields() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(DESTINATION_KEY, destinationPath);
        values.put(TYPE_KEY, type);
        values.put(SERVER_KEY, server);
        values.put(SHARE_KEY, share);
        values.put(SUBPATH_KEY, subpath);
        values.put(AUTH_MODE_KEY, authMode);
        values.put(USERNAME_KEY, username);
        values.put(CREDENTIAL_REF_KEY, credentialRef);
        return values;
    }

    private static String normalizeType(String raw, String destinationPath) {
        String value = normalizeField(raw).toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(value)) {
            value = isUnc(destinationPath) ? TYPE_SMB : TYPE_LOCAL;
        }
        if (!TYPES.contains(value)) {
            throw new IllegalArgumentException("Неподдерживаемый тип backup destination: " + value);
        }
        return value;
    }

    private static String normalizeSmbAuth(String raw) {
        String value = normalizeField(raw).toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(value)) {
            return AUTH_CURRENT_IDENTITY;
        }
        if (!SMB_AUTH.contains(value)) {
            throw new IllegalArgumentException("Неподдерживаемый SMB auth mode: " + value);
        }
        return value;
    }

    private static String normalizePath(String raw) {
        String value = raw != null ? raw.trim() : "";
        validateControlCharacters(value, "Путь backup-хранилища");
        if (value.length() > MAX_PATH_LENGTH) {
            throw new IllegalArgumentException("Путь backup-хранилища слишком длинный.");
        }
        return value;
    }

    private static String normalizeField(String raw) {
        String value = raw != null ? raw.trim() : "";
        validateControlCharacters(value, "Поле backup destination");
        if (value.length() > MAX_FIELD_LENGTH) {
            throw new IllegalArgumentException("Поле backup destination слишком длинное.");
        }
        return value;
    }

    private static String normalizeSegment(String raw, String label) {
        String value = normalizeField(raw);
        value = trimSlashes(value);
        if (value.indexOf('\\') >= 0 || value.indexOf('/') >= 0) {
            throw new IllegalArgumentException("SMB " + label + " не должен содержать разделители пути.");
        }
        return value;
    }

    private static String normalizeSubpath(String raw) {
        String value = normalizeField(raw).replace('/', '\\');
        return trimSlashes(value);
    }

    private static void validateControlCharacters(String value, String label) {
        if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\u0000') >= 0) {
            throw new IllegalArgumentException(label + " содержит недопустимые управляющие символы.");
        }
    }

    private static void rejectInlineSecrets(Map<String, Object> source) {
        List<String> forbidden = new ArrayList<>();
        for (String key : source.keySet()) {
            if (key == null) {
                continue;
            }
            String normalized = key.trim().toLowerCase(Locale.ROOT);
            if (normalized.equals("destination_password")
                    || normalized.equals("smb_password")
                    || normalized.equals("destination_secret")
                    || normalized.equals("private_key")
                    || normalized.equals("access_key")) {
                forbidden.add(key);
            }
        }
        if (!forbidden.isEmpty()) {
            throw new IllegalArgumentException(
                    "Backup secrets нельзя сохранять в backup.properties; используйте credential_ref."
            );
        }
    }

    private static UncParts parseUnc(String raw) {
        if (!isUnc(raw)) {
            return new UncParts("", "", "");
        }
        String body = raw.substring(2);
        String[] parts = body.split("[\\\\/]+");
        String server = parts.length > 0 ? parts[0] : "";
        String share = parts.length > 1 ? parts[1] : "";
        String subpath = "";
        if (parts.length > 2) {
            List<String> tail = new ArrayList<>();
            for (int index = 2; index < parts.length; index++) {
                if (StringUtils.hasText(parts[index])) {
                    tail.add(parts[index]);
                }
            }
            subpath = String.join("\\", tail);
        }
        return new UncParts(server, share, subpath);
    }

    private static String buildUnc(String server, String share, String subpath) {
        if (!StringUtils.hasText(server) || !StringUtils.hasText(share)) {
            return "";
        }
        StringBuilder value = new StringBuilder("\\\\")
                .append(server)
                .append('\\')
                .append(share);
        if (StringUtils.hasText(subpath)) {
            value.append('\\').append(subpath);
        }
        return value.toString();
    }

    private static boolean isUnc(String raw) {
        return raw != null && (raw.startsWith("\\\\") || raw.startsWith("//"));
    }

    private static String firstText(String first, String fallback) {
        return StringUtils.hasText(first) ? first.trim() : (fallback != null ? fallback.trim() : "");
    }

    private static String trimSlashes(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && (value.charAt(start) == '\\' || value.charAt(start) == '/')) {
            start++;
        }
        while (end > start && (value.charAt(end - 1) == '\\' || value.charAt(end - 1) == '/')) {
            end--;
        }
        return value.substring(start, end);
    }

    private static String asString(Object raw) {
        return raw != null ? raw.toString() : "";
    }

    private record UncParts(String server, String share, String subpath) {
    }
}
