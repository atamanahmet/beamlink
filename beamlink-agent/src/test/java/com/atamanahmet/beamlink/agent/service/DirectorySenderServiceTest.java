package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.config.AgentConfig;
import com.atamanahmet.beamlink.agent.domain.DirectoryTransfer;
import com.atamanahmet.beamlink.agent.domain.FileTransfer;
import com.atamanahmet.beamlink.agent.domain.enums.GroupTransferStatus;
import com.atamanahmet.beamlink.agent.dto.InitiateDirectoryTransferRequest;
import com.atamanahmet.beamlink.agent.dto.InitiateDirectoryTransferResponse;
import com.atamanahmet.beamlink.agent.exception.FileTransferException;
import com.atamanahmet.beamlink.agent.repository.DirectoryTransferRepository;
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
class DirectorySenderServiceTest {

    @Mock private DirectoryTransferRepository directoryTransferRepository;
    @Mock private FileTransferRepository fileTransferRepository;
    @Mock private AgentService agentService;
    @Mock private AgentConfig agentConfig;
    @Mock private DirectoryAsyncSender directoryAsyncSender;
    @Mock private ObjectMapper objectMapper;
    @Mock private HttpClient httpClient;

    @InjectMocks
    private DirectorySenderService directorySenderService;

    @TempDir
    Path tempDir;

    private Path createFile(String name, byte[] content) throws Exception {
        Path file = tempDir.resolve(name);
        Files.write(file, content);
        return file;
    }

    /** Creates a nested path like subdir/file.txt under tempDir */
    private Path createFileInSubdir(String subdir, String name, byte[] content) throws Exception {
        Path dir = tempDir.resolve(subdir);
        Files.createDirectories(dir);
        Path file = dir.resolve(name);
        Files.write(file, content);
        return file;
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> mockHttpResponse(int status) {
        HttpResponse<String> r = mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(status);
        return r;
    }

    /** Stubs everything that must succeed for a normal initiate() call */
    @SuppressWarnings("unchecked")
    private void stubHappyPath(HttpResponse<String> httpResponse) throws Exception {
        when(agentService.getAgentId()).thenReturn(UUID.randomUUID());
        when(agentConfig.getTransferExpiryHours()).thenReturn(24L);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(directoryTransferRepository.save(any(DirectoryTransfer.class)))
                .thenAnswer(i -> i.getArgument(0));
        when(fileTransferRepository.saveAll(any()))
                .thenAnswer(i -> i.getArgument(0));
    }

    private InitiateDirectoryTransferRequest buildRequest(String sourcePath) {
        InitiateDirectoryTransferRequest req = new InitiateDirectoryTransferRequest();
        req.setSourcePath(sourcePath);
        req.setTargetAgentId(UUID.randomUUID());
        req.setTargetIp("192.168.1.5");
        req.setTargetPort(9090);
        req.setTargetToken("token");
        return req;
    }

    /**
     * Path that does not exist on disk must be caught before any DB or network call.
     */
    @Test
    void initiate_rejectsNonExistentDirectory() {
        String ghost = tempDir.resolve("ghost-dir").toString();

        assertThatThrownBy(() -> directorySenderService.initiate(buildRequest(ghost)))
                .isInstanceOf(FileTransferException.class)
                .hasMessageContaining("Directory not found");

        verifyNoInteractions(directoryTransferRepository, fileTransferRepository, httpClient);
    }

    /**
     * A regular file path where a directory is expected must be rejected early.
     */
    @Test
    void initiate_rejectsFilePathInsteadOfDirectory() throws Exception {
        Path file = createFile("notadir.txt", "x".getBytes());

        assertThatThrownBy(() -> directorySenderService.initiate(buildRequest(file.toString())))
                .isInstanceOf(FileTransferException.class)
                .hasMessageContaining("Directory not found");

        verifyNoInteractions(directoryTransferRepository);
    }

    /**
     * A directory that contains zero regular files anywhere must be rejected.
     */
    @Test
    void initiate_rejectsDirectoryWithNoFiles() throws Exception {
        Path emptyDir = tempDir.resolve("empty");
        Files.createDirectories(emptyDir);

        assertThatThrownBy(() -> directorySenderService.initiate(buildRequest(emptyDir.toString())))
                .isInstanceOf(FileTransferException.class)
                .hasMessageContaining("no files");

        verifyNoInteractions(directoryTransferRepository);
    }

    /**
     * A directory whose only contents are empty subdirectories also has no files.
     * Same rejection as above, the walk finds nothing transferable.
     */
    @Test
    void initiate_rejectsDirectoryContainingOnlyEmptySubdirs() throws Exception {
        Path emptyDir = tempDir.resolve("outer");
        Files.createDirectories(emptyDir.resolve("inner"));

        assertThatThrownBy(() -> directorySenderService.initiate(buildRequest(emptyDir.toString())))
                .isInstanceOf(FileTransferException.class)
                .hasMessageContaining("no files");
    }

    /**
     * PathNormalizer strips surrounding quotes, quoted path must still resolve.
     */
    @Test
    void initiate_normalizesQuotedPath() throws Exception {
        createFile("file.txt", "data".getBytes());
        String quotedPath = "\"" + tempDir.toString() + "\"";
        stubHappyPath(mockHttpResponse(200));

        InitiateDirectoryTransferResponse response =
                directorySenderService.initiate(buildRequest(quotedPath));

        assertThat(response.getDirectoryTransferId()).isNotNull();
    }

    /**
     * Happy path must persist one DirectoryTransfer and one FileTransfer per file.
     */
    @Test
    void initiate_persistsDirectoryAndFileTransfers() throws Exception {
        createFile("a.txt", "aaa".getBytes());
        createFile("b.txt", "bbb".getBytes());
        stubHappyPath(mockHttpResponse(200));

        directorySenderService.initiate(buildRequest(tempDir.toString()));

        verify(directoryTransferRepository, atLeast(2)).save(any(DirectoryTransfer.class));

        verify(fileTransferRepository).saveAll(argThat(list -> ((List<?>) list).size() == 2));
    }

    /**
     * TotalSize on the DirectoryTransfer must equal the sum of all file sizes.
     */
    @Test
    void initiate_calculatesTotalSizeCorrectly() throws Exception {
        createFile("x.bin", new byte[150]);
        createFile("y.bin", new byte[250]);
        stubHappyPath(mockHttpResponse(200));

        directorySenderService.initiate(buildRequest(tempDir.toString()));

        ArgumentCaptor<DirectoryTransfer> captor =
                ArgumentCaptor.forClass(DirectoryTransfer.class);
        verify(directoryTransferRepository, atLeast(1)).save(captor.capture());

        long maxSeen = captor.getAllValues().stream()
                .mapToLong(DirectoryTransfer::getTotalSize)
                .max().orElse(0);
        assertThat(maxSeen).isEqualTo(400L);
    }

    /**
     * Each FileTransfer must carry the correct relative path so the receiver
     * can reconstruct the directory structure on disk.
     */
    @Test
    void initiate_setsRelativePathOnFileTransfers() throws Exception {
        createFileInSubdir("sub", "deep.txt", "content".getBytes());
        stubHappyPath(mockHttpResponse(200));

        directorySenderService.initiate(buildRequest(tempDir.toString()));

        ArgumentCaptor<List<FileTransfer>> captor = ArgumentCaptor.forClass(List.class);
        verify(fileTransferRepository).saveAll(captor.capture());

        List<FileTransfer> saved = captor.getValue();
        assertThat(saved).hasSize(1);
        // Relative path must include the subdir, not just the filename
        assertThat(saved.get(0).getRelativePath()).contains("sub");
    }

    /**
     * directoryTransferId on each FileTransfer must match the parent DirectoryTransfer.
     * Without this link, resume and completion tracking break entirely.
     */
    @Test
    void initiate_linksFileTransfersToDirectoryTransfer() throws Exception {
        createFile("linked.txt", "data".getBytes());
        stubHappyPath(mockHttpResponse(200));

        InitiateDirectoryTransferResponse response =
                directorySenderService.initiate(buildRequest(tempDir.toString()));

        ArgumentCaptor<List<FileTransfer>> captor = ArgumentCaptor.forClass(List.class);
        verify(fileTransferRepository).saveAll(captor.capture());

        captor.getValue().forEach(ft ->
                assertThat(ft.getDirectoryTransferId())
                        .isEqualTo(response.getDirectoryTransferId()));
    }

    /**
     * A subdirectory that has no files anywhere beneath it is truly empty
     * and must be included in the emptyDirectories list sent to the target.
     * Fix: walk uses Files.walk not Files.list, so this now works correctly.
     */
    @Test
    void initiate_tracksEmptyLeafSubdirectory() throws Exception {
        createFile("root.txt", "data".getBytes());

        Files.createDirectories(tempDir.resolve("empty-leaf"));
        stubHappyPath(mockHttpResponse(200));

        directorySenderService.initiate(buildRequest(tempDir.toString()));

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(objectMapper).writeValueAsString(payloadCaptor.capture());

        Object payload = payloadCaptor.getValue();
        assertThat(payload).hasFieldOrProperty("emptyDirectories");

        java.lang.reflect.Method getter =
                payload.getClass().getMethod("getEmptyDirectories");
        List<?> emptyDirs = (List<?>) getter.invoke(payload);
        assertThat(emptyDirs).anyMatch(d -> d.toString().contains("empty-leaf"));
    }

    /**
     * A subdir that contains only deeper subdirs, but those subdirs have files
     * must NOT be marked as empty.
     */
    @Test
    void initiate_doesNotFlagSubdirAsEmptyIfDescendantHasFiles() throws Exception {

        createFileInSubdir("outer/inner", "file.txt", "x".getBytes());
        stubHappyPath(mockHttpResponse(200));

        directorySenderService.initiate(buildRequest(tempDir.toString()));

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(objectMapper).writeValueAsString(payloadCaptor.capture());

        Object payload = payloadCaptor.getValue();
        java.lang.reflect.Method getter =
                payload.getClass().getMethod("getEmptyDirectories");
        List<?> emptyDirs = (List<?>) getter.invoke(payload);

        assertThat(emptyDirs).noneMatch(d -> d.toString().contains("outer"));
    }

    /**
     * DirectoryTransfer must be set to ACTIVE only after the target accepts registration.
     * If target rejects, it must never reach ACTIVE.
     */
    @Test
    void initiate_setsDirectoryTransferActiveAfterTargetAccepts() throws Exception {
        createFile("f.txt", "x".getBytes());
        stubHappyPath(mockHttpResponse(200));

        directorySenderService.initiate(buildRequest(tempDir.toString()));

        verify(directoryTransferRepository, atLeastOnce()).save(argThat(dt ->
                dt.getStatus() == GroupTransferStatus.ACTIVE));
    }

    /**
     * Async sender must be called once with the correct connection params.
     */
    @Test
    void initiate_triggersAsyncSend() throws Exception {
        createFile("send.txt", "payload".getBytes());
        stubHappyPath(mockHttpResponse(200));

        directorySenderService.initiate(buildRequest(tempDir.toString()));

        verify(directoryAsyncSender).sendAsync(
                any(UUID.class), eq("192.168.1.5"), eq(9090), eq("token"));
    }

    /**
     * Returns a non-null directoryTransferId immediately so the caller can poll status.
     */
    @Test
    void initiate_returnsDirectoryTransferId() throws Exception {
        createFile("ret.txt", "data".getBytes());
        stubHappyPath(mockHttpResponse(200));

        InitiateDirectoryTransferResponse response =
                directorySenderService.initiate(buildRequest(tempDir.toString()));

        assertThat(response.getDirectoryTransferId()).isNotNull();
    }

    /**
     * Non-200 from target must throw and must never mark the transfer ACTIVE.
     */
    @Test
    @SuppressWarnings("unchecked")
    void initiate_throwsWhenTargetRejectsRegistration() throws Exception {
        createFile("reject.txt", "data".getBytes());
        when(agentService.getAgentId()).thenReturn(UUID.randomUUID());
        when(agentConfig.getTransferExpiryHours()).thenReturn(24L);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(directoryTransferRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(fileTransferRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));
        HttpResponse<String> rejected = mockHttpResponse(403);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(rejected);

        assertThatThrownBy(() ->
                directorySenderService.initiate(buildRequest(tempDir.toString())))
                .isInstanceOf(FileTransferException.class)
                .hasMessageContaining("Target rejected");

        verify(directoryTransferRepository, never()).save(argThat(dt ->
                dt.getStatus() == GroupTransferStatus.ACTIVE));
        verifyNoInteractions(directoryAsyncSender);
    }

    /**
     * Network failure reaching target must throw before marking ACTIVE or firing async.
     */
    @Test
    @SuppressWarnings("unchecked")
    void initiate_throwsOnNetworkFailureToTarget() throws Exception {
        createFile("net.txt", "data".getBytes());
        when(agentService.getAgentId()).thenReturn(UUID.randomUUID());
        when(agentConfig.getTransferExpiryHours()).thenReturn(24L);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(directoryTransferRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(fileTransferRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new java.io.IOException("connection refused"));

        assertThatThrownBy(() ->
                directorySenderService.initiate(buildRequest(tempDir.toString())))
                .isInstanceOf(FileTransferException.class)
                .hasMessageContaining("Cannot reach target");

        verifyNoInteractions(directoryAsyncSender);
    }

    /**
     * Resume on a missing UUID must throw immediately.
     */
    @Test
    void resume_throwsWhenDirectoryTransferNotFound() {
        UUID id = UUID.randomUUID();
        when(directoryTransferRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> directorySenderService.resume(id))
                .isInstanceOf(FileTransferException.class)
                .hasMessageContaining("not found");
    }

    /**
     * Resume on a non-PAUSED transfer must throw, resuming ACTIVE or COMPLETED is wrong.
     */
    @Test
    void resume_rejectsNonPausedTransfer() {
        UUID id = UUID.randomUUID();
        DirectoryTransfer dt = mock(DirectoryTransfer.class);
        when(dt.getStatus()).thenReturn(GroupTransferStatus.ACTIVE);
        when(directoryTransferRepository.findById(id)).thenReturn(Optional.of(dt));

        assertThatThrownBy(() -> directorySenderService.resume(id))
                .isInstanceOf(FileTransferException.class)
                .hasMessageContaining("not paused");
    }

    /**
     * Resume on a PAUSED transfer must set it ACTIVE and fire async sender.
     */
    @Test
    void resume_setsPausedTransferActiveAndTriggersAsyncSend() {
        UUID id = UUID.randomUUID();
        DirectoryTransfer dt = mock(DirectoryTransfer.class);
        when(dt.getStatus()).thenReturn(GroupTransferStatus.PAUSED);
        when(dt.getTargetIp()).thenReturn("10.0.0.1");
        when(dt.getTargetPort()).thenReturn(8080);
        when(directoryTransferRepository.findById(id)).thenReturn(Optional.of(dt));
        when(directoryTransferRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        directorySenderService.resume(id);

        verify(dt).setStatus(GroupTransferStatus.ACTIVE);
        verify(directoryTransferRepository).save(dt);
        // null token is current known behavior — documented here intentionally
        verify(directoryAsyncSender).sendAsync(eq(id), eq("10.0.0.1"), eq(8080), isNull());
    }
}