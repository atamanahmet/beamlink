package com.atamanahmet.beamlink.agent.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@Slf4j
public class UpdateService {

    private static final String UPDATE_ZIP   = "update.zip";
    private static final String SIGNAL_FILE  = "update.ready";
    private static final String JAR_NAME     = "beamlink-agent.jar";
    private static final String STATIC_DIR   = "static";

    // Base dir = wherever the jar is running from
    private final Path baseDir = Paths.get("").toAbsolutePath();

    public void applyUpdate(byte[] zipBytes) throws IOException, URISyntaxException {
        log.info("Update received ({} bytes), applying...", zipBytes.length);

        log.info("RUNNING JAR PATH: {}",
                UpdateService.class.getProtectionDomain()
                        .getCodeSource()
                        .getLocation()
                        .toURI());

        // 1. Save zip
        Path zipPath = baseDir.resolve(UPDATE_ZIP);
        Files.write(zipPath, zipBytes);
        log.info("Update zip saved to {}", zipPath);

        // 2. Extract
        extractZip(zipPath);

        // 3. Signal launcher to restart
        Path signal = baseDir.resolve(SIGNAL_FILE);
        Files.writeString(signal, "ready");
        Files.writeString(signal, "ready");
        log.info("Signal file written. Shutting down for update...");
        System.exit(0);
    }

    private void extractZip(Path zipPath) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(
                new BufferedInputStream(Files.newInputStream(zipPath)))) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path target = baseDir.resolve(entry.getName()).normalize();

                // Security check - prevent zip slip
                if (!target.startsWith(baseDir)) {
                    throw new IOException("Bad zip entry: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    // If it's the jar, replace directly
                    // If it's static, wipe folder first on first static entry
                    if (entry.getName().startsWith(STATIC_DIR + "/")
                            && !Files.exists(target.getParent())) {
                        Files.createDirectories(target.getParent());
                    }

                    Files.createDirectories(target.getParent());
                    Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
                    log.info("Extracted: {}", target);
                }
                zis.closeEntry();
            }
        }

        // Wipe old zip
        Files.deleteIfExists(zipPath);
    }
}