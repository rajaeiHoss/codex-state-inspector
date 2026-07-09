package com.rajaei.codexinspector.core.domain;

import java.nio.file.Path;
import java.util.List;

public record MigrationPlan(
        Path codexHome,
        String fromPrefix,
        String toPrefix,
        List<MigrationChange> globalStateChanges,
        List<MigrationChange> sqliteChanges
) {
    public boolean hasChanges() {
        return !(globalStateChanges.isEmpty() && sqliteChanges.isEmpty());
    }

    public int totalChanges() {
        return globalStateChanges.size() + sqliteChanges.size();
    }
}
