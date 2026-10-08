package com.example.panel.runtime;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SidebarRuntimeUiSourceContractTest {

    private static final Path UI_HEAD = Path.of("src/main/resources/templates/fragments/ui-head.html");
    private static final Path NAVBAR = Path.of("src/main/resources/templates/fragments/navbar.html");
    private static final Path SIDEBAR_JS = Path.of("src/main/resources/static/js/sidebar.js");
    private static final Path SETTINGS = Path.of("src/main/resources/templates/settings/index.html");
    private static final Path DASHBOARD = Path.of("src/main/resources/templates/dashboard/index.html");
    private static final Path PROJECTS = Path.of("src/main/resources/templates/projects/index.html");
    private static final Path TASKS = Path.of("src/main/resources/templates/tasks/index.html");
    private static final Path UI_PREFS_JS = Path.of("src/main/resources/static/js/ui-preferences.js");
    private static final Path SHELL_SCSS = Path.of("src/main/resources/scss/sidebar/_shell.scss");
    private static final Path SECTIONS_SCSS = Path.of("src/main/resources/scss/sidebar/_sections.scss");
    private static final Path NOTIFICATIONS_SCSS = Path.of("src/main/resources/scss/sidebar/_notifications.scss");
    private static final Path SIDEBAR_CSS = Path.of("src/main/resources/static/css/sidebar.css");
    private static final Path UI_PREF_SERVICE = Path.of("src/main/java/com/example/panel/service/UiPreferenceService.java");

    @Test
    void collapsedSidebarUsesExplicitChevronWithoutHoverExpansion() throws IOException {
        String navbar = read(NAVBAR);
        String sidebarJs = read(SIDEBAR_JS);
        String shellScss = read(SHELL_SCSS);
        String sectionsScss = read(SECTIONS_SCSS);
        String notificationsScss = read(NOTIFICATIONS_SCSS);
        String sidebarCss = read(SIDEBAR_CSS);

        assertThat(navbar)
            .contains("id=\"sidebarCollapseToggle\"")
            .contains("sidebar-collapse-toggle__icon")
            .contains("data-sidebar-resize-handle")
            .contains("aria-valuemin=\"240\"")
            .contains("aria-valuemax=\"420\"")
            .doesNotContain("pinSidebarBtn")
            .doesNotContain("📌");

        assertThat(sidebarJs)
            .contains("const collapseToggleBtn = document.getElementById('sidebarCollapseToggle');")
            .contains("const PREF_KEY_WIDTH = 'sidebarWidth';")
            .contains("const SIDEBAR_WIDTH_MIN = 240;")
            .contains("const SIDEBAR_WIDTH_MAX = 420;")
            .contains("resizeHandle.addEventListener('pointerdown', beginSidebarResize);")
            .contains("resizeHandle.addEventListener('pointermove', moveSidebarResize);")
            .contains("setPointerCapture(event.pointerId)")
            .contains("document.documentElement.style.setProperty('--sidebar-w'")
            .contains("icon.textContent = expanded ? '‹' : '›';")
            .doesNotContain("HOVER_LEAVE_DELAY_MS")
            .doesNotContain("sidebar.addEventListener('mouseenter'")
            .doesNotContain("sidebar-hovering")
            .doesNotContain("classList.add('hovering')");

        assertThat(shellScss)
            .contains(".sidebar-resize-handle")
            .contains("body.sidebar-resizing")
            .doesNotContain("sidebar-hovering")
            .doesNotContain(".sidebar.collapsed.hovering")
            .doesNotContain(":not(.hovering)");
        assertThat(sectionsScss)
            .contains(".sidebar-collapse-toggle")
            .doesNotContain(":not(.hovering)");
        assertThat(notificationsScss)
            .contains(".sidebar-collapse-toggle,")
            .contains(".sidebar-resize-handle")
            .doesNotContain("sidebar-hovering");
        assertThat(sidebarCss)
            .contains(".sidebar-resize-handle")
            .contains("body.sidebar-resizing")
            .contains(".sidebar-collapse-toggle")
            .doesNotContain("sidebar-hovering")
            .doesNotContain(":not(.hovering)");
    }

    @Test
    void sidebarWidthUsesSharedUiPreferencesAndServerNormalization() throws IOException {
        String uiPrefs = read(UI_PREFS_JS);
        String service = read(UI_PREF_SERVICE);

        assertThat(uiPrefs)
            .contains("function normalizeSidebarWidth(value)")
            .contains("sidebarWidth: Object.freeze({")
            .contains("storageKey: 'sidebarWidth'")
            .contains("fallback: '286'")
            .contains("Math.max(240, Math.min(420, parsed))");
        assertThat(service)
            .contains("mergeField(target, normalized, requestPayload, \"sidebarWidth\");")
            .contains("Integer sidebarWidth = normalizeSidebarWidth(payload.get(\"sidebarWidth\"));")
            .contains("return Math.max(240, Math.min(420, width));");
    }

    @Test
    void sidebarRuntimeIsSingleInitializedAcrossLegacyPages() throws IOException {
        String navbar = read(NAVBAR);
        String sidebarJs = read(SIDEBAR_JS);

        assertThat(navbar)
            .contains("sidebar.js(v='20261008-sidebar-prepaint-r75')");
        assertThat(sidebarJs)
            .contains("const SIDEBAR_RUNTIME_FLAG = '__IGUANA_SIDEBAR_RUNTIME_READY__';")
            .contains("if (window[SIDEBAR_RUNTIME_FLAG] === true) return;")
            .contains("window[SIDEBAR_RUNTIME_FLAG] = true;");

        assertThat(read(SETTINGS)).doesNotContain("/js/sidebar.js");
        assertThat(read(DASHBOARD)).doesNotContain("/js/sidebar.js");
        assertThat(read(PROJECTS)).doesNotContain("/js/sidebar.js");
        assertThat(read(TASKS)).doesNotContain("/js/sidebar.js");
    }

    @Test
    void sidebarWidthAndCollapsedStateAreAppliedBeforeFirstPaint() throws IOException {
        String uiHead = read(UI_HEAD);
        String sidebarJs = read(SIDEBAR_JS);

        assertThat(uiHead)
            .contains("html[data-sidebar-expanded='0'] body.with-sidebar")
            .contains("html[data-sidebar-expanded='0'] #app-sidebar")
            .contains("function applySidebarFirstPaintPreference()")
            .contains("readPreference('sidebarWidth', 'sidebarWidth', '286')")
            .contains("readPreference('sidebarPinned', 'sidebarPinned', '0')")
            .contains("Math.max(240, Math.min(420, rawWidth))")
            .contains("root.style.setProperty('--sidebar-w', String(width) + 'px');")
            .contains("root.dataset.sidebarExpanded = expanded ? '1' : '0';");
        assertThat(sidebarJs)
            .contains("document.documentElement.dataset.sidebarExpanded = expanded ? '1' : '0';");
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
