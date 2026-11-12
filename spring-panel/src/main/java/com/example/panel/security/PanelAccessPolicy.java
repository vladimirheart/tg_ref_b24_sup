package com.example.panel.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

/** Shared vocabulary for role grants, individual overrides, HTTP checks and UI capabilities. */
public final class PanelAccessPolicy {
    private static final ObjectMapper JSON = new ObjectMapper();
    public record Page(String key, String label, String authority) {}
    public static final List<Page> PAGE_CATALOG = List.of(
            new Page("dialogs", "Диалоги", "PAGE_DIALOGS"),
            new Page("ai_ops", "AI Ops", "PAGE_AI_OPS"),
            new Page("tasks", "Задачи и проекты", "PAGE_TASKS"),
            new Page("clients", "Клиенты", "PAGE_CLIENTS"),
            new Page("object_passports", "Паспорт объектов", "PAGE_OBJECT_PASSPORTS"),
            new Page("knowledge_base", "База знаний и AI-источники", "PAGE_KNOWLEDGE_BASE"),
            new Page("dashboard", "Отчёты", "PAGE_DASHBOARD"),
            new Page("analytics", "Аналитика", "PAGE_ANALYTICS"),
            new Page("settings", "Настройки", "PAGE_SETTINGS"));
    public static final Map<String, String> PAGES;
    // Existing standalone routes remain compatible, but are no longer offered as current pages.
    public static final Map<String, String> LEGACY_PAGES = Map.of(
            "channels", "PAGE_CHANNELS", "user_management", "PAGE_USERS");
    public static final String PAGE_ACCESS_NOTE = "Главная доступна после входа, Инциденты — действующим пользователям панели. "
            + "Задачи и Проекты используют общее право, как и База знаний и AI-источники. "
            + "AI-источники отображаются при включённом модуле. Каналы, пользователи и роли находятся в Настройках; "
            + "права на их изменение выдаются отдельно.";
    public static final Map<String, String> BLOCKS;
    public static final List<String> USER_FIELDS = List.of("user.create", "user.username", "user.password", "user.role",
            "user.delete", "user.block", "role.create", "role.name", "role.description", "role.pages",
            "role.fields.edit", "role.fields.view", "role.delete");
    static {
        Map<String, String> pages = new LinkedHashMap<>();
        PAGE_CATALOG.forEach(page -> pages.put(page.key(), page.authority()));
        PAGES = java.util.Collections.unmodifiableMap(pages);
        Map<String, String> blocks = new LinkedHashMap<>();
        blocks.put("channels", "Каналы и запуск ботов");
        blocks.put("bot_templates", "Шаблоны и параметры ботов");
        blocks.put("auto_close", "Автозакрытие обращений");
        blocks.put("reporting", "Автоотчёты");
        blocks.put("input_formatting", "Форматы вводимых данных");
        blocks.put("dialogs", "Категории, шаблоны и настройки диалогов");
        blocks.put("appearance", "Дизайн панели и статусы клиентов");
        blocks.put("parameters", "Общие справочники");
        blocks.put("it_connections", "Подключения IT-блока");
        blocks.put("equipment", "Оборудование, характеристики и профили");
        blocks.put("legal_entities", "Юридические лица и контакты партнёров");
        blocks.put("locations", "Структура и синхронизация локаций");
        blocks.put("manager_bindings", "Привязка управляющих");
        blocks.put("organization", "Организационная структура");
        blocks.put("workforce", "Должности, графики и настройки сотрудников");
        blocks.put("network_profiles", "Сетевые профили и интеграции");
        blocks.put("netbox", "Синхронизация NetBox");
        blocks.put("notification_routing", "Маршрутизация уведомлений");
        blocks.put("backup", "Резервное копирование и восстановление");
        blocks.put("access", "Персональные разрешения пользователей");
        BLOCKS = java.util.Collections.unmodifiableMap(blocks);
    }

    private PanelAccessPolicy() {}

    public static String editAuthority(String key) {
        return "EDIT_" + key.toUpperCase(java.util.Locale.ROOT).replace('.', '_');
    }

    public static Set<String> managedAuthorities() {
        Set<String> result = new LinkedHashSet<>(PAGES.values());
        result.addAll(LEGACY_PAGES.values());
        BLOCKS.keySet().forEach(block -> result.add(editAuthority("settings." + block)));
        USER_FIELDS.forEach(field -> result.add(editAuthority(field)));
        return result;
    }

    public static Map<String, Object> parse(Object raw) {
        if (raw == null) return Map.of();
        try {
            Map<String, Object> parsed = JSON.readValue(raw.toString(), new TypeReference<Map<String, Object>>() {});
            return parsed == null ? Map.of() : parsed;
        } catch (Exception ex) {
            return Map.of();
        }
    }

    public static Set<String> roleAuthorities(Object raw) {
        Map<String, Object> permissions = parse(raw);
        Set<String> result = new LinkedHashSet<>();
        if (permissions.get("pages") instanceof List<?> pages) {
            result.addAll(pageAuthorities(pages));
        }
        if (permissions.get("fields") instanceof Map<?, ?> fields && fields.get("edit") instanceof List<?> edit) {
            BLOCKS.keySet().forEach(block -> {
                if (edit.contains("*") || edit.contains("settings.*") || edit.contains("settings." + block))
                    result.add(editAuthority("settings." + block));
            });
            USER_FIELDS.forEach(field -> { if (edit.contains("*") || edit.contains(field)) result.add(editAuthority(field)); });
        }
        return result;
    }

    public static Set<String> pageAuthorities(List<?> pages) {
        Set<String> result = new LinkedHashSet<>();
        if (pages == null) return result;
        boolean all = pages.contains("*");
        PAGES.forEach((page, authority) -> { if (all || pages.contains(page)) result.add(authority); });
        LEGACY_PAGES.forEach((page, authority) -> { if (all || pages.contains(page)) result.add(authority); });
        return result;
    }

    public static Set<String> effective(Set<String> inherited, Set<String> direct) {
        Set<String> result = new LinkedHashSet<>(inherited);
        direct.stream().filter(value -> !value.startsWith("DENY_")).forEach(result::add);
        if (result.contains("ROLE_ADMIN") || result.contains("ROLE_PORTAL_ADMIN"))
            managedAuthorities().stream().filter(right -> !right.startsWith("PAGE_")).forEach(result::add);
        direct.stream().filter(value -> value.startsWith("DENY_")).forEach(value -> {
            result.remove(value.substring(5));
            result.add(value);
        });
        return result;
    }

    public static boolean has(Authentication authentication, String authority) {
        if (authentication == null || !authentication.isAuthenticated()) return false;
        Set<String> rights = AuthorityUtils.authorityListToSet(authentication.getAuthorities());
        if (rights.contains("DENY_" + authority)) return false;
        if (authority.startsWith("PAGE_")) return rights.contains(authority);
        return rights.contains(authority) || (!rights.contains("DENY_" + authority)
                && (rights.contains("ROLE_ADMIN") || rights.contains("ROLE_PORTAL_ADMIN")));
    }

    public static boolean canEdit(Authentication authentication, String block) {
        return BLOCKS.containsKey(block) && has(authentication, editAuthority("settings." + block));
    }

    public static void requireBlock(String block) {
        if (block == null || !canEdit(SecurityContextHolder.getContext().getAuthentication(), block))
            throw new AccessDeniedException("Нет права изменять этот блок настроек");
    }

    public static String payloadBlock(String key) {
        if (key.startsWith("dialog_") || key.equals("categories")) return "dialogs";
        return switch (key) {
            case "auto_close_config", "auto_close_hours" -> "auto_close";
            case "client_statuses", "client_status_colors", "business_cell_styles" -> "appearance";
            case "bot_settings", "unblock_request_cooldown_minutes" -> "bot_templates";
            case "reporting_config" -> "reporting";
            case "manager_location_bindings" -> "manager_bindings";
            case "locations", "locations_iiko_server_sources", "locations_iiko_sync" -> "locations";
            case "network_profiles", "integration_network", "integration_network_profiles" -> "network_profiles";
            case "netbox_sync" -> "netbox";
            case "notification_routing" -> "notification_routing";
            default -> null;
        };
    }

    public static void requirePayload(Map<String, Object> payload) {
        // Check the complete request before any of its blocks is persisted.
        for (String key : payload.keySet()) requireBlock(payloadBlock(key));
    }

    public static String parameterBlock(String type) {
        return switch (type == null ? "" : type) {
            case "legal_entity", "partner_contact" -> "legal_entities";
            case "it_connection", "remote_access", "iiko_server" -> "it_connections";
            default -> type != null && type.startsWith("equipment_") ? "equipment" : "parameters";
        };
    }
}
