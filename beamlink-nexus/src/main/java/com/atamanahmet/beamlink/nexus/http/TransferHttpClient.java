package com.atamanahmet.beamlink.nexus.http;

import com.atamanahmet.beamlink.nexus.dto.InitiateTransferRequest;
import com.atamanahmet.beamlink.nexus.exception.FileTransferException;
import com.atamanahmet.beamlink.nexus.service.NexusService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TransferHttpClient {

    private final HttpSender httpSender;
    private final ObjectMapper objectMapper;
    private final NexusService nexusService;

    /**
     * Registers the transfer on the target before any chunks are sent
     */
    public void registerTransfer(InitiateTransferRequest request, UUID transferId, String fileName, long fileSize) {
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "transferId",       transferId.toString(),
                    "sourceAgentId",    nexusService.getNexusId().toString(),
                    "fileName",         fileName,
                    "sourceIp",         nexusService.getNexusIp(),
                    "sourcePort",       nexusService.getNexusPort(),
                    "fileSize",         fileSize
            ));

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + request.getTargetIp() + ":" + request.getTargetPort() + "/api/transfers/receive"))
                    .header("Content-Type", "application/json")
                    .header("X-Public-Token",   nexusService.getPeerPublicToken())
                    .header("X-Public-Id",      nexusService.getNexusId().toString())
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpSender.send(httpRequest);
            if (response.statusCode() != 200) {
                throw new FileTransferException("Target rejected transfer registration. Status: " + response.statusCode(), null);
            }
        } catch (FileTransferException e) {
            throw e;
        } catch (Exception e) {
            throw new FileTransferException("Cannot reach target agent", e);
        }
    }

    /**
     * Queries the target's confirmed offset before resuming a paused transfer
     */
    public long queryConfirmedOffset(String targetIp, int targetPort, UUID transferId) {
        String url = "http://" + targetIp + ":" + targetPort + "/api/transfers/" + transferId + "/offset";

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("X-Public-Token", nexusService.getPeerPublicToken())
                    .header("X-Public-Id", nexusService.getNexusId().toString())
                    .GET()
                    .build();

            HttpResponse<String> response = httpSender.send(request);
            if (response.statusCode() != 200) {
                throw new FileTransferException("Target returned " + response.statusCode() + " when querying offset", null);
            }

            Map<String, Long> body = objectMapper.readValue(response.body(), new TypeReference<>() {});
            return body.get("confirmedOffset");

        } catch (FileTransferException e) {
            throw e;
        } catch (Exception e) {
            throw new FileTransferException("Cannot reach target to query offset: " + url, e);
        }
    }

    /**
     * Tells peer to cancel their side of the transfer. Fire-and-forget, caller handles logging.
     */
    public void notifyCancelToPeer(String peerIp, int peerPort, UUID transferId) {
        String url = "http://" + peerIp + ":" + peerPort + "/api/transfers/" + transferId + "/cancel-notify";
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("X-Public-Token",   nexusService.getPeerPublicToken())
                    .header("X-Public-Id",      nexusService.getNexusId().toString())
                    .DELETE()
                    .build();

            HttpResponse<String> response = httpSender.send(request);
            if (response.statusCode() != 200) {
                throw new FileTransferException("Peer returned " + response.statusCode() + " on cancel notify", null);
            }
        } catch (Exception e) {
            throw new FileTransferException("Cannot reach peer for cancel notify: " + url, e);
        }
    }
}