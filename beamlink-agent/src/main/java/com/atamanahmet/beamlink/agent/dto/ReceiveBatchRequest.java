package com.atamanahmet.beamlink.agent.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class ReceiveBatchRequest {

    private UUID batchTransferId;
    private UUID sourceAgentId;
    private int totalFiles;
    private long totalSize;
    private List<FileEntry> files;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class FileEntry {
        private UUID transferId;
        private String fileName;
        private long fileSize;
    }
}