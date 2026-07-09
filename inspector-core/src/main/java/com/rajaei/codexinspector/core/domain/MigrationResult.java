package com.rajaei.codexinspector.core.domain;

import java.nio.file.Path;
import java.util.List;

public record MigrationResult(
        MigrationPlan plan,
        boolean applied,
        List<Path> backupFiles
) {
}
