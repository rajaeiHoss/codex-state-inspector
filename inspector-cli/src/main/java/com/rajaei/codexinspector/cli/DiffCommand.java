package com.rajaei.codexinspector.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.rajaei.codexinspector.core.domain.Snapshot;
import com.rajaei.codexinspector.core.service.DefaultDiffService;
import com.rajaei.codexinspector.core.service.DefaultJsonStateReader;
import com.rajaei.codexinspector.core.service.DefaultSqliteStateReader;
import com.rajaei.codexinspector.core.util.JsonSupport;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@Command(name = "diff", description = "Compare two snapshot JSON files.")
public final class DiffCommand implements Callable<Integer> {
    @Option(names = "--before", required = true)
    String before;

    @Option(names = "--after", required = true)
    String after;

    @Option(names = "--semantic", description = "Print Codex-aware semantic changes.")
    boolean semantic;

    @Override
    public Integer call() throws Exception {
        Path beforePath = PathOptions.expand(before);
        Path afterPath = PathOptions.expand(after);
        Snapshot beforeSnapshot = JsonSupport.mapper().readValue(beforePath.toFile(), Snapshot.class);
        Snapshot afterSnapshot = JsonSupport.mapper().readValue(afterPath.toFile(), Snapshot.class);
        var diff = new DefaultDiffService().diff(beforeSnapshot, afterSnapshot);

        System.out.println("Structural diff");
        print("Added files", diff.addedFiles());
        print("Removed files", diff.removedFiles());
        print("Modified files", diff.modifiedFiles());
        print("Changed directories", diff.changedDirectories());

        if (semantic) {
            printSemantic(beforeSnapshot, afterSnapshot);
        }
        return diff.hasChanges() ? 1 : 0;
    }

    private static void print(String title, java.util.List<String> values) {
        System.out.printf("%s: %d%n", title, values.size());
        values.stream().limit(50).forEach(value -> System.out.println("  " + value));
        if (values.size() > 50) {
            System.out.printf("  ... %d more%n", values.size() - 50);
        }
    }

    private static void printSemantic(Snapshot beforeSnapshot, Snapshot afterSnapshot) throws Exception {
        System.out.println();
        System.out.println("Semantic diff");
        Path beforeRoot = Path.of(beforeSnapshot.rootPath());
        Path afterRoot = Path.of(afterSnapshot.rootPath());
        Path beforeGlobal = beforeRoot.resolve(DefaultJsonStateReader.GLOBAL_STATE_FILE);
        Path afterGlobal = afterRoot.resolve(DefaultJsonStateReader.GLOBAL_STATE_FILE);
        if (Files.isRegularFile(beforeGlobal) && Files.isRegularFile(afterGlobal)) {
            JsonNode beforeJson = JsonSupport.mapper().readTree(beforeGlobal.toFile());
            JsonNode afterJson = JsonSupport.mapper().readTree(afterGlobal.toFile());
            compareSet("workspace roots", DefaultJsonStateReader.textSet(beforeJson.path("electron-saved-workspace-roots")),
                    DefaultJsonStateReader.textSet(afterJson.path("electron-saved-workspace-roots")));
            compareSet("active workspace roots", DefaultJsonStateReader.textSet(beforeJson.path("active-workspace-roots")),
                    DefaultJsonStateReader.textSet(afterJson.path("active-workspace-roots")));
            compareObject("labels", beforeJson.path("electron-workspace-root-labels"), afterJson.path("electron-workspace-root-labels"));
            compareObject("prompt drafts", beforeJson.at("/electron-persisted-atom-state/composer-prompt-drafts-v1"),
                    afterJson.at("/electron-persisted-atom-state/composer-prompt-drafts-v1"));
            compareObject("permissions", beforeJson.path("permissions"), afterJson.path("permissions"));
        } else {
            System.out.println("Global state semantic diff skipped because one or both source files are unavailable.");
        }

        if (beforeRoot.equals(afterRoot)) {
            System.out.println("SQLite semantic diff skipped because both snapshots reference the same live root path. Use copied Codex directories for SQLite row-level comparison.");
        } else if (Files.isRegularFile(beforeRoot.resolve("state_5.sqlite")) && Files.isRegularFile(afterRoot.resolve("state_5.sqlite"))) {
            var reader = new DefaultSqliteStateReader();
            var beforeCounts = reader.tableCounts(beforeRoot);
            var afterCounts = reader.tableCounts(afterRoot);
            System.out.println("SQLite table count changes");
            afterCounts.keySet().stream().sorted().forEach(table -> {
                long beforeCount = beforeCounts.getOrDefault(table, 0L);
                long afterCount = afterCounts.getOrDefault(table, 0L);
                if (beforeCount != afterCount) {
                    System.out.printf("  %s: %d -> %d%n", table, beforeCount, afterCount);
                }
            });
            var beforeThreadIds = reader.threads(beforeRoot, Integer.MAX_VALUE).stream().map(thread -> thread.id()).collect(Collectors.toSet());
            var afterThreadIds = reader.threads(afterRoot, Integer.MAX_VALUE).stream().map(thread -> thread.id()).collect(Collectors.toSet());
            compareSet("SQLite thread ids", beforeThreadIds, afterThreadIds);
        } else {
            System.out.println("SQLite semantic diff skipped because one or both database files are unavailable.");
        }
    }

    private static void compareObject(String label, JsonNode before, JsonNode after) {
        Set<String> beforeKeys = keys(before);
        Set<String> afterKeys = keys(after);
        compareSet(label, beforeKeys, afterKeys);
        beforeKeys.stream()
                .filter(afterKeys::contains)
                .filter(key -> !before.path(key).equals(after.path(key)))
                .sorted()
                .limit(20)
                .forEach(key -> System.out.println("  changed " + key));
    }

    private static Set<String> keys(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Set.of();
        }
        Iterator<String> names = node.fieldNames();
        java.util.HashSet<String> keys = new java.util.HashSet<>();
        while (names.hasNext()) {
            keys.add(names.next());
        }
        return keys;
    }

    private static void compareSet(String label, Set<String> before, Set<String> after) {
        var added = after.stream().filter(value -> !before.contains(value)).sorted().toList();
        var removed = before.stream().filter(value -> !after.contains(value)).sorted().toList();
        System.out.printf("%s added=%d removed=%d%n", label, added.size(), removed.size());
        added.stream().limit(20).forEach(value -> System.out.println("  + " + value));
        removed.stream().limit(20).forEach(value -> System.out.println("  - " + value));
    }
}
