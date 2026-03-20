package io.github.tli.vtcrawler;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CrawlerApplication {
    private CrawlerApplication() {
    }

    public static void main(String[] args) throws Exception {
        Arguments arguments = Arguments.parse(args);
        CommandOptions options = CommandOptions.from(arguments);

        switch (options.command()) {
            case "crawl" -> runSingleMode("virtual-threads", options, false);
            case "compare" -> runCompareMode(options);
            case "demo-local" -> runLocalDemo(options);
            default -> throw new IllegalArgumentException("Unsupported command: " + options.command());
        }
    }

    private static void runSingleMode(String label, CommandOptions options, boolean usePlatformThreads)
            throws Exception {
        List<URI> targets = buildTargets(options);
        HttpClientFactory httpClientFactory = new HttpClientFactory(options.connectTimeout(), options.userAgent());
        CrawlReport report = usePlatformThreads
                ? CrawlerEngine.crawlWithPlatformThreadPool(
                        label,
                        httpClientFactory,
                        targets,
                        options.requestTimeout(),
                        options.scopeTimeout(),
                        options.platformThreads())
                : CrawlerEngine.crawlWithVirtualThreads(
                        label,
                        httpClientFactory,
                        targets,
                        options.requestTimeout(),
                        options.scopeTimeout());
        System.out.println(report.toPrettyString());
    }

    private static void runCompareMode(CommandOptions options) throws Exception {
        List<URI> targets = buildTargets(options);
        HttpClientFactory httpClientFactory = new HttpClientFactory(options.connectTimeout(), options.userAgent());
        CrawlReport virtualReport = CrawlerEngine.crawlWithVirtualThreads(
                "virtual-threads",
                httpClientFactory,
                targets,
                options.requestTimeout(),
                options.scopeTimeout());
        CrawlReport platformReport = CrawlerEngine.crawlWithPlatformThreadPool(
                "fixed-thread-pool",
                httpClientFactory,
                targets,
                options.requestTimeout(),
                options.scopeTimeout(),
                options.platformThreads());

        System.out.println(virtualReport.toPrettyString());
        System.out.println();
        System.out.println(platformReport.toPrettyString());
        System.out.println();
        System.out.println(CrawlReportComparison.summarize(virtualReport, platformReport));
    }

    private static void runLocalDemo(CommandOptions options) throws Exception {
        try (LocalTestServer server = LocalTestServer.start(
                options.serverPort(),
                options.serverDelay(),
                options.payloadBytes())) {
            URI baseUri = server.baseUri().resolve("/page");
            CommandOptions overridden = options.withBaseUrl(baseUri.toString());
            if (options.compare()) {
                runCompareMode(overridden);
            } else {
                runSingleMode("virtual-threads", overridden, false);
            }
        }
    }

    private static List<URI> buildTargets(CommandOptions options) {
        if (options.baseUrl() == null || options.baseUrl().isBlank()) {
            throw new IllegalArgumentException("Missing required option --url for command " + options.command());
        }

        URI baseUri = URI.create(options.baseUrl());
        List<URI> targets = new ArrayList<>(options.count());
        for (int i = 0; i < options.count(); i++) {
            String separator = baseUri.getQuery() == null ? "?" : "&";
            targets.add(URI.create(baseUri + separator + "requestId=" + i));
        }
        return targets;
    }

    private record CommandOptions(
            String command,
            String baseUrl,
            int count,
            Duration connectTimeout,
            Duration requestTimeout,
            Duration scopeTimeout,
            int platformThreads,
            String userAgent,
            int serverPort,
            Duration serverDelay,
            int payloadBytes,
            boolean compare) {

        private static CommandOptions from(Arguments arguments) {
            String command = arguments.command();
            return new CommandOptions(
                    command,
                    arguments.get("url"),
                    arguments.getInt("count", 10_000),
                    Duration.ofMillis(arguments.getInt("connect-timeout-millis", 10_000)),
                    Duration.ofMillis(arguments.getInt("request-timeout-millis", 15_000)),
                    Duration.ofSeconds(arguments.getInt("timeout-seconds", 120)),
                    arguments.getInt("platform-threads", 200),
                    arguments.get("user-agent", "vt-crawler/1.0"),
                    arguments.getInt("server-port", 0),
                    Duration.ofMillis(arguments.getInt("server-delay-millis", 25)),
                    arguments.getInt("payload-bytes", 1_024),
                    arguments.hasFlag("compare"));
        }

        private CommandOptions withBaseUrl(String overriddenBaseUrl) {
            return new CommandOptions(
                    command,
                    overriddenBaseUrl,
                    count,
                    connectTimeout,
                    requestTimeout,
                    scopeTimeout,
                    platformThreads,
                    userAgent,
                    serverPort,
                    serverDelay,
                    payloadBytes,
                    compare);
        }
    }

    private static final class Arguments {
        private final String command;
        private final Map<String, String> values;

        private Arguments(String command, Map<String, String> values) {
            this.command = command;
            this.values = values;
        }

        static Arguments parse(String[] args) {
            String command = args.length == 0 ? "demo-local" : args[0].toLowerCase(Locale.ROOT);
            Map<String, String> values = new LinkedHashMap<>();
            for (int i = 1; i < args.length; i++) {
                String argument = args[i];
                if (!argument.startsWith("--")) {
                    throw new IllegalArgumentException("Unrecognized argument: " + argument);
                }

                String raw = argument.substring(2);
                int equalsIndex = raw.indexOf('=');
                if (equalsIndex >= 0) {
                    values.put(raw.substring(0, equalsIndex), raw.substring(equalsIndex + 1));
                } else {
                    values.put(raw, "true");
                }
            }
            return new Arguments(command, values);
        }

        String command() {
            return command;
        }

        String get(String key) {
            return values.get(key);
        }

        String get(String key, String defaultValue) {
            return values.getOrDefault(key, defaultValue);
        }

        int getInt(String key, int defaultValue) {
            String raw = values.get(key);
            return raw == null ? defaultValue : Integer.parseInt(raw);
        }

        boolean hasFlag(String key) {
            return Boolean.parseBoolean(values.getOrDefault(key, "false"));
        }
    }
}
