package com.rajaei.codexinspector.core.domain;

import java.util.List;

public record DoctorReport(
        boolean healthy,
        List<String> checks,
        List<String> warnings,
        List<String> errors,
        long workspaces,
        long activeWorkspaces,
        long threads,
        long archivedThreads,
        long remoteAccounts,
        long missingRolloutFiles,
        long missingWorkspacePaths
) {
}
