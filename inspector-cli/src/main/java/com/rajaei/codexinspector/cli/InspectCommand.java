package com.rajaei.codexinspector.cli;

import com.rajaei.codexinspector.core.service.CodexStateService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.Comparator;
import java.util.concurrent.Callable;

@Command(name = "inspect", description = "Inspect Codex global JSON and SQLite state.")
public final class InspectCommand implements Callable<Integer> {
    @Option(names = "--source", required = true)
    String source;

    @Override
    public Integer call() throws Exception {
        var state = new CodexStateService().inspect(PathOptions.expand(source));
        System.out.println("Summary");
        System.out.printf("workspaces=%d activeWorkspaces=%d threads=%d archivedThreads=%d remoteAccounts=%d dynamicTools=%d agentJobs=%d spawnEdges=%d%n",
                state.workspaces().size(), state.activeWorkspaceCount(), state.threads().size(), state.archivedThreadCount(),
                state.remoteAccounts().size(), state.tableCounts().getOrDefault("thread_dynamic_tools", 0L),
                state.agentJobs(), state.spawnEdges());

        System.out.println();
        System.out.println("Recent threads");
        state.threads().stream()
                .sorted(Comparator.comparingLong(thread -> -coalesce(thread.recencyAtMs(), thread.updatedAtMs(), thread.createdAtMs(), 0L)))
                .limit(10)
                .forEach(thread -> System.out.printf("%s | %s | cwd=%s | archived=%s%n", thread.id(), value(thread.title()), value(thread.cwd()), thread.archived()));

        System.out.println();
        System.out.println("Workspaces");
        state.workspaces().stream().limit(10).forEach(workspace ->
                System.out.printf("%s%s%s%n", workspace.path(), workspace.active() ? " [active]" : "", workspace.exists() ? "" : " [missing]"));
        return 0;
    }

    private static long coalesce(Long... values) {
        for (Long value : values) {
            if (value != null) {
                return value;
            }
        }
        return 0L;
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
