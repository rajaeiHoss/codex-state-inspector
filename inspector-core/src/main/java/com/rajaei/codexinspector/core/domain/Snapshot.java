package com.rajaei.codexinspector.core.domain;

import java.time.Instant;

public record Snapshot(
        String rootPath,
        Instant generatedAt,
        long totalFiles,
        long totalDirectories,
        long totalSizeBytes,
        String rootChecksum,
        SnapshotDirectory tree
) {
}
