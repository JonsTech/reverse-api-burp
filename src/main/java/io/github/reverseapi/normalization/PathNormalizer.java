package io.github.reverseapi.normalization;

import java.util.regex.Pattern;

public final class PathNormalizer {
    private static final Pattern INTEGER = Pattern.compile("[0-9]+");
    private static final Pattern UUID = Pattern.compile("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
    private static final Pattern HEX_TOKEN = Pattern.compile("(?i)[0-9a-f]{24,}");
    private static final Pattern OPAQUE_TOKEN = Pattern.compile("(?=.*[A-Za-z])(?=.*[0-9])[A-Za-z0-9_-]{32,}");

    public String normalize(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) return "/";
        String path = rawPath.split("\\?", 2)[0];
        if (!path.startsWith("/")) path = "/" + path;
        String[] segments = path.split("/", -1);
        int identifier = 0;
        for (int i = 0; i < segments.length; i++) {
            if (isDynamic(segments[i])) segments[i] = ++identifier == 1 ? "{id}" : "{id" + identifier + "}";
        }
        String result = String.join("/", segments);
        return result.isEmpty() ? "/" : result;
    }

    private boolean isDynamic(String segment) {
        return INTEGER.matcher(segment).matches() || UUID.matcher(segment).matches()
                || HEX_TOKEN.matcher(segment).matches() || OPAQUE_TOKEN.matcher(segment).matches();
    }
}
