package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.domain.enums.TransferType;
import com.atamanahmet.beamlink.nexus.dto.*;
import com.atamanahmet.beamlink.nexus.exception.FileTransferException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferDispatchService {

    private final TransferSenderService transferSenderService;
    private final BatchSenderService batchSenderService;
    private final DirectorySenderService directorySenderService;
    private final TransferSender asyncSender;

    /** One dispatchId groups all transfers from this single send action */
    public List<DispatchResultItem> dispatch(InitiateSendRequest request) {
        if (request.getPaths() == null || request.getPaths().isEmpty()) {
            throw new FileTransferException("No paths provided", null);
        }

        List<String> filePaths = new ArrayList<>();
        List<String> directoryPaths = new ArrayList<>();
        classifyPaths(request.getPaths(), filePaths, directoryPaths);

        UUID dispatchId = UUID.randomUUID();

        List<DispatchResultItem> results = new ArrayList<>();
        dispatchFiles(request, filePaths, results, dispatchId);
        dispatchDirectories(request, directoryPaths, results, dispatchId);

        log.info("Dispatch complete: {} transfer(s) initiated", results.size());
        return results;
    }

    /** Separates incoming paths into files and directories, fails fast on anything invalid. */
    private void classifyPaths(List<String> paths,
                               List<String> filePaths,
                               List<String> directoryPaths) {
        for (String raw : paths) {
            Path path = Paths.get(raw);
            if (!Files.exists(path)) {
                throw new FileTransferException("Path does not exist: " + raw, null);
            }
            if (Files.isDirectory(path)) {
                directoryPaths.add(raw);
            } else if (Files.isRegularFile(path)) {
                filePaths.add(raw);
            } else {
                throw new FileTransferException("Not a file or directory: " + raw, null);
            }
        }
    }

    /**
     * Single file: SINGLE, multiple files: BATCH.
     * single transfer don't need dispatchId
     */
    private void dispatchFiles(InitiateSendRequest request,
                               List<String> filePaths,
                               List<DispatchResultItem> results,
                               UUID dispatchId) {
        if (filePaths.isEmpty()) return;

        if (filePaths.size() == 1) {
            InitiateTransferRequest req = new InitiateTransferRequest();
            req.setFilePath(filePaths.get(0));
            req.setTargetAgentId(request.getTargetAgentId());
            req.setTargetIp(request.getTargetIp());
            req.setTargetPort(request.getTargetPort());

            UUID id = transferSenderService.initiate(req).getTransferId();
            asyncSender.sendAsync(id, request.getTargetIp(), request.getTargetPort());
            results.add(new DispatchResultItem(id, TransferType.SINGLE, dispatchId));

        } else {
            InitiateBatchTransferRequest req = new InitiateBatchTransferRequest();
            req.setFilePaths(filePaths);
            req.setTargetAgentId(request.getTargetAgentId());
            req.setTargetIp(request.getTargetIp());
            req.setTargetPort(request.getTargetPort());
            req.setDispatchId(dispatchId);

            UUID id = batchSenderService.initiate(req).getBatchTransferId();
            results.add(new DispatchResultItem(id, TransferType.BATCH, dispatchId));
        }
    }

    /** One dispatch per directory. */
    private void dispatchDirectories(InitiateSendRequest request,
                                     List<String> directoryPaths,
                                     List<DispatchResultItem> results,
                                     UUID dispatchId) {
        for (String dirPath : directoryPaths) {
            InitiateDirectoryTransferRequest req = new InitiateDirectoryTransferRequest();
            req.setSourcePath(dirPath);
            req.setTargetAgentId(request.getTargetAgentId());
            req.setTargetIp(request.getTargetIp());
            req.setTargetPort(request.getTargetPort());
            req.setDispatchId(dispatchId);

            UUID id = directorySenderService.initiate(req).getDirectoryTransferId();
            results.add(new DispatchResultItem(id, TransferType.DIRECTORY, dispatchId));
        }
    }
}