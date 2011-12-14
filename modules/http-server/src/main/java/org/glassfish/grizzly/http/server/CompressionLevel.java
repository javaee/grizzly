package org.glassfish.grizzly.http.server;

public enum CompressionLevel {
    OFF,
    ON,
    FORCE;

    /**
     * Set compression level.
     */
    public static CompressionLevel getCompressionLevel(String compression) {
        if ("on".equalsIgnoreCase(compression)) {
            return CompressionLevel.ON;
        } else if ("force".equalsIgnoreCase(compression)) {
            return CompressionLevel.FORCE;
        } else if ("off".equalsIgnoreCase(compression)) {
            return CompressionLevel.OFF;
        }
        throw new IllegalArgumentException();
    }
}