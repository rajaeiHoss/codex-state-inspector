package com.rajaei.codexinspector.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
        name = "codex-state-inspector",
        mixinStandardHelpOptions = true,
        version = "0.1.0-SNAPSHOT",
        description = "Inspect, diff, watch, analyze, repair, and migrate local Codex state.",
        subcommands = {
                SnapshotCommand.class,
                DiffCommand.class,
                TreeCommand.class,
                DoctorCommand.class,
                InspectCommand.class,
                SqliteCommand.class,
                RolloutCommand.class,
                RepairCommand.class,
                MigrateCommand.class,
                ReportCommand.class,
                WatchCommand.class
        }
)
public final class CodexStateInspectorApp implements Runnable {
    public static void main(String[] args) {
        int exitCode = new CommandLine(new CodexStateInspectorApp()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }
}
