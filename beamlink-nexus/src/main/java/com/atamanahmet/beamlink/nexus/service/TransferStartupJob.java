package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.domain.FileTransfer;
import com.atamanahmet.beamlink.nexus.domain.enums.TransferStatus;
import com.atamanahmet.beamlink.nexus.repository.FileTransferRepository;
import com.atamanahmet.beamlink.nexus.util.PathNormalizer;
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
    private final AgentService agentService;

    private static final UUID NEXUS_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        UUID agentId = NEXUS_ID;

        List<FileTransfer> interrupted = new ArrayList<>();
        interrupted.addAll(transferRepository.findByStatus(TransferStatus.ACTIVE));
        interrupted.addAll(transferRepository.findByStatus(TransferStatus.PENDING));

        // Only pause transfers where THIS agent is the sender
        // Sender will reconnect and resume
        List<FileTransfer> pausedTransfers = interrupted.stream()
                .filter(t -> agentId.equals(t.getSourceAgentId()))
                .filter(t -> PathNormalizer.normalize(t.getFilePath()) != null)
                .toList();

        if (pausedTransfers.isEmpty()) {
            log.debug("No interrupted transfers found on startup");
            return;
        }

        for (FileTransfer t : pausedTransfers) {
            t.setStatus(TransferStatus.PAUSED);
            log.info("Paused interrupted transfer on startup: {} ({})",
                    t.getFileName(), t.getTransferId());
        }

        transferRepository.saveAll(pausedTransfers);
        log.info("Paused {} interrupted transfer(s) on startup", pausedTransfers.size());
    }
}