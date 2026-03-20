package io.github.tli.vtcrawler.site;

import java.net.URI;
import java.time.Duration;

public record PageFetchResult(
        URI uri,
        int depth,
        int statusCode,
        int bytes,
        Duration latency,
        String contentType,
        String title,
        int extractedLinkCount,
        Throwable failure,
        boolean cancelled,
        String skipReason) {

    public static PageFetchResult success(
            URI uri,
            int depth,
            int statusCode,
            int bytes,
            Duration latency,
            String contentType,
            String title,
            int extractedLinkCount) {
        return new PageFetchResult(
                uri,
                depth,
                statusCode,
                bytes,
                latency,
                contentType,
                title,
                extractedLinkCount,
                null,
                false,
                null);
    }

    public static PageFetchResult failure(
            URI uri,
            int depth,
            Throwable failure,
            Duration latency,
            String contentType) {
        return new PageFetchResult(
                uri,
                depth,
                -1,
                0,
                latency,
                contentType,
                null,
                0,
                failure,
                false,
                null);
    }

    public static PageFetchResult skipped(URI uri, int depth, String skipReason) {
        return new PageFetchResult(
                uri,
                depth,
                -1,
                0,
                Duration.ZERO,
                "",
                null,
                0,
                null,
                false,
                skipReason);
    }

    public boolean success() {
        return !cancelled && failure == null && skipReason == null && statusCode >= 200 && statusCode < 400;
    }

    public boolean html() {
        return contentType != null && contentType.toLowerCase().contains("text/html");
    }

    public boolean skipped() {
        return skipReason != null;
    }
}
