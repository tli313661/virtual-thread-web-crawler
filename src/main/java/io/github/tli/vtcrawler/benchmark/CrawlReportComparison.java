package io.github.tli.vtcrawler.benchmark;

import java.util.Locale;

public final class CrawlReportComparison {
    private CrawlReportComparison() {
    }

    public static String summarize(CrawlReport left, CrawlReport right) {
        StringBuilder builder = new StringBuilder();
        builder.append("== comparison ==\n");
        builder.append(compareLine("throughput", left.requestsPerSecond(), right.requestsPerSecond(), " req/s"));
        builder.append('\n');
        builder.append(compareLine(
                "wallClock",
                left.wallClock().toMillis(),
                right.wallClock().toMillis(),
                " ms"));
        builder.append('\n');
        builder.append(compareLine(
                "peakHeapUsed",
                left.resourceSnapshot().peakHeapUsedBytes() / (1024.0 * 1024.0),
                right.resourceSnapshot().peakHeapUsedBytes() / (1024.0 * 1024.0),
                " MiB"));
        builder.append('\n');
        builder.append(compareLine(
                "avgCpuLoad",
                left.resourceSnapshot().averageProcessCpuLoad() * 100.0,
                right.resourceSnapshot().averageProcessCpuLoad() * 100.0,
                " %"));
        builder.append('\n');
        builder.append(compareLine(
                "peakPlatformThreads",
                left.resourceSnapshot().peakPlatformThreads(),
                right.resourceSnapshot().peakPlatformThreads(),
                ""));
        return builder.toString();
    }

    private static String compareLine(String label, double left, double right, String unit) {
        double delta = left - right;
        return String.format(
                Locale.ROOT,
                "%-18s: virtual=%.2f%s, fixed=%.2f%s, delta=%.2f%s",
                label,
                left,
                unit,
                right,
                unit,
                delta,
                unit);
    }
}
