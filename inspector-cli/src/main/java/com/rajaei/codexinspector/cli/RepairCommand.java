package com.rajaei.codexinspector.cli;

import com.rajaei.codexinspector.core.service.RepairService;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.util.concurrent.Callable;

@Command(
        name = "repair",
        description = "Repair safe, well-understood Codex state issues.",
        subcommands = RepairCommand.MissingWorkspaces.class
)
public final class RepairCommand implements Runnable {
    @Option(names = "--source", required = true, scope = CommandLine.ScopeType.INHERIT)
    String source;

    @Override
    public void run() {
        new CommandLine(this).usage(System.out);
    }

    @Command(name = "missing-workspaces", description = "Remove workspace roots that no longer exist on disk.")
    static final class MissingWorkspaces implements Callable<Integer> {
        @ParentCommand
        RepairCommand parent;

        @Option(names = "--apply", description = "Write the repair. Without this flag, only prints a dry-run preview.")
        boolean apply;

        @Override
        public Integer call() throws Exception {
            var result = new RepairService().removeMissingWorkspaceRoots(PathOptions.expand(parent.source), apply);
            if (!result.changed()) {
                System.out.println("No missing workspace roots found.");
                return 0;
            }

            System.out.printf("%s missing workspace root(s):%n", result.removedWorkspaceRoots().size());
            result.removedWorkspaceRoots().forEach(path -> System.out.println("  " + path));

            if (apply) {
                System.out.println("Applied repair to: " + result.stateFile());
                System.out.println("Backup written to: " + result.backupFile());
            } else {
                System.out.println("Dry run only. Re-run with --apply to write changes.");
            }
            return 0;
        }
    }
}
