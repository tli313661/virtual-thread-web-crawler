package io.github.tli.vtcrawler.site;

import java.net.URI;

public record CrawlTask(URI uri, int depth) {
}
