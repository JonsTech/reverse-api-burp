package io.github.reverseapi.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;


import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class JsonSchemaInferer {
    private final ObjectMapper mapper;

    public JsonSchemaInferer(ObjectMapper mapper) {
        this.mapper = mapper.copy().enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.mapper.getFactory().setStreamReadConstraints(JsonSupport.mapper().getFactory().streamReadConstraints());
    }

    public ObjectNode infer(String json) throws java.io.IOException { return inferNode(mapper.readTree(json), 0, new int[]{4096}); }

    public ObjectNode inferAll(List<String> payloads) {
        ObjectNode merged = null;
        for (String payload : payloads) {
            if (payload == null || payload.isBlank()) continue;
            try { merged = merged == null ? infer(payload) : merge(merged, infer(payload)); }
            catch (Exception ignored) { }
        }
        return merged;
    }

    private ObjectNode inferNode(JsonNode value, int depth, int[] budget) {
        ObjectNode schema = mapper.createObjectNode();
        if (depth > 32 || --budget[0] < 0) return schema;
        if (value == null || value.isNull()) { schema.put("type", "null"); return schema; }
        if (value.isObject()) {
            schema.put("type", "object");
            ObjectNode properties = schema.putObject("properties");
            ArrayNode required = schema.putArray("required");
            var fields = value.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                properties.set(field.getKey(), inferNode(field.getValue(), depth + 1, budget)); required.add(field.getKey());
                if (budget[0] < 0) return mapper.createObjectNode();
            }
        } else if (value.isArray()) {
            schema.put("type", "array");
            ObjectNode items = null;
            for (JsonNode item : value) {
                items = items == null ? inferNode(item, depth + 1, budget) : merge(items, inferNode(item, depth + 1, budget));
                if (budget[0] < 0) { items = mapper.createObjectNode(); break; }
            }
            schema.set("items", items == null ? mapper.createObjectNode() : items);
        } else if (value.isIntegralNumber()) schema.put("type", "integer");
        else if (value.isFloatingPointNumber()) schema.put("type", "number");
        else if (value.isBoolean()) schema.put("type", "boolean");
        else schema.put("type", "string");
        return schema;
    }

    public ObjectNode merge(ObjectNode left, ObjectNode right) {
        if (left.isEmpty() || right.isEmpty()) return mapper.createObjectNode();
        Set<String> types = types(left); types.addAll(types(right));
        ObjectNode result = left.deepCopy();
        if (types.size() > 1) { ArrayNode values = result.putArray("type"); types.forEach(values::add); }
        else if (!types.isEmpty()) result.put("type", types.iterator().next());
        if (types.contains("object")) {
            if (!left.has("properties") && right.has("properties")) {
                result.set("properties", right.get("properties").deepCopy());
                if (right.has("required")) result.set("required", right.get("required").deepCopy());
            } else if (left.has("properties") && right.has("properties")) {
                ObjectNode properties = (ObjectNode) result.get("properties");
                right.get("properties").fields().forEachRemaining(e -> {
                    if (properties.has(e.getKey())) properties.set(e.getKey(), merge((ObjectNode) properties.get(e.getKey()), (ObjectNode) e.getValue()));
                    else properties.set(e.getKey(), e.getValue().deepCopy());
                });
                Set<String> required = arrayValues(left.get("required")); required.retainAll(arrayValues(right.get("required")));
                ArrayNode values = result.putArray("required"); required.forEach(values::add);
                if (required.isEmpty()) result.remove("required");
            }
        }
        if (types.contains("array")) {
            if (left.get("items") instanceof ObjectNode li && right.get("items") instanceof ObjectNode ri) result.set("items", merge(li, ri));
            else if (right.has("items")) result.set("items", right.get("items").deepCopy());
        }
        return result;
    }

    private static Set<String> types(ObjectNode schema) {
        Set<String> result = new LinkedHashSet<>(); JsonNode node = schema.get("type");
        if (node == null) return result;
        if (node.isArray()) node.forEach(n -> result.add(n.asText())); else result.add(node.asText());
        return result;
    }

    private static Set<String> arrayValues(JsonNode node) {
        Set<String> result = new LinkedHashSet<>(); if (node != null && node.isArray()) node.forEach(v -> result.add(v.asText())); return result;
    }
}
