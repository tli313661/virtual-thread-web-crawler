package io.github.tli.vtcrawler.benchmark;

import io.github.tli.vtcrawler.support.ResourceSnapshot;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public record CrawlReport(
        String label,
        int totalRequests,
        int completedRequests,
        int successCount,
        int failureCount,
        int cancelledCount,
        long totalBytes,
        Duration wallClock,
        double requestsPerSecond,
        Duration averageLatency,
        Duration p95Latency,
        Duration maxLatency,
        ResourceSnapshot resourceSnapshot,
        Throwable scopeFailure) {

    static CrawlReport from(
            String label,
            int totalRequests,
            List<FetchResult> results,
            Duration wallClock,
            ResourceSnapshot resourceSnapshot,
            Throwable scopeFailure) {
        int successCount = 0;
        int failureCount = 0;
        int cancelledCount = 0;
        long totalBytes = 0;
        long totalLatencyNanos = 0;
        List<FetchResult> completedResults = results.stream()
                .filter(FetchResult::completed)
                .sorted(Comparator.comparing(FetchResult::latency))
                .toList();

        for (FetchResult result : results) {
            if (result.cancelled()) {
                cancelledCount++;
                continue;
            }
            if (result.success()) {
                successCount++;
                totalBytes += result.bytes();
            } else {
                failureCount++;
            }
            if (result.latency() != null) {
                totalLatencyNanos += result.latency().toNanos();
            }
        }

        Duration averageLatency = completedResults.isEmpty()
                ? Duration.ZERO
                : Duration.ofNanos(totalLatencyNanos / completedResults.size());
        Duration p95Latency = percentile(completedResults, 0.95);
        Duration maxLatency = completedResults.isEmpty()
                ? Duration.ZERO
                : completedResults.get(completedResults.size() - 1).latency();

        double requestsPerSecond = wallClock.isZero()
                ? 0.0
                : completedResults.size() / (wallClock.toNanos() / 1_000_000_000.0);

        return new CrawlReport(
                label,
                totalRequests,
                completedResults.size(),
                successCount,
                failureCount,
                cancelledCount,
                totalBytes,
                wallClock,
                requestsPerSecond,
                averageLatency,
                p95Latency,
                maxLatency,
                resourceSnapshot,
                scopeFailure);
    }

    private static Duration percentile(List<FetchResult> completedResults, double percentile) {
        if (completedResults.isEmpty()) {
            return Duration.ZERO;
        }
        int index = Math.max(0, (int) Math.ceil(completedResults.size() * percentile) - 1);
        return completedResults.get(index).latency();
    }

    public String toPrettyString() {
        StringBuilder builder = new StringBuilder();
        builder.append("== ").append(label).append(" ==\n");
        builder.append("totalRequests      : ").append(totalRequests).append('\n');
        builder.append("completedRequests  : ").append(completedRequests).append('\n');
        builder.append("successCount       : ").append(successCount).append('\n');
        builder.append("failureCount       : ").append(failureCount).append('\n');
        builder.append("cancelledCount     : ").append(cancelledCount).append('\n');
        builder.append("totalBytes         : ").append(totalBytes).append('\n');
        builder.append("wallClock          : ").append(formatDuration(wallClock)).append('\n');
        builder.append("requestsPerSecond  : ").append(String.format(Locale.ROOT, "%.2f", requestsPerSecond)).append('\n');
        builder.append("averageLatency     : ").append(formatDuration(averageLatency)).append('\n');
        builder.append("p95Latency         : ").append(formatDuration(p95Latency)).append('\n');
        builder.append("maxLatency         : ").append(formatDuration(maxLatency)).append('\n');
        builder.append("avgProcessCpuLoad  : ").append(String.format(Locale.ROOT, "%.2f%%", resourceSnapshot.averageProcessCpuLoad() * 100.0)).append('\n');
        builder.append("peakHeapUsed       : ").append(resourceSnapshot.peakHeapUsedBytes() / (1024 * 1024)).append(" MiB\n");
        builder.append("peakLiveThreads    : ").append(resourceSnapshot.peakLiveThreads()).append('\n');
        builder.append("peakPlatformThreads: ").append(resourceSnapshot.peakPlatformThreads());
        if (scopeFailure != null) {
            builder.append('\n').append("scopeFailure       : ").append(scopeFailure.getClass().getSimpleName())
                    .append(": ").append(scopeFailure.getMessage());
        }
        return builder.toString();
    }

    private static String formatDuration(Duration duration) {
        double millis = duration.toNanos() / 1_000_000.0;
        return String.format(Locale.ROOT, "%.2f ms", millis);
    }
}
