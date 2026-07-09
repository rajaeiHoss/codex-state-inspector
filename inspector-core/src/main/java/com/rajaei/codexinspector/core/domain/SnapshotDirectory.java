package com.rajaei.codexinspector.core.domain;

import java.util.List;

public record SnapshotDirectory(
        String path,
        String type,
        String sha256,
        List<SnapshotDirectory> directories,
        List<SnapshotFile> files
) {
}
