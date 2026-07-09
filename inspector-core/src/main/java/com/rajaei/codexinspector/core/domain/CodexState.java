package com.rajaei.codexinspector.core.domain;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record CodexState(
        List<CodexWorkspace> workspaces,
        List<CodexThread> threads,
        List<CodexRemoteAccount> remoteAccounts,
        Map<String, Long> tableCounts,
        Set<String> dynamicToolThreadIds,
        long agentJobs,
        long spawnEdges
) {
    public long archivedThreadCount() {
        return threads.stream().filter(CodexThread::archived).count();
    }

    public long activeWorkspaceCount() {
        return workspaces.stream().filter(CodexWorkspace::active).count();
    }
}
