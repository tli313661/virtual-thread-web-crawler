package io.github.tli.vtcrawler.site;

import io.github.tli.vtcrawler.support.ResourceSnapshot;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public record SiteCrawlReport(
        String label,
        URI seedUri,
        int scheduledPages,
        int visitedPages,
        int pendingPages,
        int successCount,
        int failureCount,
        int robotsSkippedCount,
        int htmlPages,
        int nonHtmlPages,
        int discoveredLinks,
        int duplicateLinksSkipped,
        int offHostLinksSkipped,
        int invalidLinksSkipped,
        int robotsDeniedPages,
        int depthLimitedLinksSkipped,
        int pageBudgetSkipped,
        int maxObservedDepth,
        Duration wallClock,
        double pagesPerSecond,
        Duration averageLatency,
        Duration p95Latency,
        Duration maxLatency,
        ResourceSnapshot resourceSnapshot,
        Throwable scopeFailure,
        Path jsonlOutputPath,
        List<PageFetchResult> samplePages) {

    static SiteCrawlReport from(
            String label,
            URI seedUri,
            int scheduledPages,
            List<PageFetchResult> pages,
            int discoveredLinks,
            int duplicateLinksSkipped,
            int offHostLinksSkipped,
            int invalidLinksSkipped,
            int robotsDeniedPages,
            int depthLimitedLinksSkipped,
            int pageBudgetSkipped,
            Duration wallClock,
            ResourceSnapshot resourceSnapshot,
            Throwable scopeFailure,
            Path jsonlOutputPath,
            int sampleLimit) {
        List<PageFetchResult> sortedPages = pages.stream()
                .sorted(Comparator
                        .comparingInt(PageFetchResult::depth)
                        .thenComparing(result -> result.uri().toString()))
                .toList();

        int successCount = 0;
        int failureCount = 0;
        int robotsSkippedCount = 0;
        int htmlPages = 0;
        int nonHtmlPages = 0;
        int maxObservedDepth = 0;
        long totalLatencyNanos = 0;

        for (PageFetchResult page : sortedPages) {
            maxObservedDepth = Math.max(maxObservedDepth, page.depth());
            totalLatencyNanos += page.latency().toNanos();
            if (page.skipped()) {
                robotsSkippedCount++;
            } else if (page.success()) {
                successCount++;
                if (page.html()) {
                    htmlPages++;
                } else {
                    nonHtmlPages++;
                }
            } else {
                failureCount++;
            }
        }

        Duration averageLatency = sortedPages.isEmpty()
                ? Duration.ZERO
                : Duration.ofNanos(totalLatencyNanos / sortedPages.size());
        Duration p95Latency = percentile(sortedPages, 0.95);
        Duration maxLatency = sortedPages.isEmpty()
                ? Duration.ZERO
                : sortedPages.stream()
                        .map(PageFetchResult::latency)
                        .max(Comparator.naturalOrder())
                        .orElse(Duration.ZERO);
        double pagesPerSecond = wallClock.isZero()
                ? 0.0
                : pages.size() / (wallClock.toNanos() / 1_000_000_000.0);
        int pendingPages = Math.max(0, scheduledPages - pages.size());

        return new SiteCrawlReport(
                label,
                seedUri,
                scheduledPages,
                pages.size(),
                pendingPages,
                successCount,
                failureCount,
                robotsSkippedCount,
                htmlPages,
                nonHtmlPages,
                discoveredLinks,
                duplicateLinksSkipped,
                offHostLinksSkipped,
                invalidLinksSkipped,
                robotsDeniedPages,
                depthLimitedLinksSkipped,
                pageBudgetSkipped,
                maxObservedDepth,
                wallClock,
                pagesPerSecond,
                averageLatency,
                p95Latency,
                maxLatency,
                resourceSnapshot,
                scopeFailure,
                jsonlOutputPath,
                sortedPages.stream().limit(sampleLimit).toList());
    }

    private static Duration percentile(List<PageFetchResult> pages, double percentile) {
        if (pages.isEmpty()) {
            return Duration.ZERO;
        }
        List<Duration> latencies = pages.stream()
                .map(PageFetchResult::latency)
                .sorted()
                .toList();
        int index = Math.max(0, (int) Math.ceil(latencies.size() * percentile) - 1);
        return latencies.get(index);
    }

    public String toPrettyString() {
        StringBuilder builder = new StringBuilder();
        builder.append("== ").append(label).append(" ==\n");
        builder.append("seedUrl              : ").append(seedUri).append('\n');
        builder.append("scheduledPages       : ").append(scheduledPages).append('\n');
        builder.append("visitedPages         : ").append(visitedPages).append('\n');
        builder.append("pendingPages         : ").append(pendingPages).append('\n');
        builder.append("successCount         : ").append(successCount).append('\n');
        builder.append("failureCount         : ").append(failureCount).append('\n');
        builder.append("robotsSkippedCount   : ").append(robotsSkippedCount).append('\n');
        builder.append("htmlPages            : ").append(htmlPages).append('\n');
        builder.append("nonHtmlPages         : ").append(nonHtmlPages).append('\n');
        builder.append("discoveredLinks      : ").append(discoveredLinks).append('\n');
        builder.append("duplicateLinksSkipped: ").append(duplicateLinksSkipped).append('\n');
        builder.append("offHostLinksSkipped  : ").append(offHostLinksSkipped).append('\n');
        builder.append("invalidLinksSkipped  : ").append(invalidLinksSkipped).append('\n');
        builder.append("robotsDeniedPages    : ").append(robotsDeniedPages).append('\n');
        builder.append("depthLimitedSkipped  : ").append(depthLimitedLinksSkipped).append('\n');
        builder.append("pageBudgetSkipped    : ").append(pageBudgetSkipped).append('\n');
        builder.append("maxObservedDepth     : ").append(maxObservedDepth).append('\n');
        builder.append("wallClock            : ").append(formatDuration(wallClock)).append('\n');
        builder.append("pagesPerSecond       : ").append(String.format(Locale.ROOT, "%.2f", pagesPerSecond)).append('\n');
        builder.append("averageLatency       : ").append(formatDuration(averageLatency)).append('\n');
        builder.append("p95Latency           : ").append(formatDuration(p95Latency)).append('\n');
        builder.append("maxLatency           : ").append(formatDuration(maxLatency)).append('\n');
        builder.append("avgProcessCpuLoad    : ")
                .append(String.format(Locale.ROOT, "%.2f%%", resourceSnapshot.averageProcessCpuLoad() * 100.0))
                .append('\n');
        builder.append("peakHeapUsed         : ").append(resourceSnapshot.peakHeapUsedBytes() / (1024 * 1024)).append(" MiB\n");
        builder.append("peakLiveThreads      : ").append(resourceSnapshot.peakLiveThreads()).append('\n');
        builder.append("peakPlatformThreads  : ").append(resourceSnapshot.peakPlatformThreads());
        if (scopeFailure != null) {
            builder.append('\n')
                    .append("scopeFailure         : ")
                    .append(scopeFailure.getClass().getSimpleName())
                    .append(": ")
                    .append(scopeFailure.getMessage());
        }
        if (jsonlOutputPath != null) {
            builder.append('\n')
                    .append("jsonlOutputPath      : ")
                    .append(jsonlOutputPath);
        }
        if (!samplePages.isEmpty()) {
            builder.append("\n\nsamplePages:\n");
            for (PageFetchResult page : samplePages) {
                builder.append(" - depth=").append(page.depth())
                        .append(" status=").append(page.statusCode())
                        .append(" links=").append(page.extractedLinkCount())
                        .append(" skip=").append(page.skipReason() == null ? "<none>" : page.skipReason())
                        .append(" title=")
                        .append(page.title() == null || page.title().isBlank() ? "<none>" : page.title())
                        .append(" uri=").append(page.uri())
                        .append('\n');
            }
        }
        return builder.toString().trim();
    }

    private static String formatDuration(Duration duration) {
        double millis = duration.toNanos() / 1_000_000.0;
        return String.format(Locale.ROOT, "%.2f ms", millis);
    }
}
