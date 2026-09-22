package com.example.panel.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UiInfoDisclosureSourceContractTest {

    private static final Path STATIC_JS = Path.of("src/main/resources/static/js");
    private static final Path TEMPLATES = Path.of("src/main/resources/templates");
    private static final Path CONTENT_DISCLOSURE = STATIC_JS.resolve("content-disclosure.js");
    private static final Path KNOWLEDGE_LIST = TEMPLATES.resolve("knowledge/list.html");
    private static final Path UI_HEAD = TEMPLATES.resolve("fragments/ui-head.html");
    private static final Path KNOWLEDGE_SCSS = Path.of("src/main/resources/scss/app/_knowledge.scss");
    private static final Path EQUIPMENT = STATIC_JS.resolve("passport-detail-equipment-runtime.js");
    private static final Path PARTNER_CONTACTS = STATIC_JS.resolve("settings-partner-contacts-runtime.js");

    @Test
    void runtimeUiDoesNotReintroduceTextualMoreDetailsDisclosure() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path root : Arrays.asList(STATIC_JS, TEMPLATES)) {
            List<Path> sources;
            try (Stream<Path> stream = Files.walk(root)) {
                sources = stream
                    .filter(Files::isRegularFile)
                    .filter(this::isApplicationUiSource)
                    .collect(Collectors.toList());
            }
            for (Path source : sources) {
                String content = read(source);
                if (content.contains("Подробнее")) {
                    offenders.add(source.toString().replace('\\', '/'));
                }
            }
        }

        assertThat(offenders)
            .as("runtime application UI sources must use compact info disclosure instead of textual Подробнее")
            .isEmpty();
    }

    @Test
    void firstSliceUsesSharedCompactInfoAffordance() throws IOException {
        String disclosure = read(CONTENT_DISCLOSURE);
        String equipment = read(EQUIPMENT);
        String partnerContacts = read(PARTNER_CONTACTS);

        assertThat(disclosure)
            .contains("function bindInfoPopover(info, toggle, panel)")
            .contains("content-disclosure page-header-info")
            .contains("bi bi-info-circle")
            .contains("parent.insertBefore(wrapper, element);")
            .contains("panel.appendChild(element);")
            .contains("element.hidden = false;")
            .doesNotContain("content-disclosure__preview")
            .doesNotContain("Подробнее")
            .doesNotContain("Скрыть");
        assertThat(equipment)
            .contains("passport-asset-details__toggle")
            .contains("page-header-info__toggle")
            .contains("bi bi-info-circle")
            .doesNotContain("<summary>Подробнее</summary>");
        assertThat(partnerContacts)
            .contains("data-partner-contact-open-modal role=\"button\"")
            .contains("bi bi-info-circle")
            .doesNotContain("Подробнее");
    }

    @Test
    void knowledgeBaseOverviewUsesSharedCompactDisclosureAndHeaderActions() throws IOException {
        String disclosure = read(CONTENT_DISCLOSURE);
        String knowledgeList = read(KNOWLEDGE_LIST);
        String knowledgeScss = read(KNOWLEDGE_SCSS);

        assertThat(disclosure)
            .contains("[data-content-disclosure-help]")
            .contains("element.dataset.disclosureLabel")
            .contains("variantClass: 'content-disclosure--inline'");
        assertThat(knowledgeList)
            .contains("data-content-disclosure-help")
            .contains("data-disclosure-label=\"Об интеграции Notion\"")
            .contains("kb-notion-header-actions")
            .contains("kb-icon-action")
            .contains("aria-label=\"Проверить подключение\"")
            .contains("bi bi-plug")
            .contains("aria-label=\"Импортировать статьи\"")
            .contains("bi bi-cloud-download")
            .contains("aria-label=\"Обновить изменённые\"")
            .contains("bi bi-arrow-repeat")
            .contains("kb-settings-action")
            .doesNotContain("class=\"btn btn-outline-primary\" type=\"submit\">Проверить подключение</button>")
            .doesNotContain("d-flex flex-wrap gap-2 mt-3 pt-3 border-top");
        assertThat(knowledgeScss)
            .contains("grid-template-columns: repeat(3, minmax(0, 1fr))")
            .contains(".kb-notion-heading .content-disclosure")
            .contains(".kb-icon-action")
            .contains(".kb-notion-status-meta:focus-visible");
    }

    @Test
    void sharedUiHeadLoadsVendoredBootstrapIconsForCompactInfoAffordances() throws IOException {
        String uiHead = read(UI_HEAD);

        assertThat(uiHead)
            .contains("/vendor/bootstrap-icons/1.10.5/bootstrap-icons.css")
            .contains("rel=\"stylesheet\"");
    }

    private boolean isApplicationUiSource(Path path) {
        String normalized = path.toString().replace('\\', '/');
        boolean supported = normalized.endsWith(".js") || normalized.endsWith(".html");
        return supported
            && !normalized.contains("/vendor/")
            && !normalized.contains("/node_modules/")
            && !normalized.endsWith(".min.js");
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8)
            .replace("\r\n", "\n")
            .replace("\r", "\n");
    }
}
