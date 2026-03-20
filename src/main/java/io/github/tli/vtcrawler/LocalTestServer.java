package io.github.tli.vtcrawler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        return new LocalTestServer(server, URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
    }

    private static void handlePage(HttpExchange exchange, Duration delay, int payloadBytes) throws IOException {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
            return;
        }

        byte[] payload = ("ok-" + "x".repeat(Math.max(0, payloadBytes - 3))).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(200, payload.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(payload);
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
