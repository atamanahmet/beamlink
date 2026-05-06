package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.domain.FileTransfer;
import com.atamanahmet.beamlink.agent.domain.enums.TransferStatus;
import com.atamanahmet.beamlink.agent.dto.ChunkAckResponse;
import com.atamanahmet.beamlink.agent.http.HttpSender;
import com.atamanahmet.beamlink.agent.repository.FileTransferRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class TransferAsyncSenderTest {

    @Mock
    private FileTransferRepository transferRepository;

    @Mock
    private ObjectMapper objectMapper;

    /**
     * Tests talk to HttpSender, not JDK HttpClient directly
     */
    @Mock
    private HttpSender httpSender;

    @InjectMocks
    private TransferAsyncSender asyncSender;

    @TempDir
    Path tempDir;

  //Helpers
    private FileTransfer makeTransfer(Path file, long fileSize) {
        FileTransfer ft = FileTransfer.initiate(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                file.getFileName().toString(), file.toString(), fileSize
        );
        ft.setStatus(TransferStatus.ACTIVE);
        return ft;
    }

    /** Receiver got the chunk but there's still more to send */
    private ChunkAckResponse ackAt(long offset) {
        return new ChunkAckResponse(offset, false);
    }

    /** Receiver says the whole file is done */
    private ChunkAckResponse ackComplete(long offset) {
        return new ChunkAckResponse(offset, true);
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> httpOk() {
        HttpResponse<String> resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(200);
        when(resp.body()).thenReturn("{}");
        return resp;
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> http4xx() {
        HttpResponse<String> resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(400);
        when(resp.body()).thenReturn("bad request");
        return resp;
    }

    /**
     * If DB record is gone, no save, no HTTP call
     */
    @Test
    void whenTransferNotFound_doesNothing() throws Exception {
        UUID transferId = UUID.randomUUID();
        when(transferRepository.findByTransferId(transferId))
                .thenReturn(Optional.empty());

        asyncSender.sendAsync(transferId, "127.0.0.1", 9999, "token");

        verify(transferRepository, never()).save(any());
        verifyNoInteractions(httpSender);
    }

    /**
     * Cancelled transfer, Stop before touching the network
     */
    @Test
    void whenCancelledBeforeFirstChunk_noHttpCallAndNoSave() throws Exception {
        Path file = tempDir.resolve("data.bin");
        Files.write(file, new byte[512]);

        FileTransfer ft = makeTransfer(file, 512);
        ft.setStatus(TransferStatus.CANCELLED);

        when(transferRepository.findByTransferId(ft.getTransferId()))
                .thenReturn(Optional.of(ft));

        asyncSender.sendAsync(ft.getTransferId(), "127.0.0.1", 9999, "token");

        verifyNoInteractions(httpSender);
        verify(transferRepository, never()).save(any());
    }

    /**
     * Same as cancelled, paused means stop cleanly, no network call
     */
    @Test
    void whenPausedBeforeFirstChunk_noHttpCallAndNoSave() throws Exception {
        Path file = tempDir.resolve("data.bin");
        Files.write(file, new byte[512]);

        FileTransfer ft = makeTransfer(file, 512);
        ft.setStatus(TransferStatus.PAUSED);

        when(transferRepository.findByTransferId(ft.getTransferId()))
                .thenReturn(Optional.of(ft));

        asyncSender.sendAsync(ft.getTransferId(), "127.0.0.1", 9999, "token");

        verifyNoInteractions(httpSender);
        verify(transferRepository, never()).save(any());
    }

    /**
     * Transfer was okay on first fetch but vanished on the re-fetch inside the loop
     */
    @Test
    void whenTransferDisappearsMidSend_stopsGracefully() throws Exception {
        Path file = tempDir.resolve("gone.bin");
        Files.write(file, new byte[512]);

        FileTransfer ft = makeTransfer(file, 512);

        when(transferRepository.findByTransferId(ft.getTransferId()))
                .thenReturn(Optional.of(ft))    // initial fetch before opening file
                .thenReturn(Optional.empty());  // re-fetch inside the loop

        asyncSender.sendAsync(ft.getTransferId(), "127.0.0.1", 9999, "token");

        verifyNoInteractions(httpSender);
        verify(transferRepository, never()).save(any());
    }

    /**
     * Small file fits in one chunk, sent once, saved as COMPLETED
     */
    @Test
    void singleChunkFile_sendsOnceAndMarksCompleted() throws Exception {
        byte[] content = "hello world".getBytes();
        Path file = tempDir.resolve("small.txt");
        Files.write(file, content);

        FileTransfer ft = makeTransfer(file, content.length);

        when(transferRepository.findByTransferId(ft.getTransferId()))
                .thenReturn(Optional.of(ft));
        HttpResponse<String> response = httpOk();
        when(httpSender.send(any(HttpRequest.class))).thenReturn(response);

        ChunkAckResponse ack = ackComplete(content.length);
        when(objectMapper.readValue("{}", ChunkAckResponse.class))
                .thenReturn(ack);

        asyncSender.sendAsync(ft.getTransferId(), "127.0.0.1", 9999, "token");

        ArgumentCaptor<FileTransfer> saved = ArgumentCaptor.forClass(FileTransfer.class);
        verify(transferRepository, atLeast(1)).save(saved.capture());

        FileTransfer last = saved.getAllValues().get(saved.getAllValues().size() - 1);
        assertEquals(TransferStatus.COMPLETED, last.getStatus());
    }

    /**
     * First chunk must tell the receiver "bytes 0 to (length-1) of total"
     */
    @Test
    void firstChunk_contentRangeStartsAtZero() throws Exception {
        byte[] content = "abcdefgh".getBytes();
        Path file = tempDir.resolve("range.bin");
        Files.write(file, content);

        FileTransfer ft = makeTransfer(file, content.length);

        when(transferRepository.findByTransferId(ft.getTransferId()))
                .thenReturn(Optional.of(ft));
        HttpResponse<String> response = httpOk();
        when(httpSender.send(any(HttpRequest.class))).thenReturn(response);
        ChunkAckResponse ack = ackComplete(content.length);
        when(objectMapper.readValue("{}", ChunkAckResponse.class))
                .thenReturn(ack);

        asyncSender.sendAsync(ft.getTransferId(), "127.0.0.1", 9999, "token");

        ArgumentCaptor<HttpRequest> req = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpSender).send(req.capture());

        String range = req.getValue().headers().firstValue("Content-Range").orElse("");
        assertEquals("bytes 0-" + (content.length - 1) + "/" + content.length, range);
    }

    /**
     * Resume: if the receiver already confirmed 10 bytes, it must start from byte 10
     * Don't resend what's already sent
     */
    @Test
    void resumedTransfer_contentRangeStartsAtConfirmedOffset() throws Exception {
        byte[] content = "0123456789ABCDEFGHIJ".getBytes(); // 20 bytes
        Path file = tempDir.resolve("resume.bin");
        Files.write(file, content);

        FileTransfer ft = makeTransfer(file, content.length);
        ft.setConfirmedOffset(10L);

        when(transferRepository.findByTransferId(ft.getTransferId()))
                .thenReturn(Optional.of(ft));
        HttpResponse<String> response = httpOk();
        when(httpSender.send(any(HttpRequest.class))).thenReturn(response);
        ChunkAckResponse ack = ackComplete(content.length);
        when(objectMapper.readValue("{}", ChunkAckResponse.class))
                .thenReturn(ack);

        asyncSender.sendAsync(ft.getTransferId(), "127.0.0.1", 9999, "token");

        ArgumentCaptor<HttpRequest> req = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpSender).send(req.capture());

        String range = req.getValue().headers().firstValue("Content-Range").orElse("");
        assertTrue(range.startsWith("bytes 10-"),
                "expected range to start at offset 10, got: " + range);
    }

    /**
     * The token must end up in the X-Auth-Token header on the request
     */
    @Test
    void authTokenIsForwardedInHeader() throws Exception {
        byte[] content = "data".getBytes();
        Path file = tempDir.resolve("auth.bin");
        Files.write(file, content);

        FileTransfer ft = makeTransfer(file, content.length);

        when(transferRepository.findByTransferId(ft.getTransferId()))
                .thenReturn(Optional.of(ft));
        HttpResponse<String> response = httpOk();
        when(httpSender.send(any(HttpRequest.class))).thenReturn(response);
        ChunkAckResponse ack = ackComplete(content.length);
        when(objectMapper.readValue("{}", ChunkAckResponse.class))
                .thenReturn(ack);

        asyncSender.sendAsync(ft.getTransferId(), "127.0.0.1", 9999, "my-secret-token");

        ArgumentCaptor<HttpRequest> req = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpSender).send(req.capture());

        assertEquals("my-secret-token",
                req.getValue().headers().firstValue("X-Auth-Token").orElse(""));
    }

    @Test
    void nullToken_sentAsEmptyString() throws Exception {
        byte[] content = "data".getBytes();
        Path file = tempDir.resolve("noauth.bin");
        Files.write(file, content);

        FileTransfer ft = makeTransfer(file, content.length);

        when(transferRepository.findByTransferId(ft.getTransferId()))
                .thenReturn(Optional.of(ft));
        HttpResponse<String> response = httpOk();
        when(httpSender.send(any(HttpRequest.class))).thenReturn(response);
        ChunkAckResponse ack = ackComplete(content.length);
        when(objectMapper.readValue("{}", ChunkAckResponse.class))
                .thenReturn(ack);

        asyncSender.sendAsync(ft.getTransferId(), "127.0.0.1", 9999, null);

        ArgumentCaptor<HttpRequest> req = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpSender).send(req.capture());

        assertEquals("",
                req.getValue().headers().firstValue("X-Auth-Token").orElse("MISSING"));
    }

    /**
     * After a successful chunk, confirmedOffset must be written to the DB
     */
    @Test
    void confirmedOffsetIsSavedAfterChunkAck() throws Exception {
        byte[] content = "hello".getBytes();
        Path file = tempDir.resolve("offset.bin");
        Files.write(file, content);

        FileTransfer ft = makeTransfer(file, content.length);

        when(transferRepository.findByTransferId(ft.getTransferId()))
                .thenReturn(Optional.of(ft));
        HttpResponse<String> response = httpOk();
        when(httpSender.send(any(HttpRequest.class))).thenReturn(response);
        ChunkAckResponse ack = ackComplete(content.length);
        when(objectMapper.readValue("{}", ChunkAckResponse.class))
                .thenReturn(ack);

        asyncSender.sendAsync(ft.getTransferId(), "127.0.0.1", 9999, "token");

        ArgumentCaptor<FileTransfer> saved = ArgumentCaptor.forClass(FileTransfer.class);
        verify(transferRepository, atLeast(1)).save(saved.capture());

        boolean offsetSaved = saved.getAllValues().stream()
                .anyMatch(t -> t.getConfirmedOffset() == content.length);
        assertTrue(offsetSaved, "confirmedOffset must be persisted after chunk ack");
    }

    /**
     * Receiver acks a lower offset than sent, rewind the file pointer and resend
     * this can happen if the receiver had a partial write and needs to back up
     */
    @Test
    void whenReceiverAckLowerOffset_senderRewindsAndResends() throws Exception {
        byte[] content = "abcdefghij".getBytes(); // 10 bytes
        Path file = tempDir.resolve("rewind.bin");
        Files.write(file, content);

        FileTransfer ft = makeTransfer(file, content.length);
        ft.setConfirmedOffset(5L);

        when(transferRepository.findByTransferId(ft.getTransferId()))
                .thenReturn(Optional.of(ft));

        HttpResponse<String> response = httpOk();
        when(httpSender.send(any(HttpRequest.class))).thenReturn(response);

        ChunkAckResponse ack = ackComplete(content.length);
        when(objectMapper.readValue("{}", ChunkAckResponse.class))
                .thenReturn(ackAt(2), ackComplete(content.length));

        asyncSender.sendAsync(ft.getTransferId(), "127.0.0.1", 9999, "token");

        verify(httpSender, times(2)).send(any());

        ArgumentCaptor<FileTransfer> saved = ArgumentCaptor.forClass(FileTransfer.class);
        verify(transferRepository, atLeast(1)).save(saved.capture());
        assertEquals(TransferStatus.COMPLETED,
                saved.getAllValues().get(saved.getAllValues().size() - 1).getStatus());
    }

    /**
     * Receiver keeps sending the same offset, stuck, mark it failed
     */
    @Test
    void whenNoForwardProgress_marksTransferFailed() throws Exception {
        byte[] content = "stuck".getBytes();
        Path file = tempDir.resolve("stuck.bin");
        Files.write(file, content);

        FileTransfer ft = makeTransfer(file, content.length);

        when(transferRepository.findByTransferId(ft.getTransferId()))
                .thenReturn(Optional.of(ft));
        HttpResponse<String> response = httpOk();
        when(httpSender.send(any(HttpRequest.class))).thenReturn(response);

        ChunkAckResponse stall = ackAt(0);
        when(objectMapper.readValue("{}", ChunkAckResponse.class))
                .thenReturn(stall, stall, stall, stall, stall);

        asyncSender.sendAsync(ft.getTransferId(), "127.0.0.1", 9999, "token");

        verify(httpSender, times(5)).send(any());

        ArgumentCaptor<FileTransfer> saved = ArgumentCaptor.forClass(FileTransfer.class);
        verify(transferRepository, atLeast(1)).save(saved.capture());

        FileTransfer last = saved.getAllValues().get(saved.getAllValues().size() - 1);
        assertEquals(TransferStatus.FAILED, last.getStatus());
        assertNotNull(last.getFailureReason());
    }

    /**
     * Receiver returned 400, fail immediately, don't retry a bad request
     */
    @Test
    void whenServerRejects_marksTransferFailed() throws Exception {
        byte[] content = "data".getBytes();
        Path file = tempDir.resolve("rejected.bin");
        Files.write(file, content);

        FileTransfer ft = makeTransfer(file, content.length);

        when(transferRepository.findByTransferId(ft.getTransferId()))
                .thenReturn(Optional.of(ft));

        HttpResponse<String> response = http4xx();
        when(httpSender.send(any(HttpRequest.class))).thenReturn(response);

        asyncSender.sendAsync(ft.getTransferId(), "127.0.0.1", 9999, "token");

        ArgumentCaptor<FileTransfer> saved = ArgumentCaptor.forClass(FileTransfer.class);
        verify(transferRepository, atLeast(1)).save(saved.capture());

        FileTransfer last = saved.getAllValues().get(saved.getAllValues().size() - 1);
        assertEquals(TransferStatus.FAILED, last.getStatus());
        assertNotNull(last.getFailureReason());
    }

    /**
     * Network blew up with IOException, mark failed, don't crash the thread
     */
    @Test
    void whenHttpSenderThrows_marksTransferFailed() throws Exception {
        byte[] content = "data".getBytes();
        Path file = tempDir.resolve("ioerror.bin");
        Files.write(file, content);

        FileTransfer ft = makeTransfer(file, content.length);

        when(transferRepository.findByTransferId(ft.getTransferId()))
                .thenReturn(Optional.of(ft));
        when(httpSender.send(any(HttpRequest.class)))
                .thenThrow(new java.io.IOException("connection refused"));

        asyncSender.sendAsync(ft.getTransferId(), "127.0.0.1", 9999, "token");

        ArgumentCaptor<FileTransfer> saved = ArgumentCaptor.forClass(FileTransfer.class);
        verify(transferRepository, atLeast(1)).save(saved.capture());

        FileTransfer last = saved.getAllValues().get(saved.getAllValues().size() - 1);
        assertEquals(TransferStatus.FAILED, last.getStatus());
        assertNotNull(last.getFailureReason());
    }

    /**
     * Fails twice then succeeds on the third attempt
     */
    @Test
    void retrySucceedsOnThirdAttempt_transferCompletes() throws Exception {
        byte[] content = "retry me".getBytes();
        Path file = tempDir.resolve("retry.bin");
        Files.write(file, content);

        FileTransfer ft = makeTransfer(file, content.length);

        when(transferRepository.findByTransferId(ft.getTransferId()))
                .thenReturn(Optional.of(ft));
        when(httpSender.send(any(HttpRequest.class)))
                .thenThrow(new java.io.IOException("timeout"))  // attempt 1
                .thenThrow(new java.io.IOException("timeout"))  // attempt 2
                .thenReturn(httpOk());                      // attempt 3
        ChunkAckResponse ack = ackComplete(content.length);
        when(objectMapper.readValue("{}", ChunkAckResponse.class))
                .thenReturn(ack);

        asyncSender.sendAsync(ft.getTransferId(), "127.0.0.1", 9999, "token");

        verify(httpSender, times(3)).send(any());

        ArgumentCaptor<FileTransfer> saved = ArgumentCaptor.forClass(FileTransfer.class);
        verify(transferRepository, atLeast(1)).save(saved.capture());
        assertEquals(TransferStatus.COMPLETED,
                saved.getAllValues().get(saved.getAllValues().size() - 1).getStatus());
    }

    /**
     * All 3 attempts fail, mark the transfer as failed
     */
    @Test
    void allRetriesExhausted_marksTransferFailed() throws Exception {
        byte[] content = "no luck".getBytes();
        Path file = tempDir.resolve("exhausted.bin");
        Files.write(file, content);

        FileTransfer ft = makeTransfer(file, content.length);

        when(transferRepository.findByTransferId(ft.getTransferId()))
                .thenReturn(Optional.of(ft));
        when(httpSender.send(any(HttpRequest.class)))
                .thenThrow(new java.io.IOException("timeout")); // all 3 attempts

        asyncSender.sendAsync(ft.getTransferId(), "127.0.0.1", 9999, "token");

        verify(httpSender, times(5)).send(any());

        ArgumentCaptor<FileTransfer> saved = ArgumentCaptor.forClass(FileTransfer.class);
        verify(transferRepository, atLeast(1)).save(saved.capture());
        assertEquals(TransferStatus.FAILED,
                saved.getAllValues().get(saved.getAllValues().size() - 1).getStatus());
    }

    /**
     * File path in the DB points to a file that doesn't exist on disk, mark failed
     */
    @Test
    void whenFileNotOnDisk_marksTransferFailed() {
        FileTransfer ft = FileTransfer.initiate(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "missing.bin",
                tempDir.resolve("missing.bin").toString(),
                1024L
        );
        ft.setStatus(TransferStatus.ACTIVE);

        when(transferRepository.findByTransferId(ft.getTransferId()))
                .thenReturn(Optional.of(ft));

        asyncSender.sendAsync(ft.getTransferId(), "127.0.0.1", 9999, "token");

        ArgumentCaptor<FileTransfer> saved = ArgumentCaptor.forClass(FileTransfer.class);
        verify(transferRepository, atLeast(1)).save(saved.capture());

        FileTransfer last = saved.getAllValues().get(saved.getAllValues().size() - 1);
        assertEquals(TransferStatus.FAILED, last.getStatus());
        assertNotNull(last.getFailureReason());
    }
}