package io.github.tli.vtcrawler.demo;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.Executors;

public final class LocalTestServer implements AutoCloseable {
    private final HttpServer server;
    private final URI baseUri;

    private LocalTestServer(HttpServer server, URI baseUri) {
        this.server = server;
        this.baseUri = baseUri;
    }

    public static LocalTestServer start(int port, Duration delay, int payloadBytes) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 10_000);
        server.createContext("/page", exchange -> handlePage(exchange, delay, payloadBytes));
        server.createContext("/site", exchange -> handleSite(exchange, delay));
        server.createContext("/asset", exchange -> handleAsset(exchange, delay));
        server.createContext("/robots.txt", exchange -> handleRobots(exchange, delay));
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        return new LocalTestServer(server, URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
    }

    private static void handlePage(HttpExchange exchange, Duration delay, int payloadBytes) throws IOException {
        if (!sleep(delay, exchange)) {
            return;
        }

        byte[] payload = ("ok-" + "x".repeat(Math.max(0, payloadBytes - 3))).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(200, payload.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(payload);
        }
    }

    private static void handleSite(HttpExchange exchange, Duration delay) throws IOException {
        if (!sleep(delay, exchange)) {
            return;
        }

        int nodeId = extractNodeId(exchange.getRequestURI().getPath());
        String body = """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <title>Local node %d</title>
                </head>
                <body>
                  <h1>Node %d</h1>
                  <p>This page links to a few internal pages, one duplicate, one asset, and one external URL.</p>
                  <a href="/site/node/%d">Next A</a>
                  <a href="/site/node/%d">Next B</a>
                  <a href="/site/node/%d#fragment">Duplicate with fragment</a>
                  <a href="/asset/%d.txt">Asset %d</a>
                  <a href="https://example.com/outside/%d">External</a>
                </body>
                </html>
                """.formatted(
                nodeId,
                nodeId,
                nodeId + 1,
                nodeId + 2,
                nodeId + 1,
                nodeId,
                nodeId,
                nodeId);

        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, payload.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(payload);
        }
    }

    private static void handleAsset(HttpExchange exchange, Duration delay) throws IOException {
        if (!sleep(delay, exchange)) {
            return;
        }

        String path = exchange.getRequestURI().getPath();
        String assetName = path.substring(path.lastIndexOf('/') + 1);
        byte[] payload = ("asset:" + assetName.toLowerCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(200, payload.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(payload);
        }
    }

    private static void handleRobots(HttpExchange exchange, Duration delay) throws IOException {
        if (!sleep(delay, exchange)) {
            return;
        }

        String body = """
                User-agent: *
                Disallow: /site/node/4
                Disallow: /asset/2.txt
                """;
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(200, payload.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(payload);
        }
    }

    private static int extractNodeId(String path) {
        if (path == null || path.equals("/site") || path.equals("/site/")) {
            return 0;
        }
        String marker = "/site/node/";
        if (!path.startsWith(marker)) {
            return 0;
        }
        String raw = path.substring(marker.length());
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static boolean sleep(Duration delay, HttpExchange exchange) throws IOException {
        try {
            Thread.sleep(delay);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
            return false;
        }
    }

    public URI baseUri() {
        return baseUri;
    }

    @Override
    public void close() {
        server.stop(0);
        if (server.getExecutor() instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception ignored) {
                // Best effort shutdown for the demo-only test server.
            }
        }
    }
}
