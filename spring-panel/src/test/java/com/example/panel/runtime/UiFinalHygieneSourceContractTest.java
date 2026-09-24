package com.example.panel.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UiFinalHygieneSourceContractTest {

    private static final Path DISCLOSURE = Path.of("src/main/resources/static/js/content-disclosure.js");
    private static final Path CORE_SCSS = Path.of("src/main/resources/scss/app/_core.scss");
    private static final Path CLIENT_PROFILE = Path.of("src/main/resources/templates/clients/profile.html");
    private static final Path PASSPORT_EDITOR = Path.of("src/main/resources/templates/passports/new.html");
    private static final Path UI_HEAD = Path.of("src/main/resources/templates/fragments/ui-head.html");

    @Test
    void sharedHeaderContractKeepsStaticHelpCompactAndDynamicIdentityVisible() throws IOException {
        String disclosure = read(DISCLOSURE);
        String core = read(CORE_SCSS);
        String clientProfile = read(CLIENT_PROFILE);
        String passportEditor = read(PASSPORT_EDITOR);

        assertThat(disclosure)
            .contains("function ensurePageHeaderTitleRow(header, title)")
            .contains("const kicker = header.querySelector('.page-kicker');")
            .contains("titleRow.insertBefore(kicker, title);")
            .contains("document.querySelectorAll('.page-header-card .page-title').forEach((title) =>")
            .contains("buildHeaderInfoDisclosure(element)")
            .contains("element.closest('[data-no-disclosure]')");

        assertThat(core)
            .contains(".page-header-title-row .page-kicker,")
            .contains(".page-header-title-row .page-title {")
            .contains("margin-top: 0;")
            .contains("margin-bottom: 0;");

        assertThat(clientProfile)
            .contains("class=\"page-subtitle mb-0\" data-no-disclosure")
            .contains("th:text=\"'ID: ' + ${profile.header.userId}\"");

        assertThat(passportEditor)
            .contains("class=\"page-subtitle object-title\" data-no-disclosure")
            .contains("id=\"objectTitle\"");
    }

    @Test
    void prePaintHeaderParityMatchesRuntimeTitleRowBeforeDisclosureBoot() throws IOException {
        String core = read(CORE_SCSS);

        assertThat(core)
            .contains("/* 01-272 S11 R2: pre-paint header parity */")
            .contains("@supports selector(.page-header-card:has(> .page-title))")
            .contains(".page-header-card:has(> .page-kicker):has(> .page-title)")
            .contains("flex-wrap: wrap;")
            .contains("column-gap: 0.45rem;")
            .contains(".client-profile-page-heading:has(> .page-kicker):has(> .page-title) > .page-subtitle[data-no-disclosure]");
    }

    @Test
    void staticHeaderHelpAndDialogListOnlyPreferenceAreStableFromFirstPaint() throws IOException {
        String disclosure = read(DISCLOSURE);
        String core = read(CORE_SCSS);
        String dialogsTemplate = read(Path.of("src/main/resources/templates/dialogs/index.html"));
        String dialogsPrepaint = read(Path.of("src/main/resources/static/js/dialogs-prepaint-runtime.js"));
        String dialogsShell = read(Path.of("src/main/resources/static/js/dialogs-shell-runtime.js"));
        String dialogsScss = read(Path.of("src/main/resources/scss/app/dialogs/_template-layout.scss"));

        assertThat(core)
            .contains("/* 01-272 S11 R3: hide static header help before disclosure boot */")
            .contains(".page-header-card .page-subtitle:not([data-no-disclosure]):not([data-disclosure-processed='true'])");
        assertThat(disclosure).contains("element.dataset.disclosureProcessed =");
        assertThat(dialogsTemplate)
            .contains("/js/dialogs-prepaint-runtime.js")
            .contains("/js/common.js");
        assertThat(dialogsTemplate.indexOf("/js/dialogs-prepaint-runtime.js"))
            .isLessThan(dialogsTemplate.indexOf("/js/common.js"));
        assertThat(dialogsPrepaint)
            .contains("iguana:dialogs:list-only-mode")
            .contains("dialog-list-only-prepaint")
            .contains("document.documentElement.classList.toggle(ROOT_CLASS, enabled);");
        assertThat(dialogsShell)
            .contains("document.documentElement.classList.toggle('dialog-list-only-prepaint', active);")
            .contains("document.body.classList.toggle('dialog-list-only-mode', active);");
        assertThat(dialogsScss)
            .contains("html.dialog-list-only-prepaint body[data-ui-page='dialogs'] .dialogs-extra-section");
    }

    @Test
    void criticalFirstPaintContractIsInlineAndIndependentOfAppCssCache() throws IOException {
        String uiHead = read(UI_HEAD);
        String disclosure = read(DISCLOSURE);

        assertThat(uiHead)
            .contains("data-ui-first-paint-critical")
            .contains("document.documentElement.classList.add('iguana-ui-first-paint');")
            .contains("html.iguana-ui-first-paint .page-header-card .page-subtitle:not([data-no-disclosure])")
            .contains("html.dialog-list-only-prepaint body[data-ui-page='dialogs'] .dialogs-extra-section")
            .contains(".page-header-title-row .page-kicker,")
            .contains("@supports selector(.page-header-card:has(> .page-title))");

        assertThat(disclosure)
            .contains("document.documentElement.classList.remove('iguana-ui-first-paint');");
    }

    @Test
    void reportsHelpAndDefaultDialogSelectionColumnAreStableBeforeHydration() throws IOException {
        String dashboard = read(Path.of("src/main/resources/templates/dashboard/index.html"));
        String dialogsTemplate = read(Path.of("src/main/resources/templates/dialogs/index.html"));
        String dialogsJs = read(Path.of("src/main/resources/static/js/dialogs.js"));

        assertThat(dashboard)
            .contains("<p class=\"page-subtitle\" hidden data-disclosure-pending>");

        assertThat(dialogsTemplate)
            .contains("<th class=\"dialog-select-column d-none\" data-column-key=\"select\">")
            .contains("<td class=\"dialog-select-column d-none\" data-column-key=\"select\">");

        assertThat(dialogsJs)
            .contains("<td class=\"dialog-select-column d-none\" data-column-key=\"select\">");
    }

    @Test
    void ellipsisFullValueAccessUsesSharedRuntimeAcrossDenseOperationalSurfaces() throws IOException {
        String uiHead = read(Path.of("src/main/resources/templates/fragments/ui-head.html"));
        String runtime = read(Path.of("src/main/resources/static/js/ellipsis-reveal.js"));
        String core = read(CORE_SCSS);
        String dialogs = read(Path.of("src/main/resources/templates/dialogs/index.html"));
        String dialogsJs = read(Path.of("src/main/resources/static/js/dialogs.js"));
        String responsibleRuntime = read(Path.of("src/main/resources/static/js/dialogs-avatar-runtime.js"));
        String reports = read(Path.of("src/main/resources/templates/dashboard/index.html"));
        String channels = read(Path.of("src/main/resources/static/js/settings-channels-catalog-runtime.js"));
        String channelWorkspace = read(Path.of("src/main/resources/templates/settings/fragments/channels-workspace.html"));
        String passport = read(Path.of("src/main/resources/templates/passports/detail.html"));
        String passportActivity = read(Path.of("src/main/resources/static/js/passport-detail-activity-runtime.js"));

        assertThat(uiHead).contains("/js/ellipsis-reveal.js(v='20260924-01-272-s12')");
        assertThat(runtime)
            .contains("const SELECTOR = '[data-ui-ellipsis-reveal]';")
            .contains("target.scrollWidth > target.clientWidth + 1")
            .contains("document.addEventListener('pointerover'")
            .contains("document.addEventListener('focusin'");
        assertThat(core)
            .contains(".ui-ellipsis-tooltip")
            .contains("[data-ui-ellipsis-reveal]:focus-visible");

        assertThat(dialogs).contains("dialog-problem-cell d-block\" data-ui-ellipsis-reveal");
        assertThat(dialogsJs).contains("dialog-problem-cell d-block\" data-ui-ellipsis-reveal");
        assertThat(responsibleRuntime).contains("dialog-responsible-name\" data-ui-ellipsis-reveal");
        assertThat(reports).contains("staff-time-name\" data-ui-ellipsis-reveal");
        assertThat(channels)
            .contains("channels-channel-name\" data-ui-ellipsis-reveal")
            .contains("data-channel-bot-runtime-status=")
            .contains("data-ui-ellipsis-reveal tabindex=\"0\"");
        assertThat(channelWorkspace).contains("channels-manage-runtime-info\" id=\"channelsBotStatusesInfo\" data-ui-ellipsis-reveal");
        assertThat(passport)
            .contains("passport-workspace-title\" data-ui-ellipsis-reveal")
            .contains("passport-workspace-subtitle\" data-ui-ellipsis-reveal");
        assertThat(passportActivity).contains("<strong data-ui-ellipsis-reveal tabindex=\"0\">");
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8)
            .replace("\r\n", "\n")
            .replace("\r", "\n");
    }
}
