package io.github.tli.vtcrawler;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RobotsService {
    private final HttpClient client;
    private final String userAgent;
    private final Duration requestTimeout;
    private final HostThrottle hostThrottle;
    private final Map<String, Object> locks;
    private final Map<String, RobotsRules> cache;

    public RobotsService(HttpClient client, String userAgent, Duration requestTimeout, HostThrottle hostThrottle) {
        this.client = client;
        this.userAgent = userAgent;
        this.requestTimeout = requestTimeout;
        this.hostThrottle = hostThrottle;
        this.locks = new ConcurrentHashMap<>();
        this.cache = new ConcurrentHashMap<>();
    }

    public boolean isAllowed(URI uri) {
        String hostKey = UrlNormalizer.originKey(uri);
        RobotsRules rules = cache.get(hostKey);
        if (rules == null) {
            Object lock = locks.computeIfAbsent(hostKey, ignored -> new Object());
            synchronized (lock) {
                rules = cache.get(hostKey);
                if (rules == null) {
                    rules = fetchRules(uri);
                    cache.put(hostKey, rules);
                }
            }
        }
        return rules.isAllowed(pathWithQuery(uri));
    }

    private RobotsRules fetchRules(URI uri) {
        URI robotsUri = URI.create(UrlNormalizer.originKey(uri) + "/robots.txt");
        try (HostThrottle.Permit ignored = hostThrottle.acquire(UrlNormalizer.originKey(uri))) {
            HttpRequest request = HttpRequest.newBuilder(robotsUri)
                    .GET()
                    .timeout(requestTimeout)
                    .header("User-Agent", userAgent)
                    .build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 400) {
                return RobotsRules.allowAll();
            }
            String body = new String(response.body(), StandardCharsets.UTF_8);
            return RobotsRules.parse(body, userAgent);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return RobotsRules.allowAll();
        } catch (RuntimeException e) {
            return RobotsRules.allowAll();
        }
    }

    private static String pathWithQuery(URI uri) {
        String path = uri.getRawPath() == null || uri.getRawPath().isBlank() ? "/" : uri.getRawPath();
        if (uri.getRawQuery() == null || uri.getRawQuery().isBlank()) {
            return path;
        }
        return path + "?" + uri.getRawQuery();
    }
}
