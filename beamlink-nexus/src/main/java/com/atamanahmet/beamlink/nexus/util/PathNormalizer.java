package com.atamanahmet.beamlink.nexus.util;

public final class PathNormalizer {

    private PathNormalizer() {
        // prevent instantiation
    }

    public static String normalize(String path) {
        if (path == null) return null;

        return path.trim()
                .replaceAll("^\"+|\"+$", "")
                .replace("\\", "/");
    }
}