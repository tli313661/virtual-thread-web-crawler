package io.github.tli.vtcrawler.benchmark;

import java.net.URI;
import java.time.Duration;

public record FetchResult(
        URI uri,
        int statusCode,
        int bytes,
        Duration latency,
        Throwable failure,
        boolean cancelled) {

    public static FetchResult success(URI uri, int statusCode, int bytes, Duration latency) {
        return new FetchResult(uri, statusCode, bytes, latency, null, false);
    }

    public static FetchResult failure(URI uri, Throwable failure, Duration latency) {
        return new FetchResult(uri, -1, 0, latency, failure, false);
    }

    public static FetchResult failure(URI uri, Throwable failure) {
        return failure(uri, failure, Duration.ZERO);
    }

    public static FetchResult cancelledResult() {
        return new FetchResult(null, -1, 0, Duration.ZERO, null, true);
    }

    public boolean success() {
        return !cancelled && failure == null && statusCode >= 200 && statusCode < 400;
    }

    public boolean completed() {
        return !cancelled;
    }
}
