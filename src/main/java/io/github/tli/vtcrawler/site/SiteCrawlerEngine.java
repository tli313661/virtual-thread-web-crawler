package io.github.tli.vtcrawler.site;

import io.github.tli.vtcrawler.support.HttpClientFactory;
import io.github.tli.vtcrawler.support.ResourceSampler;
import io.github.tli.vtcrawler.support.ResourceSnapshot;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class SiteCrawlerEngine {
    private SiteCrawlerEngine() {
    }

    public static SiteCrawlReport crawl(String label, HttpClientFactory httpClientFactory, SiteCrawlRequest request)
            throws InterruptedException {
        URI seedUri = UrlNormalizer.normalizeSeed(request.seedUri());
        ResourceSampler sampler = ResourceSampler.start();
        long startNanos = System.nanoTime();
        BlockingQueue<CrawlTask> frontier = new LinkedBlockingQueue<>();
        Set<URI> seen = java.util.concurrent.ConcurrentHashMap.newKeySet();
        List<PageFetchResult> pages = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger scheduledPages = new AtomicInteger(1);
        AtomicInteger activeWorkers = new AtomicInteger();
        AtomicInteger discoveredLinks = new AtomicInteger();
        AtomicInteger duplicateLinksSkipped = new AtomicInteger();
        AtomicInteger offHostLinksSkipped = new AtomicInteger();
        AtomicInteger invalidLinksSkipped = new AtomicInteger();
        AtomicInteger robotsDeniedPages = new AtomicInteger();
        AtomicInteger depthLimitedLinksSkipped = new AtomicInteger();
        AtomicInteger pageBudgetSkipped = new AtomicInteger();
        AtomicBoolean stop = new AtomicBoolean(false);
        Throwable scopeFailure = null;
        long deadlineNanos = System.nanoTime() + request.crawlTimeout().toNanos();

        seen.add(seedUri);
        frontier.add(new CrawlTask(seedUri, 0));

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            HttpClient client = httpClientFactory.create();
            HostThrottle hostThrottle = new HostThrottle(request.maxConcurrentPerHost(), request.minHostDelay());
            RobotsService robotsService = new RobotsService(
                    client,
                    request.userAgent(),
                    request.requestTimeout(),
                    hostThrottle);
            int workerCount = Math.max(1, Math.min(request.parallelism(), request.maxPages()));
            try (JsonLinesPageWriter writer = openWriter(request.jsonlOutputPath())) {
                List<Future<?>> workers = new ArrayList<>(workerCount);
                for (int i = 0; i < workerCount; i++) {
                    workers.add(executor.submit(() -> workerLoop(
                            client,
                            request,
                            seedUri,
                            frontier,
                            seen,
                            pages,
                            scheduledPages,
                            activeWorkers,
                            discoveredLinks,
                            duplicateLinksSkipped,
                            offHostLinksSkipped,
                            invalidLinksSkipped,
                            robotsDeniedPages,
                            depthLimitedLinksSkipped,
                            pageBudgetSkipped,
                            stop,
                            deadlineNanos,
                            hostThrottle,
                            robotsService,
                            writer)));
                }

                for (Future<?> worker : workers) {
                    long remainingNanos = deadlineNanos - System.nanoTime();
                    if (remainingNanos <= 0) {
                        scopeFailure = new TimeoutException("Crawl timed out after " + request.crawlTimeout());
                        stop.set(true);
                        cancelAll(workers);
                        break;
                    }
                    try {
                        worker.get(remainingNanos, TimeUnit.NANOSECONDS);
                    } catch (TimeoutException e) {
                        scopeFailure = e;
                        stop.set(true);
                        cancelAll(workers);
                        break;
                    } catch (ExecutionException e) {
                        scopeFailure = e.getCause();
                        stop.set(true);
                        cancelAll(workers);
                        break;
                    }
                }
            } catch (IOException e) {
                scopeFailure = e;
                stop.set(true);
            }
        }

        ResourceSnapshot snapshot = sampler.stop();
        return SiteCrawlReport.from(
                label,
                seedUri,
                scheduledPages.get(),
                pages,
                discoveredLinks.get(),
                duplicateLinksSkipped.get(),
                offHostLinksSkipped.get(),
                invalidLinksSkipped.get(),
                robotsDeniedPages.get(),
                depthLimitedLinksSkipped.get(),
                pageBudgetSkipped.get(),
                Duration.ofNanos(System.nanoTime() - startNanos),
                snapshot,
                scopeFailure,
                request.jsonlOutputPath(),
                request.samplePages());
    }

    private static void workerLoop(
            HttpClient client,
            SiteCrawlRequest request,
            URI seedUri,
            BlockingQueue<CrawlTask> frontier,
            Set<URI> seen,
            List<PageFetchResult> pages,
            AtomicInteger scheduledPages,
            AtomicInteger activeWorkers,
            AtomicInteger discoveredLinks,
            AtomicInteger duplicateLinksSkipped,
            AtomicInteger offHostLinksSkipped,
            AtomicInteger invalidLinksSkipped,
            AtomicInteger robotsDeniedPages,
            AtomicInteger depthLimitedLinksSkipped,
            AtomicInteger pageBudgetSkipped,
            AtomicBoolean stop,
            long deadlineNanos,
            HostThrottle hostThrottle,
            RobotsService robotsService,
            JsonLinesPageWriter writer) {
        while (!stop.get()) {
            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0) {
                stop.set(true);
                return;
            }

            CrawlTask task;
            try {
                task = frontier.poll(Math.min(remainingNanos, TimeUnit.MILLISECONDS.toNanos(200)), TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            if (task == null) {
                if (frontier.isEmpty() && activeWorkers.get() == 0) {
                    return;
                }
                continue;
            }

            activeWorkers.incrementAndGet();
            try {
                PageFetchResult robotsSkippedResult = checkRobots(task, request, robotsService);
                if (robotsSkippedResult != null) {
                    robotsDeniedPages.incrementAndGet();
                    pages.add(robotsSkippedResult);
                    writePage(writer, robotsSkippedResult);
                    continue;
                }

                FetchedPage fetchedPage = fetchPage(client, task, request, hostThrottle);
                pages.add(fetchedPage.result());
                writePage(writer, fetchedPage.result());

                if (!fetchedPage.result().success() || !fetchedPage.result().html()) {
                    continue;
                }

                int nextDepth = task.depth() + 1;
                for (String href : fetchedPage.extracted().hrefs()) {
                    discoveredLinks.incrementAndGet();
                    if (nextDepth > request.maxDepth()) {
                        depthLimitedLinksSkipped.incrementAndGet();
                        continue;
                    }

                    URI normalized = UrlNormalizer.normalizeDiscovered(task.uri(), href).orElse(null);
                    if (normalized == null) {
                        invalidLinksSkipped.incrementAndGet();
                        continue;
                    }
                    if (request.sameHostOnly() && !UrlNormalizer.isSameHost(seedUri, normalized)) {
                        offHostLinksSkipped.incrementAndGet();
                        continue;
                    }
                    if (!seen.add(normalized)) {
                        duplicateLinksSkipped.incrementAndGet();
                        continue;
                    }
                    if (!reservePageSlot(scheduledPages, request.maxPages())) {
                        seen.remove(normalized);
                        pageBudgetSkipped.incrementAndGet();
                        continue;
                    }
                    frontier.offer(new CrawlTask(normalized, nextDepth));
                }
            } finally {
                activeWorkers.decrementAndGet();
            }
        }
    }

    private static PageFetchResult checkRobots(CrawlTask task, SiteCrawlRequest request, RobotsService robotsService) {
        if (!request.honorRobotsTxt()) {
            return null;
        }
        if (robotsService.isAllowed(task.uri())) {
            return null;
        }
        return PageFetchResult.skipped(task.uri(), task.depth(), "robots.txt");
    }

    private static FetchedPage fetchPage(
            HttpClient client,
            CrawlTask task,
            SiteCrawlRequest request,
            HostThrottle hostThrottle) {
        long startNanos = System.nanoTime();
        String hostKey = UrlNormalizer.originKey(task.uri());
        try (HostThrottle.Permit ignored = hostThrottle.acquire(hostKey)) {
            HttpRequest httpRequest = HttpRequest.newBuilder(task.uri())
                    .GET()
                    .timeout(request.requestTimeout())
                    .header("User-Agent", request.userAgent())
                    .build();
            HttpResponse<byte[]> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            HtmlLinkExtractor.Extraction extraction = new HtmlLinkExtractor.Extraction(null, List.of());
            String title = null;
            if (contentType.toLowerCase().contains("text/html")) {
                String body = new String(response.body(), StandardCharsets.UTF_8);
                extraction = HtmlLinkExtractor.extract(body);
                title = extraction.title();
            }
            return new FetchedPage(
                    PageFetchResult.success(
                            task.uri(),
                            task.depth(),
                            response.statusCode(),
                            response.body().length,
                            Duration.ofNanos(System.nanoTime() - startNanos),
                            contentType,
                            title,
                            extraction.hrefs().size()),
                    extraction);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new FetchedPage(
                    PageFetchResult.failure(
                            task.uri(),
                            task.depth(),
                            e,
                            Duration.ofNanos(System.nanoTime() - startNanos),
                            ""),
                    new HtmlLinkExtractor.Extraction(null, List.of()));
        } catch (RuntimeException e) {
            return new FetchedPage(
                    PageFetchResult.failure(
                            task.uri(),
                            task.depth(),
                            e,
                            Duration.ofNanos(System.nanoTime() - startNanos),
                            ""),
                    new HtmlLinkExtractor.Extraction(null, List.of()));
        }
    }

    private static JsonLinesPageWriter openWriter(java.nio.file.Path outputPath) throws IOException {
        return outputPath == null ? null : new JsonLinesPageWriter(outputPath);
    }

    private static void writePage(JsonLinesPageWriter writer, PageFetchResult result) {
        if (writer == null) {
            return;
        }
        try {
            writer.write(result);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write JSON Lines output", e);
        }
    }

    private static boolean reservePageSlot(AtomicInteger scheduledPages, int maxPages) {
        while (true) {
            int current = scheduledPages.get();
            if (current >= maxPages) {
                return false;
            }
            if (scheduledPages.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }

    private static void cancelAll(List<Future<?>> workers) {
        for (Future<?> worker : workers) {
            worker.cancel(true);
        }
    }

    private record FetchedPage(PageFetchResult result, HtmlLinkExtractor.Extraction extracted) {
    }
}
