package com.rajaei.codexinspector.core.domain;

import java.util.List;

public record DiffResult(
        List<String> addedFiles,
        List<String> removedFiles,
        List<String> modifiedFiles,
        List<String> changedDirectories
) {
    public boolean hasChanges() {
        return !(addedFiles.isEmpty() && removedFiles.isEmpty() && modifiedFiles.isEmpty() && changedDirectories.isEmpty());
    }
}
