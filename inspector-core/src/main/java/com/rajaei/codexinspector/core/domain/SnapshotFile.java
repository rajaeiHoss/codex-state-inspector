package com.rajaei.codexinspector.core.domain;

public record SnapshotFile(
        String path,
        String type,
        long sizeBytes,
        String sha256,
        String symlinkTarget
) {
}
