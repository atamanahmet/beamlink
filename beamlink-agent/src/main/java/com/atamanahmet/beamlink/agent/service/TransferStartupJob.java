package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.config.AgentConfig;
import com.atamanahmet.beamlink.agent.domain.BatchTransfer;
import com.atamanahmet.beamlink.agent.domain.DirectoryTransfer;
import com.atamanahmet.beamlink.agent.domain.FileTransfer;
import com.atamanahmet.beamlink.agent.domain.enums.GroupTransferStatus;
import com.atamanahmet.beamlink.agent.domain.enums.TransferStatus;
import com.atamanahmet.beamlink.agent.repository.BatchTransferRepository;
import com.atamanahmet.beamlink.agent.repository.DirectoryTransferRepository;
import com.atamanahmet.beamlink.agent.repository.FileTransferRepository;
import com.atamanahmet.beamlink.agent.util.PathNormalizer;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TransferStartupJob implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TransferStartupJob.class);

    private final FileTransferRepository transferRepository;
    private final DirectoryTransferRepository directoryTransferRepository;
    private final BatchTransferRepository batchTransferRepository;
    private final DirectorySenderService directorySenderService;
    private final DirectoryAsyncSender directoryAsyncSender;
    private final BatchSenderService batchSenderService;
    private final BatchAsyncSender batchAsyncSender;
    private final AgentService agentService;
    private final AgentConfig agentConfig;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        UUID agentId;
        try {
            agentId = agentService.getAgentId();
        } catch (Exception e) {
            log.debug("Agent not registered yet, skipping transfer resume check");
            return;
        }

        if (agentId == null) {
            log.debug("Agent ID not available yet, skipping transfer resume check");
            return;
        }

        pauseInterruptedStandaloneTransfers(agentId);
        handleInterruptedGroupTransfers(agentId);
    }

    /* Standalone file transfers interrupted mid-send, mark paused, user resumes manually */
    private void pauseInterruptedStandaloneTransfers(UUID agentId) {
        List<FileTransfer> interrupted = new ArrayList<>();
        interrupted.addAll(transferRepository.findByStatus(TransferStatus.ACTIVE));
        interrupted.addAll(transferRepository.findByStatus(TransferStatus.PENDING));

        List<FileTransfer> topause = interrupted.stream()
                .filter(t -> agentId.equals(t.getSourceAgentId()))
                .filter(t -> t.getDirectoryTransferId() == null && t.getBatchTransferId() == null)
                .filter(t -> PathNormalizer.normalize(t.getFilePath()) != null)
                .toList();

        if (topause.isEmpty()) {
            log.debug("No interrupted standalone transfers found on startup");
            return;
        }

        topause.forEach(t -> t.setStatus(TransferStatus.PAUSED));
        transferRepository.saveAll(topause);
        log.info("Paused {} interrupted standalone transfer(s) on startup", topause.size());
    }

    /* Group transfers (directory and batch) interrupted mid-send */
    private void handleInterruptedGroupTransfers(UUID agentId) {
        handleInterruptedDirectoryTransfers(agentId);
        handleInterruptedBatchTransfers(agentId);
    }

    private void handleInterruptedDirectoryTransfers(UUID agentId) {
        List<DirectoryTransfer> interrupted = directoryTransferRepository.findAll()
                .stream()
                .filter(dt -> agentId.equals(dt.getSourceAgentId()))
                .filter(dt -> dt.getStatus() == GroupTransferStatus.ACTIVE)
                .toList();

        if (interrupted.isEmpty()) return;

        for (DirectoryTransfer dt : interrupted) {

            // Pause child file transfers that were mid-flight
            List<FileTransfer> activeChildren = transferRepository
                    .findByDirectoryTransferIdAndStatus(
                            dt.getDirectoryTransferId(), TransferStatus.ACTIVE);
            activeChildren.forEach(ft -> ft.setStatus(TransferStatus.PAUSED));
            transferRepository.saveAll(activeChildren);

            dt.setStatus(GroupTransferStatus.PAUSED);
            directoryTransferRepository.save(dt);

            log.info("Paused interrupted directory transfer on startup: {} ({})",
                    dt.getDirectoryName(), dt.getDirectoryTransferId());

            if (agentConfig.isAutoResumeGroupTransfers()) {
                dt.setStatus(GroupTransferStatus.ACTIVE);
                directoryTransferRepository.save(dt);
                directoryAsyncSender.sendAsync(
                        dt.getDirectoryTransferId(), dt.getTargetIp(), dt.getTargetPort(), null);
                log.info("Auto-resumed directory transfer: {}", dt.getDirectoryTransferId());
            }
        }
    }

    private void handleInterruptedBatchTransfers(UUID agentId) {
        List<BatchTransfer> interrupted = batchTransferRepository.findAll()
                .stream()
                .filter(bt -> agentId.equals(bt.getSourceAgentId()))
                .filter(bt -> bt.getStatus() == GroupTransferStatus.ACTIVE)
                .toList();

        if (interrupted.isEmpty()) return;

        for (BatchTransfer bt : interrupted) {

            // Pause child file transfers that were mid-flight
            List<FileTransfer> activeChildren = transferRepository
                    .findByBatchTransferIdAndStatus(
                            bt.getBatchTransferId(), TransferStatus.ACTIVE);
            activeChildren.forEach(ft -> ft.setStatus(TransferStatus.PAUSED));
            transferRepository.saveAll(activeChildren);

            bt.setStatus(GroupTransferStatus.PAUSED);
            batchTransferRepository.save(bt);

            log.info("Paused interrupted batch transfer on startup: {}",
                    bt.getBatchTransferId());

            if (agentConfig.isAutoResumeGroupTransfers()) {
                bt.setStatus(GroupTransferStatus.ACTIVE);
                batchTransferRepository.save(bt);
                batchAsyncSender.sendAsync(
                        bt.getBatchTransferId(), bt.getTargetIp(), bt.getTargetPort(), null);
                log.info("Auto-resumed batch transfer: {}", bt.getBatchTransferId());
            }
        }
    }
}