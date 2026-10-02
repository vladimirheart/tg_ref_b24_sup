package com.example.panel.runtime;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class UiTimeZoneSelectorSourceContractTest {

    @Test
    void hidesTechnicalEtcZonesAndLabelsPersistedLegacyFixedOffsetsClearly() throws IOException {
        String source = Files.readString(Path.of("src/main/resources/static/js/ui-time.js"), UTF_8);

        assertThat(source)
            .contains("function isSelectableTimeZone(zone)")
            .contains("!normalized.startsWith('Etc/')")
            .contains("if (isSelectableTimeZone(zone) && !result.includes(zone)) result.push(zone);")
            .contains("const fixedOffset = normalized.match(/^Etc\\/GMT([+-])(\\d{1,2})$/);")
            .contains("const displaySign = fixedOffset[1] === '+' ? '-' : '+';")
            .contains("return `UTC${displaySign}${hours}:00 (legacy fixed offset)`;")
            .contains("if (!result.includes(current)) result.splice(1, 0, current);")
            .contains("option.textContent = timeZoneOptionLabel(zone);")
            .doesNotContain("option.textContent = zone === DEFAULT_TIME_ZONE ? 'UTC (по умолчанию)' : zone;");
    }
}
