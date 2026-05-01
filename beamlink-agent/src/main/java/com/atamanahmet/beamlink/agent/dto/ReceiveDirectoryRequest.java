package com.atamanahmet.beamlink.agent.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class ReceiveDirectoryRequest {

    private UUID directoryTransferId;
    private UUID sourceAgentId;
    private String directoryName;
    private int totalFiles;
    private long totalSize;
    private List<String> emptyDirectories;
    // child file registrations, one per file in walk order
    private List<FileEntry> files;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class FileEntry {
        private UUID transferId;
        private String fileName;
        private String relativePath;
        private long fileSize;
    }
}