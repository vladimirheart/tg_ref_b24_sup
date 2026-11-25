package com.example.panel.model.notification;

/** Presentation metadata only: never an authorization decision or a delivery target. */
public enum NotificationSource {
    DIALOGS("dialogs", "Диалоги"), INCIDENTS("incidents", "Инциденты"),
    TASKS("tasks", "Задачи"), PROJECTS("projects", "Проекты"),
    MONITORING("monitoring", "Мониторинг"), KNOWLEDGE("knowledge", "База знаний"),
    SETTINGS("settings", "Настройки"), SYSTEM("system", "Система");

    private final String key;
    private final String label;
    NotificationSource(String key, String label) { this.key = key; this.label = label; }
    public String key() { return key; }
    public String label() { return label; }

    public static NotificationSource fromUrl(String url) {
        if (url == null || !url.startsWith("/") || url.startsWith("//")) return SYSTEM;
        String path = url.split("[?#]", 2)[0];
        if (matches(path, "/dialogs")) return DIALOGS;
        if (matches(path, "/incidents")) return INCIDENTS;
        if (matches(path, "/tasks")) return TASKS;
        if (matches(path, "/projects")) return PROJECTS;
        if (matches(path, "/analytics") || matches(path, "/monitoring")) return MONITORING;
        if (matches(path, "/knowledge-base") || matches(path, "/knowledge")) return KNOWLEDGE;
        if (matches(path, "/settings")) return SETTINGS;
        return SYSTEM;
    }
    private static boolean matches(String path, String root) {
        return path.equals(root) || path.startsWith(root + "/");
    }
}
