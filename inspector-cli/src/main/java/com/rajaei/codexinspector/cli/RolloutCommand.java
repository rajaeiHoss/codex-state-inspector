package com.rajaei.codexinspector.cli;

import com.rajaei.codexinspector.core.service.DefaultRolloutReader;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

@Command(name = "rollout", description = "Summarize a thread rollout JSONL file.")
public final class RolloutCommand implements Callable<Integer> {
    @Option(names = "--source", required = true)
    String source;

    @Option(names = "--thread-id", required = true)
    String threadId;

    @Override
    public Integer call() throws Exception {
        var summary = new DefaultRolloutReader().summarize(PathOptions.expand(source), threadId);
        System.out.printf("threadId=%s%nrolloutPath=%s%nrecords=%d userMessages=%d assistantMessages=%d toolCalls=%d commands=%d%n",
                summary.threadId(), summary.rolloutPath(), summary.records(), summary.userMessages(), summary.assistantMessages(), summary.toolCalls(), summary.commands());
        System.out.println("filesReferenced=" + summary.filesReferenced());
        System.out.println("firstUserMessage=" + value(summary.firstUserMessage()));
        System.out.println("lastUserMessage=" + value(summary.lastUserMessage()));
        return 0;
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
