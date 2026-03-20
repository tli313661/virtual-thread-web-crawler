package io.github.tli.vtcrawler.site;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;

public final class UrlNormalizer {
    private UrlNormalizer() {
    }

    public static URI normalizeSeed(URI seedUri) {
        return normalize(seedUri).orElseThrow(() -> new IllegalArgumentException("Unsupported seed URL: " + seedUri));
    }

    public static Optional<URI> normalizeDiscovered(URI baseUri, String href) {
        if (href == null || href.isBlank()) {
            return Optional.empty();
        }
        String trimmed = href.trim();
        if (trimmed.startsWith("#")
                || trimmed.startsWith("javascript:")
                || trimmed.startsWith("mailto:")
                || trimmed.startsWith("tel:")) {
            return Optional.empty();
        }
        return normalize(baseUri.resolve(trimmed));
    }

    public static boolean isSameHost(URI left, URI right) {
        return left.getHost() != null
                && right.getHost() != null
                && left.getHost().equalsIgnoreCase(right.getHost())
                && normalizedPort(left) == normalizedPort(right);
    }

    public static String originKey(URI uri) {
        URI normalized = normalize(uri).orElseThrow(() -> new IllegalArgumentException("Unsupported URL: " + uri));
        return normalized.getScheme() + "://" + normalized.getHost() + ":" + effectivePort(normalized);
    }

    private static Optional<URI> normalize(URI uri) {
        if (uri == null || uri.getScheme() == null || uri.getHost() == null) {
            return Optional.empty();
        }

        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return Optional.empty();
        }

        String host = uri.getHost().toLowerCase(Locale.ROOT);
        int port = normalizedPort(uri);
        String path = uri.getRawPath();
        if (path == null || path.isBlank()) {
            path = "/";
        }

        try {
            return Optional.of(new URI(
                    scheme,
                    uri.getRawUserInfo(),
                    host,
                    port,
                    path,
                    uri.getRawQuery(),
                    null));
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }

    private static int normalizedPort(URI uri) {
        int port = uri.getPort();
        if (port == 80 && "http".equalsIgnoreCase(uri.getScheme())) {
            return -1;
        }
        if (port == 443 && "https".equalsIgnoreCase(uri.getScheme())) {
            return -1;
        }
        return port;
    }

    private static int effectivePort(URI uri) {
        int port = normalizedPort(uri);
        if (port != -1) {
            return port;
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }
}
