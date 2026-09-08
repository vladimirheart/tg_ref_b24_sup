package com.example.panel.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class UiRuntimeIntegritySourceContractTest {

    private String read(String relative) throws IOException {
        return Files.readString(Path.of(relative), StandardCharsets.UTF_8);
    }

    private String sri384(String relative) throws IOException, NoSuchAlgorithmException {
        byte[] bytes = Files.readAllBytes(Path.of(relative));
        byte[] digest = MessageDigest.getInstance("SHA-384").digest(bytes);
        return "sha384-" + Base64.getEncoder().encodeToString(digest);
    }

    @Test
    void bootstrapVendorBytesMatchTheSRIUsedByTemplates() throws Exception {
        assertEquals(
            "sha384-QWTKZyjpPEjISv5WaRU9OFeRpok6YctnYmDr5pNlyT2bRjXh0JMhjY6hW+ALEwIH",
            sri384("src/main/resources/static/vendor/bootstrap/5.3.3/bootstrap.min.css")
        );
        assertEquals(
            "sha384-YvpcrYf0tY3lHB60NNkmXc5s9fDVZLESaAA55NDzOxhy9GkcIdslK1eN7N6jIeHz",
            sri384("src/main/resources/static/vendor/bootstrap/5.3.3/bootstrap.bundle.min.js")
        );
    }

    @Test
    void pageFontScaleIsRelativeToTheStylesheetBaseline() throws IOException {
        String runtime = read("src/main/resources/static/js/ui-preferences.js");
        String uiHead = read("src/main/resources/templates/fragments/ui-head.html");

        assertTrue(runtime.contains("resolveBaseRootFontPx"));
        assertTrue(runtime.contains("const effectivePx = basePx * scale / 100"));
        assertTrue(runtime.contains("DOMContentLoaded', initializePageFontScale"));
        assertFalse(runtime.contains("document.documentElement.style.fontSize = " + Character.toString(96) + "$" + "{scale}%" + Character.toString(96) + ";"));
        assertTrue(uiHead.contains("ui-preferences.js(v='20260908-01-259-r4-2')"));
    }

    @Test
    void sidebarAccountFooterStaysCompactAndQuiet() throws IOException {
        String navbar = read("src/main/resources/templates/fragments/navbar.html");
        String scss = read("src/main/resources/scss/sidebar/_sections.scss");

        assertFalse(navbar.contains("sidebar-footer-kicker"));
        assertTrue(navbar.contains("sidebarUserUsername != sidebarUserDisplayName"));
        assertTrue(scss.contains("Sidebar account calm refinement — 01-259 r4.2"));
        assertTrue(scss.contains("min-height: 44px"));
        assertTrue(scss.contains("width: 34px"));
        assertTrue(scss.contains("width: 38px"));
        assertTrue(scss.contains("grid-template-columns: 28px minmax(0, 1fr) 28px"));
    }
}
