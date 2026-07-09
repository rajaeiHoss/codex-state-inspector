package com.rajaei.codexinspector.cli;

import com.rajaei.codexinspector.core.service.DefaultSqliteStateReader;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Parameters;

import java.util.concurrent.Callable;

@Command(
        name = "sqlite",
        description = "Explore state_5.sqlite.",
        subcommands = {SqliteCommand.Tables.class, SqliteCommand.Threads.class, SqliteCommand.Thread.class}
)
public final class SqliteCommand implements Runnable {
    @Option(names = "--source", required = true, scope = CommandLine.ScopeType.INHERIT)
    String source;

    final DefaultSqliteStateReader reader = new DefaultSqliteStateReader();

    @Override
    public void run() {
        new picocli.CommandLine(this).usage(System.out);
    }

    @Command(name = "tables", description = "Print table list, schemas, and row counts.")
    static final class Tables implements Callable<Integer> {
        @ParentCommand
        SqliteCommand parent;

        @Override
        public Integer call() throws Exception {
            var reader = new DefaultSqliteStateReader();
            var counts = reader.tableCounts(PathOptions.expand(parent.source));
            var schemas = reader.schemas(PathOptions.expand(parent.source));
            counts.forEach((table, count) -> {
                System.out.printf("%s rows=%d%n", table, count);
                System.out.println("  " + schemas.getOrDefault(table, ""));
            });
            return 0;
        }
    }

    @Command(name = "threads", description = "Print recent threads.")
    static final class Threads implements Callable<Integer> {
        @ParentCommand
        SqliteCommand parent;

        @Option(names = "--limit", defaultValue = "20")
        int limit;

        @Override
        public Integer call() throws Exception {
            var reader = new DefaultSqliteStateReader();
            for (var thread : reader.threads(PathOptions.expand(parent.source), limit)) {
                System.out.printf("%s | %s | cwd=%s | rollout=%s | archived=%s%n", thread.id(), thread.title(), thread.cwd(), thread.rolloutPath(), thread.archived());
            }
            return 0;
        }
    }

    @Command(name = "thread", description = "Print selected thread details.")
    static final class Thread implements Callable<Integer> {
        @ParentCommand
        SqliteCommand parent;

        @Parameters(paramLabel = "<threadId>", arity = "0..1", description = "Thread id")
        String positionalId;

        @Option(names = "--id", description = "Thread id")
        String optionId;

        @Override
        public Integer call() throws Exception {
            String id = optionId == null ? positionalId : optionId;
            if (id == null || id.isBlank()) {
                System.out.println("Missing thread id. Use --id <threadId> or pass it as an argument.");
                return 2;
            }
            var thread = new DefaultSqliteStateReader().thread(PathOptions.expand(parent.source), id);
            if (thread == null) {
                System.out.println("Thread not found: " + id);
                return 1;
            }
            System.out.printf("id=%s%n", thread.id());
            System.out.printf("title=%s%n", thread.title());
            System.out.printf("cwd=%s%n", thread.cwd());
            System.out.printf("rollout_path=%s%n", thread.rolloutPath());
            System.out.printf("git=%s%n", thread.gitInfo());
            System.out.printf("model=%s%n", thread.model());
            System.out.printf("reasoning_effort=%s%n", thread.reasoningEffort());
            System.out.printf("tokens_used=%s%n", thread.tokensUsed());
            System.out.printf("archived=%s%n", thread.archived());
            System.out.printf("created_at_ms=%s%nupdated_at_ms=%s%nrecency_at_ms=%s%n", thread.createdAtMs(), thread.updatedAtMs(), thread.recencyAtMs());
            return 0;
        }
    }
}
