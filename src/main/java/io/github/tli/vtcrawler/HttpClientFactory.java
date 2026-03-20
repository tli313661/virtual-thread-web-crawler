package io.github.tli.vtcrawler;

import java.net.http.HttpClient;
import java.time.Duration;

public record HttpClientFactory(Duration connectTimeout, String userAgent) {
    public HttpClient create() {
        return HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }
}
