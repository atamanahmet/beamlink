package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.domain.BatchTransfer;
import com.atamanahmet.beamlink.agent.domain.FileTransfer;
import com.atamanahmet.beamlink.agent.domain.enums.GroupTransferStatus;
import com.atamanahmet.beamlink.agent.domain.enums.TransferStatus;
import com.atamanahmet.beamlink.agent.repository.BatchTransferRepository;
import com.atamanahmet.beamlink.agent.repository.FileTransferRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class BatchAsyncSenderTest {

    @Mock
    private BatchTransferRepository batchTransferRepository;

    @Mock
    private FileTransferRepository fileTransferRepository;

    @Mock
    private TransferAsyncSender transferAsyncSender;

    @InjectMocks
    private BatchAsyncSender batchAsyncSender;

    // Helpers

    /**
     * Builds a minimal FileTransfer with the fields BatchAsyncSender actually reads.
     */
    private FileTransfer makeFile(UUID batchId, String name, TransferStatus status) {
        FileTransfer ft = new FileTransfer();
        ft.setTransferId(UUID.randomUUID());
        ft.setFileName(name);
        ft.setBatchTransferId(batchId);
        ft.setStatus(status);
        ft.setFileSize(1024);
        ft.setConfirmedOffset(0);
        ft.setRetryCount(0);
        ft.setMaxRetries(5);
        ft.setCreatedAt(Instant.now());
        return ft;
    }

    /**
     * Builds a BatchTransfer with the given status.
     */
    private BatchTransfer makeBatch(UUID batchId, GroupTransferStatus status) {
        BatchTransfer bt = new BatchTransfer();
        bt.setBatchTransferId(batchId);
        bt.setStatus(status);
        bt.setCreatedAt(Instant.now());
        bt.setTotalFiles(1);
        bt.setTotalSize(1024);
        return bt;
    }

    /**
     * Returns a new instance with COMPLETED status, does not mutate the original.
     * UUID is preserved so stubs on the original's transferId still match.
     */
    private FileTransfer done(FileTransfer src) {
        FileTransfer ft = makeFile(src.getBatchTransferId(), src.getFileName(), TransferStatus.COMPLETED);
        ft.setTransferId(src.getTransferId());
        return ft;
    }

    /**
     * Early exit: batch not found
     */
    @Test
    void whenBatchNotFound_returnsImmediately() {
        UUID batchId = UUID.randomUUID();

        when(fileTransferRepository.findByBatchTransferId(batchId))
                .thenReturn(List.of());
        when(batchTransferRepository.findById(batchId))
                .thenReturn(Optional.empty());

        batchAsyncSender.sendAsync(batchId, "192.168.1.10", 8080, "test-token");

        verifyNoInteractions(transferAsyncSender);
    }

    /**
     * Queue filtering: only PENDING, ACTIVE, PAUSED files get queued
     */
    @Test
    void onlyPendingActiveAndPausedFilesAreQueued() throws Exception {
        UUID batchId = UUID.randomUUID();

        FileTransfer pending   = makeFile(batchId, "pending.txt",   TransferStatus.PENDING);
        FileTransfer active    = makeFile(batchId, "active.txt",    TransferStatus.ACTIVE);
        FileTransfer paused    = makeFile(batchId, "paused.txt",    TransferStatus.PAUSED);
        FileTransfer completed = makeFile(batchId, "completed.txt", TransferStatus.COMPLETED);
        FileTransfer failed    = makeFile(batchId, "failed.txt",    TransferStatus.FAILED);
        FileTransfer cancelled = makeFile(batchId, "cancelled.txt", TransferStatus.CANCELLED);

        BatchTransfer bt = makeBatch(batchId, GroupTransferStatus.ACTIVE);

        when(fileTransferRepository.findByBatchTransferId(batchId))
                .thenReturn(List.of(pending, active, paused, completed, failed, cancelled))
                .thenReturn(List.of(done(pending), done(active), done(paused), completed, failed, cancelled));
        when(batchTransferRepository.findById(batchId))
                .thenReturn(Optional.of(bt));

        batchAsyncSender.sendAsync(batchId, "192.168.1.10", 8080, "test-token");

        ArgumentCaptor<UUID> sentIds = ArgumentCaptor.forClass(UUID.class);
        verify(transferAsyncSender, times(3))
                .sendBlocking(sentIds.capture(), eq("192.168.1.10"), eq(8080), eq("test-token"));

        List<UUID> sent = sentIds.getAllValues();
        assertFalse(sent.contains(completed.getTransferId()), "COMPLETED must not be re-sent");
        assertFalse(sent.contains(failed.getTransferId()),    "FAILED must not be re-sent");
        assertFalse(sent.contains(cancelled.getTransferId()), "CANCELLED must not be re-sent");
        assertTrue(sent.contains(pending.getTransferId()));
        assertTrue(sent.contains(active.getTransferId()));
        assertTrue(sent.contains(paused.getTransferId()));
    }

    /**
     * Queue ordering: PAUSED files go before PENDING/ACTIVE
     */
    @Test
    void pausedFilesAreQueuedBeforePendingFiles() throws Exception {
        UUID batchId = UUID.randomUUID();

        FileTransfer pending = makeFile(batchId, "first.txt",  TransferStatus.PENDING);
        FileTransfer paused  = makeFile(batchId, "resume.txt", TransferStatus.PAUSED);

        BatchTransfer bt = makeBatch(batchId, GroupTransferStatus.ACTIVE);

        when(fileTransferRepository.findByBatchTransferId(batchId))
                .thenReturn(List.of(pending, paused))
                .thenReturn(List.of(done(paused), done(pending)));
        when(batchTransferRepository.findById(batchId))
                .thenReturn(Optional.of(bt));

        batchAsyncSender.sendAsync(batchId, "192.168.1.10", 8080, "test-token");

        ArgumentCaptor<UUID> order = ArgumentCaptor.forClass(UUID.class);
        verify(transferAsyncSender, times(2))
                .sendBlocking(order.capture(), any(), anyInt(), any());

        // paused must come first
        assertEquals(paused.getTransferId(),  order.getAllValues().get(0));
        assertEquals(pending.getTransferId(), order.getAllValues().get(1));
    }

    /**
     * sendBlocking called with correct args: ip, port, token, transferId
     */
    @Test
    void sendBlockingReceivesCorrectConnectionArgs() throws Exception {
        UUID batchId = UUID.randomUUID();

        FileTransfer ft = makeFile(batchId, "data.bin", TransferStatus.PENDING);
        BatchTransfer bt = makeBatch(batchId, GroupTransferStatus.ACTIVE);

        when(fileTransferRepository.findByBatchTransferId(batchId))
                .thenReturn(List.of(ft))
                .thenReturn(List.of(done(ft)));
        when(batchTransferRepository.findById(batchId))
                .thenReturn(Optional.of(bt));

        batchAsyncSender.sendAsync(batchId, "192.168.1.10", 8080, "test-token");

        verify(transferAsyncSender).sendBlocking(
                ft.getTransferId(),
                "192.168.1.10",
                8080,
                "test-token"
        );
    }

    /**
     * Two files, both sent, all connection args forwarded correctly
     */
    @Test
    void twoFiles_bothSentWithCorrectArgs() throws Exception {
        UUID batchId = UUID.randomUUID();
        String ip    = "10.0.0.1";
        int    port  = 9090;
        String token = "tok-abc";

        FileTransfer alpha = makeFile(batchId, "alpha.txt", TransferStatus.PENDING);
        FileTransfer beta  = makeFile(batchId, "beta.bin",  TransferStatus.PENDING);
        BatchTransfer bt   = makeBatch(batchId, GroupTransferStatus.ACTIVE);

        when(fileTransferRepository.findByBatchTransferId(batchId))
                .thenReturn(List.of(alpha, beta))
                .thenReturn(List.of(done(alpha), done(beta)));
        when(batchTransferRepository.findById(batchId))
                .thenReturn(Optional.of(bt));

        batchAsyncSender.sendAsync(batchId, ip, port, token);

        verify(transferAsyncSender).sendBlocking(alpha.getTransferId(), ip, port, token);
        verify(transferAsyncSender).sendBlocking(beta.getTransferId(),  ip, port, token);
        verifyNoMoreInteractions(transferAsyncSender);
    }

    /**
     * Files are sent one by one, sequential.
     */
    @Test
    void filesAreSentSequentially() throws Exception {
        UUID batchId = UUID.randomUUID();

        FileTransfer f1 = makeFile(batchId, "a.txt", TransferStatus.PENDING);
        FileTransfer f2 = makeFile(batchId, "b.txt", TransferStatus.PENDING);
        FileTransfer f3 = makeFile(batchId, "c.txt", TransferStatus.PENDING);

        BatchTransfer bt = makeBatch(batchId, GroupTransferStatus.ACTIVE);

        when(fileTransferRepository.findByBatchTransferId(batchId))
                .thenReturn(List.of(f1, f2, f3))
                .thenReturn(List.of(done(f1), done(f2), done(f3)));
        when(batchTransferRepository.findById(batchId))
                .thenReturn(Optional.of(bt));

        batchAsyncSender.sendAsync(batchId, "192.168.1.10", 8080, "test-token");

        var inOrder = inOrder(transferAsyncSender);
        inOrder.verify(transferAsyncSender).sendBlocking(eq(f1.getTransferId()), any(), anyInt(), any());
        inOrder.verify(transferAsyncSender).sendBlocking(eq(f2.getTransferId()), any(), anyInt(), any());
        inOrder.verify(transferAsyncSender).sendBlocking(eq(f3.getTransferId()), any(), anyInt(), any());
    }

    /**
     * Five files, sequential, no overlap
     */
    @Test
    void fiveFiles_sentInOrder_sequentially() throws Exception {
        UUID batchId = UUID.randomUUID();

        List<FileTransfer> files = List.of(
                makeFile(batchId, "f1.dat", TransferStatus.PENDING),
                makeFile(batchId, "f2.dat", TransferStatus.PENDING),
                makeFile(batchId, "f3.dat", TransferStatus.PENDING),
                makeFile(batchId, "f4.dat", TransferStatus.PENDING),
                makeFile(batchId, "f5.dat", TransferStatus.PENDING)
        );

        BatchTransfer bt = makeBatch(batchId, GroupTransferStatus.ACTIVE);

        when(batchTransferRepository.findById(batchId))
                .thenReturn(Optional.of(bt));
        when(fileTransferRepository.findByBatchTransferId(batchId))
                .thenReturn(files)
                .thenReturn(files.stream().map(this::done).collect(Collectors.toList()));

        batchAsyncSender.sendAsync(batchId, "192.168.1.10", 8080, "test-token");

        var inOrder = inOrder(transferAsyncSender);
        for (FileTransfer f : files) {
            inOrder.verify(transferAsyncSender)
                    .sendBlocking(eq(f.getTransferId()), any(), anyInt(), any());
        }
        verifyNoMoreInteractions(transferAsyncSender);
    }

    /**
     * Large list, insertion order
     */
    @Test
    void largeFileList_orderingIsDeterministic() throws Exception {
        UUID batchId = UUID.randomUUID();
        int  count   = 20;

        List<FileTransfer> files = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            files.add(makeFile(batchId, "file-" + i + ".bin", TransferStatus.PENDING));
        }

        BatchTransfer bt = makeBatch(batchId, GroupTransferStatus.ACTIVE);

        when(batchTransferRepository.findById(batchId))
                .thenReturn(Optional.of(bt));
        when(fileTransferRepository.findByBatchTransferId(batchId))
                .thenReturn(files)
                .thenReturn(files.stream().map(this::done).collect(Collectors.toList()));

        batchAsyncSender.sendAsync(batchId, "192.168.1.10", 8080, "test-token");

        var inOrder = inOrder(transferAsyncSender);
        for (FileTransfer f : files) {
            inOrder.verify(transferAsyncSender)
                    .sendBlocking(eq(f.getTransferId()), any(), anyInt(), any());
        }
        verify(transferAsyncSender, times(count)).sendBlocking(any(), any(), anyInt(), any());
    }

    /**
     * Duplicate file names in different paths
     */
    @Test
    void duplicateFileNames_treatedAsDistinctTransfers() throws Exception {
        UUID batchId = UUID.randomUUID();

        FileTransfer first  = makeFile(batchId, "readme.txt", TransferStatus.PENDING);
        FileTransfer second = makeFile(batchId, "readme.txt", TransferStatus.PENDING);

        assertNotEquals(first.getTransferId(), second.getTransferId(),
                "Sanity: factory must assign unique IDs");

        BatchTransfer bt = makeBatch(batchId, GroupTransferStatus.ACTIVE);

        when(batchTransferRepository.findById(batchId))
                .thenReturn(Optional.of(bt));
        when(fileTransferRepository.findByBatchTransferId(batchId))
                .thenReturn(List.of(first, second))
                .thenReturn(List.of(done(first), done(second)));

        batchAsyncSender.sendAsync(batchId, "192.168.1.10", 8080, "test-token");

        verify(transferAsyncSender).sendBlocking(eq(first.getTransferId()),  any(), anyInt(), any());
        verify(transferAsyncSender).sendBlocking(eq(second.getTransferId()), any(), anyInt(), any());
        verify(transferAsyncSender, times(2)).sendBlocking(any(), any(), anyInt(), any());
    }

    /**
     * Batch status guard: CANCELLED stops the loop before next file
     */
    @Test
    void whenBatchCancelled_stopsBeforeNextFile() throws Exception {
        UUID batchId = UUID.randomUUID();

        FileTransfer f1 = makeFile(batchId, "first.txt",  TransferStatus.PENDING);
        FileTransfer f2 = makeFile(batchId, "second.txt", TransferStatus.PENDING);

        BatchTransfer activeBt    = makeBatch(batchId, GroupTransferStatus.ACTIVE);
        BatchTransfer cancelledBt = makeBatch(batchId, GroupTransferStatus.CANCELLED);

        when(fileTransferRepository.findByBatchTransferId(batchId))
                .thenReturn(List.of(f1, f2));
        when(batchTransferRepository.findById(batchId))
                .thenReturn(Optional.of(activeBt))    // before f1
                .thenReturn(Optional.of(cancelledBt)); // re-fetch after f1 done

        batchAsyncSender.sendAsync(batchId, "192.168.1.10", 8080, "test-token");

        verify(transferAsyncSender, times(1))
                .sendBlocking(eq(f1.getTransferId()), any(), anyInt(), any());
        verify(transferAsyncSender, never())
                .sendBlocking(eq(f2.getTransferId()), any(), anyInt(), any());
    }

    /**
     * Batch status guard: FAILED stops the loop
     */
    @Test
    void whenBatchFailed_stopsBeforeNextFile() throws Exception {
        UUID batchId = UUID.randomUUID();

        FileTransfer f1 = makeFile(batchId, "first.txt",  TransferStatus.PENDING);
        FileTransfer f2 = makeFile(batchId, "second.txt", TransferStatus.PENDING);

        BatchTransfer activeBt = makeBatch(batchId, GroupTransferStatus.ACTIVE);
        BatchTransfer failedBt = makeBatch(batchId, GroupTransferStatus.FAILED);

        when(fileTransferRepository.findByBatchTransferId(batchId))
                .thenReturn(List.of(f1, f2));
        when(batchTransferRepository.findById(batchId))
                .thenReturn(Optional.of(activeBt))
                .thenReturn(Optional.of(failedBt));

        batchAsyncSender.sendAsync(batchId, "192.168.1.10", 8080, "test-token");

        verify(transferAsyncSender, times(1))
                .sendBlocking(eq(f1.getTransferId()), any(), anyInt(), any());
        verify(transferAsyncSender, never())
                .sendBlocking(eq(f2.getTransferId()), any(), anyInt(), any());
    }

    /**
     * Batch status guard, PAUSED stops the loop
     */
    @Test
    void whenBatchPaused_stopsBeforeNextFile() throws Exception {
        UUID batchId = UUID.randomUUID();

        FileTransfer f1 = makeFile(batchId, "first.txt",  TransferStatus.PENDING);
        FileTransfer f2 = makeFile(batchId, "second.txt", TransferStatus.PENDING);

        BatchTransfer activeBt = makeBatch(batchId, GroupTransferStatus.ACTIVE);
        BatchTransfer pausedBt = makeBatch(batchId, GroupTransferStatus.PAUSED);

        when(fileTransferRepository.findByBatchTransferId(batchId))
                .thenReturn(List.of(f1, f2));
        when(batchTransferRepository.findById(batchId))
                .thenReturn(Optional.of(activeBt))
                .thenReturn(Optional.of(pausedBt));

        batchAsyncSender.sendAsync(batchId, "192.168.1.10", 8080, "test-token");

        verify(transferAsyncSender, times(1))
                .sendBlocking(eq(f1.getTransferId()), any(), anyInt(), any());
        verify(transferAsyncSender, never())
                .sendBlocking(eq(f2.getTransferId()), any(), anyInt(), any());
    }

    /**
     * File-level failure: one file throws, others still run
     */
    @Test
    void whenOneFileFails_remainingFilesStillRun() throws Exception {
        UUID batchId = UUID.randomUUID();

        FileTransfer f1 = makeFile(batchId, "good.txt",  TransferStatus.PENDING);
        FileTransfer f2 = makeFile(batchId, "bad.txt",   TransferStatus.PENDING);
        FileTransfer f3 = makeFile(batchId, "after.txt", TransferStatus.PENDING);

        BatchTransfer bt = makeBatch(batchId, GroupTransferStatus.ACTIVE);

        doThrow(new RuntimeException("disk full"))
                .when(transferAsyncSender)
                .sendBlocking(eq(f1.getTransferId()), any(), anyInt(), any());

        when(fileTransferRepository.findByBatchTransferId(batchId))
                .thenReturn(List.of(f1, f2, f3))
                .thenReturn(List.of(failedTransfer(f1), done(f2), done(f3)));



        when(batchTransferRepository.findById(batchId))
                .thenReturn(Optional.of(bt));

        batchAsyncSender.sendAsync(batchId, "192.168.1.10", 8080, "test-token");

        // f2 and f3 must still run despite f1 throwing
        verify(transferAsyncSender).sendBlocking(eq(f2.getTransferId()), any(), anyInt(), any());
        verify(transferAsyncSender).sendBlocking(eq(f3.getTransferId()), any(), anyInt(), any());
    }

    /**
     * completeBatchTransfer: mix of completed and failed, status PARTIAL
     */
    @Test
    void whenSomeFilesFailAndSomeComplete_batchMarkedPartial() throws Exception {
        UUID batchId = UUID.randomUUID();

        FileTransfer f1 = makeFile(batchId, "ok.txt",  TransferStatus.PENDING);
        FileTransfer f2 = makeFile(batchId, "bad.txt", TransferStatus.PENDING);

        BatchTransfer bt = makeBatch(batchId, GroupTransferStatus.ACTIVE);

        doThrow(new RuntimeException("connection reset"))
                .when(transferAsyncSender)
                .sendBlocking(eq(f1.getTransferId()), any(), anyInt(), any());

        when(fileTransferRepository.findByBatchTransferId(batchId))
                .thenReturn(List.of(f1, f2))
                .thenReturn(List.of(failedTransfer(f1), done(f2)));

        when(batchTransferRepository.findById(batchId))
                .thenReturn(Optional.of(bt));

        batchAsyncSender.sendAsync(batchId, "192.168.1.10", 8080, "test-token");

        ArgumentCaptor<BatchTransfer> saved = ArgumentCaptor.forClass(BatchTransfer.class);
        verify(batchTransferRepository).save(saved.capture());
        assertEquals(GroupTransferStatus.PARTIAL, saved.getValue().getStatus());
    }

    /**
     * completeBatchTransfer: all completed, status COMPLETED
     */
    @Test
    void whenAllFilesComplete_batchMarkedCompleted() throws Exception {
        UUID batchId = UUID.randomUUID();

        FileTransfer f1 = makeFile(batchId, "a.txt", TransferStatus.PENDING);
        FileTransfer f2 = makeFile(batchId, "b.txt", TransferStatus.PENDING);

        BatchTransfer bt = makeBatch(batchId, GroupTransferStatus.ACTIVE);

        when(fileTransferRepository.findByBatchTransferId(batchId))
                .thenReturn(List.of(f1, f2))
                .thenReturn(List.of(done(f1), done(f2)));
        when(batchTransferRepository.findById(batchId))
                .thenReturn(Optional.of(bt));

        batchAsyncSender.sendAsync(batchId, "192.168.1.10", 8080, "test-token");

        ArgumentCaptor<BatchTransfer> saved = ArgumentCaptor.forClass(BatchTransfer.class);
        verify(batchTransferRepository).save(saved.capture());
        assertEquals(GroupTransferStatus.COMPLETED, saved.getValue().getStatus());
        assertNotNull(saved.getValue().getCompletedAt());
    }

    /**
     * completeBatchTransfer: all failed
     */
    @Test
    void whenAllFilesFail_batchMarkedFailed() throws Exception {
        UUID batchId = UUID.randomUUID();

        FileTransfer f1 = makeFile(batchId, "a.txt", TransferStatus.PENDING);
        FileTransfer f2 = makeFile(batchId, "b.txt", TransferStatus.PENDING);

        BatchTransfer bt = makeBatch(batchId, GroupTransferStatus.ACTIVE);

        when(fileTransferRepository.findByBatchTransferId(batchId))
                .thenReturn(List.of(f1, f2))
                .thenReturn(List.of(failedTransfer(f1), failedTransfer(f2)));
        when(batchTransferRepository.findById(batchId))
                .thenReturn(Optional.of(bt));

        doThrow(new RuntimeException("refused"))
                .when(transferAsyncSender)
                .sendBlocking(any(), any(), anyInt(), any());

        batchAsyncSender.sendAsync(batchId, "192.168.1.10", 8080, "test-token");

        ArgumentCaptor<BatchTransfer> saved = ArgumentCaptor.forClass(BatchTransfer.class);
        verify(batchTransferRepository).save(saved.capture());
        assertEquals(GroupTransferStatus.FAILED, saved.getValue().getStatus());
    }

    /**
     * Empty queue, no files, no sends, batch still finalized
     */
    @Test
    void whenNoEligibleFiles_nothingIsSent() throws Exception {
        UUID batchId = UUID.randomUUID();

        FileTransfer completed = makeFile(batchId, "done.txt",      TransferStatus.COMPLETED);
        FileTransfer cancelled = makeFile(batchId, "cancelled.txt", TransferStatus.CANCELLED);

        BatchTransfer bt = makeBatch(batchId, GroupTransferStatus.ACTIVE);

        when(fileTransferRepository.findByBatchTransferId(batchId))
                .thenReturn(List.of(completed, cancelled))
                .thenReturn(List.of(completed, cancelled));
        when(batchTransferRepository.findById(batchId))
                .thenReturn(Optional.of(bt));

        batchAsyncSender.sendAsync(batchId, "192.168.1.10", 8080, "test-token");

        verifyNoInteractions(transferAsyncSender);
    }

    /**
     * Batch disappears mid-loop (findById returns empty after a file)
     */
    @Test
    void whenBatchDisappearsMidLoop_stopsGracefully() throws Exception {
        UUID batchId = UUID.randomUUID();

        FileTransfer f1 = makeFile(batchId, "first.txt",  TransferStatus.PENDING);
        FileTransfer f2 = makeFile(batchId, "second.txt", TransferStatus.PENDING);

        BatchTransfer bt = makeBatch(batchId, GroupTransferStatus.ACTIVE);

        when(fileTransferRepository.findByBatchTransferId(batchId))
                .thenReturn(List.of(f1, f2));
        when(batchTransferRepository.findById(batchId))
                .thenReturn(Optional.of(bt))   // initial fetch
                .thenReturn(Optional.empty()); // re-fetch after f1 — gone

        batchAsyncSender.sendAsync(batchId, "192.168.1.10", 8080, "test-token");

        // f1 was sent, f2 should not be
        verify(transferAsyncSender, times(1))
                .sendBlocking(eq(f1.getTransferId()), any(), anyInt(), any());
        verify(transferAsyncSender, never())
                .sendBlocking(eq(f2.getTransferId()), any(), anyInt(), any());
    }

    /** Same as done() but with FAILED status, for re-fetch lists in failure scenarios. */
    private FileTransfer failedTransfer(FileTransfer src) {
        FileTransfer ft = makeFile(src.getBatchTransferId(), src.getFileName(), TransferStatus.FAILED);
        ft.setTransferId(src.getTransferId()); // preserve identity
        return ft;
    }
}