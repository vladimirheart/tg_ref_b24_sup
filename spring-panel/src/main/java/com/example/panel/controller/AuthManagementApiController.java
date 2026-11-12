package com.example.panel.controller;

import com.example.panel.support.JdbcSchemaInspector;
import com.example.panel.security.PanelUserPrincipal;
import com.example.panel.service.PermissionService;
import com.example.panel.service.PanelUserPhotoService;
import com.example.panel.service.SharedConfigService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
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
    private final PanelUserPhotoService panelUserPhotoService;
    private final ObjectMapper objectMapper;
    private volatile Set<String> userColumns;

    public AuthManagementApiController(@Qualifier("usersJdbcTemplate") JdbcTemplate usersJdbcTemplate,
                                       SharedConfigService sharedConfigService,
                                       PermissionService permissionService,
                                       PasswordEncoder passwordEncoder,
                                       PanelUserPhotoService panelUserPhotoService,
                                       ObjectMapper objectMapper) {
        this.usersJdbcTemplate = usersJdbcTemplate;
        this.sharedConfigService = sharedConfigService;
        this.permissionService = permissionService;
        this.passwordEncoder = passwordEncoder;
        this.panelUserPhotoService = panelUserPhotoService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/auth/state")
    @PreAuthorize("hasAuthority('PAGE_SETTINGS') or hasAuthority('PAGE_USERS')")
    public Map<String, Object> getAuthState(Authentication authentication) {
        List<Map<String, Object>> users = fetchUsers(authentication);
        List<Map<String, Object>> roles = fetchRoles();
        List<Map<String, String>> editableFields = new ArrayList<>(EDITABLE_FIELD_PERMISSIONS);
        com.example.panel.security.PanelAccessPolicy.BLOCKS.forEach((key, label) ->
                editableFields.add(Map.of("key", "settings." + key, "label", "Изменение настроек: " + label)));

        Map<String, Object> catalog = Map.of(
            "pages", com.example.panel.security.PanelAccessPolicy.PAGE_CATALOG.stream()
                    .map(page -> Map.of("key", page.key(), "label", page.label())).toList(),
            "legacy_pages", com.example.panel.security.PanelAccessPolicy.LEGACY_PAGES.keySet(),
            "page_access_note", com.example.panel.security.PanelAccessPolicy.PAGE_ACCESS_NOTE,
            "fields", Map.of("edit", editableFields, "view", VIEWABLE_FIELD_PERMISSIONS)
        );

        Map<String, Object> capabilities = Map.of(
            "fields", Map.of(
                "edit", buildCapabilityMap(authentication, editableFields, "edit"),
                "view", buildCapabilityMap(authentication, VIEWABLE_FIELD_PERMISSIONS, "view")
            )
        );

        Long currentUserId = resolveCurrentUserId(authentication);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("users", users);
        response.put("roles", roles);
        response.put("catalog", catalog);
        response.put("capabilities", capabilities);
        response.put("current_user_id", currentUserId);
        response.put("org_structure", sharedConfigService.loadOrgStructure());
        return response;
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
    public Map<String, Object> createUser(Authentication authentication, HttpServletRequest request) throws IOException {
        if (!hasEditPermission(authentication, "user.create")) {
            return Map.of("success", false, "error", "Недостаточно прав для создания пользователя");
        }
        Map<String, Object> payload = RequestPayloadUtils.readPayload(request, objectMapper);
        if (payload.get("role_id") != null && !hasEditPermission(authentication, "user.role"))
            throw new org.springframework.security.access.AccessDeniedException("Нет права назначать роли");
        requireAssignableRole(authentication, payload.get("role_id"));
        if ("admin".equalsIgnoreCase(stringValue(payload.get("username")))
                && !permissionService.isSuperUser(authentication))
            throw new org.springframework.security.access.AccessDeniedException("Системный логин защищён");
        return createUserFromPayload(payload);
    }

    private Map<String, Object> createUserFromPayload(Map<String, Object> payload) {
        Set<String> userColumns = loadUserColumns();
        String username = normalizeCredentialLogin(payload.get("username"));
        String password = payload.get("password") instanceof String text ? text : "";
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            return Map.of("success", false, "error", "Имя пользователя и пароль не могут быть пустыми");
        }
        String loginError = credentialLoginError(username);
        if (!loginError.isEmpty()) return credentialFailure("username", loginError);
        String passwordError = credentialPasswordError(password);
        if (!passwordError.isEmpty()) return credentialFailure("password", passwordError);
        Integer existing = usersJdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM users WHERE lower(username) = lower(?)",
            Integer.class,
            username
        );
        if (existing != null && existing > 0) {
            return Map.of("success", false, "error", "Пользователь уже существует");
        }

        // Conservative conflict detection only: never resolve an existing identity by trimming it.
        Integer legacyCollision = usersJdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM users WHERE lower(trim(username)) = lower(?)", Integer.class, username);
        if (legacyCollision != null && legacyCollision > 0) return credentialFailure("username", "Пользователь с таким логином уже существует, включая старый логин с пробелами.");
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
            fields.put("registration_date", OffsetDateTime.now());
        }

        addOptionalUserField(fields, payload, userColumns, "full_name");
        addOptionalUserField(fields, payload, userColumns, "role");
        addOptionalUserField(fields, payload, userColumns, "photo");
        addOptionalUserField(fields, payload, userColumns, "birth_date");
        addOptionalUserField(fields, payload, userColumns, "email");
        addOptionalUserField(fields, payload, userColumns, "department");

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
                                          Authentication authentication,
                                          @RequestBody Map<String, Object> payload) {
        Set<String> userColumns = loadUserColumns();
        List<String> updates = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        Long currentUserId = resolveCurrentUserId(authentication);
        boolean isSelf = currentUserId != null && currentUserId.equals(userId);

        if (!isSelf) requireUnprotectedAccount(authentication, userId);

        if (hasEditPermission(authentication, "user.username") && payload.containsKey("username") && userColumns.contains("username")) {
            if (!(payload.get("username") instanceof String rawUsername)) {
                return credentialFailure("username", "Укажите логин пользователя.");
            }
            List<Map<String, Object>> current = usersJdbcTemplate.queryForList("SELECT username FROM users WHERE id = ?", userId);
            boolean unchanged = current.size() == 1 && rawUsername.equals(current.get(0).get("username"));
            if (!unchanged) {
                String username = normalizeCredentialLogin(rawUsername);
                if ("admin".equalsIgnoreCase(username) && !permissionService.isSuperUser(authentication))
                    throw new org.springframework.security.access.AccessDeniedException("Системный логин защищён");
                String error = credentialLoginError(username);
                if (!error.isEmpty()) return credentialFailure("username", error);
                Integer existing = usersJdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM users WHERE lower(username) = lower(?) AND id <> ?", Integer.class, username, userId);
                if (existing != null && existing > 0) return credentialFailure("username", "Пользователь уже существует");
                Integer legacyCollision = usersJdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM users WHERE lower(trim(username)) = lower(?) AND id <> ?", Integer.class, username, userId);
                if (legacyCollision != null && legacyCollision > 0) return credentialFailure("username", "Пользователь с таким логином уже существует, включая старый логин с пробелами.");
                updates.add("username = ?");
                params.add(username);
            }
        }
        if (hasEditPermission(authentication, "user.username") || isSelf) {
            updateColumnIfPresent(payload, updates, params, userColumns, "full_name", "full_name");
            updateColumnIfPresent(payload, updates, params, userColumns, "email", "email");
            updateColumnIfPresent(payload, updates, params, userColumns, "department", "department");
            updateColumnIfPresent(payload, updates, params, userColumns, "photo", "photo");
            updateColumnIfPresent(payload, updates, params, userColumns, "birth_date", "birth_date");
        }

        if ((hasEditPermission(authentication, "user.username") || isSelf)
            && payload.containsKey("phones") && userColumns.contains("phones")) {
            updates.add("phones = ?");
            params.add(encodePhones(payload.get("phones")));
        }

        if (hasEditPermission(authentication, "user.role")
            && payload.containsKey("role_id") && userColumns.contains("role_id")) {
            requireAssignableRole(authentication, payload.get("role_id"));
            updates.add("role_id = ?");
            params.add(payload.get("role_id"));
        }

        if (hasEditPermission(authentication, "user.role")
            && payload.containsKey("role") && userColumns.contains("role")) {
            updates.add("role = ?");
            params.add(stringValue(payload.get("role")));
        }

        if (hasEditPermission(authentication, "user.block")
            && payload.containsKey("is_blocked") && userColumns.contains("is_blocked")) {
            if (isSelf) {
                return Map.of("success", false, "error", "Нельзя заблокировать собственную учётную запись");
            }
            boolean blocked = Boolean.TRUE.equals(payload.get("is_blocked"));
            updates.add("is_blocked = ?");
            params.add(blocked);
            if (userColumns.contains("enabled")) {
                updates.add("enabled = ?");
                params.add(!blocked);
            }
        }

        if ((hasEditPermission(authentication, "user.password") || isSelf) && payload.containsKey("password")) {
            if (!(payload.get("password") instanceof String password)) {
                return credentialFailure("password", "Укажите пароль.");
            }
            if (!password.isEmpty()) {
                String error = credentialPasswordError(password);
                if (!error.isEmpty()) return credentialFailure("password", error);
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

        if (updates.contains("photo = ?") && !StringUtils.hasText(stringValue(payload.get("photo")))) {
            panelUserPhotoService.deleteUserAvatarFiles(userId);
        }

        return Map.of("success", true);
    }

    @DeleteMapping("/users/{userId}")
    @PreAuthorize("hasAuthority('PAGE_SETTINGS') or hasAuthority('PAGE_USERS')")
    public Map<String, Object> deleteUser(@PathVariable long userId, Authentication authentication) {
        if (!hasEditPermission(authentication, "user.delete")) {
            return Map.of("success", false, "error", "Недостаточно прав для удаления пользователя");
        }
        requireUnprotectedAccount(authentication, userId);
        Long currentUserId = resolveCurrentUserId(authentication);
        if (currentUserId != null && currentUserId.equals(userId)) {
            return Map.of("success", false, "error", "Нельзя удалить собственную учётную запись");
        }
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
    public Map<String, Object> createRole(Authentication authentication, HttpServletRequest request) throws IOException {
        if (!hasEditPermission(authentication, "role.create")) {
            return Map.of("success", false, "error", "Недостаточно прав для создания роли");
        }
        Map<String, Object> payload = RequestPayloadUtils.readPayload(request, objectMapper);
        requireDelegableRole(authentication, payload.get("name"), payload.get("permissions"));
        requireInitialRolePermissions(authentication, payload.get("permissions"));
        return createRoleFromPayload(payload);
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
                                          Authentication authentication,
                                          @RequestBody Map<String, Object> payload) {
        if (!permissionService.isSuperUser(authentication)) {
            var existingRole = usersJdbcTemplate.queryForList("SELECT name FROM roles WHERE id = ?", roleId);
            if ((!existingRole.isEmpty() && UserPermissionsController.protectedRole(existingRole.get(0).get("name")))
                    || UserPermissionsController.protectedRole(payload.get("name")))
                throw new org.springframework.security.access.AccessDeniedException("Системная роль защищена");
        }
        List<String> updates = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        if (payload.containsKey("name")) {
            if (!hasEditPermission(authentication, "role.name")) {
                return Map.of("success", false, "error", "Недостаточно прав для изменения названия роли");
            }
            String name = stringValue(payload.get("name"));
            if (!StringUtils.hasText(name)) {
                return Map.of("success", false, "error", "Название роли не может быть пустым");
            }
            updates.add("name = ?");
            params.add(name);
        }
        if (payload.containsKey("description")) {
            if (!hasEditPermission(authentication, "role.description")) {
                return Map.of("success", false, "error", "Недостаточно прав для изменения описания роли");
            }
            updates.add("description = ?");
            params.add(stringValue(payload.get("description")));
        }
        if (payload.containsKey("permissions")) {
            if (!(hasEditPermission(authentication, "role.pages") || hasEditPermission(authentication, "role.fields.edit")
                    || hasEditPermission(authentication, "role.fields.view")))
                return Map.of("success", false, "error", "Недостаточно прав для изменения разрешений роли");
            requireRolePermissionUpdate(authentication, roleId, payload.get("permissions"));
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
    public Map<String, Object> deleteRole(@PathVariable long roleId, Authentication authentication) {
        if (!hasEditPermission(authentication, "role.delete")) {
            return Map.of("success", false, "error", "Недостаточно прав для удаления роли");
        }
        if (!permissionService.isSuperUser(authentication)) {
            var role = usersJdbcTemplate.queryForList("SELECT name FROM roles WHERE id = ?", roleId);
            if (role.stream().anyMatch(row -> UserPermissionsController.protectedRole(row.get("name"))))
                throw new org.springframework.security.access.AccessDeniedException("Системная роль защищена");
        }
        Set<String> userColumns = loadUserColumns();
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
    public Map<String, Object> uploadUserPhoto(Authentication authentication,
                                               @RequestParam("photo") MultipartFile file,
                                               @RequestParam(value = "userId", required = false) Long userId) throws IOException {
        if (file.isEmpty()) {
            return Map.of("success", false, "error", "Файл не может быть пустым");
        }
        if (userId == null && !hasEditPermission(authentication, "user.create"))
            throw new org.springframework.security.access.AccessDeniedException("Нет права загружать фото для нового пользователя");
        if (userId != null && !canEditUserDetails(authentication, userId)) {
            return Map.of("success", false, "error", "Not enough permissions to update user photo.");
        }
        if (userId != null && !userId.equals(resolveCurrentUserId(authentication)))
            requireUnprotectedAccount(authentication, userId);
        String extension = extractExtension(file.getOriginalFilename());
        if (!isAllowedImageExtension(extension)) {
            return Map.of("success", false, "error", "Поддерживаются изображения PNG, JPG, GIF или WebP.");
        }
        PanelUserPhotoService.StoredAvatar storedAvatar = panelUserPhotoService.storeUploadedAvatar(userId, file);
        String url = storedAvatar.url();
        if (userId != null && !persistUserPhoto(userId, url)) {
            return Map.of(
                "success", false,
                "error", "Файл загружен, но ссылка на фото не сохранилась в профиле пользователя."
            );
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("url", url);
        response.put("photo", url);
        response.put("filename", storedAvatar.storedName());
        response.put("userId", userId);
        response.put("persisted", userId != null);
        return response;
    }

    private List<Map<String, Object>> fetchUsers(Authentication authentication) {
        Set<String> userColumns = loadUserColumns();
        String usersQuery = buildUsersQuery(userColumns);
        List<Map<String, Object>> rows = usersJdbcTemplate.queryForList(usersQuery);
        Long currentId = resolveCurrentUserId(authentication);
        boolean canEditPassword = hasEditPermission(authentication, "user.password");
        boolean canEditUsername = hasEditPermission(authentication, "user.username");
        boolean canEditRole = hasEditPermission(authentication, "user.role");
        boolean canDeleteUser = hasEditPermission(authentication, "user.delete");
        boolean canBlockUser = hasEditPermission(authentication, "user.block");

        List<Map<String, Object>> users = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Long userId = row.get("id") instanceof Number n ? n.longValue() : null;
            boolean isSelf = userId != null && userId.equals(currentId);
            Map<String, Object> user = new LinkedHashMap<>();
            user.put("id", userId);
            user.put("permissions_user_id", userId == null ? "" : userId.toString());
            user.put("username", row.get("username"));
            user.put("full_name", row.get("full_name"));
            user.put("role", row.getOrDefault("role_name", row.get("role")));
            user.put("role_id", row.get("role_id"));
            user.put("photo", panelUserPhotoService.resolveUserAvatarUrl(userId, stringValue(row.get("photo")), null));
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

    private String buildUsersQuery(Set<String> userColumns) {
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

    private Map<String, Boolean> buildCapabilityMap(Authentication authentication, List<Map<String, String>> catalog, String mode) {
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (Map<String, String> entry : catalog) {
            result.put(entry.get("key"), hasFieldPermission(authentication, entry.get("key"), mode));
        }
        return result;
    }

    private boolean hasEditPermission(Authentication authentication, String permissionKey) {
        return hasFieldPermission(authentication, permissionKey, "edit");
    }

    private boolean canEditUserDetails(Authentication authentication, Long userId) {
        if (userId == null) {
            return false;
        }
        Long currentUserId = resolveCurrentUserId(authentication);
        boolean isSelf = currentUserId != null && currentUserId.equals(userId);
        return isSelf || hasEditPermission(authentication, "user.username");
    }

    private boolean persistUserPhoto(long userId, String photoUrl) {
        Set<String> userColumns = loadUserColumns();
        if (!userColumns.contains("photo")) {
            return false;
        }

        int updated = usersJdbcTemplate.update("UPDATE users SET photo = ? WHERE id = ?", photoUrl, userId);
        if (updated != 1) {
            return false;
        }

        List<String> persisted = usersJdbcTemplate.query(
            "SELECT photo FROM users WHERE id = ?",
            (rs, rowNum) -> rs.getString("photo"),
            userId
        );
        return persisted.size() == 1 && photoUrl.equals(stringValue(persisted.get(0)));
    }

    private boolean hasFieldPermission(Authentication authentication, String permissionKey, String mode) {
        if (!StringUtils.hasText(permissionKey)) {
            return false;
        }
        String authority = com.example.panel.security.PanelAccessPolicy.editAuthority(permissionKey);
        Set<String> tokenRights = authentication == null ? Set.of() : authentication.getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority).collect(java.util.stream.Collectors.toSet());
        if ("edit".equals(mode) && tokenRights.contains("DENY_" + authority)) return false;
        if (permissionKey.startsWith("settings."))
            return com.example.panel.security.PanelAccessPolicy.has(authentication, authority);
        if (permissionService.isSuperUser(authentication)
            || permissionService.hasAuthority(authentication, "ROLE_PORTAL_ADMIN")) {
            return true;
        }
        if (!permissionService.hasAuthority(authentication, "PAGE_SETTINGS")
                && !permissionService.hasAuthority(authentication, "PAGE_USERS")) {
            return false;
        }
        Map<String, Object> currentPermissions = resolveCurrentRolePermissions(authentication);
        if (!Boolean.TRUE.equals(currentPermissions.get("__verified_account"))) return false;
        if ("edit".equals(mode) && tokenRights.contains(authority)) {
            Long id = resolveCurrentUserId(authentication);
            List<String> direct = usersJdbcTemplate.query("SELECT authority FROM user_authorities WHERE user_id = ?",
                    (rs, row) -> rs.getString("authority"), id);
            if (direct.contains("DENY_" + authority)) return false;
            if (direct.contains(authority)) return true;
        }
        return containsPermission(extractFieldPermissions(currentPermissions, mode), permissionKey);
    }

    private void requireRolePermissionUpdate(Authentication actor, long roleId, Object raw) {
        if (raw != null && !(raw instanceof Map<?, ?>))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST);
        if (permissionService.isSuperUser(actor)) return;
        Map<?, ?> requested = raw instanceof Map<?, ?> map ? map : Map.of();
        var rows = usersJdbcTemplate.queryForList("SELECT name, permissions FROM roles WHERE id = ?", roleId);
        if (rows.size() != 1) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        boolean admin = permissionService.isSuperUser(actor);
        if (!admin && UserPermissionsController.protectedRole(rows.get(0).get("name")))
            throw new org.springframework.security.access.AccessDeniedException("Системная роль защищена");
        Map<String, Object> previous = parsePermissions(rows.get(0).get("permissions"));
        Object nextFields = requested.get("fields");
        Map<?, ?> previousFields = previous.get("fields") instanceof Map<?, ?> map ? map : Map.of();
        Map<?, ?> fields = nextFields instanceof Map<?, ?> map ? map : Map.of();
        if ((!permissionValues(previous.get("pages")).equals(permissionValues(requested.get("pages"))) && !hasEditPermission(actor, "role.pages"))
                || (!permissionValues(previousFields.get("edit")).equals(permissionValues(fields.get("edit"))) && !hasEditPermission(actor, "role.fields.edit"))
                || (!permissionValues(previousFields.get("view")).equals(permissionValues(fields.get("view"))) && !hasEditPermission(actor, "role.fields.view")))
            throw new org.springframework.security.access.AccessDeniedException("Нет права изменять эту группу разрешений роли");
        if (!admin) {
            Set<String> previousRights = com.example.panel.security.PanelAccessPolicy.roleAuthorities(encodePermissions(previous));
            Set<String> nextRights = com.example.panel.security.PanelAccessPolicy.roleAuthorities(encodePermissions(raw));
            Set<String> changedRights = new java.util.LinkedHashSet<>(previousRights);
            changedRights.addAll(nextRights);
            Set<String> unchangedRights = new java.util.LinkedHashSet<>(previousRights);
            unchangedRights.retainAll(nextRights);
            changedRights.removeAll(unchangedRights);
            for (String right : changedRights)
                if (!com.example.panel.security.PanelAccessPolicy.has(actor, right))
                    throw new org.springframework.security.access.AccessDeniedException("Нельзя выдать роли право выше собственных полномочий");
        }
    }

    private Set<?> permissionValues(Object raw) {
        return raw instanceof List<?> list ? new java.util.LinkedHashSet<>(list) : Set.of();
    }

    private void requireInitialRolePermissions(Authentication actor, Object raw) {
        if (raw == null) return;
        if (!(raw instanceof Map<?, ?> permissions))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST);
        Map<?, ?> fields = permissions.get("fields") instanceof Map<?, ?> map ? map : Map.of();
        if ((!permissionValues(permissions.get("pages")).isEmpty() && !hasEditPermission(actor, "role.pages"))
                || (!permissionValues(fields.get("edit")).isEmpty() && !hasEditPermission(actor, "role.fields.edit"))
                || (!permissionValues(fields.get("view")).isEmpty() && !hasEditPermission(actor, "role.fields.view")))
            throw new org.springframework.security.access.AccessDeniedException("Нет права задавать эту группу разрешений новой роли");
    }

    private void requireAssignableRole(Authentication actor, Object id) {
        if (permissionService.isSuperUser(actor)) return;
        if (id == null || id.toString().isBlank()) return;
        var roles = usersJdbcTemplate.queryForList("SELECT name, permissions FROM roles WHERE id = ?", id);
        if (roles.size() != 1) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST);
        requireDelegableRole(actor, roles.get(0).get("name"), roles.get(0).get("permissions"));
    }

    private void requireUnprotectedAccount(Authentication actor, long id) {
        if (permissionService.isSuperUser(actor)) return;
        var rows = usersJdbcTemplate.queryForList("SELECT u.username, r.name AS role_name FROM users u LEFT JOIN roles r ON r.id = u.role_id WHERE u.id = ?", id);
        if (rows.stream().anyMatch(row -> UserPermissionsController.systemUsername(row.get("username"))
                || UserPermissionsController.protectedRole(row.get("role_name"))))
            throw new org.springframework.security.access.AccessDeniedException("Системный администратор защищён");
        var direct = usersJdbcTemplate.queryForList("SELECT authority FROM user_authorities WHERE user_id = ?", id);
        if (direct.stream().anyMatch(row -> java.util.Set.of("ROLE_ADMIN", "ROLE_PORTAL_ADMIN").contains(row.get("authority"))))
            throw new org.springframework.security.access.AccessDeniedException("Системный администратор защищён");
    }

    private void requireDelegableRole(Authentication actor, Object name, Object permissions) {
        if (permissionService.isSuperUser(actor)) return;
        if (UserPermissionsController.protectedRole(name))
            throw new org.springframework.security.access.AccessDeniedException("Системная роль защищена");
        Object encoded = permissions instanceof Map<?, ?> ? encodePermissions(permissions) : permissions;
        for (String right : com.example.panel.security.PanelAccessPolicy.roleAuthorities(encoded))
            if (!com.example.panel.security.PanelAccessPolicy.has(actor, right))
                throw new org.springframework.security.access.AccessDeniedException("Нельзя назначить роль выше собственных полномочий");
    }

    private Map<String, Object> resolveCurrentRolePermissions(Authentication authentication) {
        Long currentUserId = resolveCurrentUserId(authentication);
        if (currentUserId == null) {
            return Map.of();
        }
        List<Map<String, Object>> rows = usersJdbcTemplate.queryForList(
            "SELECT u.*, r.permissions AS permissions FROM users u LEFT JOIN roles r ON r.id = u.role_id " +
                "WHERE u.id = ?",
            currentUserId
        );
        if (rows.size() != 1) {
            return Map.of();
        }
        Map<String, Object> account = rows.get(0);
        if (!(account.get("id") instanceof Number id) || id.longValue() != currentUserId
                || !authentication.getName().equals(account.get("username"))
                || (account.get("enabled") != null && !asBoolean(account.get("enabled")))
                || asBoolean(account.get("is_blocked"))) {
            return Map.of();
        }
        Map<String, Object> verified = new LinkedHashMap<>(parsePermissions(account.get("permissions")));
        verified.put("__verified_account", true);
        return verified;
    }

    private List<String> extractFieldPermissions(Map<String, Object> permissions, String mode) {
        if (!(permissions.get("fields") instanceof Map<?, ?> fields)) {
            return List.of();
        }
        Object raw = fields.get(mode);
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (item != null) {
                String value = item.toString().trim();
                if (!value.isEmpty()) {
                    result.add(value);
                }
            }
        }
        return result;
    }

    private boolean containsPermission(List<String> permissions, String key) {
        return permissions.contains("*") || permissions.contains(key);
    }

    private Long resolveCurrentUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof PanelUserPrincipal principal)
                || principal.getUserId() <= 0 || !StringUtils.hasText(principal.getUsername())) {
            return null;
        }
        return principal.getUserId();
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
                                       Set<String> userColumns, String payloadKey, String column) {
        if (!payload.containsKey(payloadKey) || !userColumns.contains(column)) {
            return;
        }
        updates.add(column + " = ?");
        params.add(payload.get(payloadKey));
    }

    private void addOptionalUserField(Map<String, Object> fields, Map<String, Object> payload,
                                      Set<String> userColumns, String key) {
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

    // Write-boundary policy only; existing account lookup keys remain exact.
    private String normalizeCredentialLogin(Object raw) {
        return raw instanceof String value ? value.replaceAll("^ +| +$", "") : "";
    }

    private String credentialLoginError(String username) {
        return username.matches("[A-Za-z0-9._@-]+") ? "" : "Логин: только латинские буквы, цифры и . _ - @, без пробелов внутри.";
    }

    private String credentialPasswordError(String password) {
        return com.example.panel.security.CredentialPasswordPolicy.error(password);
    }

    private Map<String, Object> credentialFailure(String field, String error) {
        return Map.of("success", false, "field", field, "error", error);
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

    private Set<String> loadUserColumns() {
        Set<String> cached = userColumns;
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }
        try {
            Set<String> columns = new LinkedHashSet<>();
            columns.addAll(JdbcSchemaInspector.loadColumnNames(usersJdbcTemplate, "users"));
            this.userColumns = columns;
            return columns;
        } catch (Exception ex) {
            return new LinkedHashSet<>();
        }
    }


}
