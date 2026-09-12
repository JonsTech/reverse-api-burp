package io.github.reverseapi.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class JsonSchemaInfererTest {
    private final JsonSchemaInferer inferer = new JsonSchemaInferer(new ObjectMapper());

    @Test void infersNestedTypesAndArrays() throws Exception {
        var schema = inferer.infer("{\"id\":1,\"active\":true,\"tags\":[\"a\"],\"profile\":{\"score\":1.5}}");
        assertThat(schema.at("/properties/id/type").asText()).isEqualTo("integer");
        assertThat(schema.at("/properties/active/type").asText()).isEqualTo("boolean");
        assertThat(schema.at("/properties/tags/items/type").asText()).isEqualTo("string");
        assertThat(schema.at("/properties/profile/properties/score/type").asText()).isEqualTo("number");
    }

    @Test void mergesOptionalAndNullableProperties() {
        var schema = inferer.inferAll(List.of("{\"id\":1,\"name\":\"a\"}", "{\"id\":2,\"name\":null,\"extra\":true}"));
        assertThat(schema.at("/required").toString()).doesNotContain("extra");
        assertThat(schema.at("/properties/name/type").toString()).contains("string", "null");
    }
    @Test void nullableObjectsKeepStructureAcrossLaterSamples() {
        var schema = inferer.inferAll(List.of("{\"profile\":{\"name\":\"a\"}}", "{\"profile\":null}", "{\"profile\":{\"age\":2}}"));
        assertThat(schema.at("/properties/profile/type").toString()).contains("object", "null");
        assertThat(schema.at("/properties/profile/properties").has("age")).isTrue();
        assertThat(schema.at("/properties/profile/properties").has("name")).isTrue();
        assertThat(schema.at("/properties/profile/required").isMissingNode()).isTrue();
    }

    @Test void deepInputIsBoundedAndMalformedSamplesAreIgnored() {
        var schema = inferer.inferAll(List.of("not json", "{\"value\":1}"));
        assertThat(schema.at("/properties/value/type").asText()).isEqualTo("integer");
        String deep = "[".repeat(200) + "1" + "]".repeat(200);
        assertThat(inferer.inferAll(List.of(deep))).isNull();
    }
}

