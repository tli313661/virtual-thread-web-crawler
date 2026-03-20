package io.github.tli.vtcrawler.support;

import com.sun.management.OperatingSystemMXBean;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ResourceSampler {
    private final OperatingSystemMXBean operatingSystemMXBean;
    private final MemoryMXBean memoryMXBean;
    private final ThreadMXBean threadMXBean;
    private final AtomicBoolean running;
    private final Thread samplerThread;
    private final Set<Long> platformThreadIds;
    private final List<Double> cpuSamples;

    private volatile long peakHeapUsedBytes;
    private volatile long peakCommittedVirtualMemoryBytes;
    private volatile int peakLiveThreads;
    private volatile int peakPlatformThreads;

    private ResourceSampler() {
        this.operatingSystemMXBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        this.memoryMXBean = ManagementFactory.getMemoryMXBean();
        this.threadMXBean = ManagementFactory.getThreadMXBean();
        this.running = new AtomicBoolean(true);
        this.platformThreadIds = ConcurrentHashMap.newKeySet();
        this.cpuSamples = new ArrayList<>();
        this.samplerThread = Thread.ofPlatform()
                .daemon()
                .name("resource-sampler")
                .start(this::sampleLoop);
    }

    public static ResourceSampler start() {
        return new ResourceSampler();
    }

    public ResourceSnapshot stop() throws InterruptedException {
        running.set(false);
        samplerThread.join(Duration.ofSeconds(1));
        double averageCpuLoad = cpuSamples.stream()
                .filter(sample -> sample >= 0)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
        return new ResourceSnapshot(
                peakHeapUsedBytes,
                peakCommittedVirtualMemoryBytes,
                peakLiveThreads,
                peakPlatformThreads,
                averageCpuLoad);
    }

    private void sampleLoop() {
        while (running.get()) {
            sampleOnce();
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        sampleOnce();
    }

    private void sampleOnce() {
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        peakHeapUsedBytes = Math.max(peakHeapUsedBytes, heapUsage.getUsed());
        peakCommittedVirtualMemoryBytes = Math.max(
                peakCommittedVirtualMemoryBytes,
                operatingSystemMXBean.getCommittedVirtualMemorySize());
        peakLiveThreads = Math.max(peakLiveThreads, threadMXBean.getThreadCount());
        cpuSamples.add(operatingSystemMXBean.getProcessCpuLoad());
        updatePlatformThreadEstimate();
    }

    private void updatePlatformThreadEstimate() {
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (!thread.isVirtual()) {
                platformThreadIds.add(thread.threadId());
            }
        }
        peakPlatformThreads = Math.max(peakPlatformThreads, platformThreadIds.size());
    }
}
