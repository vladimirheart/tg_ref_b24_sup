package com.example.panel.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SidebarAvatarLoadingSourceContractTest {

    @Test
    void sidebarAvatarStartsWithFallbackAndLoadsEagerly() throws IOException {
        String navbar = read("src/main/resources/templates/fragments/navbar.html");
        assertThat(navbar)
                .contains("th:classappend=\"${sidebarUserHasAvatar} ? ' has-image' : ''\"")
                .contains("loading=\"eager\" decoding=\"async\" data-sidebar-user-avatar-img")
                .doesNotContain("has-image is-loaded")
                .doesNotContain("loading=\"lazy\" decoding=\"async\" data-sidebar-user-avatar-img");
    }

    @Test
    void hiddenBeforeLoadNeverUsesDisplayNoneOnImage() throws IOException {
        for (String path : new String[] {
                "src/main/resources/scss/sidebar/_sections.scss",
                "src/main/resources/static/css/sidebar.css"
        }) {
            String style = read(path);
            assertThat(style)
                    .contains(".sidebar-user-avatar img {\n  position: relative;\n  z-index: 1;\n  display: block;\n  opacity: 0;")
                    .contains(".sidebar-user-avatar.is-loaded img {\n  opacity: 1;")
                    .contains(".sidebar-user-avatar.is-loaded .sidebar-user-avatar-fallback {\n  display: none;");
        }
    }

    @Test
    void runtimeUsesRealImageLoadStateAndErrorFallback() throws IOException {
        String sidebarJs = read("src/main/resources/static/js/sidebar.js");
        assertThat(sidebarJs)
                .contains("sidebarUserAvatarImg.addEventListener('load', applyStateFromImage)")
                .contains("sidebarUserAvatarImg.addEventListener('error', () => setSidebarAvatarLoaded(false))")
                .contains("sidebarUserAvatarImg.naturalWidth > 0")
                .contains("applyStateFromImage();");
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
