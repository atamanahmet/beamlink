package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.config.AgentConfig;
import com.atamanahmet.beamlink.agent.domain.enums.TransferStatus;
import com.atamanahmet.beamlink.agent.dto.InitiateTransferRequest;
import com.atamanahmet.beamlink.agent.exception.FileTransferException;
import com.atamanahmet.beamlink.agent.repository.FileTransferRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransferSenderServiceTest {

    @Mock
    private FileTransferRepository transferRepository;

    @Mock
    private AgentConfig agentConfig;

    @Mock
    private AgentService agentService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private TransferSenderService transferSenderService;

    @Mock
    private TransferAsyncSender asyncSender;

    @TempDir
    Path tempDir;

    /**
     * A path that does not exist on disk must be rejected immediately.
     */
    @Test
    void initiate_rejectsNonExistentFile() {
        InitiateTransferRequest request = buildRequest(
                tempDir.resolve("does-not-exist.txt").toString()
        );

        assertThatThrownBy(() -> transferSenderService.initiate(request))
                .isInstanceOf(FileTransferException.class)
                .hasMessageContaining("File not found");
    }

    /**
     * A directory path must be rejected, only regular files are valid.
     */
    @Test
    void initiate_rejectsDirectory() {
        InitiateTransferRequest request = buildRequest(tempDir.toString());

        assertThatThrownBy(() -> transferSenderService.initiate(request))
                .isInstanceOf(FileTransferException.class)
                .hasMessageContaining("File not found");
    }

    /**
     * A valid file passes validation and proceeds to target registration.
     * Fails transfer with no target
     */
    @Test
    void initiate_acceptsExistingFile_thenFailsOnTargetUnreachable() throws Exception {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "hello");

        when(agentService.getAgentId()).thenReturn(UUID.randomUUID());
        when(agentConfig.getTransferExpiryHours()).thenReturn(24L);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        assertThatThrownBy(() -> transferSenderService.initiate(buildRequest(file.toString())))
                .isInstanceOf(FileTransferException.class)
                .hasMessageContaining("Cannot reach target agent");
    }

    /**
     * PENDING must be persisted before registerOnTarget is called.
     * Ensures the record exists in DB even if the network call fails.
     */
    @Test
    void initiate_savesWithPendingBeforeRegistration() throws Exception {
        Path file = tempDir.resolve("payload.bin");
        Files.write(file, new byte[]{1, 2, 3, 4});

        when(agentService.getAgentId()).thenReturn(UUID.randomUUID());
        when(agentConfig.getTransferExpiryHours()).thenReturn(24L);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        assertThatThrownBy(() -> transferSenderService.initiate(buildRequest(file.toString())))
                .isInstanceOf(FileTransferException.class);

        verify(transferRepository, atLeastOnce()).save(argThat(t ->
                t.getStatus() == TransferStatus.PENDING
        ));
    }

    /**
     * A zero-byte file must not fail on size validation.
     * It should reach target registration like any other valid file.
     */
    @Test
    void initiate_zeroByteFile_passesValidationAndAttemptsTargetRegistration() throws Exception {
        Path file = tempDir.resolve("empty.bin");
        Files.createFile(file);

        when(agentService.getAgentId()).thenReturn(UUID.randomUUID());
        when(agentConfig.getTransferExpiryHours()).thenReturn(24L);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        assertThatThrownBy(() -> transferSenderService.initiate(buildRequest(file.toString())))
                .isInstanceOf(FileTransferException.class)
                .hasMessageContaining("Cannot reach target agent");
    }

    // helper
    private InitiateTransferRequest buildRequest(String filePath) {
        InitiateTransferRequest req = new InitiateTransferRequest();
        req.setFilePath(filePath);
        req.setTargetAgentId(UUID.randomUUID());
        req.setTargetIp("127.0.0.1");
        req.setTargetPort(8081);
        req.setTargetToken("token");
        return req;
    }
}