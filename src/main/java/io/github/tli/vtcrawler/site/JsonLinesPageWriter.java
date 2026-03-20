package io.github.tli.vtcrawler.site;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;

public final class JsonLinesPageWriter implements Closeable {
    private final Path outputPath;
    private final BufferedWriter writer;

    public JsonLinesPageWriter(Path outputPath) throws IOException {
        this.outputPath = outputPath;
        Path parent = outputPath.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        this.writer = Files.newBufferedWriter(
                outputPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }

    public Path outputPath() {
        return outputPath;
    }

    public synchronized void write(PageFetchResult result) throws IOException {
        writer.write(toJsonLine(result));
        writer.newLine();
        writer.flush();
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }

    private static String toJsonLine(PageFetchResult result) {
        StringBuilder builder = new StringBuilder();
        builder.append('{');
        append(builder, "uri", result.uri().toString());
        append(builder, "depth", result.depth());
        append(builder, "statusCode", result.statusCode());
        append(builder, "bytes", result.bytes());
        append(builder, "latencyMillis", durationToMillis(result.latency()));
        append(builder, "contentType", result.contentType());
        append(builder, "title", result.title());
        append(builder, "extractedLinkCount", result.extractedLinkCount());
        append(builder, "success", result.success());
        append(builder, "skipped", result.skipped());
        append(builder, "skipReason", result.skipReason());
        append(builder, "failureType", result.failure() == null ? null : result.failure().getClass().getSimpleName());
        append(builder, "failureMessage", result.failure() == null ? null : result.failure().getMessage());
        if (builder.charAt(builder.length() - 1) == ',') {
            builder.deleteCharAt(builder.length() - 1);
        }
        builder.append('}');
        return builder.toString();
    }

    private static long durationToMillis(Duration duration) {
        return duration == null ? 0L : duration.toMillis();
    }

    private static void append(StringBuilder builder, String key, String value) {
        builder.append('"').append(escape(key)).append("\":");
        if (value == null) {
            builder.append("null");
        } else {
            builder.append('"').append(escape(value)).append('"');
        }
        builder.append(',');
    }

    private static void append(StringBuilder builder, String key, int value) {
        builder.append('"').append(escape(key)).append("\":").append(value).append(',');
    }

    private static void append(StringBuilder builder, String key, long value) {
        builder.append('"').append(escape(key)).append("\":").append(value).append(',');
    }

    private static void append(StringBuilder builder, String key, boolean value) {
        builder.append('"').append(escape(key)).append("\":").append(value).append(',');
    }

    private static String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
