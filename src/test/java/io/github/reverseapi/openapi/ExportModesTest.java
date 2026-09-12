package io.github.reverseapi.openapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.reverseapi.TestExchange;
import io.github.reverseapi.detection.*;
import io.github.reverseapi.model.*;
import io.github.reverseapi.export.PostmanGenerator;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ExportModesTest {
    private final ObjectMapper json = new ObjectMapper();
    private final DetectionSettings settings = new DetectionSettings();
    private ApiOperation operation() {
        var base = TestExchange.exchange("POST", "/api/users/123?token=query-secret&tag=a&tag=b", "application/json",
                "{\"user\":\"admin\",\"pass\":\"admin\",\"nested\":[{\"token\":\"body-secret\"}]}",
                200, "application/json", "{\"token\":\"response-secret\"}");
        var sample = new CapturedExchange(base.url(),base.scheme(),base.host(),base.port(),base.method(),base.path(),base.queryParameters(),
                Map.of("Authorization",List.of("Bearer auth-secret"), "Cookie",List.of("sid=cookie-secret"),
                        "Content-Type",List.of("application/json"), "X-Tenant",List.of("tenant-secret"), "Content-Length",List.of("99")),
                base.requestBody(),base.statusCode(),Map.of("Set-Cookie",List.of("sid=response-cookie"),"Content-Type",List.of("application/json")),base.responseBody(),base.capturedAt());
        return new ApiOperation(sample,"/api/users/{id}",new DetectionResult(true,false,100,"test"),false);
    }

    @Test void openApiObfuscationIsExplicitAndDoesNotMutateCaptures() throws Exception {
        var op = operation(); String original = op.latest().requestBody();
        var generator = new OpenApiGenerator(settings);
        var clean = generator.generate(List.of(op),true);
        var raw = generator.generate(List.of(op),false);
        var cleanTree = json.readTree(clean.json()); var rawTree = json.readTree(raw.json());
        String pointer = "/paths/~1api~1users~1{id}/post/requestBody/content/application~1json/example";
        assertThat(cleanTree.at(pointer + "/user").asText()).isEmpty();
        assertThat(cleanTree.at(pointer + "/pass").asText()).isEmpty();
        assertThat(rawTree.at(pointer + "/pass").asText()).isEqualTo("admin");
        for (String text : List.of(clean.json(), clean.yaml())) assertThat(text).doesNotContain("admin","auth-secret","cookie-secret","query-secret","body-secret","response-secret","tenant-secret");
        for (String text : List.of(raw.json(), raw.yaml())) assertThat(text).contains("admin","auth-secret","cookie-secret","query-secret","body-secret","response-secret","tenant-secret");
        assertThat(op.latest().requestBody()).isEqualTo(original);
        assertThat(generator.generate(List.of(op),true)).isEqualTo(clean);
    }

    @Test void postmanRawExportRetainsPairedReplayDataAndRepeatedQueryValues() throws Exception {
        var op = operation(); var sample = op.latest();
        var root = json.readTree(new PostmanGenerator(settings).generate(List.of(op),false));
        assertThat(root.at("/info/schema").asText()).contains("v2.1.0");
        assertThat(root.at("/item/0/request/url").asText()).isEqualTo(sample.url());
        assertThat(root.at("/item/0/request/body/raw").asText()).isEqualTo(sample.requestBody());
        assertThat(root.at("/item/0/request/header").toString()).contains("auth-secret","cookie-secret").doesNotContain("Content-Length");
        assertThat(root.at("/item/0/response/0/body").asText()).isEqualTo(sample.responseBody());
        assertThat(root.at("/item/0/response/0/header").toString()).contains("response-cookie");
        assertThat(root.at("/item/0/response/0/originalRequest")).isEqualTo(root.at("/item/0/request"));
    }

    @Test void postmanObfuscationBlanksValuesWithoutChangingBurpSamples() throws Exception {
        var op = operation(); var generator = new PostmanGenerator(settings);
        String clean = generator.generate(List.of(op),true);
        assertThat(clean).doesNotContain("admin","auth-secret","cookie-secret","query-secret","body-secret","response-secret","tenant-secret","response-cookie","123");
        var root = json.readTree(clean);
        assertThat(root.at("/item/0/request/url/query").size()).isEqualTo(3);
        assertThat(root.at("/item/0/request/url/variable/0/value").asText()).isEmpty();
        var body = json.readTree(root.at("/item/0/request/body/raw").asText());
        assertThat(body.path("user").asText()).isEmpty(); assertThat(body.path("pass").asText()).isEmpty();
        assertThat(op.latest().requestBody()).contains("admin");
        assertThat(generator.generate(List.of(op),false)).contains("auth-secret");
    }

    @Test void opaquePayloadsRemainLiteralOnlyWhenObfuscationIsOff() throws Exception {
        String xml = "<!DOCTYPE x SYSTEM 'file:///secret'><x>xml-secret</x>";
        var sample = TestExchange.exchange("POST","/soap","text/xml",xml,200,"text/plain","response-value");
        var op = new ApiOperation(sample,"/soap",new DetectionResult(true,false,100,"test"),false);
        assertThat(new PostmanGenerator(settings).generate(List.of(op),true)).doesNotContain("file:///secret","xml-secret","response-value");
        var raw = json.readTree(new PostmanGenerator(settings).generate(List.of(op),false));
        assertThat(raw.at("/item/0/request/body/raw").asText()).isEqualTo(xml);
    }

    @Test void postmanHonorsExclusionWithoutOpenApiCollisionRestrictions() throws Exception {
        var first = operation(); var second = operation();
        var generator = new PostmanGenerator(settings);
        assertThat(json.readTree(generator.generate(List.of(first,second),false)).path("item").size()).isEqualTo(2);
        second.ignore();
        assertThat(json.readTree(generator.generate(List.of(first,second),false)).path("item").size()).isEqualTo(1);
    }

    @Test void formExamplesMatchSchemaTypesAndPostmanRetainsRawEncoding() throws Exception {
        var sample = TestExchange.exchange("POST","/form","application/x-www-form-urlencoded","pass=admin&count=12&flag=true&tag=a&tag=b",200,"","");
        var op = new ApiOperation(sample,"/form",new DetectionResult(true,false,100,"test"),false);
        var generator = new OpenApiGenerator(settings);
        var clean = json.readTree(generator.generate(List.of(op),true).json());
        var example = clean.at("/paths/~1form/post/requestBody/content/application~1x-www-form-urlencoded/example");
        assertThat(example.path("count").isIntegralNumber()).isTrue();
        assertThat(example.path("flag").isBoolean()).isTrue();
        assertThat(example.path("pass").asText()).isEmpty();
        var postman = new PostmanGenerator(settings);
        assertThat(json.readTree(postman.generate(List.of(op),false)).at("/item/0/request/body/raw").asText()).isEqualTo(sample.requestBody());
        assertThat(json.readTree(postman.generate(List.of(op),true)).at("/item/0/request/body/raw").asText()).isEqualTo("pass=&count=&flag=&tag=&tag=");
    }
}
