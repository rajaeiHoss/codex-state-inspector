package com.rajaei.codexinspector.core.domain;

import java.nio.file.Path;
import java.util.List;

public record RepairResult(
        boolean applied,
        Path stateFile,
        Path backupFile,
        List<String> removedWorkspaceRoots
) {
    public boolean changed() {
        return !removedWorkspaceRoots.isEmpty();
    }
}
