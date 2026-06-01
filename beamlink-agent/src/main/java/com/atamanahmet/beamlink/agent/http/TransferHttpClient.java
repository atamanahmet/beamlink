package com.atamanahmet.beamlink.agent.http;

import com.atamanahmet.beamlink.agent.dto.*;
import com.atamanahmet.beamlink.agent.exception.FileTransferException;
import com.atamanahmet.beamlink.agent.service.AgentService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * All outbound HTTP calls from this agent to other agents for transfer coordination.
 * Auth headers injected automatically by AgentAuthHttpSender.
 */
@Component
@RequiredArgsConstructor
public class TransferHttpClient {

    private final HttpSender httpSender;
    private final ObjectMapper objectMapper;
    private final AgentService agentService;

    public void registerTransfer(
            InitiateTransferRequest request,
            UUID transferId,
            UUID sourceAgentId,
            String fileName,
            long fileSize
    ) {
        String url = "http://" + request.getTargetIp() + ":"
                + request.getTargetPort() + "/api/transfers/receive";
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "transferId", transferId.toString(),
                    "sourceAgentId", sourceAgentId.toString(),
                    "fileName", fileName,
                    "sourceIp", agentService.getAgentIp(),
                    "sourcePort", agentService.getAgentPort(),
                    "fileSize", fileSize
            ));

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpSender.send(httpRequest);

            if (response.statusCode() != 200) {
                throw new FileTransferException(
                        "Target rejected transfer registration. Status: "
                                + response.statusCode(), null);
            }

        } catch (FileTransferException e) {
            throw e;
        } catch (Exception e) {
            throw new FileTransferException("Cannot reach target agent: " + url, e);
        }
    }

    public void registerBatch(
            String targetIp,
            int targetPort,
            UUID batchTransferId,
            UUID sourceAgentId,
            int totalFiles,
            long totalSize,
            List<ReceiveBatchRequest.FileEntry> fileEntries
    ) {
        String url = "http://" + targetIp + ":" + targetPort
                + "/api/transfers/receive-batch";
        try {
            ReceiveBatchRequest payload = new ReceiveBatchRequest();
            payload.setBatchTransferId(batchTransferId);
            payload.setSourceAgentId(sourceAgentId);
            payload.setTotalFiles(totalFiles);
            payload.setTotalSize(totalSize);
            payload.setFiles(fileEntries);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = httpSender.send(httpRequest);

            if (response.statusCode() != 200) {
                throw new FileTransferException(
                        "Target rejected batch registration. Status: "
                                + response.statusCode(), null);
            }

        } catch (FileTransferException e) {
            throw e;
        } catch (Exception e) {
            throw new FileTransferException("Cannot reach target agent: " + url, e);
        }
    }

    public void registerDirectory(
            String targetIp,
            int targetPort,
            UUID directoryTransferId,
            UUID sourceAgentId,
            String directoryName,
            int totalFiles,
            long totalSize,
            List<String> emptyDirectories,
            List<ReceiveDirectoryRequest.FileEntry> fileEntries
    ) {
        String url = "http://" + targetIp + ":" + targetPort
                + "/api/transfers/receive-directory";
        try {
            ReceiveDirectoryRequest payload = new ReceiveDirectoryRequest();
            payload.setDirectoryTransferId(directoryTransferId);
            payload.setSourceAgentId(sourceAgentId);
            payload.setDirectoryName(directoryName);
            payload.setTotalFiles(totalFiles);
            payload.setTotalSize(totalSize);
            payload.setEmptyDirectories(emptyDirectories);
            payload.setFiles(fileEntries);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = httpSender.send(httpRequest);

            if (response.statusCode() != 200) {
                throw new FileTransferException(
                        "Target rejected directory registration. Status: "
                                + response.statusCode(), null);
            }

        } catch (FileTransferException e) {
            throw e;
        } catch (Exception e) {
            throw new FileTransferException("Cannot reach target agent: " + url, e);
        }
    }

    public long queryConfirmedOffset(String targetIp, int targetPort, UUID transferId) {
        String url = "http://" + targetIp + ":" + targetPort
                + "/api/transfers/" + transferId + "/offset";
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = httpSender.send(request);

            if (response.statusCode() != 200) {
                throw new FileTransferException(
                        "Target returned " + response.statusCode()
                                + " when querying offset", null);
            }

            Map<String, Long> body = objectMapper.readValue(
                    response.body(), new TypeReference<>() {});

            return body.get("confirmedOffset");

        } catch (FileTransferException e) {
            throw e;
        } catch (Exception e) {
            throw new FileTransferException(
                    "Cannot reach target to query offset: " + url, e);
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
                    .header("X-Public-Token",   agentService.getPublicToken())
                    .header("X-Public-Id",      agentService.getPublicId().toString())
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