package io.github.tli.vtcrawler.site;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HtmlLinkExtractor {
    private static final Pattern HREF_PATTERN = Pattern.compile(
            "(?is)<a\\b[^>]*\\bhref\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))");
    private static final Pattern TITLE_PATTERN = Pattern.compile("(?is)<title\\b[^>]*>(.*?)</title>");
    private static final Pattern TAG_PATTERN = Pattern.compile("(?is)<[^>]+>");

    private HtmlLinkExtractor() {
    }

    public static Extraction extract(String html) {
        List<String> hrefs = new ArrayList<>();
        Matcher hrefMatcher = HREF_PATTERN.matcher(html);
        while (hrefMatcher.find()) {
            String href = firstNonBlank(hrefMatcher.group(1), hrefMatcher.group(2), hrefMatcher.group(3));
            if (href != null) {
                hrefs.add(href.trim());
            }
        }

        Matcher titleMatcher = TITLE_PATTERN.matcher(html);
        String title = null;
        if (titleMatcher.find()) {
            title = normalizeWhitespace(TAG_PATTERN.matcher(titleMatcher.group(1)).replaceAll(" "));
        }

        return new Extraction(title, hrefs);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String normalizeWhitespace(String input) {
        return input == null ? null : input.replaceAll("\\s+", " ").trim();
    }

    public record Extraction(String title, List<String> hrefs) {
    }
}
