package io.github.tli.vtcrawler.support;

public record ResourceSnapshot(
        long peakHeapUsedBytes,
        long peakCommittedVirtualMemoryBytes,
        int peakLiveThreads,
        int peakPlatformThreads,
        double averageProcessCpuLoad) {
}
