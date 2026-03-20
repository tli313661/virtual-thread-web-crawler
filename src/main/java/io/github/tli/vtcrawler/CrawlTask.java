package io.github.tli.vtcrawler;

import java.net.URI;

public record CrawlTask(URI uri, int depth) {
}
