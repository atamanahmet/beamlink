package com.atamanahmet.beamlink.agent.http;

import com.atamanahmet.beamlink.agent.service.AgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

/**
 * Injects agent auth headers on every outbound request.
 * Only place in codebase that knows about X-Public-Token and X-Public-Id.
 */
@Primary
@Component
@RequiredArgsConstructor
public class AgentAuthHttpSender implements HttpSender {

    private final DefaultHttpSender delegate;
    private final AgentService agentService;

    @Override
    public HttpResponse<String> send(HttpRequest request)
            throws IOException, InterruptedException {

        UUID publicId = agentService.getPublicId();
        String publicToken = agentService.getPublicToken();

        if (publicId == null || publicToken == null) {
            throw new IllegalStateException(
                    "Agent not approved yet. publicId or publicToken is null.");
        }

        HttpRequest authenticated = HttpRequest
                .newBuilder(request, (k, v) -> true)
                .header("X-Public-Token", publicToken)
                .header("X-Public-Id", publicId.toString())
                .build();

        return delegate.send(authenticated);
    }
}