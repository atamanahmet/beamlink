package com.atamanahmet.beamlink.nexus.http;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Delegates to the JDK HttpClient
 */
@Component
public class DefaultHttpSender implements HttpSender {

    private final HttpClient httpClient;

    public DefaultHttpSender(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}