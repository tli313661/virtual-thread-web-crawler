package io.github.tli.vtcrawler;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.TimeUnit;

public final class CrawlerEngine {
    private CrawlerEngine() {
    }

    public static CrawlReport crawlWithVirtualThreads(
            String label,
            HttpClientFactory httpClientFactory,
            List<URI> targets,
            Duration requestTimeout,
            Duration scopeTimeout) throws Exception {
        ResourceSampler sampler = ResourceSampler.start();
        long startNanos = System.nanoTime();
        List<FetchResult> results = new ArrayList<>(targets.size());
        Throwable scopeFailure = null;

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
                StructuredTaskScope<List<FetchResult>, Void> scope = StructuredTaskScope.open(
                        StructuredTaskScope.Joiner.awaitAll(),
                        config -> config
                                .withTimeout(scopeTimeout)
                                .withThreadFactory(Thread.ofVirtual().name("crawler-supervisor-", 0).factory())
                                .withName("crawler-virtual-scope"))) {
            HttpClient client = httpClientFactory.create();
            StructuredTaskScope.Subtask<List<FetchResult>> crawlSubtask = scope.fork(() -> submitAndCollect(
                    executor,
                    client,
                    targets,
                    requestTimeout,
                    httpClientFactory.userAgent(),
                    scopeTimeout));

            scope.join();
            if (crawlSubtask.state() == StructuredTaskScope.Subtask.State.SUCCESS) {
                results.addAll(crawlSubtask.get());
            }
        } catch (Throwable failure) {
            scopeFailure = failure;
        }

        ResourceSnapshot snapshot = sampler.stop();
        return CrawlReport.from(
                label,
                targets.size(),
                results,
                Duration.ofNanos(System.nanoTime() - startNanos),
                snapshot,
                scopeFailure);
    }

    public static CrawlReport crawlWithPlatformThreadPool(
            String label,
            HttpClientFactory httpClientFactory,
            List<URI> targets,
            Duration requestTimeout,
            Duration scopeTimeout,
            int platformThreads) throws InterruptedException {
        ResourceSampler sampler = ResourceSampler.start();
        long startNanos = System.nanoTime();
        List<FetchResult> results = new ArrayList<>(targets.size());
        Throwable scopeFailure = null;

        try (ExecutorService executor = Executors.newFixedThreadPool(platformThreads)) {
            HttpClient client = httpClientFactory.create();
            results.addAll(submitAndCollect(
                    executor,
                    client,
                    targets,
                    requestTimeout,
                    httpClientFactory.userAgent(),
                    scopeTimeout));
        } catch (RuntimeException e) {
            scopeFailure = e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scopeFailure = e;
        }

        ResourceSnapshot snapshot = sampler.stop();
        return CrawlReport.from(
                label,
                targets.size(),
                results,
                Duration.ofNanos(System.nanoTime() - startNanos),
                snapshot,
                scopeFailure);
    }

    private static List<FetchResult> submitAndCollect(
            ExecutorService executor,
            HttpClient client,
            List<URI> targets,
            Duration requestTimeout,
            String userAgent,
            Duration scopeTimeout) throws InterruptedException {
        ExecutorCompletionService<FetchResult> completionService = new ExecutorCompletionService<>(executor);
        List<Future<FetchResult>> futures = new ArrayList<>(targets.size());
        List<FetchResult> results = new ArrayList<>(targets.size());

        for (URI uri : targets) {
            futures.add(completionService.submit(new FetchCallable(client, uri, requestTimeout, userAgent)));
        }

        long deadlineNanos = System.nanoTime() + scopeTimeout.toNanos();
        try {
            for (int i = 0; i < futures.size(); i++) {
                long remainingNanos = deadlineNanos - System.nanoTime();
                if (remainingNanos <= 0) {
                    cancelAll(futures);
                    throw new RuntimeException("Timed out waiting for tasks after " + scopeTimeout);
                }

                Future<FetchResult> future = completionService.poll(remainingNanos, TimeUnit.NANOSECONDS);
                if (future == null) {
                    cancelAll(futures);
                    throw new RuntimeException("Timed out waiting for tasks after " + scopeTimeout);
                }

                try {
                    results.add(future.get());
                } catch (ExecutionException e) {
                    results.add(FetchResult.failure(null, e.getCause()));
                }
            }
            return results;
        } catch (InterruptedException e) {
            cancelAll(futures);
            throw e;
        }
    }

    private static void cancelAll(List<Future<FetchResult>> futures) {
        for (Future<FetchResult> future : futures) {
            future.cancel(true);
        }
    }

    private static FetchResult fetch(HttpClient client, URI uri, Duration requestTimeout, String userAgent) {
        long startNanos = System.nanoTime();
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .GET()
                    .timeout(requestTimeout)
                    .header("User-Agent", userAgent)
                    .build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            return FetchResult.success(
                    uri,
                    response.statusCode(),
                    response.body().length,
                    Duration.ofNanos(System.nanoTime() - startNanos));
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return FetchResult.failure(uri, e, Duration.ofNanos(System.nanoTime() - startNanos));
        } catch (RuntimeException e) {
            return FetchResult.failure(uri, e, Duration.ofNanos(System.nanoTime() - startNanos));
        }
    }

    private record FetchCallable(HttpClient client, URI uri, Duration requestTimeout, String userAgent)
            implements Callable<FetchResult> {
        @Override
        public FetchResult call() {
            return fetch(client, uri, requestTimeout, userAgent);
        }
    }
}
