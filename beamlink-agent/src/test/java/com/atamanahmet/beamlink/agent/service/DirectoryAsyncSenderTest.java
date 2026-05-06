package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.domain.DirectoryTransfer;
import com.atamanahmet.beamlink.agent.domain.FileTransfer;
import com.atamanahmet.beamlink.agent.domain.enums.GroupTransferStatus;
import com.atamanahmet.beamlink.agent.domain.enums.TransferStatus;
import com.atamanahmet.beamlink.agent.repository.DirectoryTransferRepository;
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
class DirectoryAsyncSenderTest {

    @Mock
    private DirectoryTransferRepository directoryTransferRepository;

    @Mock
    private FileTransferRepository fileTransferRepository;

    @Mock
    private TransferAsyncSender transferAsyncSender;

    @InjectMocks
    private DirectoryAsyncSender directoryAsyncSender;

    // Helpers

    private FileTransfer makeFile(UUID directoryId, String name, TransferStatus status) {
        FileTransfer ft = new FileTransfer();
        ft.setTransferId(UUID.randomUUID());
        ft.setFileName(name);
        ft.setDirectoryTransferId(directoryId);
        ft.setStatus(status);
        ft.setFileSize(1024);
        ft.setConfirmedOffset(0);
        ft.setRetryCount(0);
        ft.setMaxRetries(5);
        ft.setCreatedAt(Instant.now());
        return ft;
    }

    private DirectoryTransfer makeDirectory(UUID directoryId, GroupTransferStatus status) {
        DirectoryTransfer dt = new DirectoryTransfer();
        dt.setDirectoryTransferId(directoryId);
        dt.setStatus(status);
        dt.setCreatedAt(Instant.now());
        dt.setTotalFiles(1);
        dt.setTotalSize(1024);
        return dt;
    }

    /** Returns a new COMPLETED file keeping the same transferId, simulates fresh DB read */
    private FileTransfer done(FileTransfer src) {
        FileTransfer ft = makeFile(src.getDirectoryTransferId(), src.getFileName(), TransferStatus.COMPLETED);
        ft.setTransferId(src.getTransferId());
        return ft;
    }

    /** Returns a new FAILED file keeping the same transferId */
    private FileTransfer failed(FileTransfer src) {
        FileTransfer ft = makeFile(src.getDirectoryTransferId(), src.getFileName(), TransferStatus.FAILED);
        ft.setTransferId(src.getTransferId());
        return ft;
    }

    /**
     * Early exit: directory not found before loop starts
     */
    @Test
    void whenDirectoryNotFound_returnsImmediately() {
        UUID directoryId = UUID.randomUUID();

        when(fileTransferRepository.findByDirectoryTransferId(directoryId))
                .thenReturn(List.of());
        when(directoryTransferRepository.findById(directoryId))
                .thenReturn(Optional.empty());

        directoryAsyncSender.sendAsync(directoryId, "192.168.1.10", 8080, "test-token");

        verifyNoInteractions(transferAsyncSender);
    }

    /**
     * Queue filtering: only PENDING, ACTIVE, PAUSED files get queued
     */
    @Test
    void onlyPendingActiveAndPausedFilesAreQueued() throws Exception {
        UUID directoryId = UUID.randomUUID();

        FileTransfer pending   = makeFile(directoryId, "pending.txt",   TransferStatus.PENDING);
        FileTransfer active    = makeFile(directoryId, "active.txt",    TransferStatus.ACTIVE);
        FileTransfer paused    = makeFile(directoryId, "paused.txt",    TransferStatus.PAUSED);
        FileTransfer completed = makeFile(directoryId, "completed.txt", TransferStatus.COMPLETED);
        FileTransfer failedFt  = makeFile(directoryId, "failed.txt",    TransferStatus.FAILED);
        FileTransfer cancelled = makeFile(directoryId, "cancelled.txt", TransferStatus.CANCELLED);

        DirectoryTransfer dt = makeDirectory(directoryId, GroupTransferStatus.ACTIVE);

        when(fileTransferRepository.findByDirectoryTransferId(directoryId))
                .thenReturn(List.of(pending, active, paused, completed, failedFt, cancelled))
                .thenReturn(List.of(done(pending), done(active), done(paused), completed, failedFt, cancelled));
        when(directoryTransferRepository.findById(directoryId))
                .thenReturn(Optional.of(dt));

        directoryAsyncSender.sendAsync(directoryId, "192.168.1.10", 8080, "test-token");

        ArgumentCaptor<UUID> sentIds = ArgumentCaptor.forClass(UUID.class);
        verify(transferAsyncSender, times(3))
                .sendBlocking(sentIds.capture(), eq("192.168.1.10"), eq(8080), eq("test-token"));

        List<UUID> sent = sentIds.getAllValues();
        assertFalse(sent.contains(completed.getTransferId()), "COMPLETED must not be re-sent");
        assertFalse(sent.contains(failedFt.getTransferId()),  "FAILED must not be re-sent");
        assertFalse(sent.contains(cancelled.getTransferId()), "CANCELLED must not be re-sent");
        assertTrue(sent.contains(pending.getTransferId()));
        assertTrue(sent.contains(active.getTransferId()));
        assertTrue(sent.contains(paused.getTransferId()));
    }

    /**
     * Queue ordering: PAUSED -> ACTIVE-> PENDING
     */
    @Test
    void queueOrderIsPausedThenActiveThenPending() throws Exception {
        UUID directoryId = UUID.randomUUID();

        FileTransfer pending = makeFile(directoryId, "pending.txt", TransferStatus.PENDING);
        FileTransfer active  = makeFile(directoryId, "active.txt",  TransferStatus.ACTIVE);
        FileTransfer paused  = makeFile(directoryId, "paused.txt",  TransferStatus.PAUSED);

        DirectoryTransfer dt = makeDirectory(directoryId, GroupTransferStatus.ACTIVE);

        when(fileTransferRepository.findByDirectoryTransferId(directoryId))
                .thenReturn(List.of(pending, active, paused))
                .thenReturn(List.of(done(pending), done(active), done(paused)));
        when(directoryTransferRepository.findById(directoryId))
                .thenReturn(Optional.of(dt));

        directoryAsyncSender.sendAsync(directoryId, "192.168.1.10", 8080, "test-token");

        ArgumentCaptor<UUID> order = ArgumentCaptor.forClass(UUID.class);
        verify(transferAsyncSender, times(3))
                .sendBlocking(order.capture(), any(), anyInt(), any());

        assertEquals(paused.getTransferId(),  order.getAllValues().get(0));
        assertEquals(active.getTransferId(),  order.getAllValues().get(1));
        assertEquals(pending.getTransferId(), order.getAllValues().get(2));
    }

    /**
     * sendBlocking gets the right ip, port, token, transferId
     */
    @Test
    void sendBlockingReceivesCorrectConnectionArgs() throws Exception {
        UUID directoryId = UUID.randomUUID();

        FileTransfer ft = makeFile(directoryId, "data.bin", TransferStatus.PENDING);
        DirectoryTransfer dt = makeDirectory(directoryId, GroupTransferStatus.ACTIVE);

        when(fileTransferRepository.findByDirectoryTransferId(directoryId))
                .thenReturn(List.of(ft))
                .thenReturn(List.of(done(ft)));
        when(directoryTransferRepository.findById(directoryId))
                .thenReturn(Optional.of(dt));

        directoryAsyncSender.sendAsync(directoryId, "192.168.1.10", 8080, "test-token");

        verify(transferAsyncSender).sendBlocking(
                ft.getTransferId(),
                "192.168.1.10",
                8080,
                "test-token"
        );
    }

    /**
     * Files are sent one at a time, strictly in order
     */
    @Test
    void filesAreSentSequentially() throws Exception {
        UUID directoryId = UUID.randomUUID();

        FileTransfer f1 = makeFile(directoryId, "a.txt", TransferStatus.PENDING);
        FileTransfer f2 = makeFile(directoryId, "b.txt", TransferStatus.PENDING);
        FileTransfer f3 = makeFile(directoryId, "c.txt", TransferStatus.PENDING);

        DirectoryTransfer dt = makeDirectory(directoryId, GroupTransferStatus.ACTIVE);

        when(fileTransferRepository.findByDirectoryTransferId(directoryId))
                .thenReturn(List.of(f1, f2, f3))
                .thenReturn(List.of(done(f1), done(f2), done(f3)));
        when(directoryTransferRepository.findById(directoryId))
                .thenReturn(Optional.of(dt));

        directoryAsyncSender.sendAsync(directoryId, "192.168.1.10", 8080, "test-token");

        var inOrder = inOrder(transferAsyncSender);
        inOrder.verify(transferAsyncSender).sendBlocking(eq(f1.getTransferId()), any(), anyInt(), any());
        inOrder.verify(transferAsyncSender).sendBlocking(eq(f2.getTransferId()), any(), anyInt(), any());
        inOrder.verify(transferAsyncSender).sendBlocking(eq(f3.getTransferId()), any(), anyInt(), any());
    }

    /**
     * Large directory, insertion order preserved across all files
     */
    @Test
    void largeDirectory_orderingIsDeterministic() throws Exception {
        UUID directoryId = UUID.randomUUID();
        int count = 20;

        List<FileTransfer> files = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            files.add(makeFile(directoryId, "file-" + i + ".bin", TransferStatus.PENDING));
        }

        DirectoryTransfer dt = makeDirectory(directoryId, GroupTransferStatus.ACTIVE);

        when(directoryTransferRepository.findById(directoryId))
                .thenReturn(Optional.of(dt));
        when(fileTransferRepository.findByDirectoryTransferId(directoryId))
                .thenReturn(files)
                .thenReturn(files.stream().map(this::done).collect(Collectors.toList()));

        directoryAsyncSender.sendAsync(directoryId, "192.168.1.10", 8080, "test-token");

        var inOrder = inOrder(transferAsyncSender);
        for (FileTransfer f : files) {
            inOrder.verify(transferAsyncSender)
                    .sendBlocking(eq(f.getTransferId()), any(), anyInt(), any());
        }
        verify(transferAsyncSender, times(count)).sendBlocking(any(), any(), anyInt(), any());
    }

    /**
     * Two files with the same name are distinct transfers, different UUIDs
     */
    @Test
    void duplicateFileNames_treatedAsDistinctTransfers() throws Exception {
        UUID directoryId = UUID.randomUUID();

        FileTransfer first  = makeFile(directoryId, "readme.txt", TransferStatus.PENDING);
        FileTransfer second = makeFile(directoryId, "readme.txt", TransferStatus.PENDING);

        assertNotEquals(first.getTransferId(), second.getTransferId(),
                "Sanity: factory must assign unique IDs");

        DirectoryTransfer dt = makeDirectory(directoryId, GroupTransferStatus.ACTIVE);

        when(directoryTransferRepository.findById(directoryId))
                .thenReturn(Optional.of(dt));
        when(fileTransferRepository.findByDirectoryTransferId(directoryId))
                .thenReturn(List.of(first, second))
                .thenReturn(List.of(done(first), done(second)));

        directoryAsyncSender.sendAsync(directoryId, "192.168.1.10", 8080, "test-token");

        verify(transferAsyncSender).sendBlocking(eq(first.getTransferId()),  any(), anyInt(), any());
        verify(transferAsyncSender).sendBlocking(eq(second.getTransferId()), any(), anyInt(), any());
        verify(transferAsyncSender, times(2)).sendBlocking(any(), any(), anyInt(), any());
    }

    /**
     * CANCELLED directory stops the loop before sending the next file
     */
    @Test
    void whenDirectoryCancelled_stopsBeforeNextFile() throws Exception {
        UUID directoryId = UUID.randomUUID();

        FileTransfer f1 = makeFile(directoryId, "first.txt",  TransferStatus.PENDING);
        FileTransfer f2 = makeFile(directoryId, "second.txt", TransferStatus.PENDING);

        DirectoryTransfer activeDt    = makeDirectory(directoryId, GroupTransferStatus.ACTIVE);
        DirectoryTransfer cancelledDt = makeDirectory(directoryId, GroupTransferStatus.CANCELLED);

        when(fileTransferRepository.findByDirectoryTransferId(directoryId))
                .thenReturn(List.of(f1, f2));
        when(directoryTransferRepository.findById(directoryId))
                .thenReturn(Optional.of(activeDt))     // before f1
                .thenReturn(Optional.of(cancelledDt)); // re-fetch after f1 done

        directoryAsyncSender.sendAsync(directoryId, "192.168.1.10", 8080, "test-token");

        verify(transferAsyncSender, times(1))
                .sendBlocking(eq(f1.getTransferId()), any(), anyInt(), any());
        verify(transferAsyncSender, never())
                .sendBlocking(eq(f2.getTransferId()), any(), anyInt(), any());
    }

    /**
     * FAILED directory stops the loop before sending the next file
     */
    @Test
    void whenDirectoryFailed_stopsBeforeNextFile() throws Exception {
        UUID directoryId = UUID.randomUUID();

        FileTransfer f1 = makeFile(directoryId, "first.txt",  TransferStatus.PENDING);
        FileTransfer f2 = makeFile(directoryId, "second.txt", TransferStatus.PENDING);

        DirectoryTransfer activeDt = makeDirectory(directoryId, GroupTransferStatus.ACTIVE);
        DirectoryTransfer failedDt = makeDirectory(directoryId, GroupTransferStatus.FAILED);

        when(fileTransferRepository.findByDirectoryTransferId(directoryId))
                .thenReturn(List.of(f1, f2));
        when(directoryTransferRepository.findById(directoryId))
                .thenReturn(Optional.of(activeDt))
                .thenReturn(Optional.of(failedDt));

        directoryAsyncSender.sendAsync(directoryId, "192.168.1.10", 8080, "test-token");

        verify(transferAsyncSender, times(1))
                .sendBlocking(eq(f1.getTransferId()), any(), anyInt(), any());
        verify(transferAsyncSender, never())
                .sendBlocking(eq(f2.getTransferId()), any(), anyInt(), any());
    }

    /**
     * PAUSED directory stops the loop before sending the next file
     */
    @Test
    void whenDirectoryPaused_stopsBeforeNextFile() throws Exception {
        UUID directoryId = UUID.randomUUID();

        FileTransfer f1 = makeFile(directoryId, "first.txt",  TransferStatus.PENDING);
        FileTransfer f2 = makeFile(directoryId, "second.txt", TransferStatus.PENDING);

        DirectoryTransfer activeDt = makeDirectory(directoryId, GroupTransferStatus.ACTIVE);
        DirectoryTransfer pausedDt = makeDirectory(directoryId, GroupTransferStatus.PAUSED);

        when(fileTransferRepository.findByDirectoryTransferId(directoryId))
                .thenReturn(List.of(f1, f2));
        when(directoryTransferRepository.findById(directoryId))
                .thenReturn(Optional.of(activeDt))
                .thenReturn(Optional.of(pausedDt));

        directoryAsyncSender.sendAsync(directoryId, "192.168.1.10", 8080, "test-token");

        verify(transferAsyncSender, times(1))
                .sendBlocking(eq(f1.getTransferId()), any(), anyInt(), any());
        verify(transferAsyncSender, never())
                .sendBlocking(eq(f2.getTransferId()), any(), anyInt(), any());
    }

    /**
     * Directory disappears mid-loop, stops, no crash
     */
    @Test
    void whenDirectoryDisappearsMidLoop_stopsGracefully() throws Exception {
        UUID directoryId = UUID.randomUUID();

        FileTransfer f1 = makeFile(directoryId, "first.txt",  TransferStatus.PENDING);
        FileTransfer f2 = makeFile(directoryId, "second.txt", TransferStatus.PENDING);

        DirectoryTransfer dt = makeDirectory(directoryId, GroupTransferStatus.ACTIVE);

        when(fileTransferRepository.findByDirectoryTransferId(directoryId))
                .thenReturn(List.of(f1, f2));
        when(directoryTransferRepository.findById(directoryId))
                .thenReturn(Optional.of(dt))
                .thenReturn(Optional.empty());

        directoryAsyncSender.sendAsync(directoryId, "192.168.1.10", 8080, "test-token");

        verify(transferAsyncSender, times(1))
                .sendBlocking(eq(f1.getTransferId()), any(), anyInt(), any());
        verify(transferAsyncSender, never())
                .sendBlocking(eq(f2.getTransferId()), any(), anyInt(), any());
    }

    /**
     * One file throws, remaining files still run, failure is isolated
     */
    @Test
    void whenOneFileFails_remainingFilesStillRun() throws Exception {
        UUID directoryId = UUID.randomUUID();

        FileTransfer f1 = makeFile(directoryId, "good.txt",  TransferStatus.PENDING);
        FileTransfer f2 = makeFile(directoryId, "bad.txt",   TransferStatus.PENDING);
        FileTransfer f3 = makeFile(directoryId, "after.txt", TransferStatus.PENDING);

        DirectoryTransfer dt = makeDirectory(directoryId, GroupTransferStatus.ACTIVE);

        doThrow(new RuntimeException("disk full"))
                .when(transferAsyncSender)
                .sendBlocking(eq(f1.getTransferId()), any(), anyInt(), any());

        when(fileTransferRepository.findByDirectoryTransferId(directoryId))
                .thenReturn(List.of(f1, f2, f3))
                .thenReturn(List.of(failed(f1), done(f2), done(f3)));
        when(directoryTransferRepository.findById(directoryId))
                .thenReturn(Optional.of(dt));

        directoryAsyncSender.sendAsync(directoryId, "192.168.1.10", 8080, "test-token");

        verify(transferAsyncSender).sendBlocking(eq(f2.getTransferId()), any(), anyInt(), any());
        verify(transferAsyncSender).sendBlocking(eq(f3.getTransferId()), any(), anyInt(), any());
    }

    /**
     * No eligible files, nothing sent, directory still finalized
     */
    @Test
    void whenNoEligibleFiles_nothingIsSent() throws Exception {
        UUID directoryId = UUID.randomUUID();

        FileTransfer completed = makeFile(directoryId, "done.txt",      TransferStatus.COMPLETED);
        FileTransfer cancelled = makeFile(directoryId, "cancelled.txt", TransferStatus.CANCELLED);

        DirectoryTransfer dt = makeDirectory(directoryId, GroupTransferStatus.ACTIVE);

        when(fileTransferRepository.findByDirectoryTransferId(directoryId))
                .thenReturn(List.of(completed, cancelled))
                .thenReturn(List.of(completed, cancelled));
        when(directoryTransferRepository.findById(directoryId))
                .thenReturn(Optional.of(dt));

        directoryAsyncSender.sendAsync(directoryId, "192.168.1.10", 8080, "test-token");

        verifyNoInteractions(transferAsyncSender);
    }

    /**
     * All files complete, status COMPLETED with a timestamp
     */
    @Test
    void whenAllFilesComplete_directoryMarkedCompleted() throws Exception {
        UUID directoryId = UUID.randomUUID();

        FileTransfer f1 = makeFile(directoryId, "a.txt", TransferStatus.PENDING);
        FileTransfer f2 = makeFile(directoryId, "b.txt", TransferStatus.PENDING);

        DirectoryTransfer dt = makeDirectory(directoryId, GroupTransferStatus.ACTIVE);

        when(fileTransferRepository.findByDirectoryTransferId(directoryId))
                .thenReturn(List.of(f1, f2))
                .thenReturn(List.of(done(f1), done(f2)));
        when(directoryTransferRepository.findById(directoryId))
                .thenReturn(Optional.of(dt));

        directoryAsyncSender.sendAsync(directoryId, "192.168.1.10", 8080, "test-token");

        ArgumentCaptor<DirectoryTransfer> saved = ArgumentCaptor.forClass(DirectoryTransfer.class);
        verify(directoryTransferRepository).save(saved.capture());
        assertEquals(GroupTransferStatus.COMPLETED, saved.getValue().getStatus());
        assertNotNull(saved.getValue().getCompletedAt());
    }

    /**
     * Some files complete, some fail, status PARTIAL
     */
    @Test
    void whenSomeFilesFailAndSomeComplete_directoryMarkedPartial() throws Exception {
        UUID directoryId = UUID.randomUUID();

        FileTransfer f1 = makeFile(directoryId, "ok.txt",  TransferStatus.PENDING);
        FileTransfer f2 = makeFile(directoryId, "bad.txt", TransferStatus.PENDING);

        DirectoryTransfer dt = makeDirectory(directoryId, GroupTransferStatus.ACTIVE);

        doThrow(new RuntimeException("connection reset"))
                .when(transferAsyncSender)
                .sendBlocking(eq(f1.getTransferId()), any(), anyInt(), any());

        when(fileTransferRepository.findByDirectoryTransferId(directoryId))
                .thenReturn(List.of(f1, f2))
                .thenReturn(List.of(failed(f1), done(f2)));
        when(directoryTransferRepository.findById(directoryId))
                .thenReturn(Optional.of(dt));

        directoryAsyncSender.sendAsync(directoryId, "192.168.1.10", 8080, "test-token");

        ArgumentCaptor<DirectoryTransfer> saved = ArgumentCaptor.forClass(DirectoryTransfer.class);
        verify(directoryTransferRepository).save(saved.capture());
        assertEquals(GroupTransferStatus.PARTIAL, saved.getValue().getStatus());
    }

    /**
     * All files fail, status FAILED
     */
    @Test
    void whenAllFilesFail_directoryMarkedFailed() throws Exception {
        UUID directoryId = UUID.randomUUID();

        FileTransfer f1 = makeFile(directoryId, "a.txt", TransferStatus.PENDING);
        FileTransfer f2 = makeFile(directoryId, "b.txt", TransferStatus.PENDING);

        DirectoryTransfer dt = makeDirectory(directoryId, GroupTransferStatus.ACTIVE);

        doThrow(new RuntimeException("refused"))
                .when(transferAsyncSender)
                .sendBlocking(any(), any(), anyInt(), any());

        when(fileTransferRepository.findByDirectoryTransferId(directoryId))
                .thenReturn(List.of(f1, f2))
                .thenReturn(List.of(failed(f1), failed(f2)));
        when(directoryTransferRepository.findById(directoryId))
                .thenReturn(Optional.of(dt));

        directoryAsyncSender.sendAsync(directoryId, "192.168.1.10", 8080, "test-token");

        ArgumentCaptor<DirectoryTransfer> saved = ArgumentCaptor.forClass(DirectoryTransfer.class);
        verify(directoryTransferRepository).save(saved.capture());
        assertEquals(GroupTransferStatus.FAILED, saved.getValue().getStatus());
    }

    /**
     * Paused files resume from where they left off, they go first in the queue
     */
    @Test
    void pausedFilesAreQueuedBeforeActiveAndPending() throws Exception {
        UUID directoryId = UUID.randomUUID();

        FileTransfer pending = makeFile(directoryId, "new.txt",    TransferStatus.PENDING);
        FileTransfer active  = makeFile(directoryId, "ongoing.txt",TransferStatus.ACTIVE);
        FileTransfer paused  = makeFile(directoryId, "resume.txt", TransferStatus.PAUSED);

        DirectoryTransfer dt = makeDirectory(directoryId, GroupTransferStatus.ACTIVE);

        when(fileTransferRepository.findByDirectoryTransferId(directoryId))
                .thenReturn(List.of(pending, active, paused))
                .thenReturn(List.of(done(pending), done(active), done(paused)));
        when(directoryTransferRepository.findById(directoryId))
                .thenReturn(Optional.of(dt));

        directoryAsyncSender.sendAsync(directoryId, "192.168.1.10", 8080, "test-token");

        ArgumentCaptor<UUID> order = ArgumentCaptor.forClass(UUID.class);
        verify(transferAsyncSender, times(3))
                .sendBlocking(order.capture(), any(), anyInt(), any());

        assertEquals(paused.getTransferId(),  order.getAllValues().get(0), "paused must go first");
        assertEquals(active.getTransferId(),  order.getAllValues().get(1), "active second");
        assertEquals(pending.getTransferId(), order.getAllValues().get(2), "pending last");
    }
}