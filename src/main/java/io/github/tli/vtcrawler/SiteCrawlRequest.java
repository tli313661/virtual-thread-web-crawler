package io.github.tli.vtcrawler;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;

public record SiteCrawlRequest(
        URI seedUri,
        int maxPages,
        int maxDepth,
        int parallelism,
        Duration requestTimeout,
        Duration crawlTimeout,
        boolean sameHostOnly,
        int samplePages,
        String userAgent,
        boolean honorRobotsTxt,
        int maxConcurrentPerHost,
        Duration minHostDelay,
        Path jsonlOutputPath) {

    public SiteCrawlRequest {
        if (seedUri == null) {
            throw new IllegalArgumentException("seedUri must not be null");
        }
        if (maxPages <= 0) {
            throw new IllegalArgumentException("maxPages must be > 0");
        }
        if (maxDepth < 0) {
            throw new IllegalArgumentException("maxDepth must be >= 0");
        }
        if (parallelism <= 0) {
            throw new IllegalArgumentException("parallelism must be > 0");
        }
        if (requestTimeout == null || requestTimeout.isNegative() || requestTimeout.isZero()) {
            throw new IllegalArgumentException("requestTimeout must be > 0");
        }
        if (crawlTimeout == null || crawlTimeout.isNegative() || crawlTimeout.isZero()) {
            throw new IllegalArgumentException("crawlTimeout must be > 0");
        }
        if (samplePages < 0) {
            throw new IllegalArgumentException("samplePages must be >= 0");
        }
        if (userAgent == null || userAgent.isBlank()) {
            throw new IllegalArgumentException("userAgent must not be blank");
        }
        if (maxConcurrentPerHost <= 0) {
            throw new IllegalArgumentException("maxConcurrentPerHost must be > 0");
        }
        if (minHostDelay == null || minHostDelay.isNegative()) {
            throw new IllegalArgumentException("minHostDelay must be >= 0");
        }
    }
}
