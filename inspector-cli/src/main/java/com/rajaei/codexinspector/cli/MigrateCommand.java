package com.rajaei.codexinspector.cli;

import com.rajaei.codexinspector.core.domain.MigrationPlan;
import com.rajaei.codexinspector.core.service.MigrationService;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.util.concurrent.Callable;

@Command(
        name = "migrate",
        mixinStandardHelpOptions = true,
        description = "Plan or apply absolute path prefix migration for Codex state.",
        subcommands = {MigrateCommand.Plan.class, MigrateCommand.Apply.class}
)
public final class MigrateCommand implements Runnable {
    @Option(names = "--source", required = true, scope = CommandLine.ScopeType.INHERIT)
    String source;

    @Option(names = "--from-prefix", required = true, scope = CommandLine.ScopeType.INHERIT)
    String fromPrefix;

    @Option(names = "--to-prefix", required = true, scope = CommandLine.ScopeType.INHERIT)
    String toPrefix;

    @Override
    public void run() {
        new CommandLine(this).usage(System.out);
    }

    @Command(name = "plan", mixinStandardHelpOptions = true, description = "Show migration changes without writing state.")
    static final class Plan implements Callable<Integer> {
        @ParentCommand
        MigrateCommand parent;

        @Option(names = "--limit", defaultValue = "50", description = "Maximum changes to print per section.")
        int limit;

        @Override
        public Integer call() throws Exception {
            var plan = new MigrationService().plan(PathOptions.expand(parent.source), parent.fromPrefix, parent.toPrefix);
            printPlan(plan, limit);
            System.out.println("Dry run only. Use `migrate apply` to write changes.");
            return 0;
        }
    }

    @Command(name = "apply", mixinStandardHelpOptions = true, description = "Apply migration after creating backups.")
    static final class Apply implements Callable<Integer> {
        @ParentCommand
        MigrateCommand parent;

        @Option(names = "--limit", defaultValue = "50", description = "Maximum changes to print per section.")
        int limit;

        @Override
        public Integer call() throws Exception {
            var result = new MigrationService().apply(PathOptions.expand(parent.source), parent.fromPrefix, parent.toPrefix);
            printPlan(result.plan(), limit);
            if (!result.plan().hasChanges()) {
                System.out.println("No migration changes found.");
                return 0;
            }
            System.out.println("Applied migration.");
            System.out.println("Backups:");
            result.backupFiles().forEach(path -> System.out.println("  " + path));
            return 0;
        }
    }

    private static void printPlan(MigrationPlan plan, int limit) {
        System.out.printf("Migration: %s -> %s%n", plan.fromPrefix(), plan.toPrefix());
        System.out.printf("Total changes: %d%n", plan.totalChanges());
        System.out.printf("Global state changes: %d%n", plan.globalStateChanges().size());
        plan.globalStateChanges().stream().limit(limit).forEach(change ->
                System.out.printf("  %s%n    %s%n    -> %s%n", change.location(), change.before(), change.after()));
        if (plan.globalStateChanges().size() > limit) {
            System.out.printf("  ... %d more%n", plan.globalStateChanges().size() - limit);
        }
        System.out.printf("SQLite changes: %d%n", plan.sqliteChanges().size());
        plan.sqliteChanges().stream().limit(limit).forEach(change ->
                System.out.printf("  %s%n    %s%n    -> %s%n", change.location(), change.before(), change.after()));
        if (plan.sqliteChanges().size() > limit) {
            System.out.printf("  ... %d more%n", plan.sqliteChanges().size() - limit);
        }
    }
}
