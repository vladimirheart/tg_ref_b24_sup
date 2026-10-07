package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.panel.entity.RmsLicenseMonitor;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RmsLocationCoverageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void matchesCorporateAndFranchiseNamesAndIgnoresClosedLocations() {
        RmsLocationCoverageService service = serviceWithLocations(Map.of(
                "tree", Map.of(
                        "\u0411\u043b\u0438\u043d\u0411\u0435\u0440\u0438", Map.of(
                                "\u041a\u043e\u0440\u043f\u043e\u0440\u0430\u0442\u0438\u0432\u043d\u0430\u044f \u0441\u0435\u0442\u044c", Map.of(
                                        "\u0412\u043e\u043b\u0433\u043e\u0433\u0440\u0430\u0434", List.of("7 \u0413\u0432\u0430\u0440\u0434\u0435\u0439\u0441\u043a\u0430\u044f", "\u041d\u043e\u0432\u044b\u0439 \u0440\u0435\u0441\u0442\u043e\u0440\u0430\u043d")
                                )
                        ),
                        "\u0421\u0443\u0448\u0438\u0412\u0451\u0441\u043b\u0430", Map.of(
                                "\u041f\u0430\u0440\u0442\u043d\u0451\u0440\u044b-\u0444\u0440\u0430\u043d\u0447\u0430\u0439\u0437\u0438", Map.of(
                                        "\u0412\u043e\u0440\u043e\u043d\u0435\u0436", List.of("\u041b\u0435\u043d\u0438\u043d\u0430", "\u0410\u0440\u0445\u0438\u0432")
                                )
                        )
                ),
                "statuses", Map.of(
                        "location::\u0421\u0443\u0448\u0438\u0412\u0451\u0441\u043b\u0430::\u041f\u0430\u0440\u0442\u043d\u0451\u0440\u044b-\u0444\u0440\u0430\u043d\u0447\u0430\u0439\u0437\u0438::\u0412\u043e\u0440\u043e\u043d\u0435\u0436::\u0410\u0440\u0445\u0438\u0432", "\u0417\u0430\u043a\u0440\u044b\u0442"
                )
        ));

        RmsLocationCoverageService.CoverageSnapshot snapshot = service.buildCoverage(List.of(
                monitor(1L, "\u0411\u0411 \u0412\u043e\u043b\u0433\u043e\u0433\u0440\u0430\u0434 7 \u0413\u0432\u0430\u0440\u0434\u0435\u0439\u0441\u043a\u0430\u044f", "IIKO_RMS"),
                monitor(2L, "\u0424\u0420_\u0421\u0412 \u0412\u043e\u0440\u043e\u043d\u0435\u0436 \u041b\u0435\u043d\u0438\u043d\u0430", "IIKO_RMS"),
                monitor(3L, "\u0411\u0411 \u0412\u043e\u043b\u0433\u043e\u0433\u0440\u0430\u0434 \u041d\u043e\u0432\u044b\u0439 \u0440\u0435\u0441\u0442\u043e\u0440\u0430\u043d", "IIKO_CHAIN")
        ));

        assertThat(snapshot.activeLocationCount()).isEqualTo(3);
        assertThat(snapshot.matchedLocationCount()).isEqualTo(2);
        assertThat(snapshot.missingLocationCount()).isEqualTo(1);
        assertThat(snapshot.ambiguousLocationCount()).isZero();
        assertThat(snapshot.chainMonitorExcludedCount()).isEqualTo(1);
        assertThat(snapshot.issues()).singleElement().satisfies(issue -> {
            assertThat(issue.kind()).isEqualTo("missing");
            assertThat(issue.location()).isEqualTo("\u041d\u043e\u0432\u044b\u0439 \u0440\u0435\u0441\u0442\u043e\u0440\u0430\u043d");
        });
    }

    @Test
    void rawPrefixedLocationNameCanMatchWithoutDuplicatingPrefix() {
        RmsLocationCoverageService service = serviceWithLocations(Map.of(
                "tree", Map.of(
                        "\u0411\u043b\u0438\u043d\u0411\u0435\u0440\u0438", Map.of(
                                "\u041f\u0430\u0440\u0442\u043d\u0451\u0440\u044b-\u0444\u0440\u0430\u043d\u0447\u0430\u0439\u0437\u0438", Map.of(
                                        "\u041d\u043e\u0432\u043e\u043a\u0443\u0439\u0431\u044b\u0448\u0435\u0432\u0441\u043a", List.of("\u0424\u0420_\u0411\u0411 \u041d\u043e\u0432\u043e\u043a\u0443\u0439\u0431\u044b\u0448\u0435\u0432\u0441\u043a")
                                )
                        )
                ),
                "statuses", Map.of()
        ));

        RmsLocationCoverageService.CoverageSnapshot snapshot = service.buildCoverage(List.of(
                monitor(9L, "\u0424\u0420_\u0411\u0411 \u041d\u043e\u0432\u043e\u043a\u0443\u0439\u0431\u044b\u0448\u0435\u0432\u0441\u043a", "IIKO_RMS")
        ));

        assertThat(snapshot.activeLocationCount()).isEqualTo(1);
        assertThat(snapshot.matchedLocationCount()).isEqualTo(1);
        assertThat(snapshot.missingLocationCount()).isZero();
        assertThat(snapshot.ambiguousLocationCount()).isZero();
    }

    @Test
    void duplicateMonitorNamesAreAmbiguousNotGreen() {
        RmsLocationCoverageService service = serviceWithLocations(Map.of(
                "tree", Map.of(
                        "\u0411\u043b\u0438\u043d\u0411\u0435\u0440\u0438", Map.of(
                                "\u041a\u043e\u0440\u043f\u043e\u0440\u0430\u0442\u0438\u0432\u043d\u0430\u044f \u0441\u0435\u0442\u044c", Map.of(
                                        "\u041c\u043e\u0441\u043a\u0432\u0430", List.of("\u0410\u0440\u043c\u0430")
                                )
                        )
                ),
                "statuses", Map.of()
        ));

        RmsLocationCoverageService.CoverageSnapshot snapshot = service.buildCoverage(List.of(
                monitor(20L, "\u0411\u0411 \u041c\u043e\u0441\u043a\u0432\u0430 \u0410\u0440\u043c\u0430", "IIKO_RMS"),
                monitor(21L, "\u0411\u0411 \u041c\u043e\u0441\u043a\u0432\u0430 \u0410\u0440\u043c\u0430", "IIKO_RMS")
        ));

        assertThat(snapshot.matchedLocationCount()).isZero();
        assertThat(snapshot.missingLocationCount()).isZero();
        assertThat(snapshot.ambiguousLocationCount()).isEqualTo(1);
        assertThat(snapshot.issues()).singleElement().satisfies(issue -> {
            assertThat(issue.kind()).isEqualTo("ambiguous");
            assertThat(issue.candidates()).hasSize(2);
        });
    }

    private RmsLocationCoverageService serviceWithLocations(Map<String, Object> payload) {
        SharedConfigService sharedConfigService = new SharedConfigService(new ObjectMapper(), tempDir.toString());
        sharedConfigService.saveLocations(payload);
        return new RmsLocationCoverageService(sharedConfigService);
    }

    private RmsLicenseMonitor monitor(long id, String serverName, String serverType) {
        RmsLicenseMonitor monitor = new RmsLicenseMonitor();
        monitor.setId(id);
        monitor.setServerName(serverName);
        monitor.setServerType(serverType);
        monitor.setRmsAddress("https://rms-" + id + ".example");
        monitor.setDeleted(false);
        return monitor;
    }
}
