package com.rajaei.codexinspector.cli;

import com.rajaei.codexinspector.core.service.DoctorService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

@Command(name = "doctor", description = "Run health checks against a Codex state directory.")
public final class DoctorCommand implements Callable<Integer> {
    @Option(names = "--source", required = true)
    String source;

    @Override
    public Integer call() {
        var report = new DoctorService().check(PathOptions.expand(source));
        System.out.println("Codex state doctor");
        report.checks().forEach(System.out::println);
        report.warnings().forEach(warning -> System.out.println("WARN: " + warning));
        report.errors().forEach(error -> System.out.println("ERROR: " + error));
        System.out.printf("workspaces=%d activeWorkspaces=%d threads=%d archivedThreads=%d remoteAccounts=%d missingRolloutFiles=%d missingWorkspacePaths=%d%n",
                report.workspaces(), report.activeWorkspaces(), report.threads(), report.archivedThreads(),
                report.remoteAccounts(), report.missingRolloutFiles(), report.missingWorkspacePaths());
        return report.healthy() ? 0 : 1;
    }
}
