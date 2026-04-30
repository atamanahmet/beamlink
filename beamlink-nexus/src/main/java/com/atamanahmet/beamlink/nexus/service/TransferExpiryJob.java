package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.config.NexusConfig;
import com.atamanahmet.beamlink.nexus.domain.FileTransfer;
import com.atamanahmet.beamlink.nexus.domain.enums.TransferStatus;
import com.atamanahmet.beamlink.nexus.repository.FileTransferRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TransferExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(TransferExpiryJob.class);

    private final FileTransferRepository transferRepository;
    private final NexusConfig nexusConfig;

    @Scheduled(fixedDelay = 3_600_000)
    @Transactional
    public void expireStaleTransfers() {
        Instant now = Instant.now();

        List<FileTransfer> stale = new ArrayList<>();
        stale.addAll(transferRepository
                .findByStatusAndExpiresAtBefore(TransferStatus.ACTIVE, now));
        stale.addAll(transferRepository
                .findByStatusAndExpiresAtBefore(TransferStatus.PAUSED, now));

        if (stale.isEmpty()) return;

        for (FileTransfer transfer : stale) {
            transfer.setStatus(TransferStatus.EXPIRED);
            deletePartialFile(transfer.getFileName());
            log.info("Transfer expired: {} ({})", transfer.getFileName(), transfer.getTransferId());
        }

        transferRepository.saveAll(stale);
    }

    private void deletePartialFile(String fileName) {
        try {
            Files.deleteIfExists(
                    Paths.get(nexusConfig.getPartialDirectory()).resolve(fileName + ".part")
            );
        } catch (IOException e) {
            log.warn("Could not delete partial file for: {}", fileName);
        }
    }

}