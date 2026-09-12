package io.github.reverseapi.ui;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DetectionSettingsPanelTest {
    @Test void wildcardListsAreAnchoredAndEscapeRegexCharacters() {
        String regex = DetectionSettingsPanel.wildcardListToRegex("api.example.com, *.internal.test\n*/health?");
        var pattern = com.google.re2j.Pattern.compile(regex, com.google.re2j.Pattern.CASE_INSENSITIVE);
        assertThat(pattern.matcher("api.example.com").find()).isTrue();
        assertThat(pattern.matcher("dev.internal.test").find()).isTrue();
        assertThat(pattern.matcher("https://api.example.com/health1").find()).isTrue();
        assertThat(pattern.matcher("not-api.example.com").find()).isFalse();
        assertThat(pattern.matcher("apiXexampleXcom").find()).isFalse();
    }
}
