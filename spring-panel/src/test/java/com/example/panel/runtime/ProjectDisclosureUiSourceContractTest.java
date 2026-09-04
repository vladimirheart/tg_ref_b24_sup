package com.example.panel.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectDisclosureUiSourceContractTest {
    private static final Path REPO_ROOT = Path.of("..").toAbsolutePath().normalize();
    private static final Pattern DETAILS = Pattern.compile("<details\\b[^>]*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    @Test
    void canonicalDisclosureSourcesAreLoadedLastAndThemeAware() throws IOException {
        String appEntry = read("spring-panel/src/main/resources/scss/app.scss");
        String settingsEntry = read("spring-panel/src/main/resources/scss/settings.scss");
        String appDisclosure = read("spring-panel/src/main/resources/scss/app/_disclosure.scss");
        String settingsDisclosure = read("spring-panel/src/main/resources/scss/settings/_disclosure.scss");
        String uiHead = read("spring-panel/src/main/resources/templates/fragments/ui-head.html");
        String runtime = read("spring-panel/src/main/resources/static/js/ui-disclosure.js");

        assertThat(appEntry.trim()).endsWith("@use 'app/disclosure';");
        assertThat(settingsEntry.trim()).endsWith("@use \"settings/disclosure\";");
        assertThat(uiHead).contains("@{/js/ui-disclosure.js}");
        assertThat(appDisclosure)
            .contains("body .accordion")
            .contains("details.ui-disclosure-native")
            .contains("var(--surface-raised)")
            .contains("var(--surface-interactive)")
            .contains("var(--surface-selected)")
            .contains("var(--surface-card)");
        assertThat(settingsDisclosure)
            .contains("#itConnectionsModal #itSettingsAccordion")
            .contains("#channelsModal .channels-manage-accordion")
            .contains("var(--surface-selected)");
        assertThat(runtime)
            .contains("details.ui-disclosure-native")
            .contains("prefers-reduced-motion: reduce")
            .contains("DURATION_MS = 350")
            .contains("details.animate");
    }

    @Test
    void runtimeNativeDetailsAreCanonicalExceptMediaMenus() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path root : List.of(
            REPO_ROOT.resolve("spring-panel/src/main/resources/templates"),
            REPO_ROOT.resolve("spring-panel/src/main/resources/static/js")
        )) {
            try (Stream<Path> stream = Files.walk(root)) {
                stream.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".html") || p.toString().endsWith(".js"))
                    .forEach(p -> inspect(p, offenders));
            }
        }
        assertThat(offenders).as("non-canonical runtime details").isEmpty();
    }

    private void inspect(Path path, List<String> offenders) {
        try {
            String text = Files.readString(path, StandardCharsets.UTF_8).replace("\\r\\n", "\\n");
            Matcher matcher = DETAILS.matcher(text);
            while (matcher.find()) {
                String tag = matcher.group();
                if (tag.contains("data-media-info-menu") || tag.contains("chat-media-info")) continue;
                if (!tag.contains("ui-disclosure-native")) {
                    offenders.add(REPO_ROOT.relativize(path) + ": " + tag.replace("\\n", " "));
                }
            }
        } catch (IOException error) {
            throw new RuntimeException(error);
        }
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(REPO_ROOT.resolve(relativePath), StandardCharsets.UTF_8).replace("\\r\\n", "\\n");
    }
}
