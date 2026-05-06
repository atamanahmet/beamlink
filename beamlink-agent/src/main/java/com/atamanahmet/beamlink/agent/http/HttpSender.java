package com.atamanahmet.beamlink.agent.http;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Thin wrapper for HttpClient so services and tests don't depend on the JDK class
 */
public interface HttpSender {
    HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException;
}