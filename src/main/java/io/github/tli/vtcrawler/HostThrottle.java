package io.github.tli.vtcrawler;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

public final class HostThrottle {
    private final int maxConcurrentPerHost;
    private final Duration minHostDelay;
    private final Map<String, HostBudget> budgets;

    public HostThrottle(int maxConcurrentPerHost, Duration minHostDelay) {
        this.maxConcurrentPerHost = maxConcurrentPerHost;
        this.minHostDelay = minHostDelay;
        this.budgets = new ConcurrentHashMap<>();
    }

    public Permit acquire(String hostKey) throws InterruptedException {
        HostBudget budget = budgets.computeIfAbsent(hostKey, ignored -> new HostBudget(maxConcurrentPerHost));
        budget.semaphore.acquire();
        boolean success = false;
        try {
            budget.awaitTurn(minHostDelay);
            success = true;
            return new Permit(budget);
        } finally {
            if (!success) {
                budget.semaphore.release();
            }
        }
    }

    public final class Permit implements AutoCloseable {
        private final HostBudget budget;
        private boolean closed;

        private Permit(HostBudget budget) {
            this.budget = budget;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                budget.semaphore.release();
            }
        }
    }

    private static final class HostBudget {
        private final Semaphore semaphore;
        private long nextAvailableAtNanos;

        private HostBudget(int maxConcurrentPerHost) {
            this.semaphore = new Semaphore(maxConcurrentPerHost);
            this.nextAvailableAtNanos = 0L;
        }

        private synchronized void awaitTurn(Duration minHostDelay) throws InterruptedException {
            if (minHostDelay.isZero()) {
                return;
            }

            long now = System.nanoTime();
            long waitNanos = nextAvailableAtNanos - now;
            if (waitNanos > 0) {
                long millis = waitNanos / 1_000_000L;
                int nanos = (int) (waitNanos % 1_000_000L);
                Thread.sleep(millis, nanos);
                now = System.nanoTime();
            }
            nextAvailableAtNanos = Math.max(now, nextAvailableAtNanos) + minHostDelay.toNanos();
        }
    }
}
