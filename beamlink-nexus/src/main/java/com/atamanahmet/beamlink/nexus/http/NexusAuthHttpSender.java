package com.atamanahmet.beamlink.nexus.http;

import com.atamanahmet.beamlink.nexus.service.NexusService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Injects nexus auth headers on every outbound request.
 * Only place in codebase that knows about X-Public-Token and X-Public-Id.
 */
@Primary
@Component
@RequiredArgsConstructor
public class NexusAuthHttpSender implements HttpSender {

    private final DefaultHttpSender delegate;
    private final NexusService nexusService;

    public HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {

        HttpRequest authenticated = HttpRequest
                .newBuilder(request, (k, v) -> true)
                .header("X-Public-Token", this.nexusService.getPublicToken())
                .header("X-Public-Id", this.nexusService.getNexusId().toString()).build();

        return this.delegate.send(authenticated);
    }
}