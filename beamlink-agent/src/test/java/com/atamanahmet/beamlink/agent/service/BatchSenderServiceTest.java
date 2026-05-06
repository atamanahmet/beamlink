package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.config.AgentConfig;
import com.atamanahmet.beamlink.agent.domain.BatchTransfer;
import com.atamanahmet.beamlink.agent.domain.FileTransfer;
import com.atamanahmet.beamlink.agent.domain.enums.GroupTransferStatus;
import com.atamanahmet.beamlink.agent.dto.InitiateBatchTransferRequest;
import com.atamanahmet.beamlink.agent.dto.InitiateBatchTransferResponse;
import com.atamanahmet.beamlink.agent.exception.FileTransferException;
import com.atamanahmet.beamlink.agent.repository.BatchTransferRepository;
import com.atamanahmet.beamlink.agent.repository.FileTransferRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BatchSenderServiceTest {

    @Mock private BatchTransferRepository batchTransferRepository;
    @Mock private FileTransferRepository fileTransferRepository;
    @Mock private AgentService agentService;
    @Mock private AgentConfig agentConfig;
    @Mock private BatchAsyncSender batchAsyncSender;
    @Mock private ObjectMapper objectMapper;
    @Mock private HttpClient httpClient;

    @InjectMocks
    private BatchSenderService batchSenderService;

    @TempDir
    Path tempDir;

    private Path createRealFile(String name, byte[] content) throws Exception {
        Path file = tempDir.resolve(name);
        Files.write(file, content);
        return file;
    }

    private HttpResponse<String> mockHttpResponse(int status) {
        HttpResponse<String> r = mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(status);
        return r;
    }

    private void stubHappyPath(HttpResponse<String> httpResponse) throws Exception {
        when(agentService.getAgentId()).thenReturn(UUID.randomUUID());
        when(agentConfig.getTransferExpiryHours()).thenReturn(24L);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(batchTransferRepository.save(any(BatchTransfer.class)))
                .thenAnswer(i -> i.getArgument(0));
        when(fileTransferRepository.saveAll(any()))
                .thenAnswer(i -> i.getArgument(0));
    }

    private InitiateBatchTransferRequest buildRequest(List<String> paths) {
        InitiateBatchTransferRequest req = new InitiateBatchTransferRequest();
        req.setFilePaths(paths);
        req.setTargetAgentId(UUID.randomUUID());
        req.setTargetIp("192.168.1.2");
        req.setTargetPort(9090);
        req.setTargetToken("token");
        return req;
    }

    /**
     * Null file list must be rejected before any DB or network call.
     */
    @Test
    void initiate_rejectsNullFilePaths() {
        InitiateBatchTransferRequest req = buildRequest(null);

        assertThatThrownBy(() -> batchSenderService.initiate(req))
                .isInstanceOf(FileTransferException.class)
                .hasMessageContaining("No file paths");

        verifyNoInteractions(batchTransferRepository, fileTransferRepository, httpClient);
    }

    @Test
    void initiate_rejectsEmptyFilePaths() {
        InitiateBatchTransferRequest req = buildRequest(List.of());

        assertThatThrownBy(() -> batchSenderService.initiate(req))
                .isInstanceOf(FileTransferException.class)
                .hasMessageContaining("No file paths");
    }

    @Test
    void initiate_rejectsNonExistentFile() {
        InitiateBatchTransferRequest req = buildRequest(
                List.of(tempDir.resolve("ghost.txt").toString()));

        assertThatThrownBy(() -> batchSenderService.initiate(req))
                .isInstanceOf(FileTransferException.class)
                .hasMessageContaining("File not found");

        verifyNoInteractions(batchTransferRepository);
    }

    /**
     * Directory path where a file is expected must be rejected.
     */
    @Test
    void initiate_rejectsDirectoryAsFilePath() throws Exception {
        Path dir = tempDir.resolve("adir");
        Files.createDirectories(dir);
        InitiateBatchTransferRequest req = buildRequest(List.of(dir.toString()));

        assertThatThrownBy(() -> batchSenderService.initiate(req))
                .isInstanceOf(FileTransferException.class)
                .hasMessageContaining("File not found");
    }

    /**
     * PathNormalizer removes surrounding quotes, quoted path must still resolve.
     * Needed for current path input problem. Will be replaced later
     */
    @Test
    void initiate_normalizesQuotedPath() throws Exception {
        Path file = createRealFile("quoted.txt", "data".getBytes());
        String quotedPath = "\"" + file.toString() + "\"";
        stubHappyPath(mockHttpResponse(200));

        InitiateBatchTransferResponse response =
                batchSenderService.initiate(buildRequest(List.of(quotedPath)));

        assertThat(response.getBatchTransferId()).isNotNull();
    }

    /**
     * Happy path returns a valid batchTransferId and persists batch + file records.
     */
    @Test
    void initiate_persistsBatchAndFileTransfers() throws Exception {
        Path f1 = createRealFile("a.txt", "aaa".getBytes());
        Path f2 = createRealFile("b.txt", "bbbb".getBytes());
        stubHappyPath(mockHttpResponse(200));

        batchSenderService.initiate(buildRequest(List.of(f1.toString(), f2.toString())));

        verify(batchTransferRepository, atLeast(2)).save(any(BatchTransfer.class));
        verify(fileTransferRepository).saveAll(argThat(list -> ((List<?>) list).size() == 2));
    }

    /**
     * Total size on the BatchTransfer must equal the sum of all file sizes.
     */
    @Test
    void initiate_calculatesTotalSizeCorrectly() throws Exception {
        Path f1 = createRealFile("s1.bin", new byte[100]);
        Path f2 = createRealFile("s2.bin", new byte[200]);
        stubHappyPath(mockHttpResponse(200));

        batchSenderService.initiate(buildRequest(List.of(f1.toString(), f2.toString())));

        ArgumentCaptor<BatchTransfer> captor = ArgumentCaptor.forClass(BatchTransfer.class);
        verify(batchTransferRepository, atLeast(1)).save(captor.capture());

        long total = captor.getAllValues().stream()
                .mapToLong(BatchTransfer::getTotalSize)
                .max().orElse(0);
        assertThat(total).isEqualTo(300L);
    }

    /**
     * Batch must be set to ACTIVE after target accepts registration.
     */
    @Test
    void initiate_setsBatchActiveAfterTargetAccepts() throws Exception {
        Path file = createRealFile("active.txt", "x".getBytes());
        stubHappyPath(mockHttpResponse(200));

        batchSenderService.initiate(buildRequest(List.of(file.toString())));

        verify(batchTransferRepository, atLeastOnce()).save(argThat(bt ->
                bt.getStatus() == GroupTransferStatus.ACTIVE));
    }

    /**
     * Async sender must be called once with correct connection params after target accepts.
     */
    @Test
    void initiate_triggersAsyncSend() throws Exception {
        Path file = createRealFile("send.txt", "payload".getBytes());
        stubHappyPath(mockHttpResponse(200));

        batchSenderService.initiate(buildRequest(List.of(file.toString())));

        verify(batchAsyncSender).sendAsync(any(), eq("192.168.1.2"), eq(9090), eq("token"));
    }

    /**
     * Target rejecting registration must throw and not mark batch as ACTIVE.
     */
    @Test
    @SuppressWarnings("unchecked")
    void initiate_throwsWhenTargetRejectsRegistration() throws Exception {
        Path file = createRealFile("reject.txt", "data".getBytes());
        when(agentService.getAgentId()).thenReturn(UUID.randomUUID());
        when(agentConfig.getTransferExpiryHours()).thenReturn(24L);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(batchTransferRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(fileTransferRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));

        HttpResponse<String> rejected = mockHttpResponse(403);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(rejected);

        assertThatThrownBy(() ->
                batchSenderService.initiate(buildRequest(List.of(file.toString()))))
                .isInstanceOf(FileTransferException.class)
                .hasMessageContaining("Target rejected");

        verify(batchTransferRepository, never()).save(argThat(bt ->
                bt.getStatus() == GroupTransferStatus.ACTIVE));
    }

    /**
     * Resume on a non-PAUSED batch must throw immediately.
     */
    @Test
    void resume_rejectsNonPausedBatch() {
        UUID batchId = UUID.randomUUID();
        BatchTransfer bt = BatchTransfer.initiate(
                batchId, UUID.randomUUID(), UUID.randomUUID(),
                "192.168.1.2", 9090, 2, 500L);
        bt.setStatus(GroupTransferStatus.ACTIVE);
        when(batchTransferRepository.findById(batchId)).thenReturn(Optional.of(bt));

        assertThatThrownBy(() -> batchSenderService.resume(batchId))
                .isInstanceOf(FileTransferException.class)
                .hasMessageContaining("not paused");
    }

    /**
     * Resume on missing batch must throw.
     */
    @Test
    void resume_throwsWhenBatchNotFound() {
        UUID batchId = UUID.randomUUID();
        when(batchTransferRepository.findById(batchId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> batchSenderService.resume(batchId))
                .isInstanceOf(FileTransferException.class)
                .hasMessageContaining("not found");
    }

    /*
    * Resume on PAUSED batch sets it ACTIVE and fires async send
    */
    @Test
    void resume_setsBatchActiveAndTriggersAsyncSend() {
        UUID batchId = UUID.randomUUID();
        BatchTransfer bt = BatchTransfer.initiate(
                batchId, UUID.randomUUID(), UUID.randomUUID(),
                "192.168.1.2", 9090, 2, 500L);
        bt.setStatus(GroupTransferStatus.PAUSED);
        when(batchTransferRepository.findById(batchId)).thenReturn(Optional.of(bt));
        when(batchTransferRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        batchSenderService.resume(batchId);

        verify(batchTransferRepository).save(argThat(b ->
                b.getStatus() == GroupTransferStatus.ACTIVE));
        verify(batchAsyncSender).sendAsync(eq(batchId), any(), anyInt(), isNull());
    }
}