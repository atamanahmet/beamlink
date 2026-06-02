package com.atamanahmet.beamlink.agent.constants;

public final class TransferConstants {

    private TransferConstants() {}

    public static final int CHUNK_SIZE = 524288;    // 512KB
    public static final int WINDOW_SIZE = 4;
    public static final long RETRY_DELAY_MS = 2000L;
}