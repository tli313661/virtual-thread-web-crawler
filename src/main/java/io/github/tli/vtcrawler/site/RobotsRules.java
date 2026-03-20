package io.github.tli.vtcrawler.site;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public record RobotsRules(List<Rule> rules, boolean fetchedSuccessfully) {
    public static RobotsRules allowAll() {
        return new RobotsRules(List.of(), false);
    }

    public boolean isAllowed(String pathWithQuery) {
        Rule bestMatch = null;
        for (Rule rule : rules) {
            if (rule.matches(pathWithQuery) && (bestMatch == null || rule.pattern().length() > bestMatch.pattern().length())) {
                bestMatch = rule;
            }
        }
        return bestMatch == null || bestMatch.allow();
    }

    public static RobotsRules parse(String robotsText, String userAgent) {
        if (robotsText == null || robotsText.isBlank()) {
            return allowAll();
        }

        String rawAgentToken = userAgent.toLowerCase(Locale.ROOT);
        int slash = rawAgentToken.indexOf('/');
        String normalizedAgentToken = slash >= 0 ? rawAgentToken.substring(0, slash) : rawAgentToken;

        List<Group> groups = new ArrayList<>();
        Group current = new Group();
        for (String rawLine : robotsText.split("\\R")) {
            String line = stripComment(rawLine).trim();
            if (line.isEmpty()) {
                if (!current.userAgents.isEmpty() || !current.rules.isEmpty()) {
                    groups.add(current);
                }
                current = new Group();
                continue;
            }

            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            if (key.equals("user-agent")) {
                if (!current.rules.isEmpty()) {
                    groups.add(current);
                    current = new Group();
                }
                current.userAgents.add(value.toLowerCase(Locale.ROOT));
            } else if (key.equals("allow")) {
                current.rules.add(new Rule(value, true));
            } else if (key.equals("disallow")) {
                current.rules.add(new Rule(value, false));
            }
        }
        if (!current.userAgents.isEmpty() || !current.rules.isEmpty()) {
            groups.add(current);
        }

        Group selected = groups.stream()
                .filter(group -> group.matches(normalizedAgentToken))
                .max(Comparator.comparingInt(group -> group.matchScore(normalizedAgentToken)))
                .orElseGet(() -> groups.stream()
                        .filter(group -> group.matches("*"))
                        .findFirst()
                        .orElse(new Group()));
        return new RobotsRules(List.copyOf(selected.rules), true);
    }

    private static String stripComment(String line) {
        int hashIndex = line.indexOf('#');
        return hashIndex >= 0 ? line.substring(0, hashIndex) : line;
    }

    public record Rule(String pattern, boolean allow) {
        public boolean matches(String pathWithQuery) {
            if (pattern == null || pattern.isBlank()) {
                return false;
            }
            return pathWithQuery.startsWith(pattern);
        }
    }

    private static final class Group {
        private final List<String> userAgents = new ArrayList<>();
        private final List<Rule> rules = new ArrayList<>();

        private boolean matches(String agentToken) {
            return userAgents.stream().anyMatch(candidate -> candidate.equals("*") || agentToken.startsWith(candidate));
        }

        private int matchScore(String agentToken) {
            return userAgents.stream()
                    .filter(candidate -> candidate.equals("*") || agentToken.startsWith(candidate))
                    .mapToInt(candidate -> candidate.equals("*") ? 0 : candidate.length())
                    .max()
                    .orElse(-1);
        }
    }
}
