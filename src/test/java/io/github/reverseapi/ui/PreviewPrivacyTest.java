package io.github.reverseapi.ui;
import io.github.reverseapi.TestExchange;
import io.github.reverseapi.model.CapturedExchange;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class PreviewPrivacyTest {
    @Test void previewsAlwaysShowCapturedValuesAndBodies() throws Exception {
        var base = TestExchange.exchange("POST","/api?token=query-secret", "application/json","{\"ordinary\":\"private-body\"}",200,"text/xml","<x>private-response</x>");
        var sample = new CapturedExchange(base.url(),base.scheme(),base.host(),base.port(),base.method(),base.path(),base.queryParameters(),
                Map.of("Unrecognized-Header",List.of("private-header")),base.requestBody(),base.statusCode(),
                Map.of("Set-Cookie",List.of("id=private-cookie")),base.responseBody(),base.capturedAt());
        var request = ReverseApiPanel.class.getDeclaredMethod("formatRequest",CapturedExchange.class); request.setAccessible(true);
        var response = ReverseApiPanel.class.getDeclaredMethod("formatResponse",CapturedExchange.class); response.setAccessible(true);
        assertThat((String)request.invoke(null,sample)).contains("private-body", "private-header", "?token=query-secret").doesNotContain("omitted");
        assertThat((String)response.invoke(null,sample)).contains("private-response", "private-cookie").doesNotContain("omitted");
    }
}
