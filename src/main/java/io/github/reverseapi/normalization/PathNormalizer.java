package io.github.reverseapi.normalization;

import java.util.regex.Pattern;

public final class PathNormalizer {
    private static final Pattern INTEGER = Pattern.compile("[0-9]+");
    private static final Pattern UUID = Pattern.compile("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
    private static final Pattern HEX_TOKEN = Pattern.compile("(?i)[0-9a-f]{24,}");
    private static final Pattern OPAQUE_TOKEN = Pattern.compile("(?=.*[A-Za-z])(?=.*[0-9])[A-Za-z0-9_-]{32,}");
    private static final Pattern DATE_TIME = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}(?:[Tt ][0-9]{2}:[0-9]{2}(?::[0-9]{2}(?:\\.[0-9]+)?)?(?:[Zz]|[+-][0-9]{2}:?[0-9]{2})?)?");

    public String normalize(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) return "/";
        String path = rawPath.split("\\?", 2)[0];
        if (!path.startsWith("/")) path = "/" + path;
        String[] segments = path.split("/", -1);
        int identifier = 0;
        int date = 0;
        for (int i = 0; i < segments.length; i++) {
            if (isDate(segments[i])) segments[i] = ++date == 1 ? "{date}" : "{date" + date + "}";
            else if (isDynamic(segments[i])) segments[i] = ++identifier == 1 ? "{id}" : "{id" + identifier + "}";
        }
        String result = String.join("/", segments);
        return result.isEmpty() ? "/" : result;
    }

    private boolean isDate(String segment) {
        try {
            // '+' in a path is literal, unlike form/query encoding.
            String decoded = java.net.URLDecoder.decode(segment.replace("+", "%2B"), java.nio.charset.StandardCharsets.UTF_8);
            return DATE_TIME.matcher(decoded).matches();
        } catch (IllegalArgumentException malformed) { return false; }
    }

    private boolean isDynamic(String segment) {
        return INTEGER.matcher(segment).matches() || UUID.matcher(segment).matches()
                || HEX_TOKEN.matcher(segment).matches() || OPAQUE_TOKEN.matcher(segment).matches();
    }
}
