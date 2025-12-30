package com.example.panel.controller;

import com.example.panel.service.PermissionService;
import com.example.panel.service.SharedConfigService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class AuthManagementApiController {

    private static final List<Map<String, String>> AVAILABLE_PAGE_PERMISSIONS = List.of(
        Map.of("key", "dialogs", "label", "Диалоги"),
        Map.of("key", "tasks", "label", "Задачи"),
        Map.of("key", "clients", "label", "Клиенты"),
        Map.of("key", "object_passports", "label", "Паспорта объектов"),
        Map.of("key", "external_forms", "label", "Внешние формы"),
        Map.of("key", "knowledge_base", "label", "База знаний"),
        Map.of("key", "dashboard", "label", "Дашборд"),
        Map.of("key", "analytics", "label", "Аналитика"),
        Map.of("key", "channels", "label", "Каналы"),
        Map.of("key", "settings", "label", "Настройки"),
        Map.of("key", "user_management", "label", "Пользователи и роли")
    );

    private static final List<Map<String, String>> EDITABLE_FIELD_PERMISSIONS = List.of(
        Map.of("key", "user.create", "label", "Создание пользователя"),
        Map.of("key", "user.username", "label", "Изменение логина пользователя"),
        Map.of("key", "user.password", "label", "Изменение пароля пользователя"),
        Map.of("key", "user.role", "label", "Изменение роли пользователя"),
        Map.of("key", "user.delete", "label", "Удаление пользователя"),
        Map.of("key", "user.block", "label", "Блокировка пользователя"),
        Map.of("key", "role.create", "label", "Создание роли"),
        Map.of("key", "role.name", "label", "Изменение названия роли"),
        Map.of("key", "role.description", "label", "Изменение описания роли"),
        Map.of("key", "role.pages", "label", "Настройка доступа к страницам"),
        Map.of("key", "role.fields.edit", "label", "Настройка прав редактирования полей"),
        Map.of("key", "role.fields.view", "label", "Настройка прав просмотра полей"),
        Map.of("key", "role.delete", "label", "Удаление роли")
    );

    private static final List<Map<String, String>> VIEWABLE_FIELD_PERMISSIONS = List.of(
        Map.of("key", "user.password", "label", "Просмотр пароля пользователя")
    );

    private final JdbcTemplate usersJdbcTemplate;
    private final SharedConfigService sharedConfigService;
    private final PermissionService permissionService;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final Path avatarsRoot;
    private final Set<String> userColumns;

    public AuthManagementApiController(@Qualifier("usersJdbcTemplate") JdbcTemplate usersJdbcTemplate,
                                       SharedConfigService sharedConfigService,
                                       PermissionService permissionService,
                                       PasswordEncoder passwordEncoder,
                                       ObjectMapper objectMapper,
                                       @Value("${app.storage.avatars:attachments/avatars}") String avatarsDir) throws IOException {
        this.usersJdbcTemplate = usersJdbcTemplate;
        this.sharedConfigService = sharedConfigService;
        this.permissionService = permissionService;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
        this.avatarsRoot = ensureDirectory(avatarsDir);
        this.userColumns = loadUserColumns();
    }

    @GetMapping("/auth/state")
    @PreAuthorize("hasAuthority('PAGE_SETTINGS') or hasAuthority('PAGE_USERS')")
    public Map<String, Object> getAuthState(Authentication authentication) {
        List<Map<String, Object>> users = fetchUsers(authentication);
        List<Map<String, Object>> roles = fetchRoles();

        Map<String, Object> catalog = Map.of(
            "pages", AVAILABLE_PAGE_PERMISSIONS,
            "fields", Map.of("edit", EDITABLE_FIELD_PERMISSIONS, "view", VIEWABLE_FIELD_PERMISSIONS)
        );

        Map<String, Object> capabilities = Map.of(
            "fields", Map.of(
                "edit", buildCapabilityMap(authentication, EDITABLE_FIELD_PERMISSIONS),
                "view", buildCapabilityMap(authentication, VIEWABLE_FIELD_PERMISSIONS)
            )
        );

        Long currentUserId = resolveCurrentUserId(authentication);

        return Map.of(
            "users", users,
            "roles", roles,
            "catalog", catalog,
            "capabilities", capabilities,
            "current_user_id", currentUserId,
            "org_structure", sharedConfigService.loadOrgStructure()
        );
    }

    @PostMapping({"/auth/org-structure", "/auth/org-structure/"})
    @PreAuthorize("hasAuthority('PAGE_SETTINGS') or hasAuthority('PAGE_USERS')")
    public Map<String, Object> updateOrgStructure(@org.springframework.web.bind.annotation.RequestBody Map<String, Object> payload) {
        Object structure = payload.getOrDefault("org_structure", payload);
        sharedConfigService.saveOrgStructure(structure);
        return Map.of("success", true, "org_structure", structure);
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('PAGE_SETTINGS') or hasAuthority('PAGE_USERS')")
    public List<Map<String, Object>> listUsers(Authentication authentication) {
        return fetchUsers(authentication);
    }

    @PostMapping({"/users", "/users/"})
    @PreAuthorize("hasAuthority('PAGE_SETTINGS') or hasAuthority('PAGE_USERS')")
    public Map<String, Object> createUser(HttpServletRequest request) throws IOException {
        return createUserFromPayload(RequestPayloadUtils.readPayload(request, objectMapper));
    }

    private Map<String, Object> createUserFromPayload(Map<String, Object> payload) {
        String username = stringValue(payload.get("username"));
        String password = stringValue(payload.get("password"));
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            return Map.of("success", false, "error", "Имя пользователя и пароль не могут быть пустыми");
        }
        Integer existing = usersJdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM users WHERE lower(username) = lower(?)",
            Integer.class,
            username
        );
        if (existing != null && existing > 0) {
            return Map.of("success", false, "error", "Пользователь уже существует");
        }

        String hashed = passwordEncoder.encode(password);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("username", username);
        fields.put("password", hashed);
        if (userColumns.contains("password_hash")) {
            fields.put("password_hash", hashed);
        }
        if (userColumns.contains("enabled")) {
            fields.put("enabled", true);
        }
        if (userColumns.contains("registration_date")) {
            fields.put("registration_date", OffsetDateTime.now().toString());
        }

        addOptionalUserField(fields, payload, "full_name");
        addOptionalUserField(fields, payload, "role");
        addOptionalUserField(fields, payload, "photo");
        addOptionalUserField(fields, payload, "birth_date");
        addOptionalUserField(fields, payload, "email");
        addOptionalUserField(fields, payload, "department");

        if (userColumns.contains("phones")) {
            fields.put("phones", encodePhones(payload.get("phones")));
        }

        if (payload.containsKey("role_id") && userColumns.contains("role_id")) {
            Object roleId = payload.get("role_id");
            fields.put("role_id", roleId);
        }

        String sql = "INSERT INTO users (" + String.join(", ", fields.keySet()) + ") VALUES (" +
            String.join(", ", fields.keySet().stream().map(key -> "?").toList()) + ")";
        usersJdbcTemplate.update(sql, fields.values().toArray());
        return Map.of("success", true);
    }

    @RequestMapping(value = "/users/{userId}", method = {RequestMethod.PUT, RequestMethod.PATCH})
    @PreAuthorize("hasAuthority('PAGE_SETTINGS') or hasAuthority('PAGE_USERS')")
    public Map<String, Object> updateUser(@PathVariable long userId,
                                          @RequestBody Map<String, Object> payload) {
        List<String> updates = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        updateColumnIfPresent(payload, updates, params, "username", "username");
        updateColumnIfPresent(payload, updates, params, "full_name", "full_name");
        updateColumnIfPresent(payload, updates, params, "email", "email");
        updateColumnIfPresent(payload, updates, params, "department", "department");
        updateColumnIfPresent(payload, updates, params, "photo", "photo");
        updateColumnIfPresent(payload, updates, params, "birth_date", "birth_date");

        if (payload.containsKey("phones") && userColumns.contains("phones")) {
            updates.add("phones = ?");
            params.add(encodePhones(payload.get("phones")));
        }

        if (payload.containsKey("role_id") && userColumns.contains("role_id")) {
            updates.add("role_id = ?");
            params.add(payload.get("role_id"));
        }

        if (payload.containsKey("role") && userColumns.contains("role")) {
            updates.add("role = ?");
            params.add(stringValue(payload.get("role")));
        }

        if (payload.containsKey("is_blocked") && userColumns.contains("is_blocked")) {
            boolean blocked = Boolean.TRUE.equals(payload.get("is_blocked"));
            updates.add("is_blocked = ?");
            params.add(blocked ? 1 : 0);
            if (userColumns.contains("enabled")) {
                updates.add("enabled = ?");
                params.add(!blocked);
            }
        }

        if (payload.containsKey("password")) {
            String password = stringValue(payload.get("password"));
            if (StringUtils.hasText(password)) {
                String hashed = passwordEncoder.encode(password);
                updates.add("password = ?");
                params.add(hashed);
                if (userColumns.contains("password_hash")) {
                    updates.add("password_hash = ?");
                    params.add(hashed);
                }
            }
        }

        if (updates.isEmpty()) {
            return Map.of("success", false, "error", "Нет данных для обновления");
        }

        params.add(userId);
        String sql = "UPDATE users SET " + String.join(", ", updates) + " WHERE id = ?";
        usersJdbcTemplate.update(sql, params.toArray());
        return Map.of("success", true);
    }

    @DeleteMapping("/users/{userId}")
    @PreAuthorize("hasAuthority('PAGE_SETTINGS') or hasAuthority('PAGE_USERS')")
    public Map<String, Object> deleteUser(@PathVariable long userId) {
        int removed = usersJdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
        if (removed == 0) {
            return Map.of("success", false, "error", "Пользователь не найден");
        }
        return Map.of("success", true);
    }

    @GetMapping("/users/{userId}/password")
    @PreAuthorize("hasAuthority('PAGE_SETTINGS') or hasAuthority('PAGE_USERS')")
    public Map<String, Object> getUserPassword() {
        return Map.of("success", false, "error", "Просмотр пароля недоступен");
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('PAGE_SETTINGS') or hasAuthority('PAGE_USERS')")
    public List<Map<String, Object>> listRoles() {
        return fetchRoles();
    }

    @PostMapping({"/roles", "/roles/"})
    @PreAuthorize("hasAuthority('PAGE_SETTINGS') or hasAuthority('PAGE_USERS')")
    public Map<String, Object> createRole(HttpServletRequest request) throws IOException {
        return createRoleFromPayload(RequestPayloadUtils.readPayload(request, objectMapper));
    }

    private Map<String, Object> createRoleFromPayload(Map<String, Object> payload) {
        String name = stringValue(payload.get("name"));
        if (!StringUtils.hasText(name)) {
            return Map.of("success", false, "error", "Название роли не может быть пустым");
        }
        Integer existing = usersJdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM roles WHERE lower(name) = lower(?)",
            Integer.class,
            name
        );
        if (existing != null && existing > 0) {
            return Map.of("success", false, "error", "Роль уже существует");
        }
        String description = stringValue(payload.get("description"));
        String permissions = encodePermissions(payload.get("permissions"));
        usersJdbcTemplate.update(
            "INSERT INTO roles(name, description, permissions) VALUES (?, ?, ?)",
            name, description, permissions
        );
        return Map.of("success", true);
    }

    @RequestMapping(value = "/roles/{roleId}", method = {RequestMethod.PUT, RequestMethod.PATCH})
    @PreAuthorize("hasAuthority('PAGE_SETTINGS') or hasAuthority('PAGE_USERS')")
    public Map<String, Object> updateRole(@PathVariable long roleId,
                                          @RequestBody Map<String, Object> payload) {
        List<String> updates = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        if (payload.containsKey("name")) {
            String name = stringValue(payload.get("name"));
            if (!StringUtils.hasText(name)) {
                return Map.of("success", false, "error", "Название роли не может быть пустым");
            }
            updates.add("name = ?");
            params.add(name);
        }
        if (payload.containsKey("description")) {
            updates.add("description = ?");
            params.add(stringValue(payload.get("description")));
        }
        if (payload.containsKey("permissions")) {
            updates.add("permissions = ?");
            params.add(encodePermissions(payload.get("permissions")));
        }

        if (updates.isEmpty()) {
            return Map.of("success", false, "error", "Нет данных для обновления");
        }

        params.add(roleId);
        String sql = "UPDATE roles SET " + String.join(", ", updates) + " WHERE id = ?";
        usersJdbcTemplate.update(sql, params.toArray());
        return Map.of("success", true);
    }

    @DeleteMapping("/roles/{roleId}")
    @PreAuthorize("hasAuthority('PAGE_SETTINGS') or hasAuthority('PAGE_USERS')")
    public Map<String, Object> deleteRole(@PathVariable long roleId) {
        if (userColumns.contains("role_id")) {
            Integer usage = usersJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE role_id = ?",
                Integer.class,
                roleId
            );
            if (usage != null && usage > 0) {
                return Map.of("success", false, "error", "Роль используется пользователями");
            }
        }
        int removed = usersJdbcTemplate.update("DELETE FROM roles WHERE id = ?", roleId);
        if (removed == 0) {
            return Map.of("success", false, "error", "Роль не найдена");
        }
        return Map.of("success", true);
    }

    @PostMapping(value = {"/users/photo-upload", "/users/photo-upload/"}, consumes = "multipart/form-data")
    @PreAuthorize("hasAuthority('PAGE_SETTINGS') or hasAuthority('PAGE_USERS')")
    public Map<String, Object> uploadUserPhoto(@RequestParam("photo") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            return Map.of("success", false, "error", "Файл не может быть пустым");
        }
        String extension = extractExtension(file.getOriginalFilename());
        if (!isAllowedImageExtension(extension)) {
            return Map.of("success", false, "error", "Поддерживаются изображения PNG, JPG, GIF или WebP.");
        }
        String filename = System.currentTimeMillis() + "_" + java.util.UUID.randomUUID() + extension;
        Path target = avatarsRoot.resolve(filename).normalize();
        Files.copy(file.getInputStream(), target);
        String url = "/api/attachments/avatars/" + filename;
        return Map.of("success", true, "url", url, "filename", filename);
    }

    private List<Map<String, Object>> fetchUsers(Authentication authentication) {
        String usersQuery = buildUsersQuery();
        List<Map<String, Object>> rows = usersJdbcTemplate.queryForList(usersQuery);
        Long currentId = resolveCurrentUserId(authentication);
        boolean canEditPassword = permissionService.hasAuthority(authentication, "PAGE_SETTINGS");
        boolean canEditUsername = permissionService.hasAuthority(authentication, "PAGE_SETTINGS");
        boolean canEditRole = permissionService.hasAuthority(authentication, "PAGE_SETTINGS");
        boolean canDeleteUser = permissionService.hasAuthority(authentication, "PAGE_SETTINGS");
        boolean canBlockUser = permissionService.hasAuthority(authentication, "PAGE_SETTINGS");

        List<Map<String, Object>> users = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Long userId = row.get("id") instanceof Number n ? n.longValue() : null;
            boolean isSelf = userId != null && userId.equals(currentId);
            Map<String, Object> user = new LinkedHashMap<>();
            user.put("id", userId);
            user.put("username", row.get("username"));
            user.put("full_name", row.get("full_name"));
            user.put("role", row.getOrDefault("role_name", row.get("role")));
            user.put("role_id", row.get("role_id"));
            user.put("photo", row.get("photo"));
            user.put("registration_date", row.get("registration_date"));
            user.put("birth_date", row.get("birth_date"));
            user.put("email", row.get("email"));
            user.put("department", row.get("department"));
            user.put("phones", decodePhones(row.get("phones")));
            user.put("is_blocked", asBoolean(row.get("is_blocked")));
            user.put("is_self", isSelf);
            user.put("role_is_admin", "admin".equalsIgnoreCase(stringValue(user.get("role"))));
            user.put("can_view_password", false);
            user.put("can_edit_password", isSelf || canEditPassword);
            user.put("can_edit_username", canEditUsername);
            user.put("can_edit_role", canEditRole);
            user.put("can_delete", canDeleteUser && !isSelf);
            user.put("can_block", canBlockUser && !isSelf);
            users.add(user);
        }
        return users;
    }

    private String buildUsersQuery() {
        if (userColumns.contains("role_id")) {
            return "SELECT u.*, r.name AS role_name, r.permissions AS role_permissions " +
                "FROM users u LEFT JOIN roles r ON r.id = u.role_id ORDER BY lower(u.username)";
        }
        return "SELECT u.* FROM users u ORDER BY lower(u.username)";
    }

    private List<Map<String, Object>> fetchRoles() {
        List<Map<String, Object>> rows = usersJdbcTemplate.queryForList(
            "SELECT id, name, description, permissions FROM roles ORDER BY lower(name)"
        );
        List<Map<String, Object>> roles = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> role = new LinkedHashMap<>();
            role.put("id", row.get("id"));
            role.put("name", row.get("name"));
            role.put("description", row.get("description"));
            role.put("permissions", parsePermissions(row.get("permissions")));
            roles.add(role);
        }
        return roles;
    }

    private Map<String, Boolean> buildCapabilityMap(Authentication authentication, List<Map<String, String>> catalog) {
        Map<String, Boolean> result = new LinkedHashMap<>();
        boolean allowed = permissionService.hasAuthority(authentication, "PAGE_SETTINGS");
        for (Map<String, String> entry : catalog) {
            result.put(entry.get("key"), allowed);
        }
        return result;
    }

    private Long resolveCurrentUserId(Authentication authentication) {
        if (authentication == null || !StringUtils.hasText(authentication.getName())) {
            return null;
        }
        List<Map<String, Object>> rows = usersJdbcTemplate.queryForList(
            "SELECT id FROM users WHERE lower(username) = lower(?) LIMIT 1",
            authentication.getName()
        );
        if (rows.isEmpty()) {
            return null;
        }
        Object id = rows.get(0).get("id");
        return id instanceof Number n ? n.longValue() : null;
    }

    private String encodePermissions(Object raw) {
        if (raw == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(raw);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private Map<String, Object> parsePermissions(Object raw) {
        if (raw == null) {
            return Map.of("pages", List.of(), "fields", Map.of("edit", List.of(), "view", List.of()));
        }
        String text = raw.toString().trim();
        if (text.isEmpty()) {
            return Map.of("pages", List.of(), "fields", Map.of("edit", List.of(), "view", List.of()));
        }
        try {
            return objectMapper.readValue(text, Map.class);
        } catch (JsonProcessingException ex) {
            return Map.of("pages", List.of(), "fields", Map.of("edit", List.of(), "view", List.of()));
        }
    }

    private String encodePhones(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(raw);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private List<Map<String, Object>> decodePhones(Object raw) {
        if (raw == null) {
            return List.of();
        }
        String text = raw.toString().trim();
        if (text.isEmpty()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(text, List.class);
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private void updateColumnIfPresent(Map<String, Object> payload, List<String> updates, List<Object> params,
                                       String payloadKey, String column) {
        if (!payload.containsKey(payloadKey) || !userColumns.contains(column)) {
            return;
        }
        updates.add(column + " = ?");
        params.add(payload.get(payloadKey));
    }

    private void addOptionalUserField(Map<String, Object> fields, Map<String, Object> payload, String key) {
        if (payload.containsKey(key) && userColumns.contains(key)) {
            Object value = payload.get(key);
            if (value != null) {
                fields.put(key, value);
            }
        }
    }

    private String extractExtension(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "";
        }
        int idx = filename.lastIndexOf('.');
        if (idx == -1) {
            return "";
        }
        return filename.substring(idx).toLowerCase(Locale.ROOT);
    }

    private boolean isAllowedImageExtension(String extension) {
        return Set.of(".jpg", ".jpeg", ".png", ".gif", ".webp").contains(extension);
    }

    private String stringValue(Object raw) {
        return raw == null ? "" : raw.toString().trim();
    }

    private boolean asBoolean(Object raw) {
        if (raw instanceof Boolean b) {
            return b;
        }
        if (raw instanceof Number n) {
            return n.intValue() != 0;
        }
        if (raw instanceof String s) {
            return s.equalsIgnoreCase("true") || s.equals("1");
        }
        return false;
    }

    private Path ensureDirectory(String dir) throws IOException {
        Path path = Paths.get(dir).toAbsolutePath().normalize();
        Files.createDirectories(path);
        return path;
    }

    private Set<String> loadUserColumns() {
        try {
            List<Map<String, Object>> rows = usersJdbcTemplate.queryForList("PRAGMA table_info(users)");
            Set<String> columns = new LinkedHashSet<>();
            for (Map<String, Object> row : rows) {
                Object name = row.get("name");
                if (name != null) {
                    columns.add(name.toString());
                }
            }
            return columns;
        } catch (Exception ex) {
            return new LinkedHashSet<>();
        }
    }


}
