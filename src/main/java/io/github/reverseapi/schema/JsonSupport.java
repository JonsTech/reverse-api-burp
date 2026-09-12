package io.github.reverseapi.schema;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class JsonSupport {
    private JsonSupport() { }
    public static ObjectMapper mapper() {
        return new ObjectMapper(JsonFactory.builder().streamReadConstraints(StreamReadConstraints.builder()
                .maxNestingDepth(64).maxStringLength(262144).maxNameLength(4096).maxNumberLength(1000).build()).build())
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }
}
